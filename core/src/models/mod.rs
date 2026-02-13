//! # Data Models Module
//!
//! Core data structures for Aeternum's key hierarchy, epoch management,
//! device identity, and encrypted storage containers.
//!
//! ## Module Organization
//!
//! - `key_hierarchy` - Master Root Seed and derived key types (IK, RK, DEK, VK)
//! - `epoch` - Cryptographic epoch and algorithm versioning
//! - `device` - Device identifiers and headers
//! - `vault` - Encrypted data containers (VaultBlob, VaultHeader)
//!
//! ## Design Principles
//!
//! 1. **Type Safety**: All key types are distinct newtypes to prevent misuse
//! 2. **Memory Safety**: All secret types implement `Zeroize` and `ZeroizeOnDrop`
//! 3. **Validation Separation**: Data structures contain no validation logic;
//!    validation is handled by the `protocol` module
//!
//! ## Security Guarantees
//!
//! - Secret keys are automatically zeroized on drop
//! - Debug implementations never expose actual key material
//! - Type system prevents accidental key type confusion

#![warn(missing_docs)]

// Sub-modules (will be implemented in subsequent phases)
pub mod device;
pub mod epoch;
pub mod key_hierarchy;
pub mod vault;

// Re-export common types for convenience
pub use device::{DeviceHeader, DeviceId, DeviceStatus, Operation, Role};
pub use epoch::{CryptoAlgorithm, CryptoEpoch};
pub use key_hierarchy::{
    DataEncryptionKey, DeviceKey, IdentityKey, MasterSeed, RecoveryKey, VaultKey,
};
pub use vault::{VaultBlob, VaultHeader};
