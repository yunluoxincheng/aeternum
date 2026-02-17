//! # Bridge Module
//!
//! This module provides UniFFI bridge implementations for Android UI layer.
//!
//! ## Architecture
//!
//! The bridge module sits on top of protocol and provides high-level interfaces:
//! - `VaultSession` - Decryption session with handle-based access
//! - `AeternumEngine` - Main engine for UI operations
//! - `DeviceInfo` - Sanitized device information
//!
//! ## Security Guarantees
//!
//! - All plaintext keys remain in Rust memory
//! - Sessions implement zeroize on drop
//! - UI layer only receives handles or sanitized data
//!
//! ## Module Organization
//!
//! - `session` - Vault session implementation
//! - `engine` - Aeternum engine implementation
//! - `types` - Bridge-specific types

#![warn(missing_docs)]
#![warn(unused_extern_crates)]
#![warn(unused_imports)]

pub mod engine;
pub mod session;
pub mod types;

// Re-export for UniFFI
pub use engine::AeternumEngine;
pub use session::VaultSession;
pub use types::DeviceInfo;

#[cfg(test)]
mod tests;
