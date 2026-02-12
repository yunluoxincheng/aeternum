//! # Aeternum Cryptographic Core
//!
//! This library provides the cryptographic foundation for the Aeternum key management system.
//!
//! ## Security Architecture
//!
//! The core is designed as the **root of trust** for the entire system:
//! - All cryptographic operations are performed in Rust
//! - All sensitive data structures implement `Zeroize`
//! - No plaintext keys ever cross the FFI boundary
//!
//! ## Module Organization
//!
//! - `crypto` - Cryptographic primitives (hash, KDF, AEAD, KEM, ECDH)
//! - `storage` - Shadow writing and crash consistency engine
//! - `sync` - Aeternum Wire protocol
//! - `models` - Epoch and Header data models
//!
//! ## Safety Guarantees
//!
//! - All secret keys are automatically zeroized on drop
//! - Memory is locked where possible (mlock support)
//! - Constant-time operations for secret data

#![warn(missing_docs)]
#![warn(unused_extern_crates)]
#![warn(unused_imports)]

/// Cryptographic primitives module
pub mod crypto;

// Re-export common types at the crate root
pub use crypto::{error::CryptoError, error::Result};

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_module_organization() {
        // Basic sanity check that modules are accessible
        let _ = CryptoError::InternalError("test".to_string());
    }
}
