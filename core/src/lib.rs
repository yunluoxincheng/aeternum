//! # Aeternum Cryptographic Core
//!
//! This library provides the cryptographic foundation for the Aeternum key management system.
//!
//! ## Security Architecture
//!
//! The core is designed as the **root of trust** for the entire system:
//! - All cryptographic operations are performed in Rust
//! - All sensitive data structures implement `Zeroize`
//! - No plaintext keys ever cross the FFI boundary
//!
//! ## Module Organization
//!
//! - `crypto` - Cryptographic primitives (hash, KDF, AEAD, KEM, ECDH)
//! - `storage` - Shadow writing and crash consistency engine
//! - `sync` - Aeternum Wire protocol
//! - `models` - Epoch and Header data models
//!
//! ## Safety Guarantees
//!
//! - All secret keys are automatically zeroized on drop
//! - Memory is locked where possible (mlock support)
//! - Constant-time operations for secret data

#![warn(missing_docs)]
#![warn(unused_extern_crates)]
#![warn(unused_imports)]

/// Cryptographic primitives module
pub mod crypto;

/// Data models module
pub mod models;

/// Storage engine module (shadow write + crash recovery)
pub mod storage;

// Re-export common types at the crate root
pub use crypto::{error::CryptoError, error::Result};
pub use models::{
    device::{DeviceHeader, DeviceId, DeviceStatus},
    epoch::{CryptoAlgorithm, CryptoEpoch},
    key_hierarchy::{DataEncryptionKey, DeviceKey, IdentityKey, MasterSeed, RecoveryKey, VaultKey},
    vault::{VaultBlob, VaultHeader},
};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_module_organization() {
        // Basic sanity check that modules are accessible
        let _ = CryptoError::InternalError("test".to_string());
    }

    // ===== 阶段 6：模块集成测试 =====

    #[test]
    fn test_crypto_error_from_crate_root() {
        // 验证 CryptoError 可从 crate root 访问
        let err = CryptoError::InternalError("integration test".to_string());
        assert!(matches!(err, CryptoError::InternalError(_)));
    }

    #[test]
    fn test_models_types_from_crate_root() {
        // 验证所有 models 类型可从 crate root 访问

        // key_hierarchy 类型
        let _ = DataEncryptionKey([0u8; 32]);
        let _ = DeviceKey { key_id: [0u8; 16] };
        let _ = IdentityKey([0u8; 32]);
        let _ = RecoveryKey([0u8; 32]);
        let _ = VaultKey([0u8; 32]);

        // epoch 类型
        let algo = CryptoAlgorithm::V1;
        assert!(algo.is_supported());
        assert_eq!(algo.version(), 1);

        let epoch = CryptoEpoch::initial();
        assert_eq!(epoch.version, 1);

        // device 类型
        let device_id = DeviceId::shadow_anchor();
        assert!(device_id.is_shadow_anchor());

        let _ = DeviceStatus::Active;

        // vault 类型
        let blob = VaultBlob::new(
            1,
            CryptoEpoch::initial(),
            vec![1, 2, 3],
            [0u8; 16],
            [0u8; 24],
        );
        // size() 返回整个 Blob 的大小，包括 epoch + ciphertext + tag + nonce
        assert!(blob.size() > 3);
    }

    #[test]
    fn test_cross_module_type_usage() {
        // 测试跨模块类型使用：创建 DeviceHeader 需要 epoch 和 device 模块

        let epoch = CryptoEpoch::initial();
        let device_id = DeviceId::shadow_anchor();

        // 创建模拟公钥和密文（仅用于测试类型兼容性）
        use crate::crypto::kem::{KyberCipherText, KyberPublicKeyBytes};
        let public_key = KyberPublicKeyBytes([0u8; 1568]);
        let encrypted_dek = KyberCipherText([0u8; 1568]);

        let header = DeviceHeader::shadow_anchor(epoch.clone(), public_key, encrypted_dek);

        // 验证 Header 关联正确的纪元
        assert!(header.belongs_to_epoch(&epoch));

        // 验证 Header 关联正确的设备 ID
        assert_eq!(header.device_id, device_id);
    }

    #[test]
    fn test_module_visibility() {
        // 验证公共 API 的可见性

        // models 模块公共导出
        let _ = models::DeviceId::generate();
        let _ = models::CryptoEpoch::new(1, CryptoAlgorithm::V1);

        // key_hierarchy 子模块
        let _ = models::key_hierarchy::MasterSeed([0u8; 64]);

        // epoch 子模块
        let _ = models::epoch::CryptoAlgorithm::V1;

        // device 子模块
        let _ = models::device::DeviceId::shadow_anchor();

        // vault 子模块
        let _ = models::vault::VaultBlob::CURRENT_BLOB_VERSION;
    }

    #[test]
    fn test_key_hierarchy_with_epoch() {
        // 测试密钥层级与纪元的集成

        // 从助记词创建 MasterSeed（使用标准 24 词 BIP-39）
        // 使用已验证的有效 24 词助记词（来自 BIP-39 规范）
        let mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";
        let seed = MasterSeed::from_mnemonic(mnemonic).expect("从助记词创建种子失败");

        // 派生密钥
        let _ik = seed.derive_identity_key();
        let _rk = seed.derive_recovery_key();

        // 创建纪元
        let epoch = CryptoEpoch::new(1, CryptoAlgorithm::V1);

        // 验证纪元格式化
        let epoch_str = epoch.as_string();
        assert!(epoch_str.contains("v=1"));
        assert!(epoch_str.contains("algo=v1"));
    }

    #[test]
    fn test_vault_blob_serialization_integration() {
        // 测试 VaultBlob 序列化与反序列化

        let epoch = CryptoEpoch::initial();
        let ciphertext = vec![1, 2, 3, 4, 5];
        let auth_tag = [0u8; 16];
        let nonce = [0u8; 24];

        let blob = VaultBlob::new(1, epoch.clone(), ciphertext.clone(), auth_tag, nonce);

        // 序列化
        let serialized = blob.serialize().expect("序列化失败");
        assert!(!serialized.is_empty());

        // 反序列化
        let deserialized = VaultBlob::deserialize(&serialized).expect("反序列化失败");

        // 验证内容一致
        assert_eq!(deserialized.epoch.version, epoch.version);
        assert_eq!(deserialized.ciphertext, ciphertext);
    }

    #[test]
    fn test_device_header_with_vault_blob() {
        // 测试设备 Header 与 Vault Blob 的集成使用场景

        // 创建设备 Header
        let epoch = CryptoEpoch::initial();
        let device_id = DeviceId::generate();
        use crate::crypto::kem::{KyberCipherText, KyberPublicKeyBytes};
        let public_key = KyberPublicKeyBytes([1u8; 1568]);
        let encrypted_dek = KyberCipherText([2u8; 1568]);

        let header = DeviceHeader::new(device_id, epoch.clone(), public_key, encrypted_dek);

        // 验证 Header 状态默认为 Active
        assert!(matches!(header.status, DeviceStatus::Active));

        // 创建关联的 Vault Blob（模拟实际使用）
        let vault_data = vec![3, 4, 5];
        let blob = VaultBlob::new(1, epoch, vault_data, [0u8; 16], [0u8; 24]);

        assert_eq!(blob.epoch.version, header.epoch.version);
    }
}
