# device-management Specification

## Purpose
TBD - created by archiving change add-protocol. Update Purpose after archive.
## Requirements
### Requirement: 设备注册

系统 **SHALL** 能够注册新设备并生成对应的 DeviceHeader。

#### Scenario: 注册新设备

**Given**:
- 设备 Device_1 请求注册
- 当前活跃设备数量为 2

**When**:
- 调用 `register_device(Device_1)`

**Then**:
- Device_1 被添加到 `device_headers`
- 为 Device_1 生成新的 DeviceHeader
- Header 包含正确的 `device_id`, `epoch`, `public_key`, `encrypted_dek`
- 返回 `Ok(DeviceHeader)`

#### Scenario: 重复注册设备

**Given**:
- 设备 Device_1 已存在

**When**:
- 尝试注册 Device_1

**Then**:
- 返回 `Err(ProtocolError::DeviceAlreadyRegistered(Device_1))`
- `device_headers` 中 Device_1 的 Header 保持不变

---

### Requirement: 设备撤销

系统 **SHALL** 能够撤销设备并清理对应的 DeviceHeader。

#### Scenario: 撤销活跃设备

**Given**:
- 设备 Device_1 处于活跃状态
- Device_1 有一个有效的 DeviceHeader

**When**:
- 调用 `revoke_device(Device_1)`

**Then**:
- Device_1 的 DeviceHeader 被移除
- 状态机转换到 `Revoked` 状态（如果是最后一个设备）
- 返回 `Ok(())`

#### Scenario: 撤销后设备无法解密

**Given**:
- 设备 Device_1 已被撤销
- 当前纪元为 5

**When**:
- Device_1 尝试解密新数据

**Then**:
- 解密操作被拒绝
- 返回 `Err(ProtocolError::DeviceRevoked(Device_1))`

---

### Requirement: Header 完备性验证

系统 **MUST** 确保每个活跃设备有且仅有一个有效的 DeviceHeader。

#### Scenario: 验证设备有 Header

**Given**:
- 设备 Device_1 有一个有效的 DeviceHeader

**When**:
- 调用 `validate_header_completeness(Device_1)`

**Then**:
- 返回 `Ok(())`

#### Scenario: 检测缺失的 Header

**Given**:
- 设备 Device_1 的 Header 被移除

**When**:
- 调用 `validate_header_completeness(Device_1)`

**Then**:
- 返回 `Err(InvariantViolation::MissingHeader(Device_1))`
- 状态机可能转换到 `Degraded` 状态

#### Scenario: 检测重复的 Header

**Given**:
- 设备 Device_1 有两个有效的 DeviceHeader

**When**:
- 调用 `validate_header_completeness(Device_1)`

**Then**:
- 返回 `Err(InvariantViolation::MultipleHeaders(Device_1, 2))`
- 状态机可能转换到 `Degraded` 状态

---

### Requirement: 设备状态管理

系统 **SHALL** 跟踪设备的状态（Active, Revoked, Degraded）。

#### Scenario: 设备状态转换

**Given**:
- 设备 Device_1 处于 Active 状态

**When**:
- 调用 `set_device_status(Device_1, DeviceStatus::Revoked)`

**Then**:
- Device_1 的状态更新为 `Revoked`
- 返回 `Ok(())`

#### Scenario: 查询设备状态

**Given**:
- 设备 Device_1 处于 Revoked 状态

**When**:
- 调用 `get_device_status(Device_1)`

**Then**:
- 返回 `Ok(DeviceStatus::Revoked)`

---

### Requirement: 设备数量限制

系统 **SHALL** 限制活跃设备的数量以防止性能下降。

#### Scenario: 达到设备数量限制

**Given**:
- 当前活跃设备数量为 10（假设限制为 10）
- 设备 Device_11 请求注册

**When**:
- 调用 `register_device(Device_11)`

**Then**:
- 返回 `Err(ProtocolError::TooManyActiveDevices { max: 10, current: 10 })`
- Device_11 未被注册

---

