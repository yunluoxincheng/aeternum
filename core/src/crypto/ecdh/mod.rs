//! # X25519 ECDH Module
//!
//! This module provides elliptic curve Diffie-Hellman key exchange using X25519,
//! and hybrid key exchange combining Kyber-1024 + X25519 for defense-in-depth.
//!
//! ## Components
//!
//! - `X25519PublicKeyBytes`: 32-byte public key
//! - `X25519SecretKeyBytes`: 32-byte secret key (zeroizes on drop)
//! - `EcdhSharedSecret`: 32-byte shared secret (zeroizes on drop)
//! - `X25519KeyPair`: Public/secret key pair
//! - `X25519ECDH`: Diffie-Hellman operations
//! - `HybridKeyExchange`: Hybrid KEX combining Kyber + X25519
//! - `HybridSharedSecret`: Combined shared secret (zeroizes on drop)
//!
//! ## Example
//!
//! ```
//! use aeternum_core::crypto::ecdh::{X25519ECDH, X25519KeyPair};
//!
//! let alice = X25519ECDH::generate_keypair();
//! let bob = X25519ECDH::generate_keypair();
//!
//! let ss_a = X25519ECDH::diffie_hellman(&alice.secret, &bob.public).unwrap();
//! let ss_b = X25519ECDH::diffie_hellman(&bob.secret, &alice.public).unwrap();
//! assert_eq!(ss_a.as_bytes(), ss_b.as_bytes());
//! ```

mod x25519;

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
    /// The public key (safe to share)
    pub public: X25519PublicKeyBytes,
    /// The secret key (must be kept private, zeroizes on drop)
    pub secret: X25519SecretKeyBytes,
}

/// X25519 ECDH operations.
///
/// Provides key generation and Diffie-Hellman key agreement using
/// the X25519 elliptic curve. All operations are implemented as
/// associated functions (no instance state).
pub struct X25519ECDH;

/// Hybrid shared secret combining Kyber and X25519
///
/// Automatically zeroizes on drop.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct HybridSharedSecret {
    /// The Kyber-1024 shared secret component
    pub kyber_secret: KyberSharedSecret,
    /// The X25519 shared secret component
    pub x25519_secret: EcdhSharedSecret,
    /// The combined 64-byte secret derived via BLAKE3
    pub combined: [u8; 64],
}

/// Hybrid key exchange combining Kyber-1024 and X25519.
///
/// Provides defense-in-depth by combining a post-quantum KEM
/// with a classical ECDH. Even if one algorithm is compromised,
/// the combined secret remains secure.
pub struct HybridKeyExchange;

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

    #[test]
    fn test_secret_key_length() {
        let bytes = [0u8; 32];
        let key = X25519SecretKeyBytes::from_bytes(&bytes).unwrap();
        assert_eq!(key.as_bytes().len(), 32);
    }

    #[test]
    fn test_secret_key_invalid_length() {
        let result = X25519SecretKeyBytes::from_bytes(&[0u8; 16]);
        assert!(result.is_err());
    }

    #[test]
    fn test_shared_secret_length() {
        let bytes = [0u8; 32];
        let ss = EcdhSharedSecret::from_bytes(&bytes).unwrap();
        assert_eq!(ss.as_bytes().len(), 32);
    }

    #[test]
    fn test_shared_secret_invalid_length() {
        let result = EcdhSharedSecret::from_bytes(&[0u8; 16]);
        assert!(result.is_err());
    }
}
