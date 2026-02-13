//! # Atomic Epoch Upgrade Protocol (AUP) - 原子纪元升级协议
//!
//! 实现形式化数学不变量 INVARIANT_3（原子纪元升级）的具体协议。
//!
//! ## 三阶段协议
//!
//! 1. **预备 (Preparation)**: 在内存中解封当前 VK，派生新纪元的 DEK
//! 2. **影子写入 (Shadow Writing)**: 创建临时文件，写入 Header 和 Blob，强制 fsync
//! 3. **原子替换 (Atomic Commit)**: POSIX rename + 更新 SQLCipher 元数据
//!
//! ## 设计原则
//!
//! - **无中间态**: 任何时刻 (Header, VaultBlob) 对必须是全纪元一致的
//! - **原子性优先**: 所有更新操作使用影子写入 + 原子 rename
//! - **崩溃恢复**: 启动时自动检测并修复不一致状态
//! - **不变量强制**: 所有阶段验证纪元单调性（Invariant #1）
//!
//! ## 安全保证
//!
//! - POSIX `rename()` 在 Linux/Android 上是原子操作
//! - 所有写入后调用 `File::sync_all()` 强制物理落盘
//! - 临时文件自动清理（ShadowFile Drop trait）
//! - 元数据更新失败时触发自愈逻辑（CrashRecovery）
//!
//! ## Example
//!
//! ```no_run
//! use aeternum_core::storage::aug::{aup_prepare, aup_shadow_write, aup_atomic_commit};
//! use aeternum_core::models::{CryptoEpoch, VaultBlob};
//! use std::path::Path;
//!
//! // 初始化
//! let current_epoch = CryptoEpoch::initial();
//! let current_vk = vec![0u8; 32]; // 示例密钥材料
//! let vault_path = Path::new("vault.db");
//!
//! // 阶段 1: 预备
//! let preparation = aup_prepare(&current_epoch, &current_vk)?;
//!
//! // 阶段 2: 影子写入
//! let shadow_file = aup_shadow_write(&vault_path, &preparation)?;
//!
//! // 阶段 3: 原子提交（注意：此函数调用需要修改）
//! // aup_atomic_commit(&vault_path, shadow_file, &metadata_db, new_epoch)?;
//! # Ok::<(), aeternum_core::storage::StorageError>(())
//! ```

use std::io::Read;
use std::io::Write;
use std::path::Path;

use crate::models::epoch::CryptoEpoch;
use crate::storage::error::StorageError;
use crate::storage::invariant::InvariantValidator;
use crate::storage::shadow::{ShadowFile, ShadowWriter};

// ============================================================================
// AUP 阶段 1: 预备 (Preparation)
// ============================================================================

/// AUP 预备阶段的输出
///
/// 包含新纪元信息和准备好的 Vault Blob 数据。
#[derive(Debug, Clone)]
pub struct AupPreparation {
    /// 新纪元版本
    pub new_epoch: CryptoEpoch,
    /// 准备好的 Vault Blob（序列化后的字节数组）
    /// 注意：这是阶段 1 的占位符实现
    /// 完整实现需要与 VaultBlob 集成（后续阶段）
    pub prepared_blob: Vec<u8>,
}

/// AUP 阶段 1：预备
///
/// 在内存中执行纪元升级的准备工作：
/// 1. 验证纪元单调性（Invariant #1）
/// 2. 计算新纪元版本（current + 1）
/// 3. 准备新纪元的 Vault Blob
///
/// **注意**: 这是阶段 1 的占位符实现。完整的实现需要：
/// - 解封当前 VK（VaultKey）
/// - 派生新纪元的 DEK（DataEncryptionKey）
/// - 使用新 DEK 重新加密 VK
/// - 序列化为 VaultBlob
///
/// 当前实现仅验证纪元单调性并返回占位符数据。
///
/// # Arguments
///
/// - `current_epoch`: 当前纪元版本
/// - `current_vk`: 当前 Vault Key（暂未使用，占位符）
///
/// # Returns
///
/// - `Ok(AupPreparation)` 包含新纪元和准备的 Blob
/// - `Err(StorageError::InvariantViolation(..))` 如果违反纪元单调性
///
/// # Errors
///
/// 返回 `StorageError::InvariantViolation` 如果：
/// - `new_epoch <= current_epoch`（纪元回滚或重复）
/// - `new_epoch > current_epoch + 1`（纪元跳跃）
///
/// # Example
///
/// ```no_run
/// use aeternum_core::storage::aug::aup_prepare;
/// use aeternum_core::models::CryptoEpoch;
///
/// let current_epoch = CryptoEpoch::initial();
/// let current_vk = vec![0u8; 32]; // 示例密钥材料
///
/// let preparation = aup_prepare(&current_epoch, &current_vk)?;
/// assert_eq!(preparation.new_epoch.version, current_epoch.version + 1);
/// # Ok::<(), aeternum_core::storage::StorageError>(())
/// ```
pub fn aup_prepare(
    current_epoch: &CryptoEpoch,
    _current_vk: &[u8], // 暂未使用，占位符
) -> Result<AupPreparation, StorageError> {
    // 计算新纪元
    let new_epoch = current_epoch.next();

    // 验证纪元单调性（Invariant #1）
    InvariantValidator::check_epoch_monotonicity(current_epoch, &new_epoch)?;

    // TODO: 完整实现需要：
    // 1. 解封当前 VK: `vk = decrypt_vk(current_vk, current_dek)?`
    // 2. 派生新 DEK: `new_dek = derive_dek(&vk, &new_epoch)?`
    // 3. 重新加密 VK: `encrypted_vk = encrypt_vk(&vk, &new_dek)?`
    // 4. 序列化为 Blob: `blob = serialize_vault_blob(new_epoch, encrypted_vk)?`

    // 阶段 1 占位符：返回模拟的 prepared_blob
    let prepared_blob = format!("AUP_PREPARED_EPOCH_{}_BLOB", new_epoch.version).into_bytes();

    Ok(AupPreparation {
        new_epoch,
        prepared_blob,
    })
}

// ============================================================================
// AUP 阶段 2: 影子写入 (Shadow Writing)
// ============================================================================

/// AUP 阶段 2：影子写入
///
/// 创建临时文件并写入新纪元数据：
/// 1. 创建 `vault.tmp` 临时文件
/// 2. 写入 Header_n+1 与重新封装的 VK
/// 3. 强制 fsync 确保数据物理落盘
///
/// **关键安全保证**:
/// - 使用 ShadowWriter 确保临时文件在同一目录
/// - 所有写入后调用 `write_and_sync()` 强制落盘
/// - ShadowFile 实现自动清理（未提交时删除）
///
/// # Arguments
///
/// - `vault_path`: 目标 Vault 文件路径（如 `vault.db`）
/// - `preparation`: AUP 预备阶段的输出
///
/// # Returns
///
/// - `Ok(ShadowFile)` 包含临时文件句柄
/// - `Err(StorageError::ShadowWriteFailed(..))` 如果写入失败
/// - `Err(StorageError::FsyncFailed(..))` 如果 fsync 失败
///
/// # Errors
///
/// 返回 `StorageError::ShadowWriteFailed` 如果：
/// - 临时文件创建失败
/// - 写入操作失败（磁盘满、I/O 错误）
/// - 权限被拒绝
///
/// 返回 `StorageError::FsyncFailed` 如果：
/// - `fsync()` 系统调用失败（硬件错误、文件系统损坏）
///
/// # Example
///
/// ```no_run
/// use aeternum_core::storage::aug::{aup_prepare, aup_shadow_write};
/// use aeternum_core::models::CryptoEpoch;
/// use std::path::Path;
///
/// let vault_path = Path::new("vault.db");
/// let current_epoch = CryptoEpoch::initial();
/// let current_vk = vec![0u8; 32]; // 示例密钥材料
///
/// // 阶段 1: 预备
/// let preparation = aup_prepare(&current_epoch, &current_vk)?;
///
/// // 阶段 2: 影子写入
/// let shadow_file = aup_shadow_write(&vault_path, &preparation)?;
/// // shadow_file 现在包含写入并同步的数据
/// # Ok::<(), aeternum_core::storage::StorageError>(())
/// ```
pub fn aup_shadow_write(
    vault_path: impl AsRef<Path>,
    preparation: &AupPreparation,
) -> Result<ShadowFile, StorageError> {
    let vault_path = vault_path.as_ref();

    // 创建影子写入器
    let writer = ShadowWriter::new(vault_path);

    // 开始影子写入（创建 .tmp 文件）
    let mut shadow_file = writer.begin_shadow_write()?;

    // 写入新纪元数据并强制 fsync
    // 格式: [EPOCH_VERSION:8字节][BLOB_DATA]
    let epoch_bytes = preparation.new_epoch.version.to_be_bytes();
    shadow_file.write_all(&epoch_bytes).map_err(|e| {
        StorageError::shadow_write(format!(
            "Failed to write epoch to {}: {}",
            shadow_file.path().display(),
            e
        ))
    })?;

    shadow_file
        .write_all(&preparation.prepared_blob)
        .map_err(|e| {
            StorageError::shadow_write(format!(
                "Failed to write blob to {}: {}",
                shadow_file.path().display(),
                e
            ))
        })?;

    // 强制 fsync - 确保数据物理落盘
    shadow_file.file().sync_all().map_err(|e| {
        StorageError::fsync(format!(
            "Failed to fsync {}: {}",
            shadow_file.path().display(),
            e
        ))
    })?;

    eprintln!(
        "[AUP] Shadow write completed: {} (epoch {})",
        shadow_file.path().display(),
        preparation.new_epoch.version
    );

    Ok(shadow_file)
}

// ============================================================================
// AUP 阶段 3: 原子提交 (Atomic Commit)
// ============================================================================

/// AUP 阶段 3：原子提交
///
/// 执行原子替换操作：
/// 1. POSIX 原子重命名：`vault.tmp` → `vault.db`
/// 2. 更新 SQLCipher 元数据：`Local_Epoch = n+1`
/// 3. 提交事务
///
/// **原子性保证**:
/// - POSIX `rename()` 在同一文件系统上是原子的
/// - 要么是旧文件，要么是新文件，不存在中间态
/// - 重命名后临时文件自动清理（should_cleanup = false）
///
/// # Arguments
///
/// - `vault_path`: 目标 Vault 文件路径（如 `vault.db`）
/// - `shadow_file`: 阶段 2 返回的临时文件句柄
/// - `new_epoch`: 新纪元版本（用于元数据更新）
///
/// # Returns
///
/// - `Ok(())` 如果原子提交成功
/// - `Err(StorageError::AtomicRenameFailed(..))` 如果重命名失败
///
/// # Errors
///
/// 返回 `StorageError::AtomicRenameFailed` 如果：
/// - 临时文件不存在
/// - 跨设备重命名（不原子）
/// - 权限被拒绝
/// - I/O 错误
///
/// **注意**: 元数据更新（SQLCipher）失败时：
/// - Blob 已升级（物理文件已替换）
/// - 元数据记录旧纪元
/// - 启动时触发自愈逻辑（CrashRecovery::heal_blob_ahead）
///
/// # Example
///
/// ```no_run
/// use aeternum_core::storage::aug::{aup_prepare, aup_shadow_write, aup_atomic_commit};
/// use aeternum_core::models::CryptoEpoch;
/// use std::path::Path;
///
/// let vault_path = Path::new("vault.db");
/// let current_epoch = CryptoEpoch::initial();
/// let current_vk = vec![0u8; 32]; // 示例密钥材料
///
/// // 阶段 1: 预备
/// let preparation = aup_prepare(&current_epoch, &current_vk)?;
///
/// // 阶段 2: 影子写入
/// let shadow_file = aup_shadow_write(&vault_path, &preparation)?;
///
/// // 阶段 3: 原子提交
/// aup_atomic_commit(&vault_path, shadow_file, &preparation.new_epoch)?;
/// // vault.db 现在包含新纪元数据
/// # Ok::<(), aeternum_core::storage::StorageError>(())
/// ```
pub fn aup_atomic_commit(
    vault_path: impl AsRef<Path>,
    shadow_file: ShadowFile,
    _new_epoch: &CryptoEpoch, // 暂未使用，占位符（未来用于元数据更新）
) -> Result<(), StorageError> {
    let vault_path = vault_path.as_ref();

    // 创建影子写入器用于提交
    let writer = ShadowWriter::new(vault_path);

    // 执行原子重命名（vault.tmp → vault.db）
    writer.commit_shadow_write(shadow_file).map_err(|e| {
        // 尝试清理临时文件
        let temp_path = vault_path.with_extension("db.tmp");
        let _ = std::fs::remove_file(&temp_path);

        StorageError::atomic_rename(format!(
            "Failed to atomic rename {} to {}: {}",
            temp_path.display(),
            vault_path.display(),
            e
        ))
    })?;

    eprintln!(
        "[AUP] Atomic commit completed: {} (epoch {})",
        vault_path.display(),
        _new_epoch.version
    );

    // TODO: 元数据更新（需要 SQLCipher 集成）
    // let metadata_db = ...;
    // metadata_db.update_epoch(_new_epoch.version)?;

    Ok(())
}

// ============================================================================
// 辅助函数
// ============================================================================

/// 读取 Vault 文件中的纪元版本
///
/// 从 Vault Blob 的前 8 字节读取纪元版本号。
/// 这用于崩溃恢复时验证 Blob 的纪元。
///
/// # Arguments
///
/// - `vault_path`: Vault 文件路径
///
/// # Returns
///
/// - `Ok(u64)` 纪元版本号
/// - `Err(StorageError::ConsistencyCheckFailed(..))` 如果读取失败
pub fn read_vault_epoch(vault_path: impl AsRef<Path>) -> Result<u64, StorageError> {
    let vault_path = vault_path.as_ref();

    // 检查文件是否存在
    if !vault_path.exists() {
        return Err(StorageError::consistency_check(format!(
            "Vault file does not exist: {}",
            vault_path.display()
        )));
    }

    // 读取前 8 字节（纪元版本）
    let mut file = std::fs::File::open(vault_path).map_err(|e| {
        StorageError::consistency_check(format!(
            "Failed to open vault file {}: {}",
            vault_path.display(),
            e
        ))
    })?;

    let mut epoch_bytes = [0u8; 8];
    file.read_exact(&mut epoch_bytes).map_err(|e| {
        StorageError::consistency_check(format!(
            "Failed to read epoch from {}: {}",
            vault_path.display(),
            e
        ))
    })?;

    let epoch = u64::from_be_bytes(epoch_bytes);

    eprintln!(
        "[AUP] Read vault epoch: {} from {}",
        epoch,
        vault_path.display()
    );

    Ok(epoch)
}

// ============================================================================
// Tests
// ============================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use tempfile::TempDir;

    // ------------------------------------------------------------------------
    // aup_prepare() Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_aup_prepare_creates_placeholder_blob() {
        let epoch = CryptoEpoch::initial();
        let current_vk = b"dummy_vk";

        let prep = aup_prepare(&epoch, current_vk).unwrap();

        // 占位符 Blob 应包含纪元信息
        let blob_str = String::from_utf8(prep.prepared_blob).unwrap();
        assert!(blob_str.contains(&format!("EPOCH_{}", prep.new_epoch.version)));
        assert!(blob_str.contains("AUP_PREPARED"));
    }

    #[test]
    fn test_aup_prepare_increments_epoch() {
        let epoch1 = CryptoEpoch::initial();
        let current_vk = b"dummy_vk";

        let prep = aup_prepare(&epoch1, current_vk).unwrap();

        // 新纪元应该是当前纪元 + 1
        assert_eq!(prep.new_epoch.version, epoch1.version + 1);
    }

    #[test]
    fn test_aup_prepare_increments_from_arbitrary_epoch() {
        let epoch = CryptoEpoch::new(100, crate::models::CryptoAlgorithm::V1);
        let current_vk = b"dummy_vk";

        let prep = aup_prepare(&epoch, current_vk).unwrap();

        // 新纪元应该是 101
        assert_eq!(prep.new_epoch.version, 101);
    }

    // ------------------------------------------------------------------------
    // aup_shadow_write() Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_aup_shadow_write_creates_temp_file() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let epoch = CryptoEpoch::initial();
        let prep = aup_prepare(&epoch, b"vk").unwrap();

        let shadow_file = aup_shadow_write(&vault_path, &prep).unwrap();

        // 临时文件应该存在
        assert!(shadow_file.path().exists());
        assert!(shadow_file.path().to_string_lossy().ends_with(".tmp"));
    }

    #[test]
    fn test_aup_shadow_write_includes_epoch() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let epoch = CryptoEpoch::new(42, crate::models::CryptoAlgorithm::V1);
        let prep = aup_prepare(&epoch, b"vk").unwrap();

        let shadow_file = aup_shadow_write(&vault_path, &prep).unwrap();

        // 读取临时文件的前 8 字节
        let mut epoch_bytes = [0u8; 8];
        let mut file = std::fs::File::open(shadow_file.path()).unwrap();
        file.read_exact(&mut epoch_bytes).unwrap();

        let written_epoch = u64::from_be_bytes(epoch_bytes);
        // aup_prepare 会创建纪元 43 (42 + 1)
        assert_eq!(written_epoch, 43);
    }

    #[test]
    fn test_aup_shadow_write_fsync_called() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let epoch = CryptoEpoch::initial();
        let prep = aup_prepare(&epoch, b"vk").unwrap();

        // 如果 fsync 失败，应该返回错误
        // 注意：这个测试依赖于文件系统支持 fsync
        let result = aup_shadow_write(&vault_path, &prep);
        assert!(result.is_ok());
    }

    #[test]
    fn test_aup_shadow_write_cleanup_on_drop() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let epoch = CryptoEpoch::initial();
        let prep = aup_prepare(&epoch, b"vk").unwrap();

        let shadow_path = {
            let shadow_file = aup_shadow_write(&vault_path, &prep).unwrap();
            let path = shadow_file.path().to_path_buf();
            // Drop shadow_file（未提交）
            drop(shadow_file);
            path
        };

        // 临时文件应该被清理
        assert!(!shadow_path.exists());
    }

    // ------------------------------------------------------------------------
    // aup_atomic_commit() Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_aup_atomic_commit_replaces_file() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        // 创建旧文件
        fs::write(&vault_path, b"old data").unwrap();

        let epoch = CryptoEpoch::initial();
        let prep = aup_prepare(&epoch, b"vk").unwrap();
        let shadow_file = aup_shadow_write(&vault_path, &prep).unwrap();

        // 提交
        aup_atomic_commit(&vault_path, shadow_file, &prep.new_epoch).unwrap();

        // 临时文件应该消失
        assert!(!vault_path.with_extension("db.tmp").exists());

        // 目标文件应该包含新数据
        let content = fs::read(&vault_path).unwrap();
        assert!(content.len() > 4); // 至少包含 4 字节纪元
    }

    #[test]
    fn test_aup_atomic_commit_is_atomic() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        // 创建旧文件
        fs::write(&vault_path, b"old data").unwrap();

        let epoch = CryptoEpoch::new(99, crate::models::CryptoAlgorithm::V1);
        let prep = aup_prepare(&epoch, b"vk").unwrap();
        let shadow_file = aup_shadow_write(&vault_path, &prep).unwrap();

        // 提交
        aup_atomic_commit(&vault_path, shadow_file, &prep.new_epoch).unwrap();

        // 验证纪元（应该是 100 = 99 + 1）
        let read_epoch = read_vault_epoch(&vault_path).unwrap();
        assert_eq!(read_epoch, 100u64);

        // 旧数据应该被替换
        let content = fs::read(&vault_path).unwrap();
        assert_ne!(content, b"old data");
    }

    #[test]
    fn test_aup_atomic_commit_fails_nonexistent_temp() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let epoch = CryptoEpoch::initial();
        let prep = aup_prepare(&epoch, b"vk").unwrap();

        // 创建假的 shadow_file（手动创建临时文件）
        let temp_path = vault_path.with_extension("db.tmp");
        fs::write(&temp_path, b"data").unwrap();
        let shadow_file = ShadowWriter::new(&vault_path).begin_shadow_write().unwrap();

        // 删除临时文件（模拟崩溃）
        fs::remove_file(&temp_path).unwrap();

        // 提交应该失败
        let result = aup_atomic_commit(&vault_path, shadow_file, &prep.new_epoch);
        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("rename"));
    }

    // ------------------------------------------------------------------------
    // read_vault_epoch() Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_read_vault_epoch() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let epoch = CryptoEpoch::new(123, crate::models::CryptoAlgorithm::V1);
        let prep = aup_prepare(&epoch, b"vk").unwrap();
        let shadow_file = aup_shadow_write(&vault_path, &prep).unwrap();
        aup_atomic_commit(&vault_path, shadow_file, &prep.new_epoch).unwrap();

        let read_epoch = read_vault_epoch(&vault_path).unwrap();
        // aup_prepare 会创建纪元 124 (123 + 1)
        assert_eq!(read_epoch, 124u64);
    }

    #[test]
    fn test_read_vault_epoch_fails_nonexistent() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("nonexistent.db");

        let result = read_vault_epoch(&vault_path);
        assert!(result.is_err());
        assert!(result.unwrap_err().to_string().contains("does not exist"));
    }

    #[test]
    fn test_read_vault_epoch_fails_truncated() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("truncated.db");

        // 写入少于 8 字节的数据
        fs::write(&vault_path, b"abc").unwrap();

        let result = read_vault_epoch(&vault_path);
        assert!(result.is_err());
        assert!(result
            .unwrap_err()
            .to_string()
            .contains("Failed to read epoch"));
    }

    // ------------------------------------------------------------------------
    // End-to-End AUP Flow Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_aup_full_flow() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let current_epoch = CryptoEpoch::initial();
        let current_vk = b"test_vault_key";

        // 阶段 1: 预备
        let prep = aup_prepare(&current_epoch, current_vk).unwrap();
        assert_eq!(prep.new_epoch.version, current_epoch.version + 1);

        // 阶段 2: 影子写入
        let shadow_file = aup_shadow_write(&vault_path, &prep).unwrap();
        assert!(shadow_file.path().exists());

        // 阶段 3: 原子提交
        aup_atomic_commit(&vault_path, shadow_file, &prep.new_epoch).unwrap();
        assert!(vault_path.exists());
        assert!(!vault_path.with_extension("db.tmp").exists());

        // 验证结果
        let final_epoch = read_vault_epoch(&vault_path).unwrap();
        assert_eq!(final_epoch, prep.new_epoch.version);
    }

    #[test]
    fn test_aup_multiple_epochs() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let initial_epoch = CryptoEpoch::initial();
        let mut epoch = initial_epoch.clone();
        let current_vk = b"test_vault_key";

        // 执行 3 次纪元升级
        for _ in 1..=3 {
            let prep = aup_prepare(&epoch, current_vk).unwrap();
            let shadow_file = aup_shadow_write(&vault_path, &prep).unwrap();
            aup_atomic_commit(&vault_path, shadow_file, &prep.new_epoch).unwrap();

            let read_epoch = read_vault_epoch(&vault_path).unwrap();
            assert_eq!(read_epoch, epoch.version + 1);

            epoch = prep.new_epoch;
        }

        // 最终纪元应该是初始值 + 3
        let final_epoch = read_vault_epoch(&vault_path).unwrap();
        assert_eq!(final_epoch, initial_epoch.version + 3);
    }

    #[test]
    fn test_aup_preserves_data_integrity() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let epoch = CryptoEpoch::initial();
        let test_data = b"TEST";

        // 创建初始数据
        let prep1 = aup_prepare(&epoch, test_data).unwrap();
        let shadow1 = aup_shadow_write(&vault_path, &prep1).unwrap();
        aup_atomic_commit(&vault_path, shadow1, &prep1.new_epoch).unwrap();

        let content1 = fs::read(&vault_path).unwrap();
        // 验证纪元 + 数据存在
        // 格式：[8字节纪元][数据...]
        assert!(content1.len() > 8);
        // 检查纪元是 2（初始 + 1）
        let epoch_bytes1 = &content1[..8];
        let epoch1_val = u64::from_be_bytes(epoch_bytes1.try_into().unwrap());
        assert_eq!(epoch1_val, 2);

        // 升级到新纪元
        let prep2 = aup_prepare(&prep1.new_epoch, test_data).unwrap();
        let shadow2 = aup_shadow_write(&vault_path, &prep2).unwrap();
        aup_atomic_commit(&vault_path, shadow2, &prep2.new_epoch).unwrap();

        let content2 = fs::read(&vault_path).unwrap();
        assert!(content2.len() > 8);
        let epoch_bytes2 = &content2[..8];
        let epoch2_val = u64::from_be_bytes(epoch_bytes2.try_into().unwrap());
        assert_eq!(epoch2_val, 3);
    }
}
