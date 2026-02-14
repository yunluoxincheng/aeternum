# Spec: 恢复协议与否决权

**Capability ID**: `recovery-protocol`
**Version**: 1.0
**Status**: Proposed

---

## ADDED Requirements

### Requirement: 恢复窗口初始化

系统 **SHALL** 能够初始化一个 48 小时的恢复窗口，跟踪发起者、时间范围和否决信号。

#### Scenario: 初始化恢复窗口

**Given**:
- 用户在 AUTHORIZED 设备上发起恢复请求
- 当前时间为 `2025-02-14T10:00:00Z`
- 不存在活跃的恢复窗口

**When**:
- 调用 `initiate_recovery_window(request_id, initiator_role)`

**Then**:
- 创建新的 `RecoveryWindow` 实例
- `start_time` 设为 `2025-02-14T10:00:00Z`
- `end_time` 设为 `2025-02-16T10:00:00Z` (+48h)
- `initiator_role` 设为 `AUTHORIZED`
- `vetoes` 初始化为空列表
- 状态机转换到 `RecoveryInitiated(RecoveryContext { ... })`

---

### Requirement: 否决信号处理

系统 **SHALL** 能够接收和记录否决信号，并立即终止恢复流程。

#### Scenario: 活跃设备发送否决信号

**Given**:
- 恢复窗口处于活跃状态
- Device_2 发送否决信号

**When**:
- 调用 `add_veto(request_id, device_id, veto_message)`

**Then**:
- 否决信号被添加到 `RecoveryWindow.vetoes`
- 系统立即调用 `terminate_recovery(request_id)`
- 状态机从 `RecoveryInitiated` 转换到 `Idle`
- 恢复请求被标记为 `REJECTED`

#### Scenario: 48h 窗口过期后收到否决

**Given**:
- 恢复窗口已过期 (`end_time` < `now`)
- Device_3 尝试发送否决信号

**When**:
- 调用 `add_veto(request_id, device_id, veto_message)`

**Then**:
- 否决信号被拒绝（窗口已过期）
- 返回 `Err(InvariantViolation::VetoWindowExpired)`
- 恢复流程继续（可完成）

---

### Requirement: 否决权优先强制执行

系统 **MUST** 在任何活跃设备发送否决信号时立即终止恢复流程。

#### Scenario: 窗口内收到否决信号

**Given**:
- 恢复窗口处于活跃状态
- `vetoes` 列表为空
- 当前时间在窗口内

**When**:
- 调用 `check_veto_supremacy(request_id)`

**Then**:
- 返回 `Ok(())`（允许恢复继续）

#### Scenario: 窗口内收到否决信号后检查

**Given**:
- 恢复窗口处于活跃状态
- `vetoes` 列表包含 1 个否决信号

**When**:
- 调用 `check_veto_supremacy(request_id)`

**Then**:
- 返回 `Err(InvariantViolation::VetoSupremacy)`
- 恢复流程被终止
- 状态机从 `RecoveryInitiated` 转换到 `Idle`

---

### Requirement: 48h 窗口超时处理

系统 **SHALL** 在窗口过期后允许恢复流程完成。

#### Scenario: 窗口超时后允许恢复

**Given**:
- 恢复窗口处于活跃状态
- `end_time` 为 `2025-02-16T10:00:00Z`
- 当前时间为 `2025-02-16T10:01:00Z`
- `vetoes` 列表为空

**When**:
- 调用 `check_veto_supremacy(request_id)`

**Then**:
- 返回 `Ok(())`（窗口已过期，无否决）
- 恢复流程可完成
- 状态机从 `RecoveryInitiated` 转换到 `Idle`

#### Scenario: 窗口未过期时拒绝恢复

**Given**:
- 恢复窗口处于活跃状态
- `end_time` 为 `2025-02-16T10:00:00Z`
- 当前时间为 `2025-02-15T10:00:00Z`
- `vetoes` 列表为空

**When**:
- 调用 `check_veto_supremacy(request_id)`

**Then**:
- 返回 `Err(InvariantViolation::VetoWindowActive)`
- 恢复流程必须等待
- 状态机保持 `RecoveryInitiated` 状态

---

### Requirement: 时间漂移容错

系统 **SHALL** 允许 ±5 分钟的时间漂移容错。

#### Scenario: 时间漂移容错（早 5 分钟）

**Given**:
- 恢复窗口 `end_time` 为 `2025-02-16T10:00:00Z`
- 当前时间为 `2025-02-16T09:55:00Z`（早 5 分钟）
- `vetoes` 列表为空

**When**:
- 调用 `check_veto_supremacy(request_id)`

**Then**:
- 返回 `Ok(())`（时间在容差内）
- 恢复流程可完成

#### Scenario: 时间漂移容错（晚 5 分钟）

**Given**:
- 恢复窗口 `end_time` 为 `2025-02-16T10:00:00Z`
- 当前时间为 `2025-02-16T10:05:00Z`（晚 5 分钟）
- `vetoes` 列表为空

**When**:
- 调用 `check_veto_supremacy(request_id)`

**Then**:
- 返回 `Ok(())`（时间在容差内）
- 恢复流程可完成

#### Scenario: 时间漂移超出容差

**Given**:
- 恢复窗口 `end_time` 为 `2025-02-16T10:00:00Z`
- 当前时间为 `2025-02-16T10:06:00Z`（晚 6 分钟）
- `vetoes` 列表为空

**When**:
- 调用 `check_veto_supremacy(request_id)`

**Then**:
- 返回 `Err(InvariantViolation::VetoWindowExpired)`
- 恢复流程仍可完成（窗口已过期）

---

### Requirement: 恢复窗口清理

系统 **SHALL** 在恢复完成或被否决后清理相关资源。

#### Scenario: 恢复完成后清理

**Given**:
- 恢复窗口已过期
- 恢复流程已完成

**When**:
- 调用 `cleanup_recovery_window(request_id)`

**Then**:
- `RecoveryWindow` 从 `recovery_windows` 移除
- 相关的 `VetoMessage` 列表被清空
- 资源被释放

#### Scenario: 恢复被否决后清理

**Given**:
- 恢复窗口处于活跃状态
- 收到否决信号
- 恢复流程已被终止

**When**:
- 调用 `cleanup_recovery_window(request_id)`

**Then**:
- `RecoveryWindow` 从 `recovery_windows` 移除
- 相关的 `VetoMessage` 列表被清空
- 资源被释放

---

## MODIFIED Requirements

无修改需求。

---

## REMOVED Requirements

无移除需求。

---

## Cross-References

- 依赖: `pqrr-state-machine` - 状态机转换
- 依赖: `models::device::Role` - 角色验证
- 依赖: `sync::wire::VetoMessage` - 否决信号格式
- 被依赖: `epoch-upgrade` - Root Rotation 使用恢复协议

---

**Spec 版本**: 1.0
**最后更新**: 2025-02-14
