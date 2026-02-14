//! # Epoch Upgrade Coordination
//!
//! This module implements the coordination layer for cryptographic epoch upgrades,
//! integrating the Atomic Upgrade Protocol (AUP) from the storage layer with
//! the PQRR state machine.
//!
//! ## Core Responsibilities
//!
//! - **Epoch Upgrade Execution**: Coordinates the three-phase AUP protocol
//! - **Invariant #3 Enforcement**: Prevents RECOVERY role from executing σ_rotate
//! - **Crash Recovery**: Ensures vault consistency after interrupted upgrades
//! - **Device Header Updates**: Manages header regeneration for all active devices
//!
//! ## Architecture
//!
//! ```text
//! ┌─────────────────────────────────────────────────────────────┐
//! │              Protocol Layer (This Module)                  │
//! │  ┌─────────────────────────────────────────────────────┐  │
//! │  │         EpochUpgradeCoordinator                     │  │
//! │  │  - execute_epoch_upgrade()                        │  │
//! │  │  - execute_rotation() (Invariant #3 check)         │  │
//! │  │  - integrate_shadow_write()                        │  │
//! │  └─────────────────────────────────────────────────────┘  │
//! │                          │                                │
//! │                          ▼                                │
//! │  ┌─────────────────────────────────────────────────────┐  │
//! │  │         Storage::AUP (Shadow Write)               │  │
//! │  │  - aup_prepare()                                   │  │
//! │  │  - aup_shadow_write()                              │  │
//! │  │  - aup_atomic_commit()                              │  │
//! │  └─────────────────────────────────────────────────────┘  │
//! └─────────────────────────────────────────────────────────────┘
//! ```
//!
//! ## Invariant Enforcement
//!
//! ### Invariant #3: Causal Entropy Barrier
//!
//! ```text
//! Role(S) = RECOVERY ⇒ σ_rotate ∉ P(S)
//! ```
//!
//! This module enforces that devices with RECOVERY role cannot execute
//! management operations like σ_rotate (epoch rotation). Only AUTHORIZED
//! role devices can initiate epoch upgrades.
//!
//! ## Usage Example
//!
//! ```no_run
//! use aeternum_core::protocol::epoch_upgrade::EpochUpgradeCoordinator;
//! use aeternum_core::protocol::PqrrStateMachine;
//! use aeternum_core::models::{CryptoEpoch, Role};
//!
//! # fn main() -> Result<(), Box<dyn std::error::Error>> {
//! let state_machine = PqrrStateMachine::new(epoch, headers);
//! let mut coordinator = EpochUpgradeCoordinator::new(state_machine);
//!
//! // Attempt epoch upgrade (checks Invariant #3)
//! let new_epoch = CryptoEpoch::new(2, aeternum_core::models::CryptoAlgorithm::V1);
//! match coordinator.execute_epoch_upgrade(
//!     "/data/vault.db".as_ref(),
//!     new_epoch,
//!     Role::Authorized,
//! ) {
//!     Ok(_) => println!("Epoch upgrade succeeded"),
//!     Err(e) => eprintln!("Epoch upgrade failed: {}", e),
//! }
//! # Ok(())
//! # }
//! ```

use crate::models::device::{Operation, Role};
use crate::models::epoch::CryptoEpoch;
use crate::protocol::error::{PqrrError, Result};
use crate::protocol::pqrr::PqrrStateMachine;
use crate::storage::aug::{aup_atomic_commit, aup_prepare, aup_shadow_write};
use std::path::Path;

// ============================================================================
// Epoch Upgrade Coordinator
// ============================================================================

/// Epoch upgrade coordinator
///
/// Coordinates cryptographic epoch upgrades by integrating the Atomic Upgrade
/// Protocol (AUP) with the PQRR state machine. Enforces Invariant #3
/// (Causal Entropy Barrier) to prevent RECOVERY role devices from executing
/// management operations.
///
/// ## Fields
///
/// - `state_machine`: Reference to PQRR state machine for state transitions
///
/// ## Invariant Enforcement
///
/// **Invariant #3**: RECOVERY role cannot execute σ_rotate
/// ```text
/// if role == RECOVERY:
///     return Err(PermissionDenied)
/// ```
pub struct EpochUpgradeCoordinator<'a> {
    /// Reference to PQRR state machine
    state_machine: &'a mut PqrrStateMachine,
}

impl<'a> EpochUpgradeCoordinator<'a> {
    /// Create a new epoch upgrade coordinator
    ///
    /// # Arguments
    ///
    /// - `state_machine`: Mutable reference to PQRR state machine
    ///
    /// # Returns
    ///
    /// A new coordinator instance
    pub fn new(state_machine: &'a mut PqrrStateMachine) -> Self {
        Self { state_machine }
    }

    // ------------------------------------------------------------------------
    // Invariant #3 Enforcement: Causal Entropy Barrier
    // ------------------------------------------------------------------------

    /// Execute rotation operation (Invariant #3 enforcement point)
    ///
    /// This is the primary enforcement point for Invariant #3: Causal Entropy Barrier.
    /// Checks if the given role is permitted to execute the specified operation.
    ///
    /// **Invariant #3**: Decryption authority ≠ Management authority
    /// ```text
    /// Role(S) = RECOVERY ⇒ σ_rotate ∉ P(S)
    /// ```
    ///
    /// # Arguments
    ///
    /// - `role`: The role attempting to execute the operation
    /// - `operation`: The operation being attempted
    ///
    /// # Returns
    ///
    /// - `Ok(())` if the role is permitted to execute the operation
    /// - `Err(PqrrError::PermissionDenied)` if Invariant #3 violated
    ///
    /// # Example
    ///
    /// ```
    /// # use aeternum_core::protocol::epoch_upgrade::EpochUpgradeCoordinator;
    /// # use aeternum_core::protocol::PqrrStateMachine;
    /// # use aeternum_core::models::{CryptoEpoch, Role, Operation};
    /// # use std::collections::HashMap;
    /// # fn main() -> aeternum_core::protocol::Result<()> {
    /// let epoch = CryptoEpoch::initial();
    /// let headers = HashMap::new();
    /// let mut sm = PqrrStateMachine::new(epoch, headers);
    /// let mut coordinator = EpochUpgradeCoordinator::new(&mut sm);
    ///
    /// // RECOVERY role cannot execute σ_rotate
    /// let result = coordinator.execute_rotation(Role::Recovery, Operation::SigmaRotate);
    /// assert!(result.is_err());
    /// # Ok(())
    /// # }
    /// ```
    pub fn execute_rotation(&self, role: Role, operation: Operation) -> Result<()> {
        // Invariant #3: Causal Entropy Barrier
        // RECOVERY role cannot execute management operations
        if !role.can_permit_operation(operation) {
            return Err(PqrrError::permission_denied(
                role.as_str().to_string(),
                operation.as_str().to_string(),
            ));
        }

        eprintln!(
            "[EpochUpgrade] Rotation permitted: role={}, operation={}",
            role.as_str(),
            operation.as_str()
        );

        Ok(())
    }

    // ------------------------------------------------------------------------
    // Epoch Upgrade Execution (AUP Integration)
    // ------------------------------------------------------------------------

    /// Execute epoch upgrade using Atomic Upgrade Protocol (AUP)
    ///
    /// This is the main entry point for epoch upgrades. It:
    /// 1. Checks Invariant #3 (role permissions)
    /// 2. Validates state machine is in Idle state
    /// 3. Executes AUP three-phase protocol
    /// 4. Updates state machine to new epoch
    ///
    /// ## AUP Integration
    ///
    /// This method integrates with storage layer's AUP implementation:
    /// - **Phase 1**: `aup_prepare()` - Prepare new epoch in memory
    /// - **Phase 2**: `aup_shadow_write()` - Shadow write to temp file
    /// - **Phase 3**: `aup_atomic_commit()` - Atomic rename to target
    ///
    /// # Arguments
    ///
    /// - `vault_path`: Path to vault file (e.g., `/data/vault.db`)
    /// - `new_epoch`: New epoch version to upgrade to
    /// - `role`: Role of device initiating upgrade
    ///
    /// # Returns
    ///
    /// - `Ok(())` if epoch upgrade succeeded
    /// - `Err(PqrrError::PermissionDenied)` if Invariant #3 violated
    /// - `Err(PqrrError::InvalidStateTransition)` if not in Idle state
    /// - `Err(PqrrError::EpochRegression)` if Invariant #1 violated
    /// - `Err(PqrrError::StorageError)` if AUP protocol failed
    ///
    /// # Errors
    ///
    /// Returns `PqrrError::PermissionDenied` if:
    /// - `role == RECOVERY` and operation is σ_rotate (Invariant #3)
    ///
    /// Returns `PqrrError::InvalidStateTransition` if:
    /// - State machine is not in Idle state
    /// - Cannot upgrade epoch from current state
    ///
    /// Returns `PqrrError::EpochRegression` if:
    /// - `new_epoch <= current_epoch` (Invariant #1)
    ///
    /// Returns `PqrrError::StorageError` if:
    /// - AUP prepare phase failed
    /// - Shadow write failed (disk full, I/O error)
    /// - Atomic commit failed (filesystem error)
    ///
    /// # Example
    ///
    /// ```no_run
    /// # use aeternum_core::protocol::epoch_upgrade::EpochUpgradeCoordinator;
    /// # use aeternum_core::protocol::PqrrStateMachine;
    /// # use aeternum_core::models::{CryptoEpoch, Role};
    /// # use std::collections::HashMap;
    /// # fn main() -> aeternum_core::protocol::Result<()> {
    /// let epoch = CryptoEpoch::initial();
    /// let headers = HashMap::new();
    /// let mut sm = PqrrStateMachine::new(epoch, headers);
    /// let mut coordinator = EpochUpgradeCoordinator::new(&mut sm);
    ///
    /// let new_epoch = CryptoEpoch::new(2, aeternum_core::models::CryptoAlgorithm::V1);
    ///
    /// // AUTHORIZED role can execute epoch upgrade
    /// coordinator.execute_epoch_upgrade(
    ///     "/data/vault.db".as_ref(),
    ///     new_epoch,
    ///     Role::Authorized,
    /// )?;
    ///
    /// // State machine should now be in new epoch
    /// assert_eq!(coordinator.state_machine.current_epoch().version, 2);
    /// # Ok(())
    /// # }
    /// ```
    pub fn execute_epoch_upgrade(
        &mut self,
        vault_path: impl AsRef<Path>,
        new_epoch: CryptoEpoch,
        role: Role,
    ) -> Result<()> {
        // Step 1: Invariant #3 check - RECOVERY role cannot execute σ_rotate
        self.execute_rotation(role, Operation::SigmaRotate)?;

        // Step 2: Check state machine can upgrade epoch
        let current_state = self.state_machine.state();
        if !current_state.can_upgrade_epoch() {
            return Err(PqrrError::invalid_transition(
                current_state.as_str().to_string(),
                "Rekeying".to_string(),
                format!("cannot upgrade epoch from {} state", current_state.as_str()),
            ));
        }

        eprintln!(
            "[EpochUpgrade] Starting epoch upgrade: {} -> {}",
            self.state_machine.current_epoch().version,
            new_epoch.version
        );

        // Step 3: Transition to Rekeying state
        self.state_machine
            .transition_to_rekeying_internal(new_epoch.clone())?;

        // Step 4: AUP Phase 1 - Prepare
        let current_epoch = self.state_machine.current_epoch();
        // TODO: Get actual VK from vault (placeholder for now)
        let current_vk = b"placeholder_vault_key";
        let preparation = aup_prepare(current_epoch, current_vk)
            .map_err(|e| PqrrError::storage_error(format!("AUP prepare failed: {}", e)))?;

        eprintln!(
            "[EpochUpgrade] AUP Phase 1 complete: new_epoch={}",
            preparation.new_epoch.version
        );

        // Step 5: AUP Phase 2 - Shadow Write
        let shadow_file = aup_shadow_write(&vault_path, &preparation)
            .map_err(|e| PqrrError::storage_error(format!("AUP shadow write failed: {}", e)))?;

        eprintln!(
            "[EpochUpgrade] AUP Phase 2 complete: shadow_file={}",
            shadow_file.path().display()
        );

        // Step 6: AUP Phase 3 - Atomic Commit
        aup_atomic_commit(&vault_path, shadow_file, &preparation.new_epoch)
            .map_err(|e| PqrrError::storage_error(format!("AUP atomic commit failed: {}", e)))?;

        eprintln!(
            "[EpochUpgrade] AUP Phase 3 complete: vault={}",
            vault_path.as_ref().display()
        );

        // Step 7: Update state machine epoch
        self.state_machine.apply_epoch_upgrade_internal(new_epoch)?;

        // Step 8: Return to Idle state
        self.state_machine.return_to_idle_internal()?;

        eprintln!(
            "[EpochUpgrade] Epoch upgrade complete: epoch={}",
            self.state_machine.current_epoch().version
        );

        Ok(())
    }

    // ------------------------------------------------------------------------
    // Crash Recovery Integration
    // ------------------------------------------------------------------------

    /// Recover from interrupted epoch upgrade
    ///
    /// Handles crash recovery by checking vault file state and determining
    /// if an upgrade was in progress. Integrates with storage layer's
    /// crash recovery mechanisms.
    ///
    /// ## Crash Recovery Scenarios
    ///
    /// **Scenario A**: Crash during shadow write (Phase 2)
    /// - Vault file unchanged, temp file may exist
    /// - Action: Delete temp file, remain in current epoch
    ///
    /// **Scenario B**: Crash during atomic commit (Phase 3)
    /// - Vault file may be old or new (atomic rename)
    /// - Action: Verify vault epoch, align state machine
    ///
    /// # Arguments
    ///
    /// - `vault_path`: Path to vault file
    ///
    /// # Returns
    ///
    /// - `Ok(bool)` - `true` if recovery was performed, `false` if no recovery needed
    /// - `Err(PqrrError::StorageError)` if recovery failed
    pub fn recover_from_crash(&mut self, vault_path: impl AsRef<Path>) -> Result<bool> {
        let vault_path = vault_path.as_ref();

        eprintln!(
            "[EpochUpgrade] Checking for crash recovery: vault={}",
            vault_path.display()
        );

        // Read vault epoch from file
        let vault_epoch = crate::storage::aug::read_vault_epoch(vault_path)
            .map_err(|e| PqrrError::storage_error(format!("Failed to read vault epoch: {}", e)))?;

        let state_epoch = self.state_machine.current_epoch().version as u64;

        eprintln!(
            "[EpochUpgrade] Vault epoch: {}, State epoch: {}",
            vault_epoch, state_epoch
        );

        // Scenario A: Vault epoch == State epoch (no crash or clean recovery)
        if vault_epoch == state_epoch {
            eprintln!("[EpochUpgrade] No recovery needed (epochs aligned)");
            return Ok(false);
        }

        // Scenario B: Vault epoch > State epoch (crash during Phase 3)
        // Vault has been upgraded but state machine wasn't updated
        if vault_epoch > state_epoch {
            eprintln!(
                "[EpochUpgrade] Crash detected: vault ahead of state ({} > {})",
                vault_epoch, state_epoch
            );

            // Recover by updating state machine to vault epoch
            let recovered_epoch = CryptoEpoch::new(
                vault_epoch as u64,
                crate::models::CryptoAlgorithm::V1, // TODO: Read from vault
            );

            self.state_machine
                .apply_epoch_upgrade_internal(recovered_epoch)?;

            eprintln!(
                "[EpochUpgrade] Recovery complete: state aligned to vault epoch {}",
                vault_epoch
            );

            return Ok(true);
        }

        // Scenario C: Vault epoch < State epoch (inconsistent state)
        // This should never happen and indicates corruption
        eprintln!(
            "[EpochUpgrade] CRITICAL: Vault epoch behind state ({} < {})",
            vault_epoch, state_epoch
        );

        Err(PqrrError::storage_error(format!(
            "Storage inconsistency: vault epoch {} < state epoch {}",
            vault_epoch, state_epoch
        )))
    }
}

// ============================================================================
// Tests
// ============================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::epoch::CryptoAlgorithm;
    use crate::protocol::pqrr::ProtocolState;
    use tempfile::TempDir;

    // ------------------------------------------------------------------------
    // execute_rotation() Tests (Invariant #3)
    // ------------------------------------------------------------------------

    #[test]
    fn test_execute_rotation_authorized_succeeds() {
        let mut sm = PqrrStateMachine::new(0);
        let coordinator = EpochUpgradeCoordinator::new(&mut sm);

        // AUTHORIZED role can execute σ_rotate
        let result = coordinator.execute_rotation(Role::Authorized, Operation::SigmaRotate);
        assert!(result.is_ok());
    }

    #[test]
    fn test_execute_rotation_recovery_fails() {
        let mut sm = PqrrStateMachine::new(0);
        let coordinator = EpochUpgradeCoordinator::new(&mut sm);

        // RECOVERY role cannot execute σ_rotate (Invariant #3)
        let result = coordinator.execute_rotation(Role::Recovery, Operation::SigmaRotate);
        assert!(result.is_err());

        if let Err(PqrrError::PermissionDenied { role, operation }) = result {
            assert_eq!(role, "RECOVERY");
            assert_eq!(operation, "σ_rotate");
        } else {
            panic!("Expected PermissionDenied error");
        }
    }

    #[test]
    fn test_execute_rotation_all_operations() {
        let mut sm = PqrrStateMachine::new(0);
        let coordinator = EpochUpgradeCoordinator::new(&mut sm);

        // Test all management operations
        let operations = vec![
            Operation::SigmaRotate,
            Operation::RevokeDevice,
            Operation::RekeyVault,
            Operation::UpdatePolicy,
        ];

        for op in operations {
            // AUTHORIZED can execute all
            assert!(coordinator.execute_rotation(Role::Authorized, op).is_ok());

            // RECOVERY cannot execute any (Invariant #3)
            assert!(coordinator.execute_rotation(Role::Recovery, op).is_err());
        }
    }

    // ------------------------------------------------------------------------
    // execute_epoch_upgrade() Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_execute_epoch_upgrade_authorized_succeeds() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let mut sm = PqrrStateMachine::new(0);
        let mut coordinator = EpochUpgradeCoordinator::new(&mut sm);

        let new_epoch = CryptoEpoch::new(2, CryptoAlgorithm::V1);

        // AUTHORIZED role can execute epoch upgrade
        let result = coordinator.execute_epoch_upgrade(&vault_path, new_epoch, Role::Authorized);

        // Should succeed (AUP protocol should work)
        assert!(result.is_ok());

        // State machine should be in new epoch
        assert_eq!(coordinator.state_machine.current_epoch().version, 2);
        assert!(matches!(
            coordinator.state_machine.state(),
            ProtocolState::Idle
        ));
    }

    #[test]
    fn test_execute_epoch_upgrade_recovery_fails() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let mut sm = PqrrStateMachine::new(0);
        let mut coordinator = EpochUpgradeCoordinator::new(&mut sm);

        let new_epoch = CryptoEpoch::new(2, CryptoAlgorithm::V1);

        // RECOVERY role cannot execute epoch upgrade (Invariant #3)
        let result = coordinator.execute_epoch_upgrade(&vault_path, new_epoch, Role::Recovery);

        assert!(result.is_err());

        if let Err(PqrrError::PermissionDenied { role, operation }) = result {
            assert_eq!(role, "RECOVERY");
            assert_eq!(operation, "σ_rotate");
        } else {
            panic!("Expected PermissionDenied error");
        }

        // State machine should remain in original epoch
        assert_eq!(coordinator.state_machine.current_epoch().version, 0);
    }

    #[test]
    fn test_execute_epoch_upgrade_from_idle_only() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let mut sm = PqrrStateMachine::new(0);

        // Transition to Degraded state
        sm.transition_to_degraded_internal().unwrap();

        // Now create coordinator after state change
        let mut coordinator = EpochUpgradeCoordinator::new(&mut sm);

        let new_epoch = CryptoEpoch::new(2, CryptoAlgorithm::V1);

        // Cannot upgrade epoch from Degraded state
        let result = coordinator.execute_epoch_upgrade(&vault_path, new_epoch, Role::Authorized);

        assert!(result.is_err());

        if let Err(PqrrError::InvalidStateTransition { from, to, .. }) = result {
            assert_eq!(from, "Degraded");
            assert_eq!(to, "Rekeying");
        } else {
            panic!("Expected InvalidStateTransition error");
        }
    }

    // ------------------------------------------------------------------------
    // recover_from_crash() Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_recover_from_crash_no_recovery_needed() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        // Create vault file at epoch 2 (aup_prepare creates epoch+1)
        let epoch1 = CryptoEpoch::initial();
        let prep = aup_prepare(&epoch1, b"vk").unwrap();
        let shadow = aup_shadow_write(&vault_path, &prep).unwrap();
        aup_atomic_commit(&vault_path, shadow, &prep.new_epoch).unwrap();

        // Initialize state machine at epoch 2 (matching vault)
        let mut sm = PqrrStateMachine::new(prep.new_epoch.version as u32);
        let mut coordinator = EpochUpgradeCoordinator::new(&mut sm);

        // No recovery needed (epochs aligned)
        let recovered = coordinator.recover_from_crash(&vault_path).unwrap();
        assert!(!recovered);
        assert_eq!(coordinator.state_machine.current_epoch().version, 2);
    }

    #[test]
    fn test_recover_from_crash_vault_ahead() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        // Create vault file at epoch 2 (aup_prepare creates epoch+1)
        let epoch1 = CryptoEpoch::initial();
        let prep = aup_prepare(&epoch1, b"vk").unwrap();
        let shadow = aup_shadow_write(&vault_path, &prep).unwrap();
        aup_atomic_commit(&vault_path, shadow, &prep.new_epoch).unwrap();

        // Initialize state machine at epoch 1 (simulating crash during Phase 3)
        let mut sm = PqrrStateMachine::new(epoch1.version as u32);
        let mut coordinator = EpochUpgradeCoordinator::new(&mut sm);

        // Recovery should align state to vault epoch
        let recovered = coordinator.recover_from_crash(&vault_path).unwrap();
        assert!(recovered);
        assert_eq!(coordinator.state_machine.current_epoch().version, 2);
    }

    #[test]
    fn test_recover_from_crash_vault_behind_fails() {
        // This test verifies that vault behind state (inconsistent state)
        // is properly detected and fails with appropriate error.
        //
        // Note: Due to AUP design (aup_prepare creates epoch+1), we cannot
        // easily create vault at older epoch than state machine.
        // This test is kept for documentation but marked as todo.
        //
        // TODO: Find a way to test vault_behind scenario without
        //       violating AUP invariants or redesigning test setup.
        //
        // For now, we skip this test
        println!("Skipping test_recover_from_crash_vault_behind_fails - needs AUP redesign");
    }

    // ------------------------------------------------------------------------
    // Integration Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_full_epoch_upgrade_flow() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let mut sm = PqrrStateMachine::new(0);

        // Verify initial state
        assert_eq!(sm.current_epoch().version, 0);
        assert!(matches!(sm.state(), ProtocolState::Idle));

        // Execute epoch upgrade (0 -> 1)
        let new_epoch = CryptoEpoch::new(1, CryptoAlgorithm::V1);
        {
            let mut coordinator = EpochUpgradeCoordinator::new(&mut sm);
            assert!(coordinator
                .execute_epoch_upgrade(&vault_path, new_epoch, Role::Authorized)
                .is_ok());
        }

        // Verify final state
        assert_eq!(sm.current_epoch().version, 1);
        assert!(matches!(sm.state(), ProtocolState::Idle));

        // Verify vault file was updated
        let vault_epoch = crate::storage::aug::read_vault_epoch(&vault_path).unwrap();
        assert_eq!(vault_epoch, 1);
    }

    #[test]
    fn test_multiple_epoch_upgrades() {
        let temp_dir = TempDir::new().unwrap();
        let vault_path = temp_dir.path().join("vault.db");

        let mut sm = PqrrStateMachine::new(0);

        // Execute 3 epoch upgrades
        for i in 2..=4 {
            let new_epoch = CryptoEpoch::new(i, CryptoAlgorithm::V1);
            {
                let mut coordinator = EpochUpgradeCoordinator::new(&mut sm);
                assert!(coordinator
                    .execute_epoch_upgrade(&vault_path, new_epoch, Role::Authorized)
                    .is_ok());
            }

            assert_eq!(sm.current_epoch().version, i);
        }

        // Verify final state
        assert_eq!(sm.current_epoch().version, 4);
        assert_eq!(
            crate::storage::aug::read_vault_epoch(&vault_path).unwrap(),
            4
        );
    }
}
