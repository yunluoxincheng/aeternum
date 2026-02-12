//! # XChaCha20-Poly1305 AEAD Module
//!
//! This module provides authenticated encryption using XChaCha20-Poly1305.
//!
//! ## Components
//!
//! - `XChaCha20Key`: 32-byte encryption key (Zeroize on drop)
//! - `XChaCha20Nonce`: 24-byte nonce (safe for random generation)
//! - `AuthTag`: 16-byte authentication tag (Poly1305)
//! - `AeadCipher`: Encryption/decryption operations
//!
//! ## Security Properties
//!
//! - 256-bit key strength (XChaCha20)
//! - 192-bit nonce allows safe random nonce generation
//! - 128-bit authentication tag (Poly1305)
//! - Associated data (AAD) authentication support
//! - All secret keys are zeroized on drop
//!
//! ## Example
//!
//! ```
//! use aeternum_core::crypto::aead::{AeadCipher, XChaCha20Key, XChaCha20Nonce};
//!
//! let key = XChaCha20Key::generate();
//! let nonce = XChaCha20Nonce::random();
//! let cipher = AeadCipher::new(&key);
//!
//! let ciphertext = cipher.encrypt(&nonce, b"secret", None).unwrap();
//! let plaintext = cipher.decrypt(&nonce, &ciphertext, None).unwrap();
//! assert_eq!(plaintext, b"secret");
//! ```

mod xchacha20;

use rand::RngCore;
use zeroize::{Zeroize, ZeroizeOnDrop};

// Re-export the cipher implementation and helper functions
pub use xchacha20::{
    encrypt_and_zeroize, encrypt_with_random_nonce, AeadCipher, KEY_SIZE, NONCE_SIZE, TAG_SIZE,
};

/// XChaCha20-Poly1305 key (32 bytes)
///
/// This key automatically zeroizes when dropped, ensuring sensitive
/// key material doesn't remain in memory.
///
/// # Example
///
/// ```
/// use aeternum_core::crypto::aead::XChaCha20Key;
///
/// // Generate a new random key
/// let key = XChaCha20Key::generate();
///
/// // Or create from existing bytes
/// let bytes = [0u8; 32];
/// let key = XChaCha20Key::from_bytes(&bytes).unwrap();
/// ```
#[derive(Clone, Zeroize, ZeroizeOnDrop)]
pub struct XChaCha20Key([u8; 32]);

// Implement Debug manually to avoid leaking key material
impl std::fmt::Debug for XChaCha20Key {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("XChaCha20Key")
            .field("bytes", &"[REDACTED]")
            .finish()
    }
}

impl XChaCha20Key {
    /// Generate a new random key using the system CSPRNG.
    ///
    /// This is the recommended way to create encryption keys.
    pub fn generate() -> Self {
        use rand::rngs::OsRng;
        let mut bytes = [0u8; 32];
        OsRng.fill_bytes(&mut bytes);
        Self(bytes)
    }

    /// Create a key from raw bytes.
    ///
    /// # Arguments
    ///
    /// - `bytes`: Exactly 32 bytes of key material
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::InvalidKeyLength` if `bytes.len() != 32`.
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::crypto::aead::XChaCha20Key;
    ///
    /// let bytes = [42u8; 32];
    /// let key = XChaCha20Key::from_bytes(&bytes).unwrap();
    /// ```
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

    /// Get a reference to the key bytes.
    ///
    /// # Security Note
    ///
    /// Be careful not to copy or log the returned bytes.
    /// Use this method only when interfacing with low-level
    /// cryptographic APIs.
    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.0
    }
}

/// XChaCha20 nonce (24 bytes)
///
/// The 24-byte (192-bit) nonce is large enough that random generation
/// is safe without risk of collision. Each (key, nonce) pair must be
/// unique across all encryptions.
///
/// # Security Considerations
///
/// - Always use `random()` for new nonces unless you have a specific
///   nonce management scheme
/// - Never reuse a nonce with the same key
/// - The 24-byte size makes random nonce generation safe (unlike 12-byte
///   ChaCha20 nonces which require careful counter management)
///
/// # Example
///
/// ```
/// use aeternum_core::crypto::aead::XChaCha20Nonce;
///
/// // Generate a random nonce (recommended)
/// let nonce = XChaCha20Nonce::random();
///
/// // Or create from existing bytes
/// let bytes = [0u8; 24];
/// let nonce = XChaCha20Nonce::from_bytes(bytes);
/// ```
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct XChaCha20Nonce([u8; 24]);

impl XChaCha20Nonce {
    /// Generate a random nonce using the system CSPRNG.
    ///
    /// This is the recommended way to create nonces. The 24-byte
    /// nonce space is large enough that random generation is safe.
    pub fn random() -> Self {
        use rand::rngs::OsRng;
        let mut bytes = [0u8; 24];
        OsRng.fill_bytes(&mut bytes);
        Self(bytes)
    }

    /// Create a nonce from raw bytes.
    ///
    /// # Arguments
    ///
    /// - `bytes`: Exactly 24 bytes of nonce material
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::crypto::aead::XChaCha20Nonce;
    ///
    /// let bytes = [0u8; 24];
    /// let nonce = XChaCha20Nonce::from_bytes(bytes);
    /// ```
    pub fn from_bytes(bytes: [u8; 24]) -> Self {
        Self(bytes)
    }

    /// Try to create a nonce from a byte slice.
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::InvalidKeyLength` if `bytes.len() != 24`.
    pub fn try_from_slice(bytes: &[u8]) -> Result<Self, crate::crypto::error::CryptoError> {
        if bytes.len() != 24 {
            return Err(crate::crypto::error::CryptoError::InvalidKeyLength {
                expected: 24,
                actual: bytes.len(),
            });
        }
        let mut nonce = [0u8; 24];
        nonce.copy_from_slice(bytes);
        Ok(Self(nonce))
    }

    /// Get a reference to the nonce bytes.
    pub fn as_bytes(&self) -> &[u8; 24] {
        &self.0
    }
}

/// Authentication tag (16 bytes / 128 bits)
///
/// The Poly1305 authentication tag provides integrity verification
/// for the ciphertext and any associated data. If verification fails,
/// the ciphertext has been tampered with.
///
/// # Example
///
/// ```
/// use aeternum_core::crypto::aead::{AeadCipher, XChaCha20Key, XChaCha20Nonce, AuthTag};
///
/// let key = XChaCha20Key::generate();
/// let nonce = XChaCha20Nonce::random();
/// let cipher = AeadCipher::new(&key);
///
/// let ciphertext = cipher.encrypt(&nonce, b"data", None).unwrap();
/// let tag = AeadCipher::extract_tag(&ciphertext).unwrap();
/// assert_eq!(tag.as_bytes().len(), 16);
/// ```
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct AuthTag([u8; 16]);

impl AuthTag {
    /// Create a tag from raw bytes.
    ///
    /// # Arguments
    ///
    /// - `bytes`: Exactly 16 bytes of tag data
    pub fn from_bytes(bytes: [u8; 16]) -> Self {
        Self(bytes)
    }

    /// Try to create a tag from a byte slice.
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::InvalidKeyLength` if `bytes.len() != 16`.
    pub fn try_from_slice(bytes: &[u8]) -> Result<Self, crate::crypto::error::CryptoError> {
        if bytes.len() != 16 {
            return Err(crate::crypto::error::CryptoError::InvalidKeyLength {
                expected: 16,
                actual: bytes.len(),
            });
        }
        let mut tag = [0u8; 16];
        tag.copy_from_slice(bytes);
        Ok(Self(tag))
    }

    /// Get a reference to the tag bytes.
    pub fn as_bytes(&self) -> &[u8; 16] {
        &self.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // ── Key tests ───────────────────────────────────────────────────

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
    fn test_key_invalid_length_short() {
        let result = XChaCha20Key::from_bytes(&[0u8; 16]);
        assert!(result.is_err());
        match result.unwrap_err() {
            crate::crypto::error::CryptoError::InvalidKeyLength { expected, actual } => {
                assert_eq!(expected, 32);
                assert_eq!(actual, 16);
            }
            _ => panic!("Expected InvalidKeyLength error"),
        }
    }

    #[test]
    fn test_key_invalid_length_long() {
        let result = XChaCha20Key::from_bytes(&[0u8; 64]);
        assert!(result.is_err());
    }

    // ── Nonce tests ─────────────────────────────────────────────────

    #[test]
    fn test_nonce_random() {
        let nonce1 = XChaCha20Nonce::random();
        let nonce2 = XChaCha20Nonce::random();
        assert_ne!(
            nonce1.as_bytes(),
            nonce2.as_bytes(),
            "nonces should be unique"
        );
    }

    #[test]
    fn test_nonce_from_bytes() {
        let bytes = [0xABu8; 24];
        let nonce = XChaCha20Nonce::from_bytes(bytes);
        assert_eq!(nonce.as_bytes(), &bytes);
    }

    #[test]
    fn test_nonce_try_from_slice() {
        let bytes = vec![0xCDu8; 24];
        let nonce = XChaCha20Nonce::try_from_slice(&bytes).unwrap();
        assert_eq!(nonce.as_bytes(), bytes.as_slice());
    }

    #[test]
    fn test_nonce_try_from_slice_invalid_length() {
        let result = XChaCha20Nonce::try_from_slice(&[0u8; 12]);
        assert!(result.is_err());
    }

    // ── Tag tests ───────────────────────────────────────────────────

    #[test]
    fn test_tag_from_bytes() {
        let bytes = [0xEFu8; 16];
        let tag = AuthTag::from_bytes(bytes);
        assert_eq!(tag.as_bytes(), &bytes);
    }

    #[test]
    fn test_tag_try_from_slice() {
        let bytes = vec![0x12u8; 16];
        let tag = AuthTag::try_from_slice(&bytes).unwrap();
        assert_eq!(tag.as_bytes(), bytes.as_slice());
    }

    #[test]
    fn test_tag_try_from_slice_invalid_length() {
        let result = AuthTag::try_from_slice(&[0u8; 8]);
        assert!(result.is_err());
    }
}
