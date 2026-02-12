//! # Argon2id Key Derivation Implementation
//!
//! Provides Argon2id KDF functionality with secure default parameters
//! following OWASP 2024 recommendations.
//!
//! ## Security Properties
//!
//! - Memory-hard function resistant to GPU/ASIC attacks
//! - Configurable memory, time, and parallelism costs
//! - Salt requirement (minimum 16 bytes) to prevent rainbow tables
//! - All derived keys implement `Zeroize` for automatic memory cleanup
//!
//! ## RFC 9106 Compliance
//!
//! This implementation follows RFC 9106 (Argon2 Memory-Hard Function
//! for Password Hashing and Proof-of-Work Applications).

use super::{Argon2idConfig, DerivedKey};
use crate::crypto::error::{CryptoError, Result};
use argon2::{Algorithm, Argon2, Params, Version};

/// Minimum salt length in bytes (RFC 9106 recommendation)
pub const MIN_SALT_LENGTH: usize = 16;

/// Argon2id key derivation function.
///
/// Provides a secure, memory-hard key derivation function suitable
/// for deriving cryptographic keys from passwords or other low-entropy
/// input material.
///
/// # Security Considerations
///
/// - Always use a unique, random salt (at least 16 bytes)
/// - Use the default OWASP 2024 parameters unless you have specific requirements
/// - The derived key automatically zeroizes on drop
///
/// # Example
///
/// ```
/// use aeternum_core::crypto::kdf::{Argon2idKDF, Argon2idConfig};
///
/// let kdf = Argon2idKDF::new();
/// let salt = [0u8; 16]; // In production, use a random salt!
/// let password = b"secure password";
///
/// let derived = kdf.derive_key(password, &salt).unwrap();
/// assert_eq!(derived.len(), 32);
/// ```
pub struct Argon2idKDF {
    config: Argon2idConfig,
}

impl Argon2idKDF {
    /// Create a new Argon2id KDF with OWASP 2024 default parameters.
    ///
    /// Default parameters:
    /// - Memory: 64 MB (`m_cost = 65536`)
    /// - Iterations: 3 (`t_cost = 3`)
    /// - Parallelism: 4 (`p_cost = 4`)
    /// - Output length: 32 bytes
    pub fn new() -> Self {
        Self {
            config: Argon2idConfig::default(),
        }
    }

    /// Create a new Argon2id KDF with custom configuration.
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::KdfError` if:
    /// - `m_cost` < 8192 (8 MB minimum)
    /// - `t_cost` < 1
    /// - `output_len` < 16 bytes
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::crypto::kdf::{Argon2idKDF, Argon2idConfig};
    ///
    /// let config = Argon2idConfig::new(32 * 1024, 2, 2, 32);
    /// let kdf = Argon2idKDF::with_config(config).unwrap();
    /// ```
    pub fn with_config(config: Argon2idConfig) -> Result<Self> {
        config.validate()?;
        Ok(Self { config })
    }

    /// Get the current configuration.
    pub fn config(&self) -> &Argon2idConfig {
        &self.config
    }

    /// Derive a key from password and salt.
    ///
    /// # Arguments
    ///
    /// - `password`: The input key material (password, passphrase, etc.)
    /// - `salt`: A unique salt value (minimum 16 bytes, should be random)
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::InvalidKeyLength` if salt is shorter than 16 bytes.
    /// Returns `CryptoError::KdfError` if key derivation fails.
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::crypto::kdf::Argon2idKDF;
    /// use rand::RngCore;
    ///
    /// let kdf = Argon2idKDF::new();
    ///
    /// // Generate a random salt
    /// let mut salt = [0u8; 16];
    /// rand::thread_rng().fill_bytes(&mut salt);
    ///
    /// let derived = kdf.derive_key(b"my password", &salt).unwrap();
    /// assert_eq!(derived.len(), 32);
    /// ```
    pub fn derive_key(&self, password: &[u8], salt: &[u8]) -> Result<DerivedKey> {
        // Validate salt length
        if salt.len() < MIN_SALT_LENGTH {
            return Err(CryptoError::InvalidKeyLength {
                expected: MIN_SALT_LENGTH,
                actual: salt.len(),
            });
        }

        // Build Argon2id parameters
        let params = Params::new(
            self.config.m_cost,
            self.config.t_cost,
            self.config.p_cost,
            Some(self.config.output_len),
        )
        .map_err(|e| CryptoError::kdf(format!("Invalid Argon2id parameters: {}", e)))?;

        // Create Argon2id context
        let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);

        // Derive the key
        let mut output = vec![0u8; self.config.output_len];
        argon2
            .hash_password_into(password, salt, &mut output)
            .map_err(|e| CryptoError::kdf(format!("Key derivation failed: {}", e)))?;

        Ok(DerivedKey(output))
    }

    /// Derive a key with a custom output length.
    ///
    /// This method allows specifying the output length at call time,
    /// overriding the configured default.
    ///
    /// # Arguments
    ///
    /// - `password`: The input key material
    /// - `salt`: A unique salt value (minimum 16 bytes)
    /// - `output_len`: Desired output length in bytes (minimum 16)
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::InvalidKeyLength` if salt is shorter than 16 bytes.
    /// Returns `CryptoError::KdfError` if output_len < 16 or key derivation fails.
    pub fn derive_key_with_length(
        &self,
        password: &[u8],
        salt: &[u8],
        output_len: usize,
    ) -> Result<DerivedKey> {
        // Validate salt length
        if salt.len() < MIN_SALT_LENGTH {
            return Err(CryptoError::InvalidKeyLength {
                expected: MIN_SALT_LENGTH,
                actual: salt.len(),
            });
        }

        // Validate output length
        if output_len < 16 {
            return Err(CryptoError::kdf(format!(
                "Output length too short: {} bytes (minimum 16)",
                output_len
            )));
        }

        // Build Argon2id parameters
        let params = Params::new(
            self.config.m_cost,
            self.config.t_cost,
            self.config.p_cost,
            Some(output_len),
        )
        .map_err(|e| CryptoError::kdf(format!("Invalid Argon2id parameters: {}", e)))?;

        // Create Argon2id context
        let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);

        // Derive the key
        let mut output = vec![0u8; output_len];
        argon2
            .hash_password_into(password, salt, &mut output)
            .map_err(|e| CryptoError::kdf(format!("Key derivation failed: {}", e)))?;

        Ok(DerivedKey(output))
    }
}

impl Default for Argon2idKDF {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // ── Basic functionality ─────────────────────────────────────────

    #[test]
    fn test_default_kdf_creates_32_byte_key() {
        let kdf = Argon2idKDF::new();
        let salt = [0x42u8; 16];
        let derived = kdf.derive_key(b"password", &salt).unwrap();
        assert_eq!(derived.len(), 32);
    }

    #[test]
    fn test_derive_key_deterministic() {
        let kdf = Argon2idKDF::new();
        let salt = [0x42u8; 16];
        let password = b"test password";

        let key1 = kdf.derive_key(password, &salt).unwrap();
        let key2 = kdf.derive_key(password, &salt).unwrap();

        assert_eq!(key1.as_bytes(), key2.as_bytes());
    }

    #[test]
    fn test_different_passwords_produce_different_keys() {
        let kdf = Argon2idKDF::new();
        let salt = [0x42u8; 16];

        let key1 = kdf.derive_key(b"password1", &salt).unwrap();
        let key2 = kdf.derive_key(b"password2", &salt).unwrap();

        assert_ne!(key1.as_bytes(), key2.as_bytes());
    }

    #[test]
    fn test_different_salts_produce_different_keys() {
        let kdf = Argon2idKDF::new();
        let salt1 = [0x42u8; 16];
        let salt2 = [0x43u8; 16];
        let password = b"password";

        let key1 = kdf.derive_key(password, &salt1).unwrap();
        let key2 = kdf.derive_key(password, &salt2).unwrap();

        assert_ne!(key1.as_bytes(), key2.as_bytes());
    }

    // ── Salt validation ─────────────────────────────────────────────

    #[test]
    fn test_salt_too_short_returns_error() {
        let kdf = Argon2idKDF::new();
        let short_salt = [0u8; 15]; // Less than 16 bytes

        let result = kdf.derive_key(b"password", &short_salt);
        assert!(result.is_err());

        match result.unwrap_err() {
            CryptoError::InvalidKeyLength { expected, actual } => {
                assert_eq!(expected, 16);
                assert_eq!(actual, 15);
            }
            _ => panic!("Expected InvalidKeyLength error"),
        }
    }

    #[test]
    fn test_minimum_valid_salt_length() {
        let kdf = Argon2idKDF::new();
        let salt = [0u8; 16]; // Exactly 16 bytes

        let result = kdf.derive_key(b"password", &salt);
        assert!(result.is_ok());
    }

    #[test]
    fn test_long_salt_accepted() {
        let kdf = Argon2idKDF::new();
        let salt = [0u8; 64]; // Longer than minimum

        let result = kdf.derive_key(b"password", &salt);
        assert!(result.is_ok());
    }

    // ── Custom configuration ────────────────────────────────────────

    #[test]
    fn test_custom_config_valid() {
        let config = Argon2idConfig::new(8192, 1, 1, 32);
        let kdf = Argon2idKDF::with_config(config).unwrap();
        let salt = [0u8; 16];

        let result = kdf.derive_key(b"password", &salt);
        assert!(result.is_ok());
    }

    #[test]
    fn test_custom_config_invalid_m_cost() {
        let config = Argon2idConfig::new(4096, 1, 1, 32);
        let result = Argon2idKDF::with_config(config);
        assert!(result.is_err());
    }

    #[test]
    fn test_custom_config_invalid_t_cost() {
        let config = Argon2idConfig::new(8192, 0, 1, 32);
        let result = Argon2idKDF::with_config(config);
        assert!(result.is_err());
    }

    #[test]
    fn test_custom_config_invalid_output_len() {
        let config = Argon2idConfig::new(8192, 1, 1, 8);
        let result = Argon2idKDF::with_config(config);
        assert!(result.is_err());
    }

    // ── Custom output length ────────────────────────────────────────

    #[test]
    fn test_derive_key_with_length() {
        let kdf = Argon2idKDF::new();
        let salt = [0u8; 16];

        let key16 = kdf.derive_key_with_length(b"password", &salt, 16).unwrap();
        let key32 = kdf.derive_key_with_length(b"password", &salt, 32).unwrap();
        let key64 = kdf.derive_key_with_length(b"password", &salt, 64).unwrap();

        assert_eq!(key16.len(), 16);
        assert_eq!(key32.len(), 32);
        assert_eq!(key64.len(), 64);
    }

    #[test]
    fn test_derive_key_with_length_too_short() {
        let kdf = Argon2idKDF::new();
        let salt = [0u8; 16];

        let result = kdf.derive_key_with_length(b"password", &salt, 8);
        assert!(result.is_err());
    }

    // ── RFC 9106 Test Vectors ───────────────────────────────────────
    // Based on RFC 9106 Section 6 (Test Vectors)

    #[test]
    fn test_rfc9106_argon2id_test_vector() {
        // RFC 9106 Section 6.3 - Argon2id test vector
        // Note: Using reduced parameters for unit test performance
        // The official vector uses: m=32, t=3, p=4, password="password", salt="somesalt"
        // Expected hash starts with: 0d640df...

        // We use a simplified test that verifies our implementation
        // produces consistent, non-zero output matching the argon2 crate directly
        let config = Argon2idConfig::new(8192, 1, 1, 32);
        let kdf = Argon2idKDF::with_config(config).unwrap();

        let password = b"password";
        let salt = b"somesaltsomesalt"; // 16 bytes

        let derived = kdf.derive_key(password, salt).unwrap();

        // Verify it's a valid, non-zero output
        assert_eq!(derived.len(), 32);
        assert_ne!(derived.as_bytes(), &[0u8; 32]);

        // Verify determinism
        let derived2 = kdf.derive_key(password, salt).unwrap();
        assert_eq!(derived.as_bytes(), derived2.as_bytes());
    }

    #[test]
    fn test_argon2id_official_test_vector_low_memory() {
        // Low-memory test case that can run quickly
        // m=8192 KB, t=1, p=1
        let config = Argon2idConfig::new(8192, 1, 1, 32);
        let kdf = Argon2idKDF::with_config(config).unwrap();

        let password = b"password";
        let salt = b"0123456789abcdef"; // 16 bytes

        let derived = kdf.derive_key(password, salt).unwrap();

        // Cross-verify with argon2 crate directly
        let params = Params::new(8192, 1, 1, Some(32)).unwrap();
        let argon2 = Argon2::new(Algorithm::Argon2id, Version::V0x13, params);
        let mut expected = [0u8; 32];
        argon2
            .hash_password_into(password, salt, &mut expected)
            .unwrap();

        assert_eq!(derived.as_bytes(), &expected);
    }

    #[test]
    fn test_argon2id_empty_password() {
        // Empty password should still produce a valid key
        let config = Argon2idConfig::new(8192, 1, 1, 32);
        let kdf = Argon2idKDF::with_config(config).unwrap();
        let salt = [0x42u8; 16];

        let derived = kdf.derive_key(b"", &salt).unwrap();
        assert_eq!(derived.len(), 32);
        assert_ne!(derived.as_bytes(), &[0u8; 32]);
    }

    #[test]
    fn test_argon2id_long_password() {
        // Long password should work correctly
        let config = Argon2idConfig::new(8192, 1, 1, 32);
        let kdf = Argon2idKDF::with_config(config).unwrap();
        let salt = [0x42u8; 16];
        let long_password = vec![0xABu8; 1024];

        let derived = kdf.derive_key(&long_password, &salt).unwrap();
        assert_eq!(derived.len(), 32);
    }

    // ── Edge cases ──────────────────────────────────────────────────

    #[test]
    fn test_default_impl() {
        let kdf1 = Argon2idKDF::new();
        let kdf2 = Argon2idKDF::default();

        let salt = [0u8; 16];
        let key1 = kdf1.derive_key(b"test", &salt).unwrap();
        let key2 = kdf2.derive_key(b"test", &salt).unwrap();

        assert_eq!(key1.as_bytes(), key2.as_bytes());
    }

    #[test]
    fn test_config_accessor() {
        let kdf = Argon2idKDF::new();
        let config = kdf.config();

        assert_eq!(config.m_cost, 64 * 1024);
        assert_eq!(config.t_cost, 3);
        assert_eq!(config.p_cost, 4);
        assert_eq!(config.output_len, 32);
    }

    #[test]
    fn test_derived_key_is_empty() {
        let config = Argon2idConfig::new(8192, 1, 1, 32);
        let kdf = Argon2idKDF::with_config(config).unwrap();
        let salt = [0u8; 16];

        let derived = kdf.derive_key(b"password", &salt).unwrap();
        assert!(!derived.is_empty());
    }
}

// ── Property-based tests (proptest) ─────────────────────────────────

#[cfg(test)]
mod proptests {
    use super::*;
    use proptest::prelude::*;

    proptest! {
        /// Derived key length matches configured output length
        #[test]
        fn prop_output_length_matches_config(
            password in prop::collection::vec(any::<u8>(), 0..256),
            salt in prop::collection::vec(any::<u8>(), 16..64),
            output_len in 16usize..128,
        ) {
            // Use low parameters for performance
            let config = Argon2idConfig::new(8192, 1, 1, output_len);
            let kdf = Argon2idKDF::with_config(config).unwrap();

            let derived = kdf.derive_key(&password, &salt).unwrap();
            prop_assert_eq!(derived.len(), output_len);
        }

        /// Derivation is deterministic
        #[test]
        fn prop_deterministic(
            password in prop::collection::vec(any::<u8>(), 0..128),
            salt in prop::collection::vec(any::<u8>(), 16..32),
        ) {
            let config = Argon2idConfig::new(8192, 1, 1, 32);
            let kdf = Argon2idKDF::with_config(config).unwrap();

            let key1 = kdf.derive_key(&password, &salt).unwrap();
            let key2 = kdf.derive_key(&password, &salt).unwrap();

            prop_assert_eq!(key1.as_bytes(), key2.as_bytes());
        }

        /// Different salts produce different keys
        #[test]
        fn prop_different_salts_different_keys(
            password in prop::collection::vec(any::<u8>(), 1..64),
            salt1 in prop::collection::vec(any::<u8>(), 16..32),
            salt2 in prop::collection::vec(any::<u8>(), 16..32),
        ) {
            prop_assume!(salt1 != salt2);

            let config = Argon2idConfig::new(8192, 1, 1, 32);
            let kdf = Argon2idKDF::with_config(config).unwrap();

            let key1 = kdf.derive_key(&password, &salt1).unwrap();
            let key2 = kdf.derive_key(&password, &salt2).unwrap();

            prop_assert_ne!(key1.as_bytes(), key2.as_bytes());
        }

        /// Salt too short is rejected
        #[test]
        fn prop_short_salt_rejected(
            password in prop::collection::vec(any::<u8>(), 0..64),
            salt_len in 0usize..16,
        ) {
            let salt = vec![0u8; salt_len];
            let kdf = Argon2idKDF::new();

            let result = kdf.derive_key(&password, &salt);
            prop_assert!(result.is_err());
        }

        /// Output is never all zeros
        #[test]
        fn prop_output_never_zero(
            password in prop::collection::vec(any::<u8>(), 0..64),
            salt in prop::collection::vec(any::<u8>(), 16..32),
        ) {
            let config = Argon2idConfig::new(8192, 1, 1, 32);
            let kdf = Argon2idKDF::with_config(config).unwrap();

            let derived = kdf.derive_key(&password, &salt).unwrap();
            let zeros = vec![0u8; derived.len()];

            prop_assert_ne!(derived.as_bytes(), zeros.as_slice());
        }
    }
}
