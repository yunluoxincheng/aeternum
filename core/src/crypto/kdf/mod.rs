//! # Argon2id Key Derivation Module
//!
//! This module provides Argon2id KDF functionality with secure default
//! parameters following OWASP 2024 recommendations.
//!
//! ## Components
//!
//! - [`Argon2idConfig`]: Configuration with safe defaults (OWASP 2024)
//! - [`DerivedKey`]: Output key material that zeroizes on drop
//! - [`Argon2idKDF`]: Key derivation function with validation
//!
//! ## Security Properties
//!
//! - Memory-hard function resistant to GPU/ASIC attacks
//! - Minimum 16-byte salt requirement (RFC 9106)
//! - All secret material implements `Zeroize` for automatic cleanup
//!
//! ## Example
//!
//! ```
//! use aeternum_core::crypto::kdf::{Argon2idKDF, Argon2idConfig};
//!
//! let kdf = Argon2idKDF::new();
//! let salt = [0u8; 16]; // In production, use a random salt!
//! let derived = kdf.derive_key(b"password", &salt).unwrap();
//! assert_eq!(derived.len(), 32);
//! ```

mod argon2id;

use zeroize::{Zeroize, ZeroizeOnDrop};

// Re-export the Argon2id KDF implementation
pub use self::argon2id::{Argon2idKDF, MIN_SALT_LENGTH};

/// Argon2id configuration with OWASP 2024 recommended defaults
#[derive(Debug, Clone, Copy)]
pub struct Argon2idConfig {
    /// Memory cost in kilobytes
    pub m_cost: u32,

    /// Time cost (number of iterations)
    pub t_cost: u32,

    /// Parallelism (number of threads/lanes)
    pub p_cost: u32,

    /// Output length in bytes
    pub output_len: usize,
}

impl Default for Argon2idConfig {
    fn default() -> Self {
        // OWASP 2024 recommendations for mobile devices
        Self {
            m_cost: 64 * 1024, // 64 MB
            t_cost: 3,         // 3 iterations
            p_cost: 4,         // 4 threads
            output_len: 32,    // 256-bit output
        }
    }
}

impl Argon2idConfig {
    /// Create a new configuration with custom parameters
    ///
    /// **Security Requirements**:
    /// - `m_cost` must be >= 8192 (8 MB)
    /// - `t_cost` must be >= 1
    /// - `output_len` must be >= 16
    pub fn new(m_cost: u32, t_cost: u32, p_cost: u32, output_len: usize) -> Self {
        Self {
            m_cost,
            t_cost,
            p_cost,
            output_len,
        }
    }

    /// Validate the configuration parameters
    pub fn validate(&self) -> Result<(), crate::crypto::error::CryptoError> {
        if self.m_cost < 8192 {
            return Err(crate::crypto::error::CryptoError::KdfError(format!(
                "Memory cost too low: {} KB (minimum 8192)",
                self.m_cost
            )));
        }
        if self.t_cost < 1 {
            return Err(crate::crypto::error::CryptoError::KdfError(
                "Time cost must be at least 1".to_string(),
            ));
        }
        if self.output_len < 16 {
            return Err(crate::crypto::error::CryptoError::KdfError(format!(
                "Output length too short: {} bytes (minimum 16)",
                self.output_len
            )));
        }
        Ok(())
    }
}

/// Derived key material that automatically zeroizes on drop
///
/// Note: Debug is implemented to show only the length, not the actual key bytes,
/// to prevent accidental key leakage in logs.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct DerivedKey(pub Vec<u8>);

impl std::fmt::Debug for DerivedKey {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        // Never print the actual key bytes to prevent leakage
        f.debug_struct("DerivedKey")
            .field("len", &self.0.len())
            .finish_non_exhaustive()
    }
}

impl DerivedKey {
    /// Get the key bytes
    pub fn as_bytes(&self) -> &[u8] {
        &self.0
    }

    /// Get the key length
    pub fn len(&self) -> usize {
        self.0.len()
    }

    /// Check if the key is empty
    pub fn is_empty(&self) -> bool {
        self.0.is_empty()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_default_config() {
        let config = Argon2idConfig::default();
        assert!(config.validate().is_ok());
        assert_eq!(config.m_cost, 64 * 1024);
        assert_eq!(config.t_cost, 3);
        assert_eq!(config.p_cost, 4);
    }

    #[test]
    fn test_config_validation() {
        // Invalid: memory too low
        let config = Argon2idConfig::new(4096, 3, 4, 32);
        assert!(config.validate().is_err());

        // Invalid: time too low
        let config = Argon2idConfig::new(8192, 0, 4, 32);
        assert!(config.validate().is_err());

        // Invalid: output too short
        let config = Argon2idConfig::new(8192, 1, 4, 8);
        assert!(config.validate().is_err());
    }
}
