//! # XChaCha20-Poly1305 AEAD Module
//!
//! This module provides authenticated encryption using XChaCha20-Poly1305.
//!
//! ## Components
//!
//! - `XChaCha20Key`: 32-byte encryption key
//! - `XChaCha20Nonce`: 24-byte nonce
//! - `AuthTag`: 16-byte authentication tag
//! - `AeadCipher`: Encryption/decryption operations

use rand::RngCore;
use zeroize::{Zeroize, ZeroizeOnDrop};

/// XChaCha20-Poly1305 key (32 bytes)
///
/// This key automatically zeroizes when dropped.
#[derive(Clone, Zeroize, ZeroizeOnDrop)]
pub struct XChaCha20Key([u8; 32]);

impl XChaCha20Key {
    /// Generate a new random key
    pub fn generate() -> Self {
        use rand::rngs::OsRng;
        let mut bytes = [0u8; 32];
        OsRng.fill_bytes(&mut bytes);
        Self(bytes)
    }

    /// Create a key from bytes
    pub fn from_bytes(bytes: &[u8]) -> Result<Self, crate::crypto::error::CryptoError> {
        if bytes.len() != 32 {
            return Err(crate::crypto::error::CryptoError::InvalidKeyLength {
                expected: 32,
                actual: bytes.len(),
            });
        }
        let mut key = [0u8; 32];
        key.copy_from_slice(bytes);
        Ok(Self(key))
    }

    /// Get the key bytes
    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.0
    }
}

/// XChaCha20 nonce (24 bytes)
///
/// The nonce must be unique for each encryption with the same key.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct XChaCha20Nonce([u8; 24]);

impl XChaCha20Nonce {
    /// Generate a random nonce
    pub fn random() -> Self {
        use rand::rngs::OsRng;
        let mut bytes = [0u8; 24];
        OsRng.fill_bytes(&mut bytes);
        Self(bytes)
    }

    /// Create a nonce from bytes
    pub fn from_bytes(bytes: [u8; 24]) -> Self {
        Self(bytes)
    }

    /// Get the nonce bytes
    pub fn as_bytes(&self) -> &[u8; 24] {
        &self.0
    }
}

/// Authentication tag (16 bytes)
///
/// This tag provides integrity verification for the ciphertext.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct AuthTag([u8; 16]);

impl AuthTag {
    /// Create a tag from bytes
    pub fn from_bytes(bytes: [u8; 16]) -> Self {
        Self(bytes)
    }

    /// Get the tag bytes
    pub fn as_bytes(&self) -> &[u8; 16] {
        &self.0
    }
}

/// XChaCha20-Poly1305 AEAD cipher
///
/// **NOTE**: This is a placeholder.
/// The full implementation will be in `xchacha20.rs`.
pub struct AeadCipher;

// TODO: Implement full AeadCipher in xchacha20.rs

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_key_generation() {
        let key1 = XChaCha20Key::generate();
        let key2 = XChaCha20Key::generate();
        assert_ne!(key1.as_bytes(), key2.as_bytes(), "keys should be unique");
    }

    #[test]
    fn test_key_from_bytes() {
        let bytes = [42u8; 32];
        let key = XChaCha20Key::from_bytes(&bytes).unwrap();
        assert_eq!(key.as_bytes(), &bytes);
    }

    #[test]
    fn test_key_invalid_length() {
        let result = XChaCha20Key::from_bytes(&[0u8; 16]);
        assert!(result.is_err());
    }
}
