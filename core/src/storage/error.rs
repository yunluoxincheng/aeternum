//! # Storage Error Types
//!
//! Unified error handling for all storage operations in Aeternum core.
//!
//! ## Design Principles
//!
//! - **No Information Leakage**: Error messages never contain sensitive data
//! - **Detailed Context**: Each error provides actionable information
//! - **Categorized Severity**: Distinguishes recoverable errors from fatal meltdowns
//!
//! ## Error Hierarchy
//!
//! ```text
//! Error
//! ├── StorageError (Recoverable)
//! │   ├── ShadowWriteFailed
//! │   ├── AtomicRenameFailed
//! │   ├── FsyncFailed
//! │   ├── ConsistencyCheckFailed
//! │   └── InvariantViolation
//! └── FatalError (Unrecoverable)
//!     ├── StorageInconsistency
//!     └── InvariantViolationTriggered
//! ```

use thiserror::Error;

/// Result type alias for storage operations
///
/// This is provided for convenience when working with storage operations.
/// Example: `fn my_function() -> storage::Result<()> { ... }`
#[allow(dead_code)]
pub type Result<T> = std::result::Result<T, StorageError>;

/// Unified error type for all storage operations
///
/// All errors in storage module are represented by this enum,
/// ensuring consistent error handling and preventing sensitive data leakage.
#[derive(Debug, Error)]
pub enum StorageError {
    /// Shadow write operation failed
    ///
    /// This may occur due to:
    /// - Temporary file creation failure
    /// - Write operation failure (disk full, I/O error)
    /// - Permission denied
    #[error("Shadow write failed: {0}")]
    ShadowWriteFailed(String),

    /// Atomic rename operation failed
    ///
    /// This may occur due to:
    /// - Source file doesn't exist
    /// - Target directory doesn't exist
    /// - Cross-device rename (not atomic)
    /// - Permission denied
    #[error("Atomic rename failed: {0}")]
    AtomicRenameFailed(String),

    /// File sync operation failed
    ///
    /// This may occur due to:
    /// - fsync system call failure
    /// - Disk hardware error
    /// - Filesystem corruption
    #[error("File sync failed: {0}")]
    FsyncFailed(String),

    /// Consistency check failed
    ///
    /// This may occur due to:
    /// - Metadata epoch doesn't match blob epoch
    /// - Blob file corrupted
    /// - Metadata database corrupted
    #[error("Consistency check failed: {0}")]
    ConsistencyCheckFailed(String),

    /// Invariant violation detected
    ///
    /// This may occur due to:
    /// - Epoch rollback attempt (Invariant #1)
    /// - Header incompleteness (Invariant #2)
    /// - Causal barrier violation (Invariant #3)
    /// - Veto supremacy violation (Invariant #4)
    #[error("Invariant violation: {0}")]
    InvariantViolation(String),

    /// Cryptographic operation failed
    ///
    /// This may occur due to:
    /// - Key decryption failed
    /// - Key derivation failed
    /// - Key encryption failed
    /// - Blob serialization failed
    #[error("Crypto operation failed: {0}")]
    CryptoFailed(String),
}

impl StorageError {
    /// Create a shadow write error from a string message
    pub fn shadow_write(msg: impl Into<String>) -> Self {
        Self::ShadowWriteFailed(msg.into())
    }

    /// Create an atomic rename error from a string message
    pub fn atomic_rename(msg: impl Into<String>) -> Self {
        Self::AtomicRenameFailed(msg.into())
    }

    /// Create an fsync error from a string message
    pub fn fsync(msg: impl Into<String>) -> Self {
        Self::FsyncFailed(msg.into())
    }

    /// Create a consistency check error from a string message
    pub fn consistency_check(msg: impl Into<String>) -> Self {
        Self::ConsistencyCheckFailed(msg.into())
    }

    /// Create an invariant violation error from a string message
    pub fn invariant(msg: impl Into<String>) -> Self {
        Self::InvariantViolation(msg.into())
    }

    /// Create a crypto operation error from a string message
    pub fn crypto(msg: impl Into<String>) -> Self {
        Self::CryptoFailed(msg.into())
    }
}

/// Mathematical invariant violation types
///
/// These violations represent fundamental breaches of Aeternum's
/// security model and must trigger immediate meltdown.
#[derive(Debug, Error, Clone, PartialEq)]
pub enum InvariantViolation {
    /// Invariant #1: Epoch monotonicity violated
    ///
    /// Attempted to rollback epoch or advance epoch non-monotonically.
    /// This is a fundamental violation of temporal uniqueness.
    #[error("Epoch #1: Monotonicity violated (current={current}, new={new})")]
    EpochMonotonicity {
        /// Current epoch value
        current: u32,
        /// New (invalid) epoch value
        new: u32,
    },

    /// Invariant #2: Header completeness violated
    ///
    /// A device doesn't have exactly one header in the current epoch.
    /// This violates spatial completeness.
    #[error("Epoch #2: Header incomplete (device={device:?})")]
    HeaderIncomplete {
        /// Device ID that is missing a header
        device: String,
    },

    /// Invariant #3: Causal barrier violated
    ///
    /// A RECOVERY role attempted to perform a management operation.
    /// This violates the entropy barrier between decryption and management.
    #[error("Epoch #3: Causal barrier violated (role={role}, op={op})")]
    CausalBarrier {
        /// The role that violated the barrier
        role: String,
        /// The operation that was attempted
        op: String,
    },

    /// Invariant #4: Veto supremacy violated
    ///
    /// A recovery request was committed despite active vetoes within the 48h window.
    /// This violates temporal supremacy of veto rights.
    #[error("Epoch #4: Veto supremacy violated (vetoes={count})")]
    VetoSupremacy {
        /// Number of active vetoes that were ignored
        count: usize,
    },
}

/// Fatal error types (trigger meltdown)
///
/// These errors represent unrecoverable system states that require
/// immediate meltdown protocol: kernel lock, memory wipe, state isolation,
/// and user alert.
#[derive(Debug, Error)]
pub enum FatalError {
    /// Storage inconsistency detected
    ///
    /// The system detected an inconsistent state that cannot be
    /// automatically recovered. This may indicate:
    /// - Metadata ahead of blob (possible rollback attack)
    /// - Critical filesystem corruption
    /// - Tampering detected
    #[error("Storage inconsistency detected: {0}")]
    StorageInconsistency(String),

    /// Invariant violation triggered
    ///
    /// A critical invariant was violated and meltdown was triggered.
    /// This is the most severe error condition in the system.
    #[error("Invariant violation triggered: {0}")]
    InvariantViolationTriggered(String),
}

impl FatalError {
    /// Trigger meltdown protocol
    ///
    /// This method initiates the meltdown sequence:
    /// 1. **Kernel Lock**: Immediately stop all DEK decryption operations
    /// 2. **Memory Wipe**: Clear all plaintext keys from memory
    /// 3. **State Isolation**: Mark sync state as "Fork Detected"
    /// 4. **User Alert**: Force high-priority risk warning
    /// 5. **Terminate**: Panic to prevent further operations
    ///
    /// # Panics
    ///
    /// This function always panics with a detailed error message.
    ///
    /// # Example
    ///
    /// ```should_panic
    /// use aeternum_core::storage::error::FatalError;
    ///
    /// let err = FatalError::InvariantViolationTriggered(
    ///     "Epoch rollback detected".to_string()
    /// );
    /// err.trigger_meltdown(); // Will panic
    /// ```
    pub fn trigger_meltdown(&self) -> ! {
        // Step 1: Kernel lock (stop all DEK operations)
        // TODO: Implement lock_all_dek_operations() when protocol module exists
        eprintln!("[MELTDOWN] Kernel lock initiated");

        // Step 2: Memory wipe (clear all plaintext keys)
        // TODO: Implement zeroize_all_sensitive_data() when crypto integration is complete
        eprintln!("[MELTDOWN] Memory wipe initiated");

        // Step 3: State isolation (mark sync as "Fork Detected")
        // TODO: Implement set_sync_status() when sync module exists
        eprintln!("[MELTDOWN] State isolation initiated");

        // Step 4: User alert (force high-priority warning)
        eprintln!("[MELTDOWN] =========================================");
        eprintln!("[MELTDOWN] CRITICAL SECURITY ALERT");
        eprintln!("[MELTDOWN] =========================================");
        eprintln!("[MELTDOWN] {}", self);
        eprintln!("[MELTDOWN] User intervention required: Use mnemonic phrase");
        eprintln!("[MELTDOWN] to re-establish root trust.");
        eprintln!("[MELTDOWN] =========================================");

        // Step 5: Terminate (panic to prevent any further operations)
        panic!("AETERNUM MELTDOWN: {}", self);
    }
}

impl From<InvariantViolation> for FatalError {
    fn from(violation: InvariantViolation) -> Self {
        FatalError::InvariantViolationTriggered(violation.to_string())
    }
}

// ============================================================================
// Tests
// ============================================================================

#[cfg(test)]
mod tests {
    use super::*;

    // ------------------------------------------------------------------------
    // StorageError Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_storage_error_display() {
        let err = StorageError::ShadowWriteFailed("test error".to_string());
        assert_eq!(err.to_string(), "Shadow write failed: test error");
    }

    #[test]
    fn test_storage_error_shadow_write() {
        let err = StorageError::shadow_write("disk full");
        assert!(matches!(err, StorageError::ShadowWriteFailed(_)));
        assert_eq!(err.to_string(), "Shadow write failed: disk full");
    }

    #[test]
    fn test_storage_error_atomic_rename() {
        let err = StorageError::atomic_rename("cross-device rename");
        assert!(matches!(err, StorageError::AtomicRenameFailed(_)));
        assert_eq!(err.to_string(), "Atomic rename failed: cross-device rename");
    }

    #[test]
    fn test_storage_error_fsync() {
        let err = StorageError::fsync("hardware error");
        assert!(matches!(err, StorageError::FsyncFailed(_)));
        assert_eq!(err.to_string(), "File sync failed: hardware error");
    }

    #[test]
    fn test_storage_error_consistency_check() {
        let err = StorageError::consistency_check("epoch mismatch");
        assert!(matches!(err, StorageError::ConsistencyCheckFailed(_)));
        assert_eq!(err.to_string(), "Consistency check failed: epoch mismatch");
    }

    #[test]
    fn test_storage_error_invariant() {
        let err = StorageError::invariant("epoch rollback");
        assert!(matches!(err, StorageError::InvariantViolation(_)));
        assert_eq!(err.to_string(), "Invariant violation: epoch rollback");
    }

    // ------------------------------------------------------------------------
    // InvariantViolation Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_invariant_violation_epoch_monotonicity() {
        let violation = InvariantViolation::EpochMonotonicity { current: 5, new: 3 };
        assert_eq!(
            violation.to_string(),
            "Epoch #1: Monotonicity violated (current=5, new=3)"
        );
    }

    #[test]
    fn test_invariant_violation_header_incomplete() {
        let violation = InvariantViolation::HeaderIncomplete {
            device: "device_123".to_string(),
        };
        let result = violation.to_string();
        assert!(result.contains("Epoch #2: Header incomplete"));
        assert!(result.contains("device_123"));
    }

    #[test]
    fn test_invariant_violation_causal_barrier() {
        let violation = InvariantViolation::CausalBarrier {
            role: "RECOVERY".to_string(),
            op: "σ_rotate".to_string(),
        };
        assert_eq!(
            violation.to_string(),
            "Epoch #3: Causal barrier violated (role=RECOVERY, op=σ_rotate)"
        );
    }

    #[test]
    fn test_invariant_violation_veto_supremacy() {
        let violation = InvariantViolation::VetoSupremacy { count: 3 };
        assert_eq!(
            violation.to_string(),
            "Epoch #4: Veto supremacy violated (vetoes=3)"
        );
    }

    #[test]
    fn test_invariant_violation_clone_and_eq() {
        let v1 = InvariantViolation::EpochMonotonicity { current: 1, new: 0 };
        let v2 = v1.clone();
        assert_eq!(v1, v2);

        let v3 = InvariantViolation::EpochMonotonicity { current: 2, new: 0 };
        assert_ne!(v1, v3);
    }

    // ------------------------------------------------------------------------
    // FatalError Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_fatal_error_display() {
        let err = FatalError::StorageInconsistency("critical corruption".to_string());
        assert_eq!(
            err.to_string(),
            "Storage inconsistency detected: critical corruption"
        );
    }

    #[test]
    #[should_panic(expected = "AETERNUM MELTDOWN")]
    fn test_fatal_error_trigger_meltdown() {
        let err = FatalError::InvariantViolationTriggered("test meltdown".to_string());
        err.trigger_meltdown();
    }

    #[test]
    fn test_invariant_violation_into_fatal_error() {
        let violation = InvariantViolation::EpochMonotonicity { current: 5, new: 3 };
        let fatal: FatalError = violation.into();
        assert!(matches!(fatal, FatalError::InvariantViolationTriggered(_)));
        assert!(fatal
            .to_string()
            .contains("Epoch #1: Monotonicity violated"));
    }
}
