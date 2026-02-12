//! # Kyber-1024 KEM Module
//!
//! This module provides post-quantum key encapsulation using Kyber-1024 (ML-KEM).
//!
//! ## Components
//!
//! - `KyberPublicKeyBytes`: 1184-byte public key
//! - `KyberSecretKeyBytes`: 2400-byte secret key (zeroizes on drop)
//! - `KyberCipherText`: 1568-byte encapsulated key
//! - `KyberSharedSecret`: 32-byte shared secret (zeroizes on drop)
//! - `KyberKeyPair`: Public/secret key pair
//! - `KyberKEM`: Encapsulation/decapsulation operations

use zeroize::{Zeroize, ZeroizeOnDrop};

/// Kyber-1024 public key (1184 bytes)
#[derive(Clone, PartialEq, Eq)]
pub struct KyberPublicKeyBytes(pub [u8; 1184]);

impl KyberPublicKeyBytes {
    /// Create from bytes
    pub fn from_bytes(bytes: &[u8]) -> Result<Self, crate::crypto::error::CryptoError> {
        if bytes.len() != 1184 {
            return Err(crate::crypto::error::CryptoError::InvalidKeyLength {
                expected: 1184,
                actual: bytes.len(),
            });
        }
        let mut key = [0u8; 1184];
        key.copy_from_slice(bytes);
        Ok(Self(key))
    }

    /// Get the key bytes
    pub fn as_bytes(&self) -> &[u8; 1184] {
        &self.0
    }
}

/// Kyber-1024 secret key (2400 bytes)
///
/// Automatically zeroizes on drop.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct KyberSecretKeyBytes(pub [u8; 2400]);

impl KyberSecretKeyBytes {
    /// Create from bytes
    pub fn from_bytes(bytes: &[u8]) -> Result<Self, crate::crypto::error::CryptoError> {
        if bytes.len() != 2400 {
            return Err(crate::crypto::error::CryptoError::InvalidKeyLength {
                expected: 2400,
                actual: bytes.len(),
            });
        }
        let mut key = [0u8; 2400];
        key.copy_from_slice(bytes);
        Ok(Self(key))
    }

    /// Get the key bytes
    pub fn as_bytes(&self) -> &[u8; 2400] {
        &self.0
    }
}

/// Kyber-1024 encapsulated key (1568 bytes)
#[derive(Clone, PartialEq, Eq)]
pub struct KyberCipherText(pub [u8; 1568]);

impl KyberCipherText {
    /// Create from bytes
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

    /// Get the ciphertext bytes
    pub fn as_bytes(&self) -> &[u8; 1568] {
        &self.0
    }
}

/// Kyber-1024 shared secret (32 bytes)
///
/// Automatically zeroizes on drop.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct KyberSharedSecret(pub [u8; 32]);

impl KyberSharedSecret {
    /// Create from bytes
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

    /// Get the secret bytes
    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.0
    }
}

/// Kyber-1024 key pair
pub struct KyberKeyPair {
    pub public: KyberPublicKeyBytes,
    pub secret: KyberSecretKeyBytes,
}

/// Kyber-1024 KEM operations
///
/// **NOTE**: This is a placeholder.
/// The full implementation will be in `kyber.rs`.
pub struct KyberKEM;

// TODO: Implement full KyberKEM in kyber.rs

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_public_key_length() {
        let bytes = [0u8; 1184];
        let key = KyberPublicKeyBytes::from_bytes(&bytes).unwrap();
        assert_eq!(key.as_bytes().len(), 1184);
    }

    #[test]
    fn test_public_key_invalid_length() {
        let result = KyberPublicKeyBytes::from_bytes(&[0u8; 100]);
        assert!(result.is_err());
    }
}
