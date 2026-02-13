//! # Storage Engine Module
//!
//! This module provides persistent storage with crash consistency guarantees
//! for the Aeternum key management system.
//!
//! ## Architecture
//!
//! The storage engine implements the "No-Middle-State" principle:
//! - **Shadow Writing**: Atomic file updates via temporary files + fsync + rename
//! - **Crash Recovery**: Automatic self-healing on startup
//! - **Invariant Enforcement**: Strict enforcement of four mathematical invariants
//! - **Integrity Audit**: BLAKE3-based MAC verification
//!
//! ## Modules
//!
//! - `error` - Storage error types
//! - `shadow` - Shadow write mechanism for atomic updates
//! - `recovery` - Crash recovery and self-healing logic
//! - `invariant` - Mathematical invariant validation
//! - `integrity` - Vault integrity verification
//! - `aug` - Atomic Epoch Upgrade Protocol (AUP) implementation
//!
//! ## Safety Guarantees
//!
//! - All file updates are atomic (POSIX rename)
//! - All writes are synced to disk before commit
//! - All sensitive buffers implement `Zeroize`
//! - All invariant violations trigger immediate meltdown

// Re-export common types
pub use error::{FatalError, InvariantViolation, StorageError};
pub use integrity::IntegrityAudit;
pub use invariant::InvariantValidator;
pub use recovery::{ConsistencyState, CrashRecovery, MetadataSource, VaultStorage};
pub use shadow::{ShadowFile, ShadowWriter};

// Re-export AUP types
pub use aug::{aup_atomic_commit, aup_prepare, aup_shadow_write, read_vault_epoch, AupPreparation};

// Public submodules for documentation examples
pub mod aug;
pub mod error;
pub mod integrity;
pub mod invariant;
pub mod recovery;
pub mod shadow;
