# Spec: 纪元升级协调

**Capability ID**: `epoch-upgrade`
**Version**: 1.0
**Status**: Proposed

---

## ADDED Requirements

### Requirement: Epoch 升级准备

系统 **SHALL** 能够准备新的 Epoch 并生成新的 DEK。

#### Scenario: 准备新 Epoch

**Given**:
- 当前纪元为 5
- 新纪元为 6

**When**:
- 调用 `prepare_epoch_upgrade(6)`

**Then**:
- 生成新的 DEK_6
- 验证 `new_epoch (6) > current_epoch (5)`
- 返回 `AupPreparation { new_dek, old_epoch: 5, new_epoch: 6 }`

#### Scenario: 拒绝 Epoch 回滚

**Given**:
- 当前纪元为 5
- 尝试准备纪元 4

**When**:
- 调用 `prepare_epoch_upgrade(4)`

**Then**:
- 返回 `Err(InvariantViolation::EpochRegression { current: 5, attempted: 4 })`
- 未生成新 DEK

---

### Requirement: 影子写入准备

系统 **SHALL** 创建临时文件并准备写入新的 Vault Blob。

#### Scenario: 创建影子文件

**Given**:
- Vault 当前位于 `vault.db`
- 新的 Vault Blob 已准备

**When**:
- 调用 `aup_shadow_write(new_vault_blob)`

**Then**:
- 创建临时文件 `vault.tmp`
- 新的 Vault Blob 被写入临时文件
- 调用 `fsync` 确保数据落盘
- 返回 `Ok(())`

#### Scenario: 影子写入失败

**Given**:
- 磁盘空间不足

**When**:
- 调用 `aup_shadow_write(new_vault_blob)`

**Then**:
- 返回 `Err(ProtocolError::ShadowWriteFailed)`
- 原始 `vault.db` 保持不变
- 状态机保持 `Rekeying` 状态

---

### Requirement: 原子提交

系统 **MUST** 原子地替换旧 Vault 为新 Vault。

#### Scenario: 原子替换成功

**Given**:
- 临时文件 `vault.tmp` 已准备好
- 旧 Vault 为 `vault.db`

**When**:
- 调用 `aup_atomic_commit()`

**Then**:
- 调用 `rename("vault.tmp", "vault.db")`（POSIX 原子操作）
- 返回 `Ok(())`
- 新 Vault 生效

#### Scenario: 原子替换失败

**Given**:
- 临时文件 `vault.tmp` 不存在

**When**:
- 调用 `aup_atomic_commit()`

**Then**:
- 返回 `Err(ProtocolError::AtomicCommitFailed)`
- 原始 `vault.db` 保持不变

---

### Requirement: 所有设备 Header 更新

系统 **SHALL** 在 Epoch 升级时更新所有活跃设备的 DeviceHeader。

#### Scenario: 更新单个设备 Header

**Given**:
- 设备 Device_1 存在
- 新纪元为 6

**When**:
- 调用 `update_device_header(Device_1, new_epoch_6)`

**Then**:
- 使用新 DEK_6 封装 VK
- 为 Device_1 生成新的 DeviceHeader
- Header 的 `epoch` 字段更新为 6
- 返回 `Ok(())`

#### Scenario: 批量更新所有设备 Header

**Given**:
- 存在 3 个活跃设备
- 新纪元为 6

**When**:
- 调用 `update_all_device_headers(new_epoch_6)`

**Then**:
- 所有 3 个设备的 Header 被更新
- 每个 Header 的 `epoch` 字段更新为 6
- 所有 Header 使用相同的 DEK_6
- 返回 `Ok(())`

#### Scenario: 部分设备更新失败

**Given**:
- 存在 3 个活跃设备
- Device_2 的 Header 更新失败

**When**:
- 调用 `update_all_device_headers(new_epoch_6)`

**Then**:
- 返回 `Err(ProtocolError::HeaderUpdateFailed(Device_2))`
- 已更新的 Header 被回滚
- 状态机保持 `Rekeying` 状态

---

### Requirement: 崩溃恢复

系统 **SHALL** 在启动时检测并修复未完成的 Epoch 升级。

#### Scenario: 检测未完成的升级

**Given**:
- 元数据纪元为 5
- Vault Blob Header 纪元为 6
- 状态机处于 `Rekeying` 状态

**When**:
- 调用 `recover_epoch_upgrade()`

**Then**:
- 检测到纪元不匹配（5 != 6）
- 自动完成 Epoch 升级
- 元数据纪元更新为 6
- 状态机转换到 `Idle` 状态
- 返回 `Ok(())`

#### Scenario: 升级已完成

**Given**:
- 元数据纪元为 6
- Vault Blob Header 纪元为 6
- 状态机处于 `Idle` 状态

**When**:
- 调用 `recover_epoch_upgrade()`

**Then**:
- 纪元匹配（6 == 6）
- 无需恢复操作
- 返回 `Ok(())`

---

### Requirement: 角色权限验证

系统 **MUST** 防止 RECOVERY 角色执行 σ_rotate 操作（Invariant #3）。

#### Scenario: AUTHORIZED 角色执行 σ_rotate

**Given**:
- 用户角色为 `AUTHORIZED`

**When**:
- 调用 `execute_rotation(Role::AUTHORIZED)`

**Then**:
- 允许执行 σ_rotate
- 返回 `Ok(())`

#### Scenario: RECOVERY 角色执行 σ_rotate

**Given**:
- 用户角色为 `RECOVERY`

**When**:
- 调用 `execute_rotation(Role::RECOVERY)`

**Then**:
- 返回 `Err(InvariantViolation::CausalEntropyBarrier)`
- σ_rotate 未被执行

---

### Requirement: 权限提升检测

系统 **SHALL** 检测并阻止权限提升尝试。

#### Scenario: 检测权限提升

**Given**:
- 当前角色为 `RECOVERY`
- 尝试提升到 `AUTHORIZED`

**When**:
- 调用 `attempt_privilege_escalation()`

**Then**:
- 返回 `Err(ProtocolError::PrivilegeEscalationDetected)`
- 角色保持 `RECOVERY`

---

## MODIFIED Requirements

无修改需求。

---

## REMOVED Requirements

无移除需求。

---

## Cross-References

- 依赖: `pqrr-state-machine` - 状态机管理
- 依赖: `storage::aug` - 原子纪元升级协议函数
- 依赖: `models::device::Role` - 角色类型定义
- 依赖: `models::epoch::CryptoEpoch` - Epoch 类型定义
- 依赖: `crypto::aead::AeadCipher` - VK 重加密
- 被依赖: `device-management` - Epoch 升级需要更新设备 Header

---

**Spec 版本**: 1.0
**最后更新**: 2025-02-14
