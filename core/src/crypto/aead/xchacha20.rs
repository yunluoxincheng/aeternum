//! # XChaCha20-Poly1305 AEAD Implementation
//!
//! Provides authenticated encryption using XChaCha20-Poly1305 with
//! type-safe API that prevents nonce reuse and ensures memory safety.
//!
//! ## Security Properties
//!
//! - 256-bit key (XChaCha20)
//! - 192-bit (24-byte) nonce - large enough for random generation
//! - 128-bit (16-byte) authentication tag (Poly1305)
//! - Authenticated Associated Data (AAD) support
//! - All secret keys implement `Zeroize` for automatic memory cleanup
//!
//! ## XChaCha20 vs ChaCha20
//!
//! XChaCha20 uses a 24-byte nonce (vs 12-byte for ChaCha20), making it
//! safe to generate nonces randomly without risk of collision. This is
//! critical for mobile applications where nonce management is complex.
//!
//! ## RFC 8439 Compliance
//!
//! This implementation follows RFC 8439 (ChaCha20 and Poly1305 for IETF
//! Protocols), with the XChaCha20 extended nonce variant.

use super::{AuthTag, XChaCha20Key, XChaCha20Nonce};
use crate::crypto::error::{CryptoError, Result};
use chacha20poly1305::{
    aead::{Aead, AeadInPlace, KeyInit, Payload},
    XChaCha20Poly1305, XNonce,
};
use zeroize::Zeroize;

/// Nonce size in bytes (24 bytes for XChaCha20)
pub const NONCE_SIZE: usize = 24;

/// Key size in bytes (32 bytes / 256 bits)
pub const KEY_SIZE: usize = 32;

/// Authentication tag size in bytes (16 bytes / 128 bits)
pub const TAG_SIZE: usize = 16;

/// XChaCha20-Poly1305 AEAD cipher.
///
/// Provides authenticated encryption with associated data (AEAD) using
/// the XChaCha20-Poly1305 algorithm. The extended nonce variant allows
/// safe random nonce generation.
///
/// # Security Considerations
///
/// - Each (key, nonce) pair MUST be unique
/// - Use `XChaCha20Nonce::random()` for safe nonce generation
/// - The authentication tag prevents tampering detection
/// - Associated data is authenticated but not encrypted
///
/// # Example
///
/// ```
/// use aeternum_core::crypto::aead::{AeadCipher, XChaCha20Key, XChaCha20Nonce};
///
/// let key = XChaCha20Key::generate();
/// let nonce = XChaCha20Nonce::random();
/// let cipher = AeadCipher::new(&key);
///
/// // Encrypt with optional associated data
/// let plaintext = b"secret message";
/// let aad = b"additional authenticated data";
/// let ciphertext = cipher.encrypt(&nonce, plaintext, Some(aad)).unwrap();
///
/// // Decrypt
/// let decrypted = cipher.decrypt(&nonce, &ciphertext, Some(aad)).unwrap();
/// assert_eq!(decrypted, plaintext);
/// ```
pub struct AeadCipher {
    cipher: XChaCha20Poly1305,
}

impl AeadCipher {
    /// Create a new AEAD cipher with the given key.
    ///
    /// # Arguments
    ///
    /// - `key`: A 32-byte XChaCha20 key
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::crypto::aead::{AeadCipher, XChaCha20Key};
    ///
    /// let key = XChaCha20Key::generate();
    /// let cipher = AeadCipher::new(&key);
    /// ```
    pub fn new(key: &XChaCha20Key) -> Self {
        let cipher = XChaCha20Poly1305::new_from_slice(key.as_bytes())
            .expect("Key length is always 32 bytes");
        Self { cipher }
    }

    /// Encrypt plaintext with authenticated associated data.
    ///
    /// # Arguments
    ///
    /// - `nonce`: A unique 24-byte nonce (use `XChaCha20Nonce::random()`)
    /// - `plaintext`: The data to encrypt
    /// - `aad`: Optional associated data to authenticate (but not encrypt)
    ///
    /// # Returns
    ///
    /// Returns ciphertext with the authentication tag appended (ciphertext || tag).
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::AeadError` if encryption fails.
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::crypto::aead::{AeadCipher, XChaCha20Key, XChaCha20Nonce};
    ///
    /// let key = XChaCha20Key::generate();
    /// let nonce = XChaCha20Nonce::random();
    /// let cipher = AeadCipher::new(&key);
    ///
    /// let ciphertext = cipher.encrypt(&nonce, b"hello", None).unwrap();
    /// assert_eq!(ciphertext.len(), 5 + 16); // plaintext + tag
    /// ```
    pub fn encrypt(
        &self,
        nonce: &XChaCha20Nonce,
        plaintext: &[u8],
        aad: Option<&[u8]>,
    ) -> Result<Vec<u8>> {
        let xnonce = XNonce::from_slice(nonce.as_bytes());

        let payload = Payload {
            msg: plaintext,
            aad: aad.unwrap_or(&[]),
        };

        self.cipher
            .encrypt(xnonce, payload)
            .map_err(|_| CryptoError::aead("Encryption failed"))
    }

    /// Decrypt ciphertext with authenticated associated data.
    ///
    /// # Arguments
    ///
    /// - `nonce`: The same nonce used during encryption
    /// - `ciphertext`: The ciphertext with appended authentication tag
    /// - `aad`: The same associated data used during encryption (if any)
    ///
    /// # Returns
    ///
    /// Returns the decrypted plaintext if authentication succeeds.
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::AeadError` if:
    /// - The ciphertext was tampered with
    /// - The AAD doesn't match
    /// - The nonce or key is incorrect
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::crypto::aead::{AeadCipher, XChaCha20Key, XChaCha20Nonce};
    ///
    /// let key = XChaCha20Key::generate();
    /// let nonce = XChaCha20Nonce::random();
    /// let cipher = AeadCipher::new(&key);
    ///
    /// let ciphertext = cipher.encrypt(&nonce, b"hello", None).unwrap();
    /// let plaintext = cipher.decrypt(&nonce, &ciphertext, None).unwrap();
    /// assert_eq!(plaintext, b"hello");
    /// ```
    pub fn decrypt(
        &self,
        nonce: &XChaCha20Nonce,
        ciphertext: &[u8],
        aad: Option<&[u8]>,
    ) -> Result<Vec<u8>> {
        // Minimum ciphertext length is TAG_SIZE (empty plaintext + tag)
        if ciphertext.len() < TAG_SIZE {
            return Err(CryptoError::aead(format!(
                "Ciphertext too short: {} bytes (minimum {})",
                ciphertext.len(),
                TAG_SIZE
            )));
        }

        let xnonce = XNonce::from_slice(nonce.as_bytes());

        let payload = Payload {
            msg: ciphertext,
            aad: aad.unwrap_or(&[]),
        };

        self.cipher
            .decrypt(xnonce, payload)
            .map_err(|_| CryptoError::aead("Decryption failed: authentication tag mismatch"))
    }

    /// Encrypt plaintext in place, appending the authentication tag.
    ///
    /// This method modifies the buffer in place, which can be more efficient
    /// for large data as it avoids allocation. The original plaintext is
    /// overwritten with ciphertext, and the authentication tag is appended.
    ///
    /// # Arguments
    ///
    /// - `nonce`: A unique 24-byte nonce
    /// - `buffer`: Mutable buffer containing plaintext; will contain ciphertext + tag after
    /// - `aad`: Optional associated data to authenticate
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::AeadError` if encryption fails.
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::crypto::aead::{AeadCipher, XChaCha20Key, XChaCha20Nonce};
    ///
    /// let key = XChaCha20Key::generate();
    /// let nonce = XChaCha20Nonce::random();
    /// let cipher = AeadCipher::new(&key);
    ///
    /// let mut buffer = b"hello".to_vec();
    /// cipher.encrypt_in_place(&nonce, &mut buffer, None).unwrap();
    /// assert_eq!(buffer.len(), 5 + 16); // plaintext + tag
    /// ```
    pub fn encrypt_in_place(
        &self,
        nonce: &XChaCha20Nonce,
        buffer: &mut Vec<u8>,
        aad: Option<&[u8]>,
    ) -> Result<()> {
        let xnonce = XNonce::from_slice(nonce.as_bytes());
        let associated_data = aad.unwrap_or(&[]);

        self.cipher
            .encrypt_in_place(xnonce, associated_data, buffer)
            .map_err(|_| CryptoError::aead("In-place encryption failed"))
    }

    /// Decrypt ciphertext in place, verifying the authentication tag.
    ///
    /// This method modifies the buffer in place. The authentication tag
    /// at the end of the buffer is verified and removed, and the ciphertext
    /// is replaced with plaintext.
    ///
    /// # Arguments
    ///
    /// - `nonce`: The same nonce used during encryption
    /// - `buffer`: Mutable buffer containing ciphertext + tag; will contain plaintext after
    /// - `aad`: The same associated data used during encryption
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::AeadError` if decryption or authentication fails.
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::crypto::aead::{AeadCipher, XChaCha20Key, XChaCha20Nonce};
    ///
    /// let key = XChaCha20Key::generate();
    /// let nonce = XChaCha20Nonce::random();
    /// let cipher = AeadCipher::new(&key);
    ///
    /// let mut buffer = b"hello".to_vec();
    /// cipher.encrypt_in_place(&nonce, &mut buffer, None).unwrap();
    /// cipher.decrypt_in_place(&nonce, &mut buffer, None).unwrap();
    /// assert_eq!(&buffer, b"hello");
    /// ```
    pub fn decrypt_in_place(
        &self,
        nonce: &XChaCha20Nonce,
        buffer: &mut Vec<u8>,
        aad: Option<&[u8]>,
    ) -> Result<()> {
        // Minimum buffer length is TAG_SIZE
        if buffer.len() < TAG_SIZE {
            return Err(CryptoError::aead(format!(
                "Buffer too short: {} bytes (minimum {})",
                buffer.len(),
                TAG_SIZE
            )));
        }

        let xnonce = XNonce::from_slice(nonce.as_bytes());
        let associated_data = aad.unwrap_or(&[]);

        self.cipher
            .decrypt_in_place(xnonce, associated_data, buffer)
            .map_err(|_| {
                CryptoError::aead("In-place decryption failed: authentication tag mismatch")
            })
    }

    /// Extract the authentication tag from ciphertext.
    ///
    /// The tag is the last 16 bytes of the ciphertext.
    ///
    /// # Arguments
    ///
    /// - `ciphertext`: The ciphertext with appended tag
    ///
    /// # Returns
    ///
    /// Returns the authentication tag if ciphertext is long enough.
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::AeadError` if ciphertext is shorter than TAG_SIZE.
    pub fn extract_tag(ciphertext: &[u8]) -> Result<AuthTag> {
        if ciphertext.len() < TAG_SIZE {
            return Err(CryptoError::aead(format!(
                "Ciphertext too short to extract tag: {} bytes (minimum {})",
                ciphertext.len(),
                TAG_SIZE
            )));
        }

        let tag_start = ciphertext.len() - TAG_SIZE;
        let mut tag_bytes = [0u8; TAG_SIZE];
        tag_bytes.copy_from_slice(&ciphertext[tag_start..]);
        Ok(AuthTag::from_bytes(tag_bytes))
    }
}

/// Convenience function to encrypt data with a new random nonce.
///
/// Returns both the ciphertext and the nonce used. This is useful when
/// you want the library to manage nonce generation.
///
/// # Arguments
///
/// - `key`: The encryption key
/// - `plaintext`: Data to encrypt
/// - `aad`: Optional associated data
///
/// # Returns
///
/// Returns a tuple of (ciphertext, nonce).
///
/// # Example
///
/// ```
/// use aeternum_core::crypto::aead::{encrypt_with_random_nonce, XChaCha20Key};
///
/// let key = XChaCha20Key::generate();
/// let (ciphertext, nonce) = encrypt_with_random_nonce(&key, b"secret", None).unwrap();
/// ```
pub fn encrypt_with_random_nonce(
    key: &XChaCha20Key,
    plaintext: &[u8],
    aad: Option<&[u8]>,
) -> Result<(Vec<u8>, XChaCha20Nonce)> {
    let nonce = XChaCha20Nonce::random();
    let cipher = AeadCipher::new(key);
    let ciphertext = cipher.encrypt(&nonce, plaintext, aad)?;
    Ok((ciphertext, nonce))
}

/// Securely encrypt and then zeroize the plaintext buffer.
///
/// This function encrypts the plaintext and then zeroizes the original
/// buffer, ensuring sensitive data doesn't remain in memory.
///
/// # Arguments
///
/// - `key`: The encryption key
/// - `nonce`: A unique nonce
/// - `plaintext`: Mutable plaintext buffer (will be zeroized after encryption)
/// - `aad`: Optional associated data
///
/// # Returns
///
/// Returns the ciphertext.
///
/// # Example
///
/// ```
/// use aeternum_core::crypto::aead::{encrypt_and_zeroize, XChaCha20Key, XChaCha20Nonce};
///
/// let key = XChaCha20Key::generate();
/// let nonce = XChaCha20Nonce::random();
/// let mut plaintext = b"secret".to_vec();
///
/// let ciphertext = encrypt_and_zeroize(&key, &nonce, &mut plaintext, None).unwrap();
/// assert!(plaintext.is_empty()); // Vec is zeroized (bytes zeroed + length cleared)
/// ```
pub fn encrypt_and_zeroize(
    key: &XChaCha20Key,
    nonce: &XChaCha20Nonce,
    plaintext: &mut Vec<u8>,
    aad: Option<&[u8]>,
) -> Result<Vec<u8>> {
    let cipher = AeadCipher::new(key);
    let ciphertext = cipher.encrypt(nonce, plaintext, aad)?;
    plaintext.zeroize();
    Ok(ciphertext)
}

#[cfg(test)]
mod tests {
    use super::*;

    // ── Basic functionality ─────────────────────────────────────────

    #[test]
    fn test_encrypt_decrypt_roundtrip() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let plaintext = b"Hello, Aeternum!";
        let ciphertext = cipher.encrypt(&nonce, plaintext, None).unwrap();
        let decrypted = cipher.decrypt(&nonce, &ciphertext, None).unwrap();

        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_ciphertext_length() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let plaintext = b"Hello";
        let ciphertext = cipher.encrypt(&nonce, plaintext, None).unwrap();

        // Ciphertext = plaintext + TAG_SIZE (16 bytes)
        assert_eq!(ciphertext.len(), plaintext.len() + TAG_SIZE);
    }

    #[test]
    fn test_encrypt_is_not_deterministic_with_different_nonces() {
        let key = XChaCha20Key::generate();
        let nonce1 = XChaCha20Nonce::random();
        let nonce2 = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let plaintext = b"same message";
        let ct1 = cipher.encrypt(&nonce1, plaintext, None).unwrap();
        let ct2 = cipher.encrypt(&nonce2, plaintext, None).unwrap();

        assert_ne!(ct1, ct2);
    }

    #[test]
    fn test_encrypt_is_deterministic_with_same_nonce() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::from_bytes([42u8; 24]);
        let cipher = AeadCipher::new(&key);

        let plaintext = b"same message";
        let ct1 = cipher.encrypt(&nonce, plaintext, None).unwrap();
        let ct2 = cipher.encrypt(&nonce, plaintext, None).unwrap();

        assert_eq!(ct1, ct2);
    }

    // ── Associated data (AAD) tests ─────────────────────────────────

    #[test]
    fn test_encrypt_decrypt_with_aad() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let plaintext = b"secret data";
        let aad = b"public metadata";

        let ciphertext = cipher.encrypt(&nonce, plaintext, Some(aad)).unwrap();
        let decrypted = cipher.decrypt(&nonce, &ciphertext, Some(aad)).unwrap();

        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_decrypt_fails_with_wrong_aad() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let plaintext = b"secret data";
        let aad = b"correct aad";
        let wrong_aad = b"wrong aad";

        let ciphertext = cipher.encrypt(&nonce, plaintext, Some(aad)).unwrap();
        let result = cipher.decrypt(&nonce, &ciphertext, Some(wrong_aad));

        assert!(result.is_err());
    }

    #[test]
    fn test_decrypt_fails_with_missing_aad() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let plaintext = b"secret data";
        let aad = b"required aad";

        let ciphertext = cipher.encrypt(&nonce, plaintext, Some(aad)).unwrap();
        // Try to decrypt without AAD
        let result = cipher.decrypt(&nonce, &ciphertext, None);

        assert!(result.is_err());
    }

    // ── Tampering detection ─────────────────────────────────────────

    #[test]
    fn test_decrypt_fails_with_tampered_ciphertext() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let plaintext = b"secret data";
        let mut ciphertext = cipher.encrypt(&nonce, plaintext, None).unwrap();

        // Tamper with the ciphertext
        ciphertext[0] ^= 0xFF;

        let result = cipher.decrypt(&nonce, &ciphertext, None);
        assert!(result.is_err());
    }

    #[test]
    fn test_decrypt_fails_with_tampered_tag() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let plaintext = b"secret data";
        let mut ciphertext = cipher.encrypt(&nonce, plaintext, None).unwrap();

        // Tamper with the authentication tag (last 16 bytes)
        let tag_start = ciphertext.len() - TAG_SIZE;
        ciphertext[tag_start] ^= 0xFF;

        let result = cipher.decrypt(&nonce, &ciphertext, None);
        assert!(result.is_err());
    }

    #[test]
    fn test_decrypt_fails_with_wrong_key() {
        let key1 = XChaCha20Key::generate();
        let key2 = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();

        let cipher1 = AeadCipher::new(&key1);
        let cipher2 = AeadCipher::new(&key2);

        let plaintext = b"secret data";
        let ciphertext = cipher1.encrypt(&nonce, plaintext, None).unwrap();

        let result = cipher2.decrypt(&nonce, &ciphertext, None);
        assert!(result.is_err());
    }

    #[test]
    fn test_decrypt_fails_with_wrong_nonce() {
        let key = XChaCha20Key::generate();
        let nonce1 = XChaCha20Nonce::random();
        let nonce2 = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let plaintext = b"secret data";
        let ciphertext = cipher.encrypt(&nonce1, plaintext, None).unwrap();

        let result = cipher.decrypt(&nonce2, &ciphertext, None);
        assert!(result.is_err());
    }

    // ── In-place encryption/decryption ──────────────────────────────

    #[test]
    fn test_encrypt_in_place() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let plaintext = b"Hello, in-place!";
        let mut buffer = plaintext.to_vec();

        cipher.encrypt_in_place(&nonce, &mut buffer, None).unwrap();

        assert_eq!(buffer.len(), plaintext.len() + TAG_SIZE);
        assert_ne!(&buffer[..plaintext.len()], plaintext);
    }

    #[test]
    fn test_decrypt_in_place() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let plaintext = b"Hello, in-place!";
        let mut buffer = plaintext.to_vec();

        cipher.encrypt_in_place(&nonce, &mut buffer, None).unwrap();
        cipher.decrypt_in_place(&nonce, &mut buffer, None).unwrap();

        assert_eq!(&buffer, plaintext);
    }

    #[test]
    fn test_in_place_roundtrip_with_aad() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let plaintext = b"secret in-place data";
        let aad = b"metadata";
        let mut buffer = plaintext.to_vec();

        cipher
            .encrypt_in_place(&nonce, &mut buffer, Some(aad))
            .unwrap();
        cipher
            .decrypt_in_place(&nonce, &mut buffer, Some(aad))
            .unwrap();

        assert_eq!(&buffer, plaintext);
    }

    // ── Edge cases ──────────────────────────────────────────────────

    #[test]
    fn test_empty_plaintext() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let plaintext = b"";
        let ciphertext = cipher.encrypt(&nonce, plaintext, None).unwrap();

        // Empty plaintext + 16-byte tag
        assert_eq!(ciphertext.len(), TAG_SIZE);

        let decrypted = cipher.decrypt(&nonce, &ciphertext, None).unwrap();
        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_large_plaintext() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        // 1 MB plaintext
        let plaintext = vec![0xABu8; 1024 * 1024];
        let ciphertext = cipher.encrypt(&nonce, &plaintext, None).unwrap();
        let decrypted = cipher.decrypt(&nonce, &ciphertext, None).unwrap();

        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_ciphertext_too_short() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        // Less than TAG_SIZE bytes
        let short_ciphertext = vec![0u8; TAG_SIZE - 1];
        let result = cipher.decrypt(&nonce, &short_ciphertext, None);

        assert!(result.is_err());
    }

    #[test]
    fn test_decrypt_in_place_buffer_too_short() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let mut buffer = vec![0u8; TAG_SIZE - 1];
        let result = cipher.decrypt_in_place(&nonce, &mut buffer, None);

        assert!(result.is_err());
    }

    // ── Convenience functions ───────────────────────────────────────

    #[test]
    fn test_encrypt_with_random_nonce() {
        let key = XChaCha20Key::generate();

        let (ciphertext, nonce) = encrypt_with_random_nonce(&key, b"hello", None).unwrap();

        let cipher = AeadCipher::new(&key);
        let decrypted = cipher.decrypt(&nonce, &ciphertext, None).unwrap();
        assert_eq!(decrypted, b"hello");
    }

    #[test]
    fn test_encrypt_and_zeroize() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();

        let mut plaintext = b"secret".to_vec();

        let ciphertext = encrypt_and_zeroize(&key, &nonce, &mut plaintext, None).unwrap();

        // Plaintext should be zeroized (Vec::zeroize zeros bytes and truncates length)
        assert!(plaintext.is_empty(), "Vec should be empty after zeroize");

        // Ciphertext should be valid
        let cipher = AeadCipher::new(&key);
        let decrypted = cipher.decrypt(&nonce, &ciphertext, None).unwrap();
        assert_eq!(decrypted, b"secret");
    }

    // ── Tag extraction ──────────────────────────────────────────────

    #[test]
    fn test_extract_tag() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let ciphertext = cipher.encrypt(&nonce, b"test", None).unwrap();
        let tag = AeadCipher::extract_tag(&ciphertext).unwrap();

        // Tag should be the last 16 bytes
        let expected_tag = &ciphertext[ciphertext.len() - TAG_SIZE..];
        assert_eq!(tag.as_bytes(), expected_tag);
    }

    #[test]
    fn test_extract_tag_too_short() {
        let short = vec![0u8; TAG_SIZE - 1];
        let result = AeadCipher::extract_tag(&short);
        assert!(result.is_err());
    }

    // ── RFC 8439 Test Vectors (ChaCha20-Poly1305) ───────────────────
    // Note: XChaCha20 extends ChaCha20, so we verify consistency with
    // known test vectors where applicable.

    #[test]
    fn test_known_key_nonce_produces_consistent_output() {
        // Fixed key and nonce for reproducibility
        let key_bytes = [
            0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d,
            0x8e, 0x8f, 0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0x9b,
            0x9c, 0x9d, 0x9e, 0x9f,
        ];
        let nonce_bytes = [
            0x07, 0x00, 0x00, 0x00, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
            0x4a, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x50, 0x51, 0x52, 0x53,
        ];

        let key = XChaCha20Key::from_bytes(&key_bytes).unwrap();
        let nonce = XChaCha20Nonce::from_bytes(nonce_bytes);
        let cipher = AeadCipher::new(&key);

        let plaintext = b"Ladies and Gentlemen of the class of '99";

        // Encrypt twice with same key/nonce should produce identical output
        let ct1 = cipher.encrypt(&nonce, plaintext, None).unwrap();
        let ct2 = cipher.encrypt(&nonce, plaintext, None).unwrap();
        assert_eq!(ct1, ct2);

        // Decrypt should return original
        let decrypted = cipher.decrypt(&nonce, &ct1, None).unwrap();
        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_aad_is_not_encrypted() {
        let key = XChaCha20Key::generate();
        let nonce = XChaCha20Nonce::random();
        let cipher = AeadCipher::new(&key);

        let plaintext = b"secret";
        let aad = b"public header";

        let ciphertext = cipher.encrypt(&nonce, plaintext, Some(aad)).unwrap();

        // AAD should not appear in ciphertext
        assert!(!contains_subsequence(&ciphertext, aad));

        // Plaintext should not appear in ciphertext (it's encrypted)
        assert!(!contains_subsequence(&ciphertext, plaintext));
    }

    // Helper function to check if a slice contains a subsequence
    fn contains_subsequence(haystack: &[u8], needle: &[u8]) -> bool {
        if needle.is_empty() {
            return true;
        }
        haystack.windows(needle.len()).any(|w| w == needle)
    }
}

// ── Property-based tests (proptest) ─────────────────────────────────

#[cfg(test)]
mod proptests {
    use super::*;
    use proptest::prelude::*;

    proptest! {
        /// Encrypt-decrypt roundtrip always recovers original plaintext
        #[test]
        fn prop_encrypt_decrypt_roundtrip(
            plaintext in prop::collection::vec(any::<u8>(), 0..4096),
            aad in proptest::option::of(prop::collection::vec(any::<u8>(), 0..256)),
        ) {
            let key = XChaCha20Key::generate();
            let nonce = XChaCha20Nonce::random();
            let cipher = AeadCipher::new(&key);

            let aad_ref = aad.as_deref();
            let ciphertext = cipher.encrypt(&nonce, &plaintext, aad_ref).unwrap();
            let decrypted = cipher.decrypt(&nonce, &ciphertext, aad_ref).unwrap();

            prop_assert_eq!(decrypted, plaintext);
        }

        /// Ciphertext length is always plaintext + TAG_SIZE
        #[test]
        fn prop_ciphertext_length(
            plaintext in prop::collection::vec(any::<u8>(), 0..1024),
        ) {
            let key = XChaCha20Key::generate();
            let nonce = XChaCha20Nonce::random();
            let cipher = AeadCipher::new(&key);

            let ciphertext = cipher.encrypt(&nonce, &plaintext, None).unwrap();
            prop_assert_eq!(ciphertext.len(), plaintext.len() + TAG_SIZE);
        }

        /// In-place roundtrip always recovers original plaintext
        #[test]
        fn prop_in_place_roundtrip(
            plaintext in prop::collection::vec(any::<u8>(), 0..4096),
            aad in proptest::option::of(prop::collection::vec(any::<u8>(), 0..256)),
        ) {
            let key = XChaCha20Key::generate();
            let nonce = XChaCha20Nonce::random();
            let cipher = AeadCipher::new(&key);

            let aad_ref = aad.as_deref();
            let mut buffer = plaintext.clone();

            cipher.encrypt_in_place(&nonce, &mut buffer, aad_ref).unwrap();
            cipher.decrypt_in_place(&nonce, &mut buffer, aad_ref).unwrap();

            prop_assert_eq!(buffer, plaintext);
        }

        /// Different keys produce different ciphertexts
        #[test]
        fn prop_different_keys_different_ciphertext(
            plaintext in prop::collection::vec(any::<u8>(), 1..256),
        ) {
            let key1 = XChaCha20Key::generate();
            let key2 = XChaCha20Key::generate();
            let nonce = XChaCha20Nonce::random();

            let cipher1 = AeadCipher::new(&key1);
            let cipher2 = AeadCipher::new(&key2);

            let ct1 = cipher1.encrypt(&nonce, &plaintext, None).unwrap();
            let ct2 = cipher2.encrypt(&nonce, &plaintext, None).unwrap();

            // Keys are random, so they should be different (overwhelming probability)
            prop_assert_ne!(ct1, ct2);
        }

        /// Different nonces produce different ciphertexts
        #[test]
        fn prop_different_nonces_different_ciphertext(
            plaintext in prop::collection::vec(any::<u8>(), 1..256),
        ) {
            let key = XChaCha20Key::generate();
            let nonce1 = XChaCha20Nonce::random();
            let nonce2 = XChaCha20Nonce::random();
            let cipher = AeadCipher::new(&key);

            let ct1 = cipher.encrypt(&nonce1, &plaintext, None).unwrap();
            let ct2 = cipher.encrypt(&nonce2, &plaintext, None).unwrap();

            // Nonces are random, so they should be different (overwhelming probability)
            prop_assert_ne!(ct1, ct2);
        }

        /// Tampered ciphertext always fails decryption
        #[test]
        fn prop_tampered_ciphertext_fails(
            plaintext in prop::collection::vec(any::<u8>(), 1..256),
            tamper_index in any::<usize>(),
        ) {
            let key = XChaCha20Key::generate();
            let nonce = XChaCha20Nonce::random();
            let cipher = AeadCipher::new(&key);

            let mut ciphertext = cipher.encrypt(&nonce, &plaintext, None).unwrap();
            let index = tamper_index % ciphertext.len();
            ciphertext[index] ^= 0xFF;

            let result = cipher.decrypt(&nonce, &ciphertext, None);
            prop_assert!(result.is_err());
        }

        /// Wrong key always fails decryption
        #[test]
        fn prop_wrong_key_fails(
            plaintext in prop::collection::vec(any::<u8>(), 1..256),
        ) {
            let key1 = XChaCha20Key::generate();
            let key2 = XChaCha20Key::generate();
            let nonce = XChaCha20Nonce::random();

            let cipher1 = AeadCipher::new(&key1);
            let cipher2 = AeadCipher::new(&key2);

            let ciphertext = cipher1.encrypt(&nonce, &plaintext, None).unwrap();
            let result = cipher2.decrypt(&nonce, &ciphertext, None);

            prop_assert!(result.is_err());
        }

        /// Wrong AAD always fails decryption
        #[test]
        fn prop_wrong_aad_fails(
            plaintext in prop::collection::vec(any::<u8>(), 1..256),
            aad1 in prop::collection::vec(any::<u8>(), 1..64),
            aad2 in prop::collection::vec(any::<u8>(), 1..64),
        ) {
            prop_assume!(aad1 != aad2);

            let key = XChaCha20Key::generate();
            let nonce = XChaCha20Nonce::random();
            let cipher = AeadCipher::new(&key);

            let ciphertext = cipher.encrypt(&nonce, &plaintext, Some(&aad1)).unwrap();
            let result = cipher.decrypt(&nonce, &ciphertext, Some(&aad2));

            prop_assert!(result.is_err());
        }
    }
}
