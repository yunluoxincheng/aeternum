//! # Kyber-1024 KEM Module
//!
//! This module provides post-quantum key encapsulation using Kyber-1024 (ML-KEM)
//! via the PQClean reference implementation.
//!
//! ## Components
//!
//! - `KyberPublicKeyBytes`: 1568-byte public key
//! - `KyberSecretKeyBytes`: 3168-byte secret key (zeroizes on drop)
//! - `KyberCipherText`: 1568-byte encapsulated ciphertext
//! - `KyberSharedSecret`: 32-byte shared secret (zeroizes on drop)
//! - `KyberKeyPair`: Public/secret key pair
//! - `KyberKEM`: Encapsulation/decapsulation operations
//!
//! ## Key Sizes (pqcrypto-kyber 0.8.1 / PQClean)
//!
//! | Parameter    | Size (bytes) |
//! |-------------|-------------|
//! | Public key  | 1568        |
//! | Secret key  | 3168        |
//! | Ciphertext  | 1568        |
//! | Shared secret | 32        |
//!
//! ## Example
//!
//! ```
//! use aeternum_core::crypto::kem::{KyberKEM, KyberKeyPair};
//!
//! let keypair = KyberKEM::generate_keypair();
//! let (ss1, ct) = KyberKEM::encapsulate(&keypair.public).unwrap();
//! let ss2 = KyberKEM::decapsulate(&keypair.secret, &ct).unwrap();
//! assert_eq!(ss1.as_bytes(), ss2.as_bytes());
//! ```

mod kyber;

use zeroize::{Zeroize, ZeroizeOnDrop};

// Re-export constants from kyber module
pub use kyber::{CIPHERTEXT_SIZE, PUBLIC_KEY_SIZE, SECRET_KEY_SIZE, SHARED_SECRET_SIZE};

/// Kyber-1024 public key (1568 bytes, PQClean)
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct KyberPublicKeyBytes(pub [u8; 1568]);

impl KyberPublicKeyBytes {
    /// Create from a byte slice.
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::InvalidKeyLength` if `bytes.len() != 1568`.
    pub fn from_bytes(bytes: &[u8]) -> Result<Self, crate::crypto::error::CryptoError> {
        if bytes.len() != 1568 {
            return Err(crate::crypto::error::CryptoError::InvalidKeyLength {
                expected: 1568,
                actual: bytes.len(),
            });
        }
        let mut key = [0u8; 1568];
        key.copy_from_slice(bytes);
        Ok(Self(key))
    }

    /// Get the key bytes.
    pub fn as_bytes(&self) -> &[u8; 1568] {
        &self.0
    }
}

/// Kyber-1024 secret key (3168 bytes, PQClean)
///
/// Automatically zeroizes on drop to prevent secret key material
/// from persisting in memory.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct KyberSecretKeyBytes(pub [u8; 3168]);

impl KyberSecretKeyBytes {
    /// Create from a byte slice.
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::InvalidKeyLength` if `bytes.len() != 3168`.
    pub fn from_bytes(bytes: &[u8]) -> Result<Self, crate::crypto::error::CryptoError> {
        if bytes.len() != 3168 {
            return Err(crate::crypto::error::CryptoError::InvalidKeyLength {
                expected: 3168,
                actual: bytes.len(),
            });
        }
        let mut key = [0u8; 3168];
        key.copy_from_slice(bytes);
        Ok(Self(key))
    }

    /// Get the key bytes.
    pub fn as_bytes(&self) -> &[u8; 3168] {
        &self.0
    }
}

/// Kyber-1024 encapsulated ciphertext (1568 bytes)
#[derive(Clone, PartialEq, Eq)]
pub struct KyberCipherText(pub [u8; 1568]);

impl KyberCipherText {
    /// Create from a byte slice.
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::InvalidKeyLength` if `bytes.len() != 1568`.
    pub fn from_bytes(bytes: &[u8]) -> Result<Self, crate::crypto::error::CryptoError> {
        if bytes.len() != 1568 {
            return Err(crate::crypto::error::CryptoError::InvalidKeyLength {
                expected: 1568,
                actual: bytes.len(),
            });
        }
        let mut ct = [0u8; 1568];
        ct.copy_from_slice(bytes);
        Ok(Self(ct))
    }

    /// Get the ciphertext bytes.
    pub fn as_bytes(&self) -> &[u8; 1568] {
        &self.0
    }
}

/// Kyber-1024 shared secret (32 bytes)
///
/// Automatically zeroizes on drop to prevent shared secret material
/// from persisting in memory.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct KyberSharedSecret(pub [u8; 32]);

impl KyberSharedSecret {
    /// Create from a byte slice.
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::InvalidKeyLength` if `bytes.len() != 32`.
    pub fn from_bytes(bytes: &[u8]) -> Result<Self, crate::crypto::error::CryptoError> {
        if bytes.len() != 32 {
            return Err(crate::crypto::error::CryptoError::InvalidKeyLength {
                expected: 32,
                actual: bytes.len(),
            });
        }
        let mut secret = [0u8; 32];
        secret.copy_from_slice(bytes);
        Ok(Self(secret))
    }

    /// Get the secret bytes.
    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.0
    }
}

/// Kyber-1024 key pair containing public and secret keys.
pub struct KyberKeyPair {
    /// The public key (safe to share)
    pub public: KyberPublicKeyBytes,
    /// The secret key (must be kept private, zeroizes on drop)
    pub secret: KyberSecretKeyBytes,
}

/// Kyber-1024 KEM operations.
///
/// Provides key generation, encapsulation, and decapsulation using
/// the Kyber-1024 post-quantum key encapsulation mechanism.
///
/// All operations are implemented as associated functions (no instance state).
pub struct KyberKEM;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_public_key_from_bytes_valid() {
        let bytes = [0u8; 1568];
        let key = KyberPublicKeyBytes::from_bytes(&bytes).unwrap();
        assert_eq!(key.as_bytes().len(), 1568);
    }

    #[test]
    fn test_public_key_from_bytes_invalid_length() {
        let result = KyberPublicKeyBytes::from_bytes(&[0u8; 100]);
        assert!(result.is_err());
        match result.unwrap_err() {
            crate::crypto::error::CryptoError::InvalidKeyLength { expected, actual } => {
                assert_eq!(expected, 1568);
                assert_eq!(actual, 100);
            }
            _ => panic!("Expected InvalidKeyLength error"),
        }
    }

    #[test]
    fn test_secret_key_from_bytes_valid() {
        let bytes = [0u8; 3168];
        let key = KyberSecretKeyBytes::from_bytes(&bytes).unwrap();
        assert_eq!(key.as_bytes().len(), 3168);
    }

    #[test]
    fn test_secret_key_from_bytes_invalid_length() {
        let result = KyberSecretKeyBytes::from_bytes(&[0u8; 100]);
        assert!(result.is_err());
    }

    #[test]
    fn test_ciphertext_from_bytes_valid() {
        let bytes = [0u8; 1568];
        let ct = KyberCipherText::from_bytes(&bytes).unwrap();
        assert_eq!(ct.as_bytes().len(), 1568);
    }

    #[test]
    fn test_ciphertext_from_bytes_invalid_length() {
        let result = KyberCipherText::from_bytes(&[0u8; 100]);
        assert!(result.is_err());
    }

    #[test]
    fn test_shared_secret_from_bytes_valid() {
        let bytes = [0u8; 32];
        let ss = KyberSharedSecret::from_bytes(&bytes).unwrap();
        assert_eq!(ss.as_bytes().len(), 32);
    }

    #[test]
    fn test_shared_secret_from_bytes_invalid_length() {
        let result = KyberSharedSecret::from_bytes(&[0u8; 16]);
        assert!(result.is_err());
    }
}
