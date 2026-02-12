//! # X25519 ECDH Module
//!
//! This module provides elliptic curve Diffie-Hellman key exchange using X25519.
//!
//! ## Components
//!
//! - `X25519PublicKeyBytes`: 32-byte public key
//! - `X25519SecretKeyBytes`: 32-byte secret key (zeroizes on drop)
//! - `EcdhSharedSecret`: 32-byte shared secret (zeroizes on drop)
//! - `X25519KeyPair`: Public/secret key pair
//! - `X25519ECDH`: Diffie-Hellman operations
//! - `HybridKeyExchange`: Hybrid KEX combining Kyber + X25519

use crate::crypto::kem::KyberSharedSecret;
use zeroize::{Zeroize, ZeroizeOnDrop};

/// X25519 public key (32 bytes)
#[derive(Clone, Copy, PartialEq, Eq)]
pub struct X25519PublicKeyBytes(pub [u8; 32]);

impl X25519PublicKeyBytes {
    /// Create from bytes
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

/// X25519 secret key (32 bytes)
///
/// Automatically zeroizes on drop.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct X25519SecretKeyBytes(pub [u8; 32]);

impl X25519SecretKeyBytes {
    /// Create from bytes
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

/// X25519 shared secret (32 bytes)
///
/// Automatically zeroizes on drop.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct EcdhSharedSecret(pub [u8; 32]);

impl EcdhSharedSecret {
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

/// X25519 key pair
pub struct X25519KeyPair {
    pub public: X25519PublicKeyBytes,
    pub secret: X25519SecretKeyBytes,
}

/// X25519 ECDH operations
///
/// **NOTE**: This is a placeholder.
/// The full implementation will be in `x25519.rs`.
pub struct X25519ECDH;

// TODO: Implement full X25519ECDH in x25519.rs

/// Hybrid shared secret combining Kyber and X25519
///
/// Automatically zeroizes on drop.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct HybridSharedSecret {
    pub kyber_secret: KyberSharedSecret,
    pub x25519_secret: EcdhSharedSecret,
    pub combined: [u8; 64],
}

/// Hybrid key exchange combining Kyber-1024 and X25519
///
/// **NOTE**: This is a placeholder.
/// The full implementation will be in `hybrid.rs`.
pub struct HybridKeyExchange;

// TODO: Implement full HybridKeyExchange in hybrid.rs

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_public_key_length() {
        let bytes = [0u8; 32];
        let key = X25519PublicKeyBytes::from_bytes(&bytes).unwrap();
        assert_eq!(key.as_bytes().len(), 32);
    }

    #[test]
    fn test_public_key_invalid_length() {
        let result = X25519PublicKeyBytes::from_bytes(&[0u8; 16]);
        assert!(result.is_err());
    }
}
