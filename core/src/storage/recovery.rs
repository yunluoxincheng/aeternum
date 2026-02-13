//! # Crash Recovery and Self-Healing Logic
//!
//! Implements startup consistency checks and automatic repair of inconsistent states
//! according to the "No-Middle-State" principle from the persistence specification.
//!
//! ## Consistency States
//!
//! - **State A (Consistent)**: `metadata_epoch == blob_epoch` → Normal startup
//! - **State B (BlobAhead)**: `blob_epoch > metadata_epoch` → Auto-heal (DB aligns to Blob)
//! - **State C (MetadataAhead)**: `blob_epoch < metadata_epoch` → Meltdown (illegal state)
//!
//! ## Design Principles
//!
//! 1. **Zero Trust**: Never trust filesystem reports, only AEAD-verified data
//! 2. **Auto-Heal**: BlobAhead state is automatically repaired on startup
//! 3. **Fail-Safe**: MetadataAhead triggers immediate meltdown
//!
//! ## Example
//!
//! ```no_run
//! use aeternum_core::storage::recovery::{CrashRecovery, ConsistencyState};
//!
//! // Note: Concrete implementations of MetadataSource and VaultStorage traits
//! // are required (e.g., SQLCipher for metadata, encrypted file for vault)
//! // This is a simplified example demonstrating the API usage.
//!
//! # Ok::<(), aeternum_core::storage::StorageError>(())
//! ```

use std::fmt;

use super::error::{FatalError, StorageError};

/// Consistency check result
///
/// Represents the three possible states after comparing metadata epoch
/// with blob epoch on startup.
#[derive(Debug, Clone, PartialEq)]
pub enum ConsistencyState {
    /// State A: Fully consistent
    ///
    /// `metadata_epoch == blob_epoch`
    /// Normal startup, no action needed.
    Consistent,

    /// State B: Blob is ahead of metadata
    ///
    /// `blob_epoch > metadata_epoch`
    ///
    /// This occurs when the atomic rename succeeded but SQLCipher update failed
    /// (crash during Phase 3.2 of AUP). The blob is the source of truth.
    /// Auto-heal by updating metadata to match blob epoch.
    BlobAhead {
        /// The epoch from the blob header
        blob_epoch: u32,
        /// The epoch from the metadata database
        metadata_epoch: u32,
    },

    /// State C: Metadata is ahead of blob
    ///
    /// `blob_epoch < metadata_epoch`
    ///
    /// This is an ILLEGAL state indicating:
    /// - Possible rollback attack
    /// - Critical filesystem corruption
    /// - Tampering detected
    ///
    /// Triggers immediate meltdown.
    MetadataAhead {
        /// The epoch from the blob header
        blob_epoch: u32,
        /// The epoch from the metadata database
        metadata_epoch: u32,
    },
}

impl ConsistencyState {
    /// Check if the system is consistent
    pub fn is_consistent(&self) -> bool {
        matches!(self, Self::Consistent)
    }

    /// Check if auto-healing is required
    pub fn needs_healing(&self) -> bool {
        matches!(self, Self::BlobAhead { .. })
    }

    /// Check if meltdown should be triggered
    pub fn is_fatal(&self) -> bool {
        matches!(self, Self::MetadataAhead { .. })
    }
}

impl fmt::Display for ConsistencyState {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Self::Consistent => write!(f, "Consistent"),
            Self::BlobAhead {
                blob_epoch,
                metadata_epoch,
            } => write!(
                f,
                "BlobAhead (blob_epoch={}, metadata_epoch={})",
                blob_epoch, metadata_epoch
            ),
            Self::MetadataAhead {
                blob_epoch,
                metadata_epoch,
            } => write!(
                f,
                "MetadataAhead (blob_epoch={}, metadata_epoch={}) - ILLEGAL STATE",
                blob_epoch, metadata_epoch
            ),
        }
    }
}

/// Trait for metadata source (e.g., SQLCipher database)
///
/// This abstraction allows the recovery logic to work with different
/// metadata storage backends.
pub trait MetadataSource: Send + Sync {
    /// Get the current epoch from metadata
    ///
    /// This should read the `Local_Epoch` field from the metadata database.
    fn get_epoch(&self) -> Result<u32, StorageError>;

    /// Update the epoch in metadata
    ///
    /// This should update the `Local_Epoch` field in the metadata database
    /// and commit the transaction.
    fn update_epoch(&self, new_epoch: u32) -> Result<(), StorageError>;
}

/// Trait for vault storage (e.g., encrypted .aet file)
///
/// This abstraction allows the recovery logic to work with different
/// vault storage implementations.
pub trait VaultStorage: Send + Sync {
    /// Get the epoch from the vault blob header
    ///
    /// This should read the epoch from the encrypted vault file's header.
    /// The implementation must verify AEAD before returning the epoch.
    fn get_blob_epoch(&self) -> Result<u32, StorageError>;
}

/// Crash recovery engine
///
/// Performs consistency checks and automatic healing on startup.
/// This ensures the system satisfies INVARIANT_1 (Epoch Monotonicity)
/// after any crash or unexpected shutdown.
///
/// # Thread Safety
///
/// This type is `Send + Sync` and can be shared across threads.
///
/// # Example
///
/// ```no_run
/// use aeternum_core::storage::recovery::{CrashRecovery, MetadataSource, VaultStorage};
/// use aeternum_core::storage::StorageError;
///
/// // Mock implementations
/// struct MockMetadata;
/// struct MockVault;
///
/// impl MetadataSource for MockMetadata {
///     fn get_epoch(&self) -> Result<u32, StorageError> { Ok(1) }
///     fn update_epoch(&self, _: u32) -> Result<(), StorageError> { Ok(()) }
/// }
///
/// impl VaultStorage for MockVault {
///     fn get_blob_epoch(&self) -> Result<u32, StorageError> { Ok(1) }
/// }
///
/// let metadata = MockMetadata;
/// let vault = MockVault;
/// let recovery = CrashRecovery::new(metadata, vault);
/// recovery.check_and_heal()?; // Performs full consistency check and auto-heal
/// # Ok::<(), aeternum_core::storage::StorageError>(())
/// ```
#[derive(Clone)]
pub struct CrashRecovery<M, V> {
    /// Metadata source (e.g., SQLCipher)
    metadata: M,
    /// Vault storage (e.g., encrypted .aet file)
    vault: V,
}

impl<M, V> CrashRecovery<M, V>
where
    M: MetadataSource,
    V: VaultStorage,
{
    /// Create a new crash recovery instance
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::recovery::{CrashRecovery, MetadataSource, VaultStorage};
    /// use aeternum_core::storage::StorageError;
    ///
    /// // Mock implementations for demonstration
    /// struct MockMetadata;
    /// struct MockVault;
    ///
    /// impl MetadataSource for MockMetadata {
    ///     fn get_epoch(&self) -> Result<u32, StorageError> { Ok(1) }
    ///     fn update_epoch(&self, _: u32) -> Result<(), StorageError> { Ok(()) }
    /// }
    ///
    /// impl VaultStorage for MockVault {
    ///     fn get_blob_epoch(&self) -> Result<u32, StorageError> { Ok(1) }
    /// }
    ///
    /// let metadata = MockMetadata;
    /// let vault = MockVault;
    /// let recovery = CrashRecovery::new(metadata, vault);
    /// ```
    pub fn new(metadata: M, vault: V) -> Self {
        Self { metadata, vault }
    }

    /// Perform consistency check
    ///
    /// Compares the epoch from metadata with the epoch from the vault blob
    /// and returns the current consistency state.
    ///
    /// # Returns
    ///
    /// - `Consistent` if epochs match
    /// - `BlobAhead` if blob epoch is greater than metadata epoch
    /// - `MetadataAhead` if blob epoch is less than metadata epoch (illegal)
    ///
    /// # Errors
    ///
    /// Returns an error if:
    /// - Metadata read fails
    /// - Vault blob read fails
    /// - AEAD verification fails
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::recovery::{CrashRecovery, ConsistencyState, MetadataSource, VaultStorage};
    /// use aeternum_core::storage::StorageError;
    ///
    /// // Mock implementations
    /// struct MockMetadata;
    /// struct MockVault;
    ///
    /// impl MetadataSource for MockMetadata {
    ///     fn get_epoch(&self) -> Result<u32, StorageError> { Ok(1) }
    ///     fn update_epoch(&self, _: u32) -> Result<(), StorageError> { Ok(()) }
    /// }
    ///
    /// impl VaultStorage for MockVault {
    ///     fn get_blob_epoch(&self) -> Result<u32, StorageError> { Ok(1) }
    /// }
    ///
    /// let metadata = MockMetadata;
    /// let vault = MockVault;
    /// let recovery = CrashRecovery::new(metadata, vault);
    /// let state = recovery.check_consistency()?;
    ///
    /// match state {
    ///     ConsistencyState::Consistent => println!("System is consistent"),
    ///     ConsistencyState::BlobAhead { .. } => println!("Auto-healing..."),
    ///     ConsistencyState::MetadataAhead { .. } => println!("FATAL ERROR!"),
    /// }
    /// # Ok::<(), aeternum_core::storage::StorageError>(())
    /// ```
    pub fn check_consistency(&self) -> Result<ConsistencyState, StorageError> {
        // Read epochs from both sources
        let metadata_epoch = self.metadata.get_epoch().map_err(|e| {
            StorageError::consistency_check(format!("Failed to read metadata epoch: {}", e))
        })?;

        let blob_epoch = self.vault.get_blob_epoch().map_err(|e| {
            StorageError::consistency_check(format!("Failed to read blob epoch: {}", e))
        })?;

        // Compare epochs
        match blob_epoch.cmp(&metadata_epoch) {
            std::cmp::Ordering::Equal => Ok(ConsistencyState::Consistent),
            std::cmp::Ordering::Greater => Ok(ConsistencyState::BlobAhead {
                blob_epoch,
                metadata_epoch,
            }),
            std::cmp::Ordering::Less => Ok(ConsistencyState::MetadataAhead {
                blob_epoch,
                metadata_epoch,
            }),
        }
    }

    /// Heal BlobAhead state
    ///
    /// When the blob epoch is ahead of metadata epoch, we update the metadata
    /// to match the blob. This is safe because the atomic rename (Phase 3.1)
    /// guarantees the blob is valid and complete.
    ///
    /// # Parameters
    ///
    /// - `new_epoch`: The epoch from the blob header
    ///
    /// # Errors
    ///
    /// Returns an error if:
    /// - Metadata update fails
    /// - Transaction commit fails
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::recovery::{CrashRecovery, MetadataSource, VaultStorage};
    /// use aeternum_core::storage::StorageError;
    ///
    /// // Mock implementations
    /// struct MockMetadata;
    /// struct MockVault;
    ///
    /// impl MetadataSource for MockMetadata {
    ///     fn get_epoch(&self) -> Result<u32, StorageError> { Ok(1) }
    ///     fn update_epoch(&self, _: u32) -> Result<(), StorageError> { Ok(()) }
    /// }
    ///
    /// impl VaultStorage for MockVault {
    ///     fn get_blob_epoch(&self) -> Result<u32, StorageError> { Ok(1) }
    /// }
    ///
    /// let metadata = MockMetadata;
    /// let vault = MockVault;
    /// let recovery = CrashRecovery::new(metadata, vault);
    ///
    /// // heal_blob_ahead is called automatically by check_and_heal(),
    /// // but can be called manually if needed
    /// recovery.heal_blob_ahead(5)?;
    /// # Ok::<(), aeternum_core::storage::StorageError>(())
    /// ```
    pub fn heal_blob_ahead(&self, new_epoch: u32) -> Result<(), StorageError> {
        eprintln!(
            "[RECOVERY] Auto-healing BlobAhead state: updating metadata to epoch {}",
            new_epoch
        );

        self.metadata.update_epoch(new_epoch).map_err(|e| {
            StorageError::consistency_check(format!("Failed to update metadata epoch: {}", e))
        })?;

        eprintln!("[RECOVERY] Successfully healed BlobAhead state");

        Ok(())
    }

    /// Handle MetadataAhead state (ILLEGAL)
    ///
    /// This state is illegal and indicates a severe problem:
    /// - Possible rollback attack (Invariant #1 violation)
    /// - Critical filesystem corruption
    /// - Tampering detected
    ///
    /// This method triggers immediate meltdown.
    ///
    /// # Panics
    ///
    /// This method always panics as part of the meltdown protocol.
    ///
    /// # Example
    ///
    /// ```should_panic
    /// use aeternum_core::storage::recovery::{CrashRecovery, MetadataSource, VaultStorage};
    /// use aeternum_core::storage::StorageError;
    ///
    /// // Mock implementations
    /// struct MockMetadata;
    /// struct MockVault;
    ///
    /// impl MetadataSource for MockMetadata {
    ///     fn get_epoch(&self) -> Result<u32, StorageError> { Ok(1) }
    ///     fn update_epoch(&self, _: u32) -> Result<(), StorageError> { Ok(()) }
    /// }
    ///
    /// impl VaultStorage for MockVault {
    ///     fn get_blob_epoch(&self) -> Result<u32, StorageError> { Ok(1) }
    /// }
    ///
    /// let metadata = MockMetadata;
    /// let vault = MockVault;
    /// let recovery = CrashRecovery::new(metadata, vault);
    /// recovery.handle_metadata_ahead()?; // Will panic
    /// # Ok::<(), aeternum_core::storage::StorageError>(())
    /// ```
    pub fn handle_metadata_ahead(&self) -> Result<(), StorageError> {
        let state = self.check_consistency()?;

        let (blob_epoch, metadata_epoch) = match state {
            ConsistencyState::MetadataAhead {
                blob_epoch,
                metadata_epoch,
            } => (blob_epoch, metadata_epoch),
            _ => {
                return Err(StorageError::consistency_check(
                    "handle_metadata_ahead called but state is not MetadataAhead",
                ))
            }
        };

        let error = FatalError::StorageInconsistency(format!(
            "MetadataAhead detected: blob_epoch={}, metadata_epoch={}. \
             This is an ILLEGAL state. Possible causes: rollback attack, \
             filesystem corruption, or tampering.",
            blob_epoch, metadata_epoch
        ));

        error.trigger_meltdown()
    }

    /// Perform full consistency check and auto-heal
    ///
    /// This is the main entry point for crash recovery. It:
    /// 1. Checks consistency state
    /// 2. Auto-heals BlobAhead state
    /// 3. Triggers meltdown on MetadataAhead state
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::recovery::{CrashRecovery, MetadataSource, VaultStorage};
    /// use aeternum_core::storage::StorageError;
    ///
    /// // Mock implementations
    /// struct MockMetadata;
    /// struct MockVault;
    ///
    /// impl MetadataSource for MockMetadata {
    ///     fn get_epoch(&self) -> Result<u32, StorageError> { Ok(1) }
    ///     fn update_epoch(&self, _: u32) -> Result<(), StorageError> { Ok(()) }
    /// }
    ///
    /// impl VaultStorage for MockVault {
    ///     fn get_blob_epoch(&self) -> Result<u32, StorageError> { Ok(1) }
    /// }
    ///
    /// // Call on startup
    /// let metadata = MockMetadata;
    /// let vault = MockVault;
    /// let recovery = CrashRecovery::new(metadata, vault);
    /// recovery.check_and_heal()?; // Returns Ok(()) if consistent or healed
    /// # Ok::<(), aeternum_core::storage::StorageError>(())
    /// ```
    pub fn check_and_heal(&self) -> Result<(), StorageError> {
        let state = self.check_consistency()?;

        match state {
            ConsistencyState::Consistent => {
                eprintln!("[RECOVERY] System is consistent, normal startup");
                Ok(())
            }
            ConsistencyState::BlobAhead { blob_epoch, .. } => self.heal_blob_ahead(blob_epoch),
            ConsistencyState::MetadataAhead { .. } => {
                // This will trigger meltdown (panic)
                self.handle_metadata_ahead()
            }
        }
    }
}

// ============================================================================
// Tests
// ============================================================================

#[cfg(test)]
mod tests {
    use super::*;

    // ------------------------------------------------------------------------
    // Mock implementations for testing
    // ------------------------------------------------------------------------

    /// Mock metadata source for testing
    #[derive(Debug, Clone)]
    struct MockMetadata {
        epoch: std::sync::Arc<std::sync::Mutex<u32>>,
        should_fail: std::sync::Arc<std::sync::Mutex<bool>>,
    }

    impl MockMetadata {
        fn new(epoch: u32) -> Self {
            Self {
                epoch: std::sync::Arc::new(std::sync::Mutex::new(epoch)),
                should_fail: std::sync::Arc::new(std::sync::Mutex::new(false)),
            }
        }


        fn fail(&self) {
            *self.should_fail.lock().unwrap() = true;
        }
    }

    impl MetadataSource for MockMetadata {
        fn get_epoch(&self) -> Result<u32, StorageError> {
            if *self.should_fail.lock().unwrap() {
                return Err(StorageError::consistency_check("Mock metadata failure"));
            }
            Ok(*self.epoch.lock().unwrap())
        }

        fn update_epoch(&self, new_epoch: u32) -> Result<(), StorageError> {
            if *self.should_fail.lock().unwrap() {
                return Err(StorageError::consistency_check("Mock metadata failure"));
            }
            *self.epoch.lock().unwrap() = new_epoch;
            Ok(())
        }
    }

    /// Mock vault storage for testing
    #[derive(Debug, Clone)]
    struct MockVault {
        epoch: u32,
        should_fail: bool,
    }

    impl MockVault {
        fn new(epoch: u32) -> Self {
            Self {
                epoch,
                should_fail: false,
            }
        }

        fn fail(&mut self) {
            self.should_fail = true;
        }
    }

    impl VaultStorage for MockVault {
        fn get_blob_epoch(&self) -> Result<u32, StorageError> {
            if self.should_fail {
                return Err(StorageError::consistency_check("Mock vault failure"));
            }
            Ok(self.epoch)
        }
    }

    // ------------------------------------------------------------------------
    // ConsistencyState Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_consistency_state_consistent() {
        let state = ConsistencyState::Consistent;
        assert!(state.is_consistent());
        assert!(!state.needs_healing());
        assert!(!state.is_fatal());
    }

    #[test]
    fn test_consistency_state_blob_ahead() {
        let state = ConsistencyState::BlobAhead {
            blob_epoch: 5,
            metadata_epoch: 3,
        };
        assert!(!state.is_consistent());
        assert!(state.needs_healing());
        assert!(!state.is_fatal());
    }

    #[test]
    fn test_consistency_state_metadata_ahead() {
        let state = ConsistencyState::MetadataAhead {
            blob_epoch: 3,
            metadata_epoch: 5,
        };
        assert!(!state.is_consistent());
        assert!(!state.needs_healing());
        assert!(state.is_fatal());
    }

    #[test]
    fn test_consistency_state_display() {
        assert_eq!(ConsistencyState::Consistent.to_string(), "Consistent");

        let blob_ahead = ConsistencyState::BlobAhead {
            blob_epoch: 5,
            metadata_epoch: 3,
        };
        assert_eq!(
            blob_ahead.to_string(),
            "BlobAhead (blob_epoch=5, metadata_epoch=3)"
        );

        let metadata_ahead = ConsistencyState::MetadataAhead {
            blob_epoch: 3,
            metadata_epoch: 5,
        };
        assert_eq!(
            metadata_ahead.to_string(),
            "MetadataAhead (blob_epoch=3, metadata_epoch=5) - ILLEGAL STATE"
        );
    }

    // ------------------------------------------------------------------------
    // CrashRecovery Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_check_consistency_state_a() {
        let metadata = MockMetadata::new(5);
        let vault = MockVault::new(5);
        let recovery = CrashRecovery::new(metadata, vault);

        let state = recovery.check_consistency().unwrap();
        assert_eq!(state, ConsistencyState::Consistent);
    }

    #[test]
    fn test_check_consistency_state_b_blob_ahead() {
        let metadata = MockMetadata::new(3);
        let vault = MockVault::new(5);
        let recovery = CrashRecovery::new(metadata, vault);

        let state = recovery.check_consistency().unwrap();
        assert_eq!(
            state,
            ConsistencyState::BlobAhead {
                blob_epoch: 5,
                metadata_epoch: 3
            }
        );
    }

    #[test]
    fn test_check_consistency_state_c_metadata_ahead() {
        let metadata = MockMetadata::new(5);
        let vault = MockVault::new(3);
        let recovery = CrashRecovery::new(metadata, vault);

        let state = recovery.check_consistency().unwrap();
        assert_eq!(
            state,
            ConsistencyState::MetadataAhead {
                blob_epoch: 3,
                metadata_epoch: 5
            }
        );
    }

    #[test]
    fn test_check_consistency_metadata_failure() {
        let metadata = MockMetadata::new(5);
        metadata.fail();
        let vault = MockVault::new(5);
        let recovery = CrashRecovery::new(metadata, vault);

        let result = recovery.check_consistency();
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            StorageError::ConsistencyCheckFailed(_)
        ));
    }

    #[test]
    fn test_check_consistency_vault_failure() {
        let metadata = MockMetadata::new(5);
        let mut vault = MockVault::new(5);
        vault.fail();
        let recovery = CrashRecovery::new(metadata, vault);

        let result = recovery.check_consistency();
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            StorageError::ConsistencyCheckFailed(_)
        ));
    }

    #[test]
    fn test_heal_blob_ahead() {
        let metadata = MockMetadata::new(3);
        let vault = MockVault::new(5);
        let recovery = CrashRecovery::new(metadata.clone(), vault);

        // Heal to epoch 5
        recovery.heal_blob_ahead(5).unwrap();

        // Verify metadata was updated
        assert_eq!(metadata.get_epoch().unwrap(), 5);

        // Verify system is now consistent
        let state = recovery.check_consistency().unwrap();
        assert_eq!(state, ConsistencyState::Consistent);
    }

    #[test]
    fn test_heal_blob_ahead_failure() {
        let metadata = MockMetadata::new(3);
        metadata.fail();
        let vault = MockVault::new(5);
        let recovery = CrashRecovery::new(metadata, vault);

        let result = recovery.heal_blob_ahead(5);
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            StorageError::ConsistencyCheckFailed(_)
        ));
    }

    #[test]
    #[should_panic(expected = "AETERNUM MELTDOWN")]
    fn test_handle_metadata_ahead_triggers_meltdown() {
        let metadata = MockMetadata::new(5);
        let vault = MockVault::new(3);
        let recovery = CrashRecovery::new(metadata, vault);

        // This should trigger meltdown (panic)
        recovery.handle_metadata_ahead().unwrap();
    }

    #[test]
    fn test_handle_metadata_ahead_wrong_state() {
        let metadata = MockMetadata::new(5);
        let vault = MockVault::new(5);
        let recovery = CrashRecovery::new(metadata, vault);

        let result = recovery.handle_metadata_ahead();
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            StorageError::ConsistencyCheckFailed(_)
        ));
    }

    #[test]
    fn test_check_and_heal_consistent() {
        let metadata = MockMetadata::new(5);
        let vault = MockVault::new(5);
        let recovery = CrashRecovery::new(metadata, vault);

        // Should return Ok immediately for consistent state
        recovery.check_and_heal().unwrap();
    }

    #[test]
    fn test_check_and_heal_blob_ahead() {
        let metadata = MockMetadata::new(3);
        let vault = MockVault::new(5);
        let recovery = CrashRecovery::new(metadata.clone(), vault);

        // Should auto-heal
        recovery.check_and_heal().unwrap();

        // Verify metadata was updated
        assert_eq!(metadata.get_epoch().unwrap(), 5);
    }

    #[test]
    #[should_panic(expected = "AETERNUM MELTDOWN")]
    fn test_check_and_heal_metadata_ahead() {
        let metadata = MockMetadata::new(5);
        let vault = MockVault::new(3);
        let recovery = CrashRecovery::new(metadata, vault);

        // Should trigger meltdown
        recovery.check_and_heal().unwrap();
    }

    #[test]
    fn test_recovery_cloned_is_independent() {
        let metadata = MockMetadata::new(5);
        let vault = MockVault::new(5);
        let recovery1 = CrashRecovery::new(metadata.clone(), vault.clone());
        let recovery2 = recovery1.clone();

        // Both should work independently
        let state1 = recovery1.check_consistency().unwrap();
        let state2 = recovery2.check_consistency().unwrap();

        assert_eq!(state1, ConsistencyState::Consistent);
        assert_eq!(state2, ConsistencyState::Consistent);
    }
}
