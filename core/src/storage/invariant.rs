//! # Invariant Enforcement (四大数学不变量)
//!
//! 实现形式化数学不变量的强制执行，确保 Aeternum 系统永不违反安全约束。
//!
//! ## 四大数学不变量
//!
//! - **Invariant #1 — 纪元单调性**: 所有设备的 epoch 必须严格单调递增，禁止回滚
//! - **Invariant #2 — Header 完备性**: 每个活跃设备必须且仅能通过一个正确的 Header 获取 DEK
//! - **Invariant #3 — 因果熵障**: 解密权限 ≠ 管理权限（RECOVERY 角色不能执行 σ_rotate）
//! - **Invariant #4 — 否决权优先**: 48h 窗口内任何活跃设备的 Veto 信号必须立即终止恢复流程
//!
//! ## 违规处理
//!
//! 检测到不变量违规时：
//! 1. 触发 `FatalError::InvariantViolationTriggered`
//! 2. 立即停止所有 DEK 解密操作
//! 3. 清除内存中的所有明文密钥
//! 4. 标记同步状态为"异常分叉"
//! 5. 强制弹出高优先级风险警告
//!
//! ## 设计原则
//!
//! 1. **无状态验证**: `InvariantValidator` 是无状态的纯函数集合
//! 2. **不可变数据**: 所有验证操作仅读取数据，不修改状态
//! 3. **快速失败**: 任何违规立即返回错误，不继续执行
//! 4. **详细上下文**: 错误消息包含足够信息用于调试和审计
//!
//! ## Example
//!
//! ```no_run
//! use aeternum_core::storage::invariant::InvariantValidator;
//! use aeternum_core::models::{CryptoEpoch, DeviceHeader, DeviceId, Role, Operation};
//! use aeternum_core::crypto::kem::KyberKEM;
//!
//! // 检查纪元单调性 (Invariant #1)
//! let current_epoch = CryptoEpoch::initial();
//! let new_epoch = current_epoch.next();
//! InvariantValidator::check_epoch_monotonicity(&current_epoch, &new_epoch)?;
//!
//! // 检查 Header 完备性 (Invariant #2)
//! let device_id = DeviceId::generate();
//! let keypair = KyberKEM::generate_keypair();
//! let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();
//! let header = DeviceHeader::new(device_id.clone(), current_epoch.clone(), keypair.public, encrypted_dek);
//! let headers = vec![header];
//! InvariantValidator::check_header_completeness(&headers, &device_id, &current_epoch)?;
//!
//! // 检查因果熵障 (Invariant #3)
//! let role = Role::Recovery;
//! let operation = Operation::SigmaRotate;
//! InvariantValidator::check_causal_barrier(&role, &operation)?;
//!
//! // 检查否决权优先 (Invariant #4)
//! let veto_count = 2;
//! let recovery_start = std::time::SystemTime::now();
//! InvariantValidator::check_veto_supremacy_with_time(veto_count, &recovery_start)?;
//! # Ok::<(), aeternum_core::storage::error::StorageError>(())
//! ```

use std::collections::HashSet;
use std::time::SystemTime;

use crate::models::device::{DeviceHeader, DeviceId, Operation, Role};
use crate::models::epoch::CryptoEpoch;

use super::error::StorageError;

/// 时间窗口：48 小时（以毫秒为单位）
///
/// 用于 Invariant #4: 否决权优先
const VETO_WINDOW_MS: u64 = 48 * 60 * 60 * 1000;

/// 不变量检查器（无状态，纯函数）
///
/// 提供四大数学不变量的验证方法。所有方法都是纯函数，
/// 不修改任何状态，仅根据输入数据返回验证结果。
///
/// # Thread Safety
///
/// 这个类型是无状态的，可以安全地在多线程环境中共享。
#[derive(Debug, Clone, Copy)]
pub struct InvariantValidator;

impl InvariantValidator {
    // ========================================================================
    // Invariant #1: 纪元单调性 (Epoch Monotonicity)
    // ========================================================================

    /// 验证纪元严格递增
    ///
    /// **Invariant #1**: 所有设备的 epoch 必须严格单调递增，禁止回滚
    ///
    /// # Arguments
    ///
    /// - `current_epoch`: 当前纪元版本
    /// - `new_epoch`: 提议的新纪元版本
    ///
    /// # Returns
    ///
    /// - `Ok(())` 如果 `new_epoch.version > current_epoch.version`
    /// - `Err(StorageError::InvariantViolation(..))` 如果违反纪元单调性
    ///
    /// # Errors
    ///
    /// 返回 `InvariantViolation::EpochMonotonicity` 如果：
    /// - `new_epoch.version <= current_epoch.version`（回滚或重复）
    /// - `new_epoch.version > current_epoch.version + 1`（跳过纪元）
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::invariant::InvariantValidator;
    /// use aeternum_core::models::CryptoEpoch;
    ///
    /// let current_epoch = CryptoEpoch::initial();
    /// let next_epoch = current_epoch.next();
    ///
    /// // 通过验证
    /// InvariantValidator::check_epoch_monotonicity(&current_epoch, &next_epoch).unwrap();
    ///
    /// // 违规：相同纪元
    /// let result = InvariantValidator::check_epoch_monotonicity(&current_epoch, &current_epoch);
    /// assert!(result.is_err());
    ///
    /// // 违规：纪元回滚
    /// let result = InvariantValidator::check_epoch_monotonicity(&next_epoch, &current_epoch);
    /// assert!(result.is_err());
    /// ```
    pub fn check_epoch_monotonicity(
        current_epoch: &CryptoEpoch,
        new_epoch: &CryptoEpoch,
    ) -> Result<(), StorageError> {
        let current = current_epoch.version;
        let new = new_epoch.version;

        // 纪元必须严格递增（禁止回滚）
        if new <= current {
            return Err(StorageError::invariant(format!(
                "Invariant #1 violation: epoch monotonicity (current={}, new={})",
                current, new
            )));
        }

        // 纪元不能跳过版本（确保线性升级）
        if new > current + 1 {
            return Err(StorageError::invariant(format!(
                "Invariant #1 violation: epoch jump detected (current={}, new={})",
                current, new
            )));
        }

        Ok(())
    }

    // ========================================================================
    // Invariant #2: Header 完备性 (Header Completeness)
    // ========================================================================

    /// 验证每个活跃设备有且仅有一个 Header
    ///
    /// **Invariant #2**: 每个活跃设备必须且仅能通过一个正确的 Header 获取 DEK
    ///
    /// # Arguments
    ///
    /// - `headers`: 所有设备的 Header 列表
    /// - `device_id`: 要检查的设备 ID
    /// - `epoch`: 当前纪元
    ///
    /// # Returns
    ///
    /// - `Ok(())` 如果设备有且仅有一个匹配纪元的 Header
    /// - `Err(StorageError::InvariantViolation(..))` 如果 Header 不完备
    ///
    /// # Errors
    ///
    /// 返回 `InvariantViolation::HeaderIncomplete` 如果：
    /// - 设备没有 Header
    /// - 设备有多个 Header（状态不一致）
    /// - Header 的纪元与当前纪元不匹配
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::invariant::InvariantValidator;
    /// use aeternum_core::models::{CryptoEpoch, DeviceId, DeviceHeader};
    ///
    /// let epoch = CryptoEpoch::initial();
    /// let device_id = DeviceId::generate();
    /// let headers = vec![/* ... */];
    ///
    /// // 通过验证：设备有一个正确的 Header
    /// InvariantValidator::check_header_completeness(&headers, &device_id, &epoch).unwrap();
    /// ```
    pub fn check_header_completeness(
        headers: &[DeviceHeader],
        device_id: &DeviceId,
        epoch: &CryptoEpoch,
    ) -> Result<(), StorageError> {
        // 查找该设备的所有 Header
        let device_headers: Vec<_> = headers
            .iter()
            .filter(|h| &h.device_id == device_id)
            .collect();

        // 检查：必须有至少一个 Header
        if device_headers.is_empty() {
            return Err(StorageError::invariant(format!(
                "Invariant #2 violation: header incomplete (device={:?}) - no header found",
                device_id
            )));
        }

        // 检查：该设备的 Header 必须在当前纪元
        let epoch_headers: Vec<_> = device_headers
            .iter()
            .filter(|h| h.belongs_to_epoch(epoch))
            .collect();

        if epoch_headers.is_empty() {
            return Err(StorageError::invariant(format!(
                "Invariant #2 violation: header incomplete (device={:?}) - no header in current epoch {}",
                device_id, epoch.version
            )));
        }

        // 检查：每个纪元只能有一个 Header（防止状态不一致）
        if epoch_headers.len() > 1 {
            return Err(StorageError::invariant(format!(
                "Invariant #2 violation: header incomplete (device={:?}) - multiple headers in same epoch",
                device_id
            )));
        }

        Ok(())
    }

    /// 验证所有活跃设备的 Header 完备性
    ///
    /// 这是批量检查版本，用于启动时验证所有设备。
    ///
    /// # Arguments
    ///
    /// - `headers`: 所有设备的 Header 列表
    /// - `epoch`: 当前纪元
    ///
    /// # Returns
    ///
    /// - `Ok(())` 如果所有活跃设备都有正确的 Header
    /// - `Err(StorageError::InvariantViolation(..))` 如果任何设备 Header 不完备
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::invariant::InvariantValidator;
    /// use aeternum_core::models::{CryptoEpoch, DeviceHeader};
    ///
    /// let epoch = CryptoEpoch::initial();
    /// let headers = vec![/* ... */];
    ///
    /// // 验证所有活跃设备
    /// InvariantValidator::check_all_headers_complete(&headers, &epoch).unwrap();
    /// ```
    pub fn check_all_headers_complete(
        headers: &[DeviceHeader],
        epoch: &CryptoEpoch,
    ) -> Result<(), StorageError> {
        // 收集所有活跃设备的 ID
        let mut active_devices = HashSet::new();
        for header in headers {
            if header.status == crate::models::DeviceStatus::Active {
                active_devices.insert(header.device_id);
            }
        }

        // 验证每个活跃设备都有 Header
        for device_id in active_devices {
            Self::check_header_completeness(headers, &device_id, epoch)?;
        }

        Ok(())
    }

    // ========================================================================
    // Invariant #3: 因果熵障 (Causal Barrier)
    // ========================================================================

    /// 验证 RECOVERY 角色不能执行管理操作
    ///
    /// **Invariant #3**: 解密权限 ≠ 管理权限（RECOVERY 角色不能执行 σ_rotate）
    ///
    /// # Arguments
    ///
    /// - `role`: 执行操作的设备角色
    /// - `operation`: 尝试执行的管理操作
    ///
    /// # Returns
    ///
    /// - `Ok(())` 如果角色允许执行该操作
    /// - `Err(StorageError::InvariantViolation(..))` 如果违反因果熵障
    ///
    /// # Errors
    ///
    /// 返回 `InvariantViolation::CausalBarrier` 如果：
    /// - `role == Role::Recovery` 且操作需要 `AUTHORIZED` 权限
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::invariant::InvariantValidator;
    /// use aeternum_core::models::{Operation, Role};
    ///
    /// let role = Role::Recovery;
    /// let operation = Operation::SigmaRotate;
    ///
    /// // 违规：RECOVERY 角色不能执行 σ_rotate
    /// let result = InvariantValidator::check_causal_barrier(&role, &operation);
    /// assert!(result.is_err());
    /// ```
    pub fn check_causal_barrier(role: &Role, operation: &Operation) -> Result<(), StorageError> {
        // 检查角色是否允许执行该操作
        if !role.can_permit_operation(*operation) {
            return Err(StorageError::invariant(format!(
                "Invariant #3 violation: causal barrier (role={}, op={})",
                role.as_str(),
                operation.as_str()
            )));
        }

        Ok(())
    }

    // ========================================================================
    // Invariant #4: 否决权优先 (Veto Supremacy)
    // ========================================================================

    /// 验证 48h 窗口内的否决权
    ///
    /// **Invariant #4**: 48h 窗口内任何活跃设备的 Veto 信号必须立即终止恢复流程
    ///
    /// # Arguments
    ///
    /// - `veto_count`: 当前活跃的否决信号数量
    /// - `recovery_start_timestamp_ms`: 恢复流程开始时间（Unix 毫秒）
    ///
    /// # Returns
    ///
    /// - `Ok(())` 如果否决权检查通过（窗口内无否决，或窗口已过期）
    /// - `Err(StorageError::InvariantViolation(..))` 如果 48h 窗口内有活跃否决
    ///
    /// # Errors
    ///
    /// 返回 `InvariantViolation::VetoSupremacy` 如果：
    /// - `veto_count > 0` 且当前时间在 48h 窗口内
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::invariant::InvariantValidator;
    /// use std::time::SystemTime;
    ///
    /// // 模拟有否决信号
    /// let veto_count = 1;
    /// let recovery_start = SystemTime::now()
    ///     .duration_since(SystemTime::UNIX_EPOCH)
    ///     .unwrap()
    ///     .as_millis() as u64;
    ///
    /// // 违规：48h 窗口内有否决
    /// let result = InvariantValidator::check_veto_supremacy(veto_count, recovery_start);
    /// assert!(result.is_err());
    /// ```
    pub fn check_veto_supremacy(
        veto_count: usize,
        recovery_start_timestamp_ms: u64,
    ) -> Result<(), StorageError> {
        // 如果没有否决信号，通过验证
        if veto_count == 0 {
            return Ok(());
        }

        // 计算当前时间
        let now_ms = SystemTime::now()
            .duration_since(SystemTime::UNIX_EPOCH)
            .map(|d| d.as_millis() as u64)
            .unwrap_or(0);

        // 检查是否在 48h 窗口内
        let elapsed_ms = now_ms.saturating_sub(recovery_start_timestamp_ms);

        if elapsed_ms < VETO_WINDOW_MS {
            return Err(StorageError::invariant(format!(
                "Invariant #4 violation: veto supremacy (vetoes={}, elapsed_ms={}, window_ms={})",
                veto_count, elapsed_ms, VETO_WINDOW_MS
            )));
        }

        Ok(())
    }

    /// 验证否决权优先（使用 SystemTime）
    ///
    /// 这是 `check_veto_supremacy` 的便捷版本，直接接受 SystemTime。
    ///
    /// # Arguments
    ///
    /// - `veto_count`: 当前活跃的否决信号数量
    /// - `recovery_start`: 恢复流程开始时间
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::invariant::InvariantValidator;
    /// use std::time::SystemTime;
    ///
    /// let veto_count = 1;
    /// let recovery_start = SystemTime::now();
    ///
    /// // 违规：48h 窗口内有否决
    /// let result = InvariantValidator::check_veto_supremacy_with_time(veto_count, &recovery_start);
    /// assert!(result.is_err());
    /// ```
    pub fn check_veto_supremacy_with_time(
        veto_count: usize,
        recovery_start: &SystemTime,
    ) -> Result<(), StorageError> {
        let recovery_start_ms = recovery_start
            .duration_since(SystemTime::UNIX_EPOCH)
            .map(|d| d.as_millis() as u64)
            .unwrap_or(0);

        Self::check_veto_supremacy(veto_count, recovery_start_ms)
    }
}

// ============================================================================
// Tests
// ============================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use crate::crypto::kem::KyberKEM;
    use crate::models::DeviceStatus;
    use std::time::{Duration, SystemTime};

    // ------------------------------------------------------------------------
    // Invariant #1: Epoch Monotonicity Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_epoch_monotonicity_pass_same_epoch() {
        let epoch1 = CryptoEpoch::initial();
        let epoch2 = epoch1.next();

        // 通过：新纪元 > 旧纪元
        assert!(InvariantValidator::check_epoch_monotonicity(&epoch1, &epoch2).is_ok());
    }

    #[test]
    fn test_epoch_monotonicity_fail_same_epoch() {
        let epoch1 = CryptoEpoch::initial();

        // 违规：相同纪元
        let result = InvariantValidator::check_epoch_monotonicity(&epoch1, &epoch1);
        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("Invariant #1"));
    }

    #[test]
    fn test_epoch_monotonicity_fail_rollback() {
        let epoch1 = CryptoEpoch::initial();
        let epoch2 = epoch1.next();

        // 违规：纪元回滚（epoch2 > epoch1）
        let result = InvariantValidator::check_epoch_monotonicity(&epoch2, &epoch1);
        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("Invariant #1"));
    }

    #[test]
    fn test_epoch_monotonicity_fail_jump() {
        let epoch1 = CryptoEpoch::initial();
        let epoch3 = CryptoEpoch::new(epoch1.version + 2, epoch1.algorithm);

        // 违规：纪元跳跃
        let result = InvariantValidator::check_epoch_monotonicity(&epoch1, &epoch3);
        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("jump detected"));
    }

    // ------------------------------------------------------------------------
    // Invariant #2: Header Completeness Tests
    // ------------------------------------------------------------------------

    fn create_test_header(device_id: DeviceId, epoch: CryptoEpoch) -> DeviceHeader {
        let keypair = KyberKEM::generate_keypair();
        let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();
        DeviceHeader::new(device_id, epoch, keypair.public, encrypted_dek)
    }

    #[test]
    fn test_header_completeness_pass() {
        let epoch = CryptoEpoch::initial();
        let device_id = DeviceId::generate();
        let header = create_test_header(device_id, epoch.clone());
        let headers = vec![header];

        // 通过：设备有一个正确的 Header
        assert!(
            InvariantValidator::check_header_completeness(&headers, &device_id, &epoch).is_ok()
        );
    }

    #[test]
    fn test_header_completeness_fail_no_header() {
        let epoch = CryptoEpoch::initial();
        let device_id = DeviceId::generate();
        let headers = vec![];

        // 违规：设备没有 Header
        let result = InvariantValidator::check_header_completeness(&headers, &device_id, &epoch);
        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("Invariant #2"));
    }

    #[test]
    fn test_header_completeness_fail_wrong_epoch() {
        let epoch1 = CryptoEpoch::initial();
        let epoch2 = epoch1.next();
        let device_id = DeviceId::generate();
        let header = create_test_header(device_id, epoch1);
        let headers = vec![header];

        // 违规：Header 的纪元与当前纪元不匹配
        let result = InvariantValidator::check_header_completeness(&headers, &device_id, &epoch2);
        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("Invariant #2"));
    }

    #[test]
    fn test_header_completeness_fail_multiple_headers() {
        let epoch = CryptoEpoch::initial();
        let device_id = DeviceId::generate();
        let keypair = KyberKEM::generate_keypair();
        let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();

        // 创建两个相同设备的 Header（模拟状态不一致）
        let header1 = DeviceHeader {
            device_id,
            epoch: epoch.clone(),
            public_key: keypair.public,
            encrypted_dek,
            status: DeviceStatus::Active,
            created_at: 0,
        };
        let header2 = header1.clone();
        let headers = vec![header1, header2];

        // 违规：设备有多个 Header
        let result = InvariantValidator::check_header_completeness(&headers, &device_id, &epoch);
        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("multiple headers"));
    }

    #[test]
    fn test_all_headers_complete_pass() {
        let epoch = CryptoEpoch::initial();
        let device1 = DeviceId::generate();
        let device2 = DeviceId::generate();
        let header1 = create_test_header(device1, epoch.clone());
        let header2 = create_test_header(device2, epoch.clone());
        let headers = vec![header1, header2];

        // 通过：所有活跃设备都有正确的 Header
        assert!(InvariantValidator::check_all_headers_complete(&headers, &epoch).is_ok());
    }

    #[test]
    fn test_all_headers_complete_with_revoked_device() {
        let epoch = CryptoEpoch::initial();
        let active_device = DeviceId::generate();
        let revoked_device = DeviceId::generate();

        let mut active_header = create_test_header(active_device, epoch.clone());
        let mut revoked_header = create_test_header(revoked_device, epoch.clone());

        active_header.status = DeviceStatus::Active;
        revoked_header.status = DeviceStatus::Revoked;

        let headers = vec![active_header, revoked_header];

        // 通过：只验证活跃设备，撤销设备不需要验证
        assert!(InvariantValidator::check_all_headers_complete(&headers, &epoch).is_ok());
    }

    // ------------------------------------------------------------------------
    // Invariant #3: Causal Barrier Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_causal_barrier_pass_authorized() {
        let role = Role::Authorized;
        let operation = Operation::SigmaRotate;

        // 通过：AUTHORIZED 角色可以执行管理操作
        assert!(InvariantValidator::check_causal_barrier(&role, &operation).is_ok());
    }

    #[test]
    fn test_causal_barrier_fail_recovery_rotate() {
        let role = Role::Recovery;
        let operation = Operation::SigmaRotate;

        // 违规：RECOVERY 角色不能执行 σ_rotate
        let result = InvariantValidator::check_causal_barrier(&role, &operation);
        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("Invariant #3"));
    }

    #[test]
    fn test_causal_barrier_fail_recovery_revoke() {
        let role = Role::Recovery;
        let operation = Operation::RevokeDevice;

        // 违规：RECOVERY 角色不能执行设备撤销
        let result = InvariantValidator::check_causal_barrier(&role, &operation);
        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("Invariant #3"));
    }

    #[test]
    fn test_causal_barrier_fail_recovery_rekey() {
        let role = Role::Recovery;
        let operation = Operation::RekeyVault;

        // 违规：RECOVERY 角色不能执行密钥重加密
        let result = InvariantValidator::check_causal_barrier(&role, &operation);
        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("Invariant #3"));
    }

    #[test]
    fn test_causal_barrier_fail_recovery_update_policy() {
        let role = Role::Recovery;
        let operation = Operation::UpdatePolicy;

        // 违规：RECOVERY 角色不能执行策略更新
        let result = InvariantValidator::check_causal_barrier(&role, &operation);
        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("Invariant #3"));
    }

    #[test]
    fn test_role_can_permit_operation() {
        // AUTHORIZED 角色可以执行所有操作
        assert!(Role::Authorized.can_permit_operation(Operation::SigmaRotate));
        assert!(Role::Authorized.can_permit_operation(Operation::RevokeDevice));
        assert!(Role::Authorized.can_permit_operation(Operation::RekeyVault));
        assert!(Role::Authorized.can_permit_operation(Operation::UpdatePolicy));

        // RECOVERY 角色不能执行任何管理操作
        assert!(!Role::Recovery.can_permit_operation(Operation::SigmaRotate));
        assert!(!Role::Recovery.can_permit_operation(Operation::RevokeDevice));
        assert!(!Role::Recovery.can_permit_operation(Operation::RekeyVault));
        assert!(!Role::Recovery.can_permit_operation(Operation::UpdatePolicy));
    }

    // ------------------------------------------------------------------------
    // Invariant #4: Veto Supremacy Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_veto_supremacy_pass_no_veto() {
        let veto_count = 0;
        let recovery_start = SystemTime::now();

        // 通过：没有否决信号
        assert!(
            InvariantValidator::check_veto_supremacy_with_time(veto_count, &recovery_start).is_ok()
        );
    }

    #[test]
    fn test_veto_supremacy_pass_expired_window() {
        let veto_count = 5;
        let recovery_start = SystemTime::now() - Duration::from_millis(48 * 60 * 60 * 1000 + 1000);

        // 通过：48h 窗口已过期
        assert!(
            InvariantValidator::check_veto_supremacy_with_time(veto_count, &recovery_start).is_ok()
        );
    }

    #[test]
    fn test_veto_supremacy_fail_veto_in_window() {
        let veto_count = 1;
        let recovery_start = SystemTime::now();

        // 违规：48h 窗口内有否决
        let result =
            InvariantValidator::check_veto_supremacy_with_time(veto_count, &recovery_start);
        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("Invariant #4"));
    }

    #[test]
    fn test_veto_supremacy_fail_multiple_vetoes() {
        let veto_count = 3;
        let recovery_start = SystemTime::now() - Duration::from_millis(1000); // 1 秒前

        // 违规：48h 窗口内有多个否决
        let result =
            InvariantValidator::check_veto_supremacy_with_time(veto_count, &recovery_start);
        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("vetoes=3"));
    }

    #[test]
    fn test_veto_window_exactly_48_hours() {
        let veto_count = 1;
        let recovery_start = SystemTime::now() - Duration::from_millis(48 * 60 * 60 * 1000);

        // 边界情况：正好 48h，应该通过
        assert!(
            InvariantValidator::check_veto_supremacy_with_time(veto_count, &recovery_start).is_ok()
        );
    }

    #[test]
    fn test_veto_window_one_ms_before() {
        let veto_count = 1;
        let recovery_start = SystemTime::now() - Duration::from_millis(48 * 60 * 60 * 1000 - 1);

        // 边界情况：48h 窗口内 1ms，应该失败
        let result =
            InvariantValidator::check_veto_supremacy_with_time(veto_count, &recovery_start);
        assert!(result.is_err());
    }
}

// ============================================================================
// Property-Based Tests (using proptest)
// ============================================================================

#[cfg(test)]
mod proptest_tests {
    use super::*;
    use crate::models::epoch::CryptoEpoch;
    use proptest::prelude::*;

    // PropTest strategy for epoch version (1..1000)
    fn epoch_version() -> impl Strategy<Value = u64> {
        1..1000u64
    }

    proptest! {
        #[test]
        fn prop_epoch_always_monotonic(current in epoch_version(), new in epoch_version()) {
            // 属性：纪元必须严格递增，且只能递增 1
            let epoch1 = CryptoEpoch::new(current, crate::models::CryptoAlgorithm::V1);
            let epoch2 = CryptoEpoch::new(new, crate::models::CryptoAlgorithm::V1);

            let result = InvariantValidator::check_epoch_monotonicity(&epoch1, &epoch2);

            // 只有当 new == current + 1 时才通过（严格递增 1）
            let expected_ok = new == current + 1;
            prop_assert_eq!(result.is_ok(), expected_ok);
        }

        #[test]
        fn prop_epoch_never_decrements(epoch1 in epoch_version(), epoch2 in epoch_version()) {
            // 属性：纪元永远不会减少，且只能递增 1
            let e1 = CryptoEpoch::new(epoch1, crate::models::CryptoAlgorithm::V1);
            let e2 = CryptoEpoch::new(epoch2, crate::models::CryptoAlgorithm::V1);

            let expected_ok = e2.version == e1.version + 1;

            // 如果 e2 == e1 + 1，验证通过
            if expected_ok {
                prop_assert!(InvariantValidator::check_epoch_monotonicity(&e1, &e2).is_ok());
            }
            // 否则验证失败
            if !expected_ok {
                prop_assert!(InvariantValidator::check_epoch_monotonicity(&e1, &e2).is_err());
            }
        }

        #[test]
        fn prop_veto_window_48_hours(hours_ago in 0u64..100) {
            // 属性：48h 窗口外的否决被忽略
            let veto_count = 1;
            let hours_ago_ms = hours_ago * 60 * 60 * 1000;
            let recovery_start = SystemTime::now() - std::time::Duration::from_millis(hours_ago_ms);

            let result = InvariantValidator::check_veto_supremacy_with_time(veto_count, &recovery_start);

            // 48+ 小时前开始的恢复应该通过
            prop_assert_eq!(result.is_ok(), hours_ago >= 48);
        }
    }
}
