//! # Aeternum Engine
//!
//! Main engine implementation for Android UI layer.
//!
//! ## Architecture
//!
//! ```text
//! Kotlin UI → AeternumEngine → Protocol/Storage Layers
//!            ↓ unlock()
//!            ↓ VaultSession (handle)
//!            ↓ get_device_list()
//!            ↓ Vec<DeviceInfo>
//! ```

use crate::bridge::session::VaultSession;
use crate::bridge::types::DeviceInfo;
use crate::models::device::{DeviceHeader, DeviceId};
use crate::models::epoch::CryptoEpoch;
use crate::protocol::device_mgmt::revoke_device;
use crate::protocol::error::{PqrrError, Result};
use crate::protocol::PqrrStateMachine;
use crate::protocol::ProtocolState;
use std::collections::HashMap;
use std::sync::{Arc, RwLock};

/// Mock recovery request ID generator
fn generate_recovery_id() -> String {
    use std::time::{SystemTime, UNIX_EPOCH};
    let timestamp = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis())
        .unwrap_or(0);
    format!("rec_{}", timestamp)
}

/// Aeternum engine - Main entry point for UI layer
///
/// Provides high-level operations for Android UI:
/// - Vault unlock with session management
/// - Device management (list, revoke)
/// - Recovery protocol (initiate, veto)
/// - Vault integrity verification
#[derive(uniffi::Object)]
pub struct AeternumEngine {
    /// Vault path
    vault_path: String,

    /// Protocol state machine
    state_machine: Arc<RwLock<PqrrStateMachine>>,

    /// Device headers (cached from state machine)
    device_headers: Arc<RwLock<HashMap<DeviceId, DeviceHeader>>>,

    /// Current device ID (this device)
    this_device_id: DeviceId,
}

impl AeternumEngine {
    /// Create a new Aeternum engine (internal constructor)
    ///
    /// # Arguments
    /// - `vault_path`: Path to vault file
    /// - `state_machine`: Protocol state machine
    /// - `this_device_id`: This device's ID
    pub fn new(
        vault_path: String,
        state_machine: PqrrStateMachine,
        this_device_id: DeviceId,
    ) -> Self {
        let device_headers = state_machine.device_headers().clone();

        Self {
            vault_path,
            state_machine: Arc::new(RwLock::new(state_machine)),
            device_headers: Arc::new(RwLock::new(device_headers)),
            this_device_id,
        }
    }
}

// ============================================================================
// UniFFI Exports
// ============================================================================

/// UniFFI-exported methods for AeternumEngine
#[uniffi::export]
impl AeternumEngine {
    /// Constructor - Create engine with vault path
    ///
    /// # Arguments
    /// - `vault_path`: Path to vault file
    ///
    /// # Errors
    /// - `PqrrError::StorageError` - Failed to initialize vault
    #[uniffi::constructor]
    pub fn new_with_path(vault_path: String) -> Result<Self> {
        // In production, this would:
        // 1. Load vault from file
        // 2. Deserialize headers and epoch
        // 3. Initialize state machine

        // For now, create a demo state machine
        let epoch = CryptoEpoch::initial();
        let headers = HashMap::new();
        let state_machine = PqrrStateMachine::create(epoch, headers);
        let this_device_id = DeviceId::generate();

        Ok(Self::new(vault_path, state_machine, this_device_id))
    }

    /// Initialize vault (for first-time setup)
    ///
    /// # Arguments
    /// - `hardware_key_blob`: Hardware key blob from StrongBox
    ///
    /// # Errors
    /// - `PqrrError::StorageError` - Failed to create vault
    /// - `PqrrError::InvalidStateTransition` - Vault already initialized
    pub fn initialize_vault(&self, _hardware_key_blob: Vec<u8>) -> Result<()> {
        // In production, this would:
        // 1. Generate new Vault Key (VK)
        // 2. Generate new DEK
        // 3. Wrap DEK with hardware key
        // 4. Create initial vault blob
        // 5. Write to disk with atomic rename

        // For demo, just return Ok
        Ok(())
    }

    /// Unlock vault - Returns session handle
    ///
    /// # Arguments
    /// - `hardware_key_blob`: Hardware key blob from StrongBox
    ///
    /// # Returns
    /// VaultSession handle for decryption operations
    ///
    /// # Errors
    /// - `PqrrError::InsufficientPrivileges` - Hardware key invalid
    /// - `PqrrError::HeaderIncomplete` - Vault data corrupted
    pub fn unlock(&self, _hardware_key_blob: Vec<u8>) -> Result<VaultSession> {
        // In production, this would:
        // 1. Use hardware key to decrypt DEK
        // 2. Use DEK to decrypt VK
        // 3. Return session with VK

        // For demo, return a session with mock VK
        let vault_key = vec![0u8; 32]; // Mock 256-bit vault key
        let epoch = self.state_machine.read().unwrap().current_epoch().version as u32;

        Ok(VaultSession::new(vault_key, epoch))
    }

    /// Get list of all devices (sanitized)
    ///
    /// Returns list of all registered devices with non-sensitive metadata.
    ///
    /// # Errors
    /// - `PqrrError::PermissionDenied` - Not authorized to view devices
    pub fn get_device_list(&self) -> Result<Vec<DeviceInfo>> {
        let headers = self.device_headers.read().unwrap();
        let state_machine = self.state_machine.read().unwrap();

        let mut devices = Vec::new();

        for (device_id, header) in headers.iter() {
            let state = state_machine.state(); // Would be per-device in production

            let info = DeviceInfo::new(
                *device_id,
                format!("Device {}", device_id.to_string()),
                header.epoch.version as u32,
                state,
                *device_id == self.this_device_id,
            );

            devices.push(info);
        }

        Ok(devices)
    }

    /// Revoke a device
    ///
    /// # Arguments
    /// - `device_id_bytes`: Device ID to revoke (16 bytes)
    ///
    /// # Errors
    /// - `PqrrError::PermissionDenied` - Not authorized to revoke
    /// - `PqrrError::InsufficientPrivileges` - Cannot revoke this device
    pub fn revoke_device(&self, device_id_bytes: Vec<u8>) -> Result<()> {
        if device_id_bytes.len() != 16 {
            return Err(PqrrError::InsufficientPrivileges {
                role: "UI".to_string(),
                operation: "revoke_device".to_string(),
            });
        }

        let mut bytes = [0u8; 16];
        bytes.copy_from_slice(&device_id_bytes);
        let device_id = DeviceId::from_bytes(bytes);

        // Check if trying to revoke this device
        if device_id == self.this_device_id {
            return Err(PqrrError::InsufficientPrivileges {
                role: "UI".to_string(),
                operation: "revoke_this_device".to_string(),
            });
        }

        // In production, this would:
        // 1. Initiate PQRR rekeying
        // 2. Remove device from headers
        // 3. Update vault blob

        revoke_device(&mut self.state_machine.write().unwrap(), &device_id)?;

        Ok(())
    }

    /// Initiate recovery protocol
    ///
    /// Starts a 48-hour veto window for recovery.
    ///
    /// # Returns
    /// Recovery request ID for tracking
    ///
    /// # Errors
    /// - `PqrrError::InvalidStateTransition` - Recovery already in progress
    /// - `PqrrError::InsufficientPrivileges` - Not authorized to initiate
    pub fn initiate_recovery(&self) -> Result<String> {
        // Generate recovery request ID
        let request_id = generate_recovery_id();

        // In production, this would:
        // 1. Transition state machine to RecoveryInitiated
        // 2. Open 48-hour veto window
        // 3. Notify other devices

        // For demo, just return an ID
        Ok(request_id)
    }

    /// Submit veto for recovery request
    ///
    /// # Arguments
    /// - `recovery_id`: Recovery request ID to veto
    ///
    /// # Errors
    /// - `PqrrError::Vetoed` - Veto window expired or already vetoed
    /// - `PqrrError::InsufficientPrivileges` - Not authorized to veto
    pub fn submit_veto(&self, _recovery_id: String) -> Result<()> {
        // In production, this would:
        // 1. Parse recovery request ID
        // 2. Check veto window is still open
        // 3. Add veto signal to recovery context
        // 4. Terminate recovery

        // For demo, just return Ok
        Ok(())
    }

    /// Verify vault integrity
    ///
    /// # Arguments
    /// - `vault_blob`: Vault data blob to verify
    ///
    /// # Returns
    /// `true` if vault is valid and intact, `false` otherwise
    ///
    /// # Errors
    /// - `PqrrError::StorageError` - Failed to read vault
    pub fn verify_vault_integrity(&self, vault_blob: Vec<u8>) -> Result<bool> {
        // In production, this would:
        // 1. Deserialize vault blob
        // 2. Verify AEAD authentication tag
        // 3. Check epoch monotonicity
        // 4. Validate header completeness

        // For demo, just check blob is not empty
        Ok(!vault_blob.is_empty())
    }

    /// Shutdown the engine - Clean up resources
    ///
    /// Should be called when the app is shutting down or vault is no longer needed.
    /// Renamed from `close` to avoid conflict with AutoCloseable in Kotlin.
    pub fn shutdown(&self) {
        // In production, this would:
        // 1. Flush any pending writes
        // 2. Close file handles
        // 3. Clear sensitive data from memory

        // For demo, this is a no-op
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_engine_creation() {
        let engine = AeternumEngine::new_with_path("/tmp/test_vault".to_string()).unwrap();
        assert_eq!(engine.vault_path, "/tmp/test_vault");
    }

    #[test]
    fn test_unlock_creates_session() {
        let engine = AeternumEngine::new_with_path("/tmp/test_vault".to_string()).unwrap();
        let hardware_key = vec![1u8, 2, 3, 4];

        let session = engine.unlock(hardware_key).unwrap();
        assert!(session.is_valid());
    }

    #[test]
    fn test_get_device_list() {
        let engine = AeternumEngine::new_with_path("/tmp/test_vault".to_string()).unwrap();
        let devices = engine.get_device_list().unwrap();

        // Should have at least this device
        assert!(!devices.is_empty());
    }

    #[test]
    fn test_revoke_this_device_fails() {
        let engine = AeternumEngine::new_with_path("/tmp/test_vault".to_string()).unwrap();
        let this_device_id = engine.this_device_id.as_bytes().to_vec();

        let result = engine.revoke_device(this_device_id);
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            PqrrError::InsufficientPrivileges { .. }
        ));
    }

    #[test]
    fn test_initiate_recovery_returns_id() {
        let engine = AeternumEngine::new_with_path("/tmp/test_vault".to_string()).unwrap();
        let recovery_id = engine.initiate_recovery().unwrap();

        assert!(!recovery_id.is_empty());
    }

    #[test]
    fn test_verify_vault_integrity_empty_blob() {
        let engine = AeternumEngine::new_with_path("/tmp/test_vault".to_string()).unwrap();
        let result = engine.verify_vault_integrity(vec![]).unwrap();

        assert!(!result); // Empty blob is invalid
    }

    #[test]
    fn test_verify_vault_integrity_valid_blob() {
        let engine = AeternumEngine::new_with_path("/tmp/test_vault".to_string()).unwrap();
        let result = engine.verify_vault_integrity(vec![1, 2, 3, 4]).unwrap();

        assert!(result); // Non-empty blob is valid (demo)
    }
}
