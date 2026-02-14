//! Cryptographic epoch and algorithm versioning
//!
//! This module defines the epoch system used to track cryptographic
//! algorithm versions and ensure monotonic progression.

use serde::{Deserialize, Serialize};

/// Cryptographic algorithm identifier
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum CryptoAlgorithm {
    /// v1: Kyber-1024 + X25519 + XChaCha20-Poly1305 + Argon2id + BLAKE3
    V1,
}

impl CryptoAlgorithm {
    /// Get the algorithm version number
    pub fn version(&self) -> u32 {
        match self {
            CryptoAlgorithm::V1 => 1,
        }
    }

    /// Check if this algorithm is supported
    pub fn is_supported(&self) -> bool {
        matches!(self, CryptoAlgorithm::V1)
    }
}

/// Cryptographic epoch - identifies the generation of keys
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct CryptoEpoch {
    /// Epoch version number (must be monotonically increasing)
    pub version: u64,
    /// Timestamp when this epoch was created (Unix timestamp in milliseconds)
    pub timestamp: u64,
    /// Algorithm used in this epoch
    pub algorithm: CryptoAlgorithm,
}

impl CryptoEpoch {
    /// Create a new epoch with the given version and algorithm
    pub fn new(version: u64, algorithm: CryptoAlgorithm) -> Self {
        Self {
            version,
            timestamp: std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .map(|d| d.as_millis() as u64)
                .unwrap_or(0),
            algorithm,
        }
    }

    /// Create the initial epoch (version 1)
    pub fn initial() -> Self {
        Self::new(1, CryptoAlgorithm::V1)
    }

    /// Create the next epoch (version + 1)
    pub fn next(&self) -> Self {
        Self::new(self.version + 1, self.algorithm)
    }

    /// Format epoch as a string
    pub fn as_string(&self) -> String {
        format!(
            "Epoch(v={}, algo=v{}, ts={})",
            self.version,
            self.algorithm.version(),
            self.timestamp
        )
    }

    /// Get estimated size of serialized epoch
    ///
    /// This returns an estimate of the number of bytes
    /// this epoch would occupy when serialized.
    #[must_use]
    pub fn size(&self) -> usize {
        std::mem::size_of::<u64>() // version
            + std::mem::size_of::<u64>() // timestamp
            + std::mem::size_of::<u32>() // algorithm (enum discriminant + value)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_crypto_algorithm_version() {
        assert_eq!(CryptoAlgorithm::V1.version(), 1);
    }

    #[test]
    fn test_crypto_algorithm_supported() {
        assert!(CryptoAlgorithm::V1.is_supported());
    }

    #[test]
    fn test_initial_epoch() {
        let epoch = CryptoEpoch::initial();
        assert_eq!(epoch.version, 1);
        assert_eq!(epoch.algorithm, CryptoAlgorithm::V1);
    }

    #[test]
    fn test_epoch_monotonicity() {
        let epoch1 = CryptoEpoch::initial();
        let epoch2 = epoch1.next();
        let epoch3 = epoch2.next();

        // 版本号必须严格递增
        assert_eq!(epoch1.version, 1);
        assert_eq!(epoch2.version, 2);
        assert_eq!(epoch3.version, 3);

        // 每个纪元的版本号必须大于前一个
        assert!(epoch2.version > epoch1.version);
        assert!(epoch3.version > epoch2.version);
    }

    #[test]
    fn test_epoch_algorithm_version() {
        let epoch = CryptoEpoch::initial();
        assert_eq!(epoch.algorithm.version(), 1);
    }

    #[test]
    fn test_epoch_serialization() {
        let epoch = CryptoEpoch::initial();

        // 序列化
        let serialized = bincode::serialize(&epoch).expect("Failed to serialize epoch");

        // 反序列化
        let deserialized: CryptoEpoch =
            bincode::deserialize(&serialized).expect("Failed to deserialize epoch");

        // 验证往返
        assert_eq!(epoch.version, deserialized.version);
        assert_eq!(epoch.algorithm, deserialized.algorithm);
    }

    #[test]
    fn test_epoch_as_string() {
        let epoch = CryptoEpoch::initial();
        let s = epoch.as_string();

        // 验证字符串格式包含版本信息
        assert!(s.contains("Epoch("));
        assert!(s.contains(&format!("v={}", epoch.version)));
        assert!(s.contains(&format!("algo=v{}", epoch.algorithm.version())));
    }

    #[test]
    fn test_epoch_rollback_detection() {
        use crate::crypto::error::CryptoError;

        let current_epoch = CryptoEpoch::initial();
        let next_epoch = current_epoch.next();

        // 验证纪元单调性：next epoch 的版本必须大于 current
        // 这实现了 Invariant #1: 纪元全局唯一性
        assert!(
            next_epoch.version > current_epoch.version,
            "Invariant violation: epoch must be monotonically increasing"
        );

        // 模拟回滚场景检测
        // 注意：这里只是演示检测逻辑，实际检测在 protocol 模块
        let old_version = current_epoch.version;
        let new_version = next_epoch.version;

        // 模拟违反 Invariant #1 的错误消息
        // 实际协议层会返回此错误
        let _error_msg = format!(
            "Invariant #1 violation: epoch monotonicity (current={}, proposed={})",
            old_version, new_version
        );

        // 创建对应的错误类型示例
        let _invariant_error = CryptoError::InvariantViolation(format!(
            "Epoch #{} is not greater than current #{}",
            new_version, old_version
        ));
    }
}
