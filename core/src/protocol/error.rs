//! # Protocol Error Types
//!
//! This module defines error types for PQRR protocol state machine.
//!
//! ## Error Categories
//!
//! - `EpochRegression` - Invariant #1 violation (epoch must increase)
//! - `HeaderIncomplete` - Invariant #2 violation (missing or duplicate headers)
//! - `InsufficientPrivileges` - Invariant #3 violation (RECOVERY role blocked)
//! - `Vetoed` - Invariant #4 violation (veto signals received)
//! - `PermissionDenied` - Invariant #3 enforcement (RECOVERY cannot σ_rotate)
//! - `InvalidStateTransition` - State machine logic error
//! - `StorageError` - Storage layer error propagation

use std::fmt;

/// Protocol error type
///
/// Represents all possible errors that can occur during PQRR protocol
/// execution, including invariant violations and state machine errors.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Error)]
pub enum PqrrError {
    /// Invariant #1 violation: epoch regression or rollback
    ///
    /// This error occurs when attempting to upgrade to an epoch that is
    /// not strictly greater than current epoch.
    EpochRegression {
        /// Current epoch version
        current: u32,
        /// Attempted epoch version
        attempted: u32,
    },

    /// Invariant #2 violation: header incompleteness
    ///
    /// This error occurs when device headers are missing, duplicated, or
    /// otherwise invalid, preventing DEK access.
    HeaderIncomplete {
        /// Device ID that caused error
        device_id: String,
        /// Error reason
        reason: String,
    },

    /// Invariant #3 violation: insufficient privileges
    ///
    /// This error occurs when a RECOVERY role device attempts to perform
    /// management operations (σ_rotate, device revocation, etc.).
    InsufficientPrivileges {
        /// Device role that attempted operation
        role: String,
        /// Operation that was denied
        operation: String,
    },

    /// Permission denied (Invariant #3 enforcement)
    ///
    /// This error occurs when a device without proper permissions attempts
    /// to execute an operation. This is primary enforcement point
    /// for Invariant #3: Causal Entropy Barrier.
    PermissionDenied {
        /// Role that was denied permission
        role: String,
        /// Operation that was denied
        operation: String,
    },

    /// Invariant #4 violation: veto signals received
    ///
    /// This error occurs when veto signals are present during a recovery
    /// attempt, causing immediate termination of recovery process.
    Vetoed {
        /// Recovery request ID
        request_id: String,
        /// Number of veto signals received
        veto_count: u32,
    },

    /// Invalid state transition
    ///
    /// This error occurs when attempting to transition to an invalid state
    /// or from an invalid state.
    InvalidStateTransition {
        /// Current state
        from: String,
        /// Target state
        to: String,
        /// Reason for invalidity
        reason: String,
    },

    /// Storage layer error
    ///
    /// This error propagates from storage layer when shadow writing,
    /// atomic commits, or crash recovery fail.
    StorageError {
        /// Error message from storage layer
        /// Renamed to `storage_msg` to avoid conflict with Throwable.message in Kotlin
        storage_msg: String,
    },
}

impl PqrrError {
    /// Create an EpochRegression error
    pub fn epoch_regression(current: u32, attempted: u32) -> Self {
        PqrrError::EpochRegression { current, attempted }
    }

    /// Create a HeaderIncomplete error
    pub fn header_incomplete(device_id: String, reason: String) -> Self {
        PqrrError::HeaderIncomplete { device_id, reason }
    }

    /// Create an InsufficientPrivileges error
    pub fn insufficient_privileges(role: String, operation: String) -> Self {
        PqrrError::InsufficientPrivileges { role, operation }
    }

    /// Create a PermissionDenied error (for epoch_upgrade module)
    pub fn permission_denied(role: String, operation: String) -> Self {
        PqrrError::PermissionDenied { role, operation }
    }

    /// Create a Vetoed error
    pub fn vetoed(request_id: String, veto_count: u32) -> Self {
        PqrrError::Vetoed {
            request_id,
            veto_count,
        }
    }

    /// Create an InvalidStateTransition error
    pub fn invalid_transition(from: String, to: String, reason: String) -> Self {
        PqrrError::InvalidStateTransition { from, to, reason }
    }

    /// Create a StorageError error (for epoch_upgrade module)
    pub fn storage_error(storage_msg: String) -> Self {
        PqrrError::StorageError { storage_msg }
    }

    /// Check if this error represents an invariant violation
    pub fn is_invariant_violation(&self) -> bool {
        matches!(
            self,
            PqrrError::EpochRegression { .. }
                | PqrrError::HeaderIncomplete { .. }
                | PqrrError::InsufficientPrivileges { .. }
                | PqrrError::Vetoed { .. }
        )
    }

    /// Get the invariant number (1-4) if this is an invariant violation
    pub fn invariant_number(&self) -> Option<u32> {
        match self {
            PqrrError::EpochRegression { .. } => Some(1),
            PqrrError::HeaderIncomplete { .. } => Some(2),
            PqrrError::InsufficientPrivileges { .. } => Some(3),
            PqrrError::Vetoed { .. } => Some(4),
            _ => None,
        }
    }
}

impl fmt::Display for PqrrError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            PqrrError::EpochRegression { current, attempted } => write!(
                f,
                "Invariant #1 violation: Epoch regression (current={}, attempted={})",
                current, attempted
            ),
            PqrrError::HeaderIncomplete { device_id, reason } => write!(
                f,
                "Invariant #2 violation: Header incomplete for device {}: {}",
                device_id, reason
            ),
            PqrrError::InsufficientPrivileges { role, operation } => write!(
                f,
                "Invariant #3 violation: Role {} cannot perform operation {}",
                role, operation
            ),
            PqrrError::PermissionDenied { role, operation } => write!(
                f,
                "Permission denied: Role {} cannot perform operation {}",
                role, operation
            ),
            PqrrError::Vetoed {
                request_id,
                veto_count,
            } => write!(
                f,
                "Invariant #4 violation: Recovery {} vetoed by {} devices",
                request_id, veto_count
            ),
            PqrrError::InvalidStateTransition { from, to, reason } => write!(
                f,
                "Invalid state transition from {} to {}: {}",
                from, to, reason
            ),
            PqrrError::StorageError { storage_msg } => {
                write!(f, "Storage error: {}", storage_msg)
            }
        }
    }
}

impl std::error::Error for PqrrError {}

/// Protocol result type
///
/// Convenience alias for Result with PqrrError.
pub type Result<T> = std::result::Result<T, PqrrError>;

// ============================================================================
// Tests
// ============================================================================

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_error_epoch_regression() {
        let err = PqrrError::epoch_regression(5, 3);
        assert!(err.is_invariant_violation());
        assert_eq!(err.invariant_number(), Some(1));
        assert!(err.to_string().contains("Invariant #1"));
    }

    #[test]
    fn test_error_header_incomplete() {
        let err = PqrrError::header_incomplete("device_1".to_string(), "missing".to_string());
        assert!(err.is_invariant_violation());
        assert_eq!(err.invariant_number(), Some(2));
        assert!(err.to_string().contains("Invariant #2"));
    }

    #[test]
    fn test_error_insufficient_privileges() {
        let err =
            PqrrError::insufficient_privileges("RECOVERY".to_string(), "σ_rotate".to_string());
        assert!(err.is_invariant_violation());
        assert_eq!(err.invariant_number(), Some(3));
        assert!(err.to_string().contains("Invariant #3"));
    }

    #[test]
    fn test_error_permission_denied() {
        let err = PqrrError::permission_denied("RECOVERY".to_string(), "σ_rotate".to_string());
        assert!(!err.is_invariant_violation()); // PermissionDenied is not invariant violation
        assert_eq!(err.invariant_number(), None);
        assert!(err.to_string().contains("Permission denied"));
    }

    #[test]
    fn test_error_vetoed() {
        let err = PqrrError::vetoed("req_123".to_string(), 2);
        assert!(err.is_invariant_violation());
        assert_eq!(err.invariant_number(), Some(4));
        assert!(err.to_string().contains("Invariant #4"));
    }

    #[test]
    fn test_error_invalid_transition() {
        let err = PqrrError::invalid_transition(
            "Idle".to_string(),
            "Rekeying".to_string(),
            "already in Rekeying".to_string(),
        );
        assert!(!err.is_invariant_violation());
        assert_eq!(err.invariant_number(), None);
    }

    #[test]
    fn test_error_storage_error() {
        let err = PqrrError::storage_error("disk full".to_string());
        assert!(!err.is_invariant_violation());
        assert_eq!(err.invariant_number(), None);
    }
}
