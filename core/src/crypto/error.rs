//! # Cryptographic Error Types
//!
//! Unified error handling for all cryptographic operations in the Aeternum core.
//!
//! ## Design Principles
//!
//! - **No Information Leakage**: Error messages never contain sensitive data
//! - **Detailed Context**: Each error provides actionable information
//! - **Type Safety**: Strongly typed errors prevent silent failures

use thiserror::Error;

/// Result type alias for cryptographic operations
pub type Result<T> = std::result::Result<T, CryptoError>;

/// Unified error type for all cryptographic operations
///
/// All errors in the crypto module are represented by this enum,
/// ensuring consistent error handling and preventing sensitive data leakage.
#[derive(Debug, Error)]
pub enum CryptoError {
    /// Key derivation function failed
    ///
    /// This may occur due to:
    /// - Invalid parameters (memory/time costs)
    /// - Invalid input (salt too short, password empty)
    #[error("Key derivation failed: {0}")]
    KdfError(String),

    /// Authenticated encryption/decryption operation failed
    ///
    /// This may occur due to:
    /// - Authentication tag verification failure (tampering detected)
    /// - Invalid nonce length
    /// - Invalid key length
    #[error("AEAD operation failed: {0}")]
    AeadError(String),

    /// Key encapsulation/decapsulation operation failed
    ///
    /// This may occur due to:
    /// - Invalid ciphertext during decapsulation
    /// - Key generation failure (CSPRNG error)
    #[error("KEM encapsulation/decapsulation failed: {0}")]
    KemError(String),

    /// Elliptic curve Diffie-Hellman operation failed
    ///
    /// This may occur due to:
    /// - Invalid public key
    /// - Invalid private key
    #[error("ECDH operation failed: {0}")]
    EcdhError(String),

    /// Invalid key length provided
    ///
    /// Indicates that a key or nonce was provided with an incorrect length.
    /// Includes expected and actual lengths for debugging.
    #[error("Invalid key length: expected {expected}, got {actual}")]
    InvalidKeyLength {
        /// The expected key length in bytes
        expected: usize,
        /// The actual key length provided in bytes
        actual: usize,
    },

    /// Verification failed
    ///
    /// Indicates that an integrity check failed. This could be:
    /// - HMAC verification failure
    /// - Signature verification failure
    /// - MAC verification failure
    #[error("Verification failed: Data integrity cannot be guaranteed")]
    VerificationFailed,

    /// Internal cryptographic error
    ///
    /// Indicates an unexpected error in the underlying cryptographic library.
    /// The underlying error is wrapped for debugging purposes.
    #[error("Internal cryptographic error: {0}")]
    InternalError(String),
}

impl CryptoError {
    /// Create a KDF error from a string message
    pub fn kdf(msg: impl Into<String>) -> Self {
        Self::KdfError(msg.into())
    }

    /// Create an AEAD error from a string message
    pub fn aead(msg: impl Into<String>) -> Self {
        Self::AeadError(msg.into())
    }

    /// Create a KEM error from a string message
    pub fn kem(msg: impl Into<String>) -> Self {
        Self::KemError(msg.into())
    }

    /// Create an ECDH error from a string message
    pub fn ecdh(msg: impl Into<String>) -> Self {
        Self::EcdhError(msg.into())
    }

    /// Create an internal error from a string message
    pub fn internal(msg: impl Into<String>) -> Self {
        Self::InternalError(msg.into())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_error_display() {
        let err = CryptoError::InvalidKeyLength {
            expected: 32,
            actual: 16,
        };
        assert_eq!(err.to_string(), "Invalid key length: expected 32, got 16");
    }

    #[test]
    fn test_kdf_error() {
        let err = CryptoError::kdf("test failure");
        assert!(matches!(err, CryptoError::KdfError(_)));
    }

    #[test]
    fn test_verification_failed() {
        let err = CryptoError::VerificationFailed;
        assert_eq!(
            err.to_string(),
            "Verification failed: Data integrity cannot be guaranteed"
        );
    }
}
