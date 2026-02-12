//! # BLAKE3 Hash Module
//!
//! This module provides BLAKE3 hashing and key derivation functionality.
//!
//! ## Components
//!
//! - [`HashOutput`]: 32-byte hash output type (implements `Zeroize`)
//! - [`Blake3Hasher`]: Incremental hasher with update/finalize API
//! - [`hash`]: One-shot convenience function
//! - [`DeriveKey`]: BLAKE3-based key derivation with domain separation

mod blake3;

use zeroize::{Zeroize, ZeroizeOnDrop};

// Re-export all public items from the blake3 submodule
pub use self::blake3::{hash, Blake3Hasher, DeriveKey};

/// 32-byte BLAKE3 hash output.
///
/// This newtype wrapper prevents accidental misuse with other 32-byte types.
/// Implements [`Zeroize`] and [`ZeroizeOnDrop`] to ensure hash values
/// used as key material are securely erased from memory.
///
/// Note: `HashOutput` is intentionally not `Copy` because it implements
/// `ZeroizeOnDrop`.
#[derive(Debug, Clone, PartialEq, Eq, Zeroize, ZeroizeOnDrop)]
pub struct HashOutput([u8; 32]);

impl HashOutput {
    /// Create a new `HashOutput` from a 32-byte array.
    pub fn from_bytes(bytes: [u8; 32]) -> Self {
        Self(bytes)
    }

    /// Get a reference to the underlying 32 bytes.
    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.0
    }

    /// Encode the hash as a lowercase hex string.
    pub fn to_hex(&self) -> String {
        hex::encode(self.0)
    }
}

impl AsRef<[u8]> for HashOutput {
    fn as_ref(&self) -> &[u8] {
        &self.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_hash_output_creation() {
        let bytes = [42u8; 32];
        let hash = HashOutput::from_bytes(bytes);
        assert_eq!(hash.as_bytes(), &bytes);
    }

    #[test]
    fn test_hash_output_zeroize() {
        let mut hash = HashOutput::from_bytes([0x42; 32]);
        hash.zeroize();
        assert_eq!(hash.as_bytes(), &[0u8; 32]);
    }

    #[test]
    fn test_hash_output_hex() {
        let hash = HashOutput::from_bytes([0xab; 32]);
        let hex = hash.to_hex();
        assert_eq!(hex.len(), 64);
        assert!(hex.chars().all(|c| c.is_ascii_hexdigit()));
    }

    #[test]
    fn test_hash_output_as_ref() {
        let hash = HashOutput::from_bytes([1u8; 32]);
        let slice: &[u8] = hash.as_ref();
        assert_eq!(slice.len(), 32);
        assert!(slice.iter().all(|&b| b == 1));
    }

    #[test]
    fn test_hash_output_clone_eq() {
        let hash1 = HashOutput::from_bytes([0x55; 32]);
        let hash2 = hash1.clone();
        assert_eq!(hash1, hash2);
    }
}
