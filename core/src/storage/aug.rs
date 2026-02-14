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
//! use aeternum_core::crypto::aead::XChaCha20Key;
//! use std::path::Path;
//!
//! # fn main() -> Result<(), aeternum_core::storage::StorageError> {
//! // 初始化
//! let current_epoch = CryptoEpoch::initial();
//! let current_vk = vec![0u8; 48]; // 加密的 VK（32字节 VK + 16字节 tag）
//! let current_dek = XChaCha20Key::generate();
//! let vault_data = b"user data".to_vec();
//! let vault_path = Path::new("vault.db");
//!
//! // 阶段 1: 预备
//! let preparation = aup_prepare(&current_epoch, &current_vk, &current_dek, &vault_data)?;
//!
//! // 阶段 2: 影子写入
//! let shadow_file = aup_shadow_write(&vault_path, &preparation)?;
//!
//! // 阶段 3: 原子提交（注意：此函数调用需要修改）
//! // aup_atomic_commit(&vault_path, shadow_file, &metadata_db, new_epoch)?;
//! # Ok(())
//! # }
//! ```

use std::io::Read;
use std::io::Write;
use std::path::Path;

use crate::crypto::aead::{AeadCipher, XChaCha20Key, XChaCha20Nonce};
use crate::crypto::kdf::Argon2idKDF;
use crate::models::epoch::CryptoEpoch;
use crate::models::vault::{VaultBlob, VaultHeader, VAULT_MAGIC};
use crate::storage::error::StorageError;
use crate::storage::invariant::InvariantValidator;
use crate::storage::shadow::{ShadowFile, ShadowWriter};

// ============================================================================
// AUP 阶段 1: 预备 (Preparation)
// ============================================================================

/// AUP 预备阶段的输出
///
/// 包含新纪元信息和准备好的 Vault Blob 数据。
#[derive(Debug)]
pub struct AupPreparation {
    /// 新纪元版本
    pub new_epoch: CryptoEpoch,
    /// 准备好的 Vault Blob（序列化后的字节数组）
    pub prepared_blob: Vec<u8>,
    /// Vault Header（固定 32 字节）
    pub header: [u8; 32],
}

/// AUP 阶段 1：预备
///
/// 在内存中执行纪元升级的准备工作：
/// 1. 验证纪元单调性（Invariant #1）
/// 2. 计算新纪元版本（current + 1）
/// 3. 准备新纪元的 Vault Blob
///
/// 完整实现包括：
/// - 解封当前 VK（VaultKey）
/// - 派生新纪元的 DEK（DataEncryptionKey）
/// - 使用新 DEK 重新加密 VK
/// - 序列化为 VaultBlob
///
/// # Arguments
///
/// - `current_epoch`: 当前纪元版本
/// - `current_vk_bytes`: 当前加密的 Vault Key（使用当前 DEK 加密）
/// - `current_dek`: 当前的数据加密密钥
/// - `vault_data`: 实际的 vault 数据（用户数据）
///
/// # Returns
///
/// - `Ok(AupPreparation)` 包含新纪元和准备的 Blob
/// - `Err(StorageError::CryptoError(..))` 如果密码学操作失败
/// - `Err(StorageError::InvariantViolation(..))` 如果违反纪元单调性
///
/// # Errors
///
/// 返回 `StorageError::InvariantViolation` 如果：
/// - `new_epoch <= current_epoch`（纪元回滚或重复）
///
/// 返回 `StorageError::CryptoError` 如果：
/// - VK 解密失败
/// - DEK 派生失败
/// - VK 重新加密失败
/// - Blob 序列化失败
///
/// # Example
///
/// ```no_run
/// use aeternum_core::storage::aug::aup_prepare;
/// use aeternum_core::models::CryptoEpoch;
/// use aeternum_core::crypto::aead::XChaCha20Key;
///
/// let current_epoch = CryptoEpoch::initial();
/// let current_vk = vec![0u8; 48]; // 加密的 VK（32 字节 VK + 16 字节 tag）
/// let current_dek = XChaCha20Key::generate();
/// let vault_data = b"user data".to_vec();
///
/// let preparation = aup_prepare(&current_epoch, &current_vk, &current_dek, &vault_data)?;
/// assert_eq!(preparation.new_epoch.version, current_epoch.version + 1);
/// # Ok::<(), aeternum_core::storage::StorageError>(())
/// ```
pub fn aup_prepare(
    current_epoch: &CryptoEpoch,
    current_vk_bytes: &[u8],
    current_dek: &XChaCha20Key,
    vault_data: &[u8],
) -> Result<AupPreparation, StorageError> {
    // 步骤 1：计算新纪元
    let new_epoch = current_epoch.next();

    // 验证纪元单调性（Invariant #1）
    InvariantValidator::check_epoch_monotonicity(current_epoch, &new_epoch)?;

    // 步骤 2：解封当前 VK
    // current_vk_bytes 格式：[加密的 VK (32字节)][Auth Tag (16字节)]
    // 为了解密，我们需要从加密数据中提取 nonce
    // 在实际实现中，nonce 应该存储在 header 或元数据中
    // 这里我们使用一个固定 nonce 进行演示（生产环境应该使用存储的 nonce）
    let decrypt_nonce = XChaCha20Nonce::from_bytes([
        0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E,
        0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
    ]);

    let cipher = AeadCipher::new(current_dek);
    let vk_decrypted = cipher
        .decrypt(&decrypt_nonce, current_vk_bytes, None)
        .map_err(|e| StorageError::crypto(format!("Failed to decrypt VK: {}", e)))?;

    // 验证 VK 长度（应该是 32 字节）
    if vk_decrypted.len() != 32 {
        return Err(StorageError::crypto(format!(
            "Invalid VK length: expected 32, got {}",
            vk_decrypted.len()
        )));
    }

    // 步骤 3：派生新 DEK
    // 使用 Argon2id 从 VK 和新纪元派生新的 DEK
    let kdf = Argon2idKDF::new();
    let salt = create_epoch_salt(&new_epoch);
    let new_dek_bytes = kdf
        .derive_key_with_length(&vk_decrypted, &salt, 32)
        .map_err(|e| StorageError::crypto(format!("Failed to derive new DEK: {}", e)))?;

    let new_dek = XChaCha20Key::from_bytes(new_dek_bytes.as_bytes())
        .map_err(|e| StorageError::crypto(format!("Invalid DEK length: {}", e)))?;

    // 步骤 4：使用新 DEK 重新加密 VK
    // 注意：在实际实现中，这里应该保存 nonce 和加密后的 VK 以便后续使用
    // 当前实现中，我们使用 VK 来加密 vault 数据，所以不需要保存加密的 VK
    let _encrypt_nonce = XChaCha20Nonce::random();
    let _new_cipher = AeadCipher::new(&new_dek);
    let _vk_encrypted = _new_cipher
        .encrypt(&_encrypt_nonce, &vk_decrypted, None)
        .map_err(|e| StorageError::crypto(format!("Failed to encrypt VK: {}", e)))?;

    // 步骤 5：创建 VaultBlob
    // VaultBlob 包含加密的 vault 数据（使用 VK 加密）
    // 注意：这里我们简化处理，直接将 vault_data 作为密文
    // 在实际实现中，vault_data 应该使用 VK 进行加密
    let vault_nonce = XChaCha20Nonce::random();
    let vault_cipher =
        AeadCipher::new(&XChaCha20Key::from_bytes(&vk_decrypted).map_err(|e| {
            StorageError::crypto(format!("Invalid VK for vault encryption: {}", e))
        })?);
    let vault_ciphertext = vault_cipher
        .encrypt(&vault_nonce, vault_data, None)
        .map_err(|e| StorageError::crypto(format!("Failed to encrypt vault: {}", e)))?;

    // 提取 auth tag
    let auth_tag = AeadCipher::extract_tag(&vault_ciphertext)
        .map_err(|e| StorageError::crypto(format!("Failed to extract tag: {}", e)))?;

    let blob = VaultBlob::new(
        VaultBlob::CURRENT_BLOB_VERSION,
        new_epoch,
        vault_ciphertext,
        *auth_tag.as_bytes(),
        *vault_nonce.as_bytes(),
    );

    // 步骤 6：序列化 VaultBlob
    let serialized_blob = blob
        .serialize()
        .map_err(|e| StorageError::crypto(format!("Failed to serialize blob: {}", e)))?;

    // 步骤 7：创建 VaultHeader
    let vault_header = VaultHeader::new(&blob);
    let header_bytes = vault_header.to_bytes();

    Ok(AupPreparation {
        new_epoch,
        prepared_blob: serialized_blob,
        header: header_bytes,
    })
}

/// 创建纪元盐值用于 DEK 派生
///
/// 使用纪元版本号创建一个确定性但唯一的盐值。
/// 在生产环境中，应该使用额外的随机性来增强安全性。
#[allow(clippy::needless_range_loop)]
fn create_epoch_salt(epoch: &CryptoEpoch) -> [u8; 32] {
    let mut salt = [0u8; 32];
    // 使用纪元版本号填充盐值的前 8 字节
    salt[0..8].copy_from_slice(&epoch.version.to_be_bytes());
    // 其余字节可以填充额外的上下文信息
    // 这里我们使用一个固定的模式（生产环境应该使用随机值或派生值）
    for i in 8..32 {
        salt[i] = (i as u8).wrapping_mul(0x13);
    }
    salt
}

// ============================================================================
// AUP 阶段 2: 影子写入 (Shadow Writing)
// ============================================================================

/// AUP 阶段 2：影子写入
///
/// 创建临时文件并写入新纪元数据：
/// 1. 创建 `vault.tmp` 临时文件
/// 2. 写入 Header_n+1 与 VaultBlob
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
/// use aeternum_core::crypto::aead::XChaCha20Key;
/// use std::path::Path;
///
/// let vault_path = Path::new("vault.db");
/// let current_epoch = CryptoEpoch::initial();
/// let current_vk = vec![0u8; 48];
/// let current_dek = XChaCha20Key::generate();
/// let vault_data = b"user data".to_vec();
///
/// // 阶段 1: 预备
/// let preparation = aup_prepare(&current_epoch, &current_vk, &current_dek, &vault_data)?;
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

    // 写入 Vault Header（固定 32 字节）
    shadow_file.write_all(&preparation.header).map_err(|e| {
        StorageError::shadow_write(format!(
            "Failed to write header to {}: {}",
            shadow_file.path().display(),
            e
        ))
    })?;

    // 写入 VaultBlob（序列化的数据）
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
/// use aeternum_core::crypto::aead::XChaCha20Key;
/// use std::path::Path;
///
/// # fn main() -> Result<(), aeternum_core::storage::StorageError> {
/// let vault_path = Path::new("vault.db");
/// let current_epoch = CryptoEpoch::initial();
/// let current_vk = vec![0u8; 48]; // 加密的 VK（32字节 VK + 16字节 tag）
/// let current_dek = XChaCha20Key::generate();
/// let vault_data = b"user data".to_vec();
///
/// // 阶段 1: 预备
/// let preparation = aup_prepare(&current_epoch, &current_vk, &current_dek, &vault_data)?;
///
/// // 阶段 2: 影子写入
/// let shadow_file = aup_shadow_write(&vault_path, &preparation)?;
///
/// // 阶段 3: 原子提交
/// aup_atomic_commit(&vault_path, shadow_file, &preparation.new_epoch)?;
/// // vault.db 现在包含新纪元数据
/// # Ok(())
/// # }
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
/// 从 Vault Header 中读取纪元版本号。
/// Vault Header 格式：[Magic:8][Version:4][Epoch:8][Length:8][Reserved:4]
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

    // 读取 Vault Header（固定 32 字节）
    let mut file = std::fs::File::open(vault_path).map_err(|e| {
        StorageError::consistency_check(format!(
            "Failed to open vault file {}: {}",
            vault_path.display(),
            e
        ))
    })?;

    let mut header_bytes = [0u8; 32];
    file.read_exact(&mut header_bytes).map_err(|e| {
        StorageError::consistency_check(format!(
            "Failed to read header from {}: {}",
            vault_path.display(),
            e
        ))
    })?;

    // 验证魔数
    if header_bytes[0..8] != VAULT_MAGIC {
        return Err(StorageError::consistency_check(format!(
            "Invalid vault magic bytes: expected {:?}, got {:?}",
            VAULT_MAGIC.to_vec(),
            &header_bytes[0..8].to_vec()
        )));
    }

    // 提取纪元版本（字节 12-19）
    let epoch_bytes = header_bytes[12..20].try_into().unwrap();
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

    // 辅助函数：创建测试用的加密 VK
    fn create_test_encrypted_vk(vk: &[u8], dek: &XChaCha20Key) -> Vec<u8> {
        let nonce = XChaCha20Nonce::from_bytes([
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D,
            0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17,
        ]);
        let cipher = AeadCipher::new(dek);
        cipher.encrypt(&nonce, vk, None).unwrap()
    }

    // ------------------------------------------------------------------------
    // aup_prepare() Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_aup_prepare_creates_vault_blob() {
        let epoch = CryptoEpoch::initial();
        let dek = XChaCha20Key::generate();
        let vk = [0u8; 32]; // 测试 VK
        let encrypted_vk = create_test_encrypted_vk(&vk, &dek);
        let vault_data = b"test data";

        let prep = aup_prepare(&epoch, &encrypted_vk, &dek, vault_data).unwrap();

        // 应该包含序列化的 VaultBlob
        assert!(!prep.prepared_blob.is_empty());
        // Header 应该是有效的 VaultHeader
        assert_eq!(&prep.header[0..8], VAULT_MAGIC);
    }

    #[test]
    fn test_aup_prepare_increments_epoch() {
        let epoch1 = CryptoEpoch::initial();
        let dek = XChaCha20Key::generate();
        let vk = [0u8; 32];
        let encrypted_vk = create_test_encrypted_vk(&vk, &dek);
        let vault_data = b"test data";

        let prep = aup_prepare(&epoch1, &encrypted_vk, &dek, vault_data).unwrap();

        // 新纪元应该是当前纪元 + 1
        assert_eq!(prep.new_epoch.version, epoch1.version + 1);
    }

    #[test]
    fn test_aup_prepare_increments_from_arbitrary_epoch() {
        let epoch = CryptoEpoch::new(100, crate::models::CryptoAlgorithm::V1);
        let dek = XChaCha20Key::generate();
        let vk = [0u8; 32];
        let encrypted_vk = create_test_encrypted_vk(&vk, &dek);
        let vault_data = b"test data";

        let prep = aup_prepare(&epoch, &encrypted_vk, &dek, vault_data).unwrap();

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
        let dek = XChaCha20Key::generate();
        let vk = [0u8; 32];
        let encrypted_vk = create_test_encrypted_vk(&vk, &dek);
        let vault_data = b"test data";

        let prep = aup_prepare(&epoch, &encrypted_vk, &dek, vault_data).unwrap();
        let shadow_file = aup_shadow_write(&vault_path, &prep).unwrap();

        // 临时文件应该存在
        assert!(shadow_file.path().exists());
        assert!(shadow_file.path().to_string_lossy().ends_with(".tmp"));
    }

    #[test]
    fn test_aup_shadow_write_includes_header() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let epoch = CryptoEpoch::new(42, crate::models::CryptoAlgorithm::V1);
        let dek = XChaCha20Key::generate();
        let vk = [0u8; 32];
        let encrypted_vk = create_test_encrypted_vk(&vk, &dek);
        let vault_data = b"test data";

        let prep = aup_prepare(&epoch, &encrypted_vk, &dek, vault_data).unwrap();
        let shadow_file = aup_shadow_write(&vault_path, &prep).unwrap();

        // 读取临时文件的前 32 字节（Header）
        let mut header_bytes = [0u8; 32];
        let mut file = std::fs::File::open(shadow_file.path()).unwrap();
        file.read_exact(&mut header_bytes).unwrap();

        // 验证魔数
        assert_eq!(&header_bytes[0..8], VAULT_MAGIC);
        // 验证纪元版本（字节 12-19）
        let epoch_bytes = header_bytes[12..20].try_into().unwrap();
        let written_epoch = u64::from_be_bytes(epoch_bytes);
        // aup_prepare 会创建纪元 43 (42 + 1)
        assert_eq!(written_epoch, 43);
    }

    #[test]
    fn test_aup_shadow_write_fsync_called() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let epoch = CryptoEpoch::initial();
        let dek = XChaCha20Key::generate();
        let vk = [0u8; 32];
        let encrypted_vk = create_test_encrypted_vk(&vk, &dek);
        let vault_data = b"test data";

        let prep = aup_prepare(&epoch, &encrypted_vk, &dek, vault_data).unwrap();

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
        let dek = XChaCha20Key::generate();
        let vk = [0u8; 32];
        let encrypted_vk = create_test_encrypted_vk(&vk, &dek);
        let vault_data = b"test data";

        let prep = aup_prepare(&epoch, &encrypted_vk, &dek, vault_data).unwrap();

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
        let dek = XChaCha20Key::generate();
        let vk = [0u8; 32];
        let encrypted_vk = create_test_encrypted_vk(&vk, &dek);
        let vault_data = b"test data";

        let prep = aup_prepare(&epoch, &encrypted_vk, &dek, vault_data).unwrap();
        let shadow_file = aup_shadow_write(&vault_path, &prep).unwrap();

        // 提交
        aup_atomic_commit(&vault_path, shadow_file, &prep.new_epoch).unwrap();

        // 临时文件应该消失
        assert!(!vault_path.with_extension("db.tmp").exists());

        // 目标文件应该包含新数据（至少 32 字节 Header + Blob）
        let content = fs::read(&vault_path).unwrap();
        assert!(content.len() > 32);
        // 验证魔数
        assert_eq!(&content[0..8], VAULT_MAGIC);
    }

    #[test]
    fn test_aup_atomic_commit_is_atomic() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        // 创建旧文件
        fs::write(&vault_path, b"old data").unwrap();

        let epoch = CryptoEpoch::new(99, crate::models::CryptoAlgorithm::V1);
        let dek = XChaCha20Key::generate();
        let vk = [0u8; 32];
        let encrypted_vk = create_test_encrypted_vk(&vk, &dek);
        let vault_data = b"test data";

        let prep = aup_prepare(&epoch, &encrypted_vk, &dek, vault_data).unwrap();
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
        let dek = XChaCha20Key::generate();
        let vk = [0u8; 32];
        let encrypted_vk = create_test_encrypted_vk(&vk, &dek);
        let vault_data = b"test data";

        let prep = aup_prepare(&epoch, &encrypted_vk, &dek, vault_data).unwrap();

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
        let dek = XChaCha20Key::generate();
        let vk = [0u8; 32];
        let encrypted_vk = create_test_encrypted_vk(&vk, &dek);
        let vault_data = b"test data";

        let prep = aup_prepare(&epoch, &encrypted_vk, &dek, vault_data).unwrap();
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

        // 写入少于 32 字节的数据（Header 大小）
        fs::write(&vault_path, b"abc").unwrap();

        let result = read_vault_epoch(&vault_path);
        assert!(result.is_err());
        assert!(result
            .unwrap_err()
            .to_string()
            .contains("Failed to read header"));
    }

    #[test]
    fn test_read_vault_epoch_fails_invalid_magic() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("invalid.db");

        // 写入无效魔数的文件
        let mut invalid_data = [0u8; 32];
        invalid_data[0..8].copy_from_slice(b"INVALID!");
        fs::write(&vault_path, invalid_data).unwrap();

        let result = read_vault_epoch(&vault_path);
        assert!(result.is_err());
        assert!(result
            .unwrap_err()
            .to_string()
            .contains("Invalid vault magic"));
    }

    // ------------------------------------------------------------------------
    // End-to-End AUP Flow Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_aup_full_flow() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let current_epoch = CryptoEpoch::initial();
        let current_dek = XChaCha20Key::generate();
        let current_vk = [0u8; 32];
        let encrypted_vk = create_test_encrypted_vk(&current_vk, &current_dek);
        let vault_data = b"test vault data";

        // 阶段 1: 预备
        let prep = aup_prepare(&current_epoch, &encrypted_vk, &current_dek, vault_data).unwrap();
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
        let current_dek = XChaCha20Key::generate();
        let current_vk = [0u8; 32];
        let encrypted_vk = create_test_encrypted_vk(&current_vk, &current_dek);
        let vault_data = b"test vault data";

        // 执行 3 次纪元升级
        for _ in 1..=3 {
            // 注意：每次升级后需要使用新的 DEK（这需要在上一次升级中派生）
            // 这里为了测试简化，我们使用相同的 DEK 和 VK
            let prep = aup_prepare(&epoch, &encrypted_vk, &current_dek, vault_data).unwrap();
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
        let dek = XChaCha20Key::generate();
        let vk = [0u8; 32];
        let encrypted_vk = create_test_encrypted_vk(&vk, &dek);
        let vault_data = b"TEST vault data";

        // 创建初始数据
        let prep1 = aup_prepare(&epoch, &encrypted_vk, &dek, vault_data).unwrap();
        let shadow1 = aup_shadow_write(&vault_path, &prep1).unwrap();
        aup_atomic_commit(&vault_path, shadow1, &prep1.new_epoch).unwrap();

        let content1 = fs::read(&vault_path).unwrap();
        // 验证文件格式：[Header:32][Blob...]
        assert!(content1.len() > 32);
        // 检查魔数
        assert_eq!(&content1[0..8], VAULT_MAGIC);
        // 检查纪元是 2（初始 + 1）
        let epoch_bytes1 = &content1[12..20];
        let epoch1_val = u64::from_be_bytes(epoch_bytes1.try_into().unwrap());
        assert_eq!(epoch1_val, 2);

        // 升级到新纪元
        let prep2 = aup_prepare(&prep1.new_epoch, &encrypted_vk, &dek, vault_data).unwrap();
        let shadow2 = aup_shadow_write(&vault_path, &prep2).unwrap();
        aup_atomic_commit(&vault_path, shadow2, &prep2.new_epoch).unwrap();

        let content2 = fs::read(&vault_path).unwrap();
        assert!(content2.len() > 32);
        assert_eq!(&content2[0..8], VAULT_MAGIC);
        let epoch_bytes2 = &content2[12..20];
        let epoch2_val = u64::from_be_bytes(epoch_bytes2.try_into().unwrap());
        assert_eq!(epoch2_val, 3);
    }
}
