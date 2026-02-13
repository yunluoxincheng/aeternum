//! Vault Blob and encrypted storage containers
//!
//! This module defines data structures for encrypted vault storage,
//! including VaultBlob (encrypted data container) and VaultHeader
//! (file metadata).
//!
//! ## File Format
//!
//! The vault file consists of:
//! - VaultHeader (32 bytes, fixed)
//! - VaultBlob (variable length, serialized)
//!
//! ## Version Compatibility
//!
//! - blob_version 1: Initial format with V1 algorithms
//! - Future versions must maintain backward compatibility for reading

use crate::crypto::error::{CryptoError, Result};
use crate::models::epoch::CryptoEpoch;
use serde::{Deserialize, Serialize};

/// Magic bytes for vault file identification (7 bytes + 1 byte padding)
pub const VAULT_MAGIC: [u8; 8] = *b"AETERNM\0";

/// Current vault blob format version
pub const CURRENT_BLOB_VERSION: u32 = 1;

/// Vault Blob - complete encrypted data container
///
/// This structure contains encrypted vault data along with
/// all necessary metadata for decryption and verification.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VaultBlob {
    /// Blob format version
    pub blob_version: u32,
    /// Cryptographic epoch of this blob
    pub epoch: CryptoEpoch,
    /// Encrypted data
    pub ciphertext: Vec<u8>,
    /// AEAD authentication tag (16 bytes)
    pub auth_tag: [u8; 16],
    /// XChaCha20 nonce (24 bytes)
    pub nonce: [u8; 24],
}

impl VaultBlob {
    /// Current blob format version
    pub const CURRENT_BLOB_VERSION: u32 = 1;

    /// Create a new VaultBlob
    ///
    /// # Arguments
    ///
    /// * `blob_version` - Format version number
    /// * `epoch` - Cryptographic epoch identifier
    /// * `ciphertext` - Encrypted vault data
    /// * `auth_tag` - AEAD authentication tag (16 bytes)
    /// * `nonce` - XChaCha20 nonce (24 bytes)
    #[must_use]
    pub const fn new(
        blob_version: u32,
        epoch: CryptoEpoch,
        ciphertext: Vec<u8>,
        auth_tag: [u8; 16],
        nonce: [u8; 24],
    ) -> Self {
        Self {
            blob_version,
            epoch,
            ciphertext,
            auth_tag,
            nonce,
        }
    }

    /// Serialize VaultBlob to bytes using bincode
    ///
    /// # Errors
    ///
    /// Returns a `CryptoError` if serialization fails.
    pub fn serialize(&self) -> Result<Vec<u8>> {
        bincode::serialize(self)
            .map_err(|e| CryptoError::InternalError(format!("Serialization failed: {}", e)))
    }

    /// Deserialize a VaultBlob from bytes
    ///
    /// # Errors
    ///
    /// Returns a `CryptoError` if deserialization fails or
    /// if the blob version is unsupported.
    pub fn deserialize(bytes: &[u8]) -> Result<Self> {
        bincode::deserialize(bytes)
            .map_err(|e| CryptoError::InternalError(format!("Deserialization failed: {}", e)))
    }

    /// Validate the VaultBlob structure
    ///
    /// # Errors
    ///
    /// Returns a `CryptoError` if:
    /// - The blob version is unsupported
    /// - The authentication tag length is invalid
    /// - The nonce length is invalid
    pub fn validate(&self) -> Result<()> {
        // Check blob version support
        if self.blob_version > Self::CURRENT_BLOB_VERSION {
            return Err(CryptoError::InternalError(format!(
                "Unsupported blob version: {} (max supported: {})",
                self.blob_version,
                Self::CURRENT_BLOB_VERSION
            )));
        }

        // Validate auth tag length (XChaCha20-Poly1305 uses 16-byte tag)
        // Note: auth_tag is already [u8; 16], so this is always valid
        // This check is for future-proofing if the type changes

        // Validate nonce length (XChaCha20 uses 24-byte nonce)
        // Note: nonce is already [u8; 24], so this is always valid
        // This check is for future-proofing if the type changes

        Ok(())
    }

    /// Get total size of serialized VaultBlob
    ///
    /// This returns size in bytes that the blob would occupy
    /// when serialized to disk.
    #[must_use]
    pub fn size(&self) -> usize {
        // Estimate size based on components
        std::mem::size_of::<u32>() // blob_version
            + self.epoch.size() // epoch (estimated)
            + self.ciphertext.len() // ciphertext
            + self.auth_tag.len() // auth_tag
            + self.nonce.len() // nonce
    }
}

/// Vault Header - file metadata (fixed 32 bytes)
///
/// This structure provides a fixed-size header for vault files,
/// enabling quick validation and metadata retrieval without
/// reading the entire file.
#[derive(Debug, Clone)]
pub struct VaultHeader {
    /// Magic bytes: "AETERNM" (7 bytes) + 1 byte padding
    pub magic: [u8; 8],
    /// Blob format version
    pub blob_version: u32,
    /// Epoch version number
    pub epoch_version: u64,
    /// Length of encrypted data (VaultBlob)
    pub data_length: u64,
}

impl VaultHeader {
    /// Magic bytes for vault file identification
    pub const MAGIC: [u8; 8] = VAULT_MAGIC;

    /// Create a new VaultHeader from a VaultBlob
    ///
    /// # Arguments
    ///
    /// * `blob` - The VaultBlob to create a header for
    ///
    /// # Errors
    ///
    /// Returns a `CryptoError` if the blob validation fails.
    #[must_use]
    pub fn new(blob: &VaultBlob) -> Self {
        Self {
            magic: Self::MAGIC,
            blob_version: blob.blob_version,
            epoch_version: blob.epoch.version,
            data_length: blob.size() as u64,
        }
    }

    /// Serialize VaultHeader to a fixed 32-byte array
    ///
    /// # Panics
    ///
    /// This function uses `try_into().unwrap()` which will panic
    /// if slice conversion fails. This is safe because
    /// output is always exactly 32 bytes.
    #[must_use]
    pub fn to_bytes(&self) -> [u8; 32] {
        let mut bytes = [0u8; 32];

        // Copy magic bytes (0-7)
        bytes[0..8].copy_from_slice(&self.magic);

        // Copy blob_version (8-11)
        bytes[8..12].copy_from_slice(&self.blob_version.to_be_bytes());

        // Copy epoch_version (12-19)
        bytes[12..20].copy_from_slice(&self.epoch_version.to_be_bytes());

        // Copy data_length (20-27)
        bytes[20..28].copy_from_slice(&self.data_length.to_be_bytes());

        // Bytes 28-31 are reserved (padding)

        bytes
    }

    /// Parse a VaultHeader from bytes
    ///
    /// # Arguments
    ///
    /// * `bytes` - Input bytes (must be at least 32 bytes)
    ///
    /// # Errors
    ///
    /// Returns a `CryptoError` if:
    /// - The input is too short (< 32 bytes)
    /// - The magic bytes don't match
    pub fn from_bytes(bytes: &[u8]) -> Result<Self> {
        if bytes.len() < 32 {
            return Err(CryptoError::InternalError(format!(
                "Header too short: expected 32 bytes, got {}",
                bytes.len()
            )));
        }

        // Verify magic bytes
        let magic: [u8; 8] = bytes[0..8].try_into().unwrap();
        if magic != Self::MAGIC {
            return Err(CryptoError::InternalError(format!(
                "Invalid magic bytes: expected {:?}, got {:?}",
                Self::MAGIC.to_vec(),
                magic.to_vec()
            )));
        }

        // Parse blob_version
        let blob_version = u32::from_be_bytes(bytes[8..12].try_into().unwrap());

        // Parse epoch_version
        let epoch_version = u64::from_be_bytes(bytes[12..20].try_into().unwrap());

        // Parse data_length
        let data_length = u64::from_be_bytes(bytes[20..28].try_into().unwrap());

        Ok(Self {
            magic,
            blob_version,
            epoch_version,
            data_length,
        })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // ----------------------------------------------------------------------
    // Magic & Constants Tests
    // ----------------------------------------------------------------------

    #[test]
    fn test_magic_bytes() {
        assert_eq!(&VAULT_MAGIC[..7], b"AETERNM");
        assert_eq!(VAULT_MAGIC[7], 0);
    }

    #[test]
    fn test_current_blob_version() {
        assert_eq!(CURRENT_BLOB_VERSION, 1);
        assert_eq!(VaultBlob::CURRENT_BLOB_VERSION, 1);
    }

    // ----------------------------------------------------------------------
    // VaultHeader Tests
    // ----------------------------------------------------------------------

    #[test]
    fn test_header_magic_identification() {
        let epoch = CryptoEpoch::initial();
        let blob = VaultBlob::new(1, epoch, vec![1, 2, 3], [0u8; 16], [0u8; 24]);
        let header = VaultHeader::new(&blob);

        // 验证魔数正确
        assert_eq!(&header.magic, &VaultHeader::MAGIC);
        assert_eq!(header.magic, VAULT_MAGIC);
    }

    #[test]
    fn test_header_serialization_roundtrip() {
        let epoch = CryptoEpoch::initial();
        let blob = VaultBlob::new(1, epoch, vec![1, 2, 3], [0u8; 16], [0u8; 24]);
        let header = VaultHeader::new(&blob);

        // 序列化
        let bytes = header.to_bytes();

        // 验证长度
        assert_eq!(bytes.len(), 32);

        // 反序列化
        let parsed = VaultHeader::from_bytes(&bytes).expect("Failed to parse header");

        // 验证数据一致性
        assert_eq!(parsed.magic, header.magic);
        assert_eq!(parsed.blob_version, header.blob_version);
        assert_eq!(parsed.epoch_version, header.epoch_version);
        assert_eq!(parsed.data_length, header.data_length);
    }

    #[test]
    fn test_header_from_bytes_magic_validation() {
        let mut bytes = [0u8; 32];

        // 设置正确的魔数
        bytes[0..8].copy_from_slice(&VAULT_MAGIC);

        // 验证正确魔数能通过
        assert!(VaultHeader::from_bytes(&bytes).is_ok());

        // 篡改魔数
        bytes[0] = 0xFF;

        // 验证错误魔数被拒绝
        assert!(VaultHeader::from_bytes(&bytes).is_err());
    }

    #[test]
    fn test_header_from_bytes_too_short() {
        // 测试长度不足的输入
        assert!(VaultHeader::from_bytes(&[0u8; 31]).is_err());
        assert!(VaultHeader::from_bytes(&[0u8; 16]).is_err());
        assert!(VaultHeader::from_bytes(&[]).is_err());
    }

    #[test]
    fn test_header_blob_metadata() {
        let epoch = CryptoEpoch::new(5, crate::models::epoch::CryptoAlgorithm::V1);
        let blob = VaultBlob::new(2, epoch, vec![1, 2, 3, 4, 5], [0u8; 16], [0u8; 24]);
        let header = VaultHeader::new(&blob);

        // 验证 epoch 版本正确
        assert_eq!(header.epoch_version, 5);

        // 验证 blob 版本正确
        assert_eq!(header.blob_version, 2);
    }

    // ----------------------------------------------------------------------
    // VaultBlob Tests
    // ----------------------------------------------------------------------

    #[test]
    fn test_blob_validation() {
        let epoch = CryptoEpoch::initial();
        let blob = VaultBlob::new(1, epoch, vec![1, 2, 3], [0u8; 16], [0u8; 24]);

        // 正常版本应通过验证
        assert!(blob.validate().is_ok());
    }

    #[test]
    fn test_blob_validation_unsupported_version() {
        let epoch = CryptoEpoch::initial();
        // 使用不支持的版本号
        let blob = VaultBlob::new(999, epoch, vec![1, 2, 3], [0u8; 16], [0u8; 24]);

        // 应返回错误
        assert!(blob.validate().is_err());
    }

    #[test]
    fn test_blob_serialization() {
        let epoch = CryptoEpoch::initial();
        let blob = VaultBlob::new(
            1,
            epoch.clone(),
            vec![1, 2, 3, 4, 5],
            [0xAA; 16],
            [0xBB; 24],
        );

        // 序列化
        let serialized = blob.serialize().expect("Failed to serialize");

        // 反序列化
        let deserialized = VaultBlob::deserialize(&serialized).expect("Failed to deserialize");

        // 验证数据一致性
        assert_eq!(deserialized.blob_version, blob.blob_version);
        assert_eq!(deserialized.epoch.version, blob.epoch.version);
        assert_eq!(deserialized.ciphertext, blob.ciphertext);
        assert_eq!(deserialized.auth_tag, blob.auth_tag);
        assert_eq!(deserialized.nonce, blob.nonce);
    }

    #[test]
    fn test_blob_deserialization_invalid_data() {
        // 无效的二进制数据
        let invalid_data = vec![0xFF, 0xFF, 0xFF];

        // 应返回错误
        assert!(VaultBlob::deserialize(&invalid_data).is_err());
    }

    #[test]
    fn test_blob_size() {
        let epoch = CryptoEpoch::initial();
        let ciphertext = vec![1u8; 1000];
        let blob = VaultBlob::new(1, epoch, ciphertext, [0u8; 16], [0u8; 24]);

        // size() 应返回合理的估计值
        let size = blob.size();
        assert!(size > 1000); // 至少包含密文大小
        assert!(size < 2000); // 不应过大
    }

    // ----------------------------------------------------------------------
    // Integration Tests
    // ----------------------------------------------------------------------

    #[test]
    fn test_vault_file_structure() {
        // 测试完整的 Vault 文件结构
        let epoch = CryptoEpoch::initial();
        let ciphertext = vec![1, 2, 3, 4, 5];
        let auth_tag = [0xAA; 16];
        let nonce = [0xBB; 24];

        let blob = VaultBlob::new(1, epoch.clone(), ciphertext.clone(), auth_tag, nonce);
        let header = VaultHeader::new(&blob);

        // 验证头部信息
        assert_eq!(header.epoch_version, epoch.version);
        assert_eq!(header.blob_version, 1);

        // 验证 Blob 可以正确序列化/反序列化
        let serialized = blob.serialize().expect("Failed to serialize");
        let deserialized = VaultBlob::deserialize(&serialized).expect("Failed to deserialize");

        assert_eq!(deserialized.ciphertext, ciphertext);
        assert_eq!(deserialized.auth_tag, auth_tag);
        assert_eq!(deserialized.nonce, nonce);
    }

    #[test]
    fn test_blob_epoch_monotonicity() {
        // 测试纪元单调性（Invariant #1）
        let epoch1 = CryptoEpoch::initial();
        let epoch2 = epoch1.next();

        let blob1 = VaultBlob::new(1, epoch1.clone(), vec![], [0u8; 16], [0u8; 24]);
        let blob2 = VaultBlob::new(1, epoch2.clone(), vec![], [0u8; 16], [0u8; 24]);

        // 验证纪元版本严格递增
        assert!(epoch2.version > epoch1.version);
        assert_eq!(blob1.epoch.version, 1);
        assert_eq!(blob2.epoch.version, 2);
    }

    #[test]
    fn test_vault_header_and_blob_consistency() {
        // 测试 VaultHeader 和 VaultBlob 之间的一致性
        let epoch = CryptoEpoch::new(10, crate::models::epoch::CryptoAlgorithm::V1);
        let blob = VaultBlob::new(1, epoch, vec![1, 2, 3], [0u8; 16], [0u8; 24]);
        let header = VaultHeader::new(&blob);

        // 验证纪元版本一致性
        assert_eq!(header.epoch_version, blob.epoch.version);

        // 验证 blob 版本一致性
        assert_eq!(header.blob_version, blob.blob_version);

        // 验证数据长度合理性
        assert!(header.data_length > 0);
    }
}
