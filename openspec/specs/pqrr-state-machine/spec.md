# pqrr-state-machine Specification

## Purpose
TBD - created by archiving change add-protocol. Update Purpose after archive.
## Requirements
### Requirement: 状态机初始化

状态机 **SHALL** 能够从当前纪元和设备 Header 集合初始化。

#### Scenario: 从已知状态初始化

**Given**:
- 当前纪元版本为 1
- 存在 3 个活跃设备 (Device_1, Device_2, Device_0)

**When**:
- 调用 `PqrrStateMachine::new(epoch, device_headers)`

**Then**:
- 状态机进入 `Idle` 状态
- `current_epoch()` 返回 1
- `state()` 返回 `ProtocolState::Idle`
- `device_headers` 包含所有 3 个设备的 Header

---

### Requirement: 纪元单调性强制执行

状态机 **MUST** 拒绝任何尝试回滚到旧纪元的操作。

#### Scenario: 拒绝纪元回滚

**Given**:
- 当前纪元版本为 5
- 收到一个纪元为 3 的 Header

**When**:
- 调用 `apply_epoch_upgrade(header_with_epoch_3)`

**Then**:
- 返回 `Err(InvariantViolation::EpochRegression { current: 5, attempted: 3 })`
- 状态机保持 `Idle` 状态
- `current_epoch()` 仍返回 5

#### Scenario: 接受纪元升级

**Given**:
- 当前纪元版本为 5
- 收到一个纪元为 6 的 Header

**When**:
- 调用 `apply_epoch_upgrade(header_with_epoch_6)`

**Then**:
- 状态机转换到 `Rekeying(RekeyingContext { old_epoch: 5, new_epoch: 6, ... })`
- `current_epoch()` 返回 6
- 所有待处理设备的队列已初始化

---

### Requirement: Header 完备性验证

状态机 **MUST** 确保每个活跃设备有且仅有一个有效 Header。

#### Scenario: 检测缺失的 Header

**Given**:
- 设备 Device_1 在 `device_headers` 中存在

**When**:
- Device_1 的 Header 被移除

**Then**:
- `validate_header_completeness(Device_1)` 返回 `Err(InvariantViolation::MissingHeader(Device_1))`
- 状态机转换到 `Degraded` 状态

#### Scenario: 检测重复的 Header

**Given**:
- 设备 Device_1 有一个有效 Header

**When**:
- 尝试为 Device_1 添加第二个 Header

**Then**:
- `validate_header_completeness(Device_1)` 返回 `Err(InvariantViolation::MultipleHeaders(Device_1, 2))`
- 状态机保持 `Idle` 状态
- 第二个 Header 未被添加

---

### Requirement: 状态转换合法性

状态机 **SHALL** 只允许合法的状态转换。

#### Scenario: Idle → Rekeying (合法)

**Given**:
- 状态机处于 `Idle` 状态
- 收到一个新 Header 且 epoch > current_epoch

**When**:
- 调用 `transition_to(ProtocolState::Rekeying(...))`

**Then**:
- 转换成功
- 状态机进入 `Rekeying` 状态

#### Scenario: Rekeying → Degraded (合法)

**Given**:
- 状态机处于 `Rekeying` 状态
- 完整性验证失败

**When**:
- 调用 `transition_to(ProtocolState::Degraded(...))`

**Then**:
- 转换成功
- 状态机进入 `Degraded` 状态

#### Scenario: Revoked → Idle (非法)

**Given**:
- 状态机处于 `Revoked` 状态

**When**:
- 尝试转换到 `Idle` 状态

**Then**:
- 转换失败并返回 `Err(ProtocolError::InvalidTransition { from: Revoked, to: Idle })`
- 状态机保持 `Revoked` 状态

---

### Requirement: Rekeying 上下文管理

Rekeying 状态 **SHALL** 跟踪升级进度和待处理设备。

#### Scenario: Rekeying 上下文初始化

**Given**:
- 状态机从 `Idle` 转换到 `Rekeying`
- 存在 5 个活跃设备

**When**:
- 转换发生

**Then**:
- `RekeyingContext` 包含:
  - `old_epoch`: 原纪元版本
  - `new_epoch`: 新纪元版本
  - `pending_devices`: 所有 5 个设备的 ID
  - `completed_devices`: 空集合
  - `temp_vault_path`: 临时文件路径

#### Scenario: Rekeying 设备完成跟踪

**Given**:
- 状态机处于 `Rekeying` 状态
- `pending_devices` 有 5 个设备

**When**:
- 3 个设备的 Header 已更新完成

**Then**:
- `completed_devices` 包含 3 个设备 ID
- `pending_devices` 包含 2 个设备 ID
- 状态机保持 `Rekeying` 状态

#### Scenario: Rekeying 完成条件

**Given**:
- 状态机处于 `Rekeying` 状态
- `pending_devices` 为空

**When**:
- 调用 `return_to_idle()`

**Then**:
- 状态机转换到 `Idle` 状态
- `current_epoch()` 等于 `new_epoch`

---

### Requirement: Degraded 状态行为

Degraded 状态 **SHALL** 限制设备功能并等待恢复或撤销。

#### Scenario: Degraded 功能限制

**Given**:
- 状态机处于 `Degraded` 状态

**When**:
- 尝试执行解密操作

**Then**:
- 解密操作被拒绝
- 返回 `Err(ProtocolError::DegradedMode)`

**When**:
- 尝试导出数据

**Then**:
- 导出操作被拒绝
- 返回 `Err(ProtocolError::DegradedMode)`

#### Scenario: Degraded → Idle (完整性恢复)

**Given**:
- 状态机处于 `Degraded` 状态
- 完整性验证通过

**When**:
- 调用 `transition_to(ProtocolState::Idle)`

**Then**:
- 转换成功
- 状态机进入 `Idle` 状态
- 所有功能恢复正常

#### Scenario: Degraded → Revoked (持续失败)

**Given**:
- 状态机处于 `Degraded` 状态
- 完整性验证持续失败

**When**:
- 收到撤销指令

**Then**:
- 转换成功
- 状态机进入 `Revoked` 状态

---

### Requirement: Revoked 终态

Revoked 状态 **MUST** 是终态,不允许任何转换出。

#### Scenario: Revoked 终态验证

**Given**:
- 状态机处于 `Revoked` 状态

**When**:
- 尝试任何状态转换

**Then**:
- 所有转换尝试被拒绝
- 状态机保持 `Revoked` 状态

**When**:
- 尝试执行任何操作

**Then**:
- 所有操作被拒绝
- 返回 `Err(ProtocolError::RevokedState)`

---

