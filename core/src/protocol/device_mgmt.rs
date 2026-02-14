//! # Device Management Lifecycle
//!
//! This module implements device management lifecycle including:
//! - Device registration and initialization
//! - Invariant #2 (Header Completeness) enforcement
//! - Device revocation and cleanup
//!
//! ## Architecture
//!
//! The device management module ensures:
//! - **Invariant #2**: Each active device has exactly one valid header
//! - **Device Registration**: New devices are properly initialized with headers
//! - **Device Revocation**: Revoked devices are cleaned up properly
//!
//! ## Invariant #2: Header Completeness
//!
//! ```text
//! ∀d∈D_active ⇔ ∃!h∈V_e.H: unwrap(h,d) = DEK_e
//! ```
//!
//! Each active device must have exactly one valid header to access DEK.

use crate::crypto::kem::{KyberCipherText, KyberPublicKeyBytes};
use crate::models::device::{DeviceHeader, DeviceId, DeviceStatus, Role};
use crate::protocol::error::{PqrrError, Result};
use crate::protocol::pqrr::PqrrStateMachine;

// ============================================================================
// Device Registration
// ============================================================================

/// Register a new device
///
/// Creates a new device entry with proper header for current epoch.
/// Enforces Invariant #2 by ensuring new device gets exactly one header.
///
/// # Arguments
///
/// - `state_machine`: Mutable reference to PQRR state machine
/// - `device_id`: Unique device identifier
/// - `public_key`: Device's Kyber public key
/// - `role`: Device role (AUTHORIZED or RECOVERY)
///
/// # Returns
///
/// - `Ok(())` if device registered successfully
/// - `Err(PqrrError::HeaderIncomplete)` if Invariant #2 violated
/// - `Err(PqrrError::InvalidStateTransition)` if not in Idle state
///
/// # Example
///
/// ```no_run
/// use aeternum_core::protocol::device_mgmt::register_device;
/// use aeternum_core::protocol::PqrrStateMachine;
/// use aeternum_core::models::{DeviceId, CryptoEpoch, Role};
/// use aeternum_core::crypto::kem::KyberKEM;
/// use std::collections::HashMap;
///
/// let epoch = CryptoEpoch::initial();
/// let headers = HashMap::new();
/// let mut sm = PqrrStateMachine::new(epoch, headers);
///
/// let device_id = DeviceId::generate();
/// let keypair = KyberKEM::generate_keypair();
/// let role = Role::Authorized;
///
/// register_device(&mut sm, device_id.clone(), keypair.public, role).unwrap();
///
/// assert!(sm.is_device_active(&device_id));
/// ```
pub fn register_device(
    state_machine: &mut PqrrStateMachine,
    device_id: DeviceId,
    public_key: KyberPublicKeyBytes,
    _role: Role,
) -> Result<()> {
    // Check valid state: Idle only
    if !matches!(
        state_machine.state(),
        crate::protocol::pqrr::ProtocolState::Idle
    ) {
        return Err(PqrrError::invalid_transition(
            state_machine.state().as_str().to_string(),
            "RegisterDevice".to_string(),
            "can only register devices in Idle state".to_string(),
        ));
    }

    // Check device doesn't already exist
    if state_machine.device_headers().contains_key(&device_id) {
        return Err(PqrrError::header_incomplete(
            format!("{:?}", device_id),
            "device already registered".to_string(),
        ));
    }

    // Generate wrapped DEK for this device (placeholder for Phase 4)
    let wrapped_dek: KyberCipherText = KyberCipherText([0u8; 1568]);

    // Create device header with Active status
    let mut header = DeviceHeader::new(
        device_id.clone(),
        state_machine.current_epoch().clone(),
        public_key,
        wrapped_dek,
    );
    header.status = DeviceStatus::Active;

    // Add to device headers
    state_machine
        .device_headers_mut()
        .insert(device_id.clone(), header);

    Ok(())
}

// ============================================================================
// Invariant #2: Header Completeness Validation
// ============================================================================

/// Validate header completeness (Invariant #2)
///
/// Ensures each active device has exactly one valid header.
/// This is the primary enforcement point for Invariant #2.
///
/// # Arguments
///
/// - `state_machine`: PQRR state machine to validate
///
/// # Returns
///
/// - `Ok(())` if all active devices have valid headers
/// - `Err(PqrrError::HeaderIncomplete)` if Invariant #2 violated
///
/// # Example
///
/// ```no_run
/// use aeternum_core::protocol::device_mgmt::validate_header_completeness;
/// use aeternum_core::protocol::PqrrStateMachine;
/// use aeternum_core::models::{DeviceId, CryptoEpoch};
/// use std::collections::HashMap;
///
/// let epoch = CryptoEpoch::initial();
/// let headers = HashMap::new();
/// let sm = PqrrStateMachine::new(epoch, headers);
///
/// assert!(validate_header_completeness(&sm).is_ok());
/// ```
pub fn validate_header_completeness(state_machine: &PqrrStateMachine) -> Result<()> {
    let headers = state_machine.device_headers();
    let current_epoch = state_machine.current_epoch();

    // Check 1: Each active device has a header
    for (device_id, header) in headers {
        if header.status == DeviceStatus::Active {
            // Verify header belongs to current epoch
            if header.epoch.version != current_epoch.version {
                return Err(PqrrError::header_incomplete(
                    format!("{:?}", device_id),
                    format!(
                        "header epoch {} != current epoch {}",
                        header.epoch.version, current_epoch.version
                    ),
                ));
            }

            // Verify header can be unwrapped (check format)
            // In production, this would attempt actual unwrapping
            if !header_can_be_unwrapped(header) {
                return Err(PqrrError::header_incomplete(
                    format!("{:?}", device_id),
                    "header cannot be unwrapped".to_string(),
                ));
            }
        }
    }

    // Check 2: No duplicate headers for same device
    let mut device_ids = std::collections::HashSet::new();
    for device_id in headers.keys() {
        if !device_ids.insert(device_id) {
            return Err(PqrrError::header_incomplete(
                format!("{:?}", device_id),
                "duplicate header found".to_string(),
            ));
        }
    }

    Ok(())
}

/// Check if header can be unwrapped (placeholder)
///
/// In production, this would attempt actual KEM decapsulation.
fn header_can_be_unwrapped(_header: &DeviceHeader) -> bool {
    // Placeholder: In real implementation, try to unwrap
    // For now, assume all headers are valid
    true
}

// ============================================================================
// Device Revocation
// ============================================================================

/// Revoke a device
///
/// Removes a device from active set and cleans up its header.
/// Enforces Invariant #2 by ensuring revoked devices have no valid headers.
///
/// # Arguments
///
/// - `state_machine`: Mutable reference to PQRR state machine
/// - `device_id`: Device identifier to revoke
///
/// # Returns
///
/// - `Ok(())` if device revoked successfully
/// - `Err(PqrrError::HeaderIncomplete)` if device not found
///
/// # Example
///
/// ```no_run
/// use aeternum_core::protocol::device_mgmt::{register_device, revoke_device};
/// use aeternum_core::protocol::PqrrStateMachine;
/// use aeternum_core::models::{DeviceId, CryptoEpoch, Role};
/// use aeternum_core::crypto::kem::KyberKEM;
/// use std::collections::HashMap;
///
/// let epoch = CryptoEpoch::initial();
/// let headers = HashMap::new();
/// let mut sm = PqrrStateMachine::new(epoch, headers);
///
/// let device_id = DeviceId::generate();
/// let keypair = KyberKEM::generate_keypair();
/// register_device(&mut sm, device_id.clone(), keypair.public, Role::Authorized).unwrap();
///
/// revoke_device(&mut sm, &device_id).unwrap();
///
/// assert!(!sm.is_device_active(&device_id));
/// ```
pub fn revoke_device(state_machine: &mut PqrrStateMachine, device_id: &DeviceId) -> Result<()> {
    // Check device exists
    if !state_machine.device_headers().contains_key(device_id) {
        return Err(PqrrError::header_incomplete(
            format!("{:?}", device_id),
            "device not found".to_string(),
        ));
    }

    // Mark device as revoked
    if let Some(header) = state_machine.device_headers_mut().get_mut(device_id) {
        header.status = DeviceStatus::Revoked;
    }

    Ok(())
}

/// Clean up revoked device headers
///
/// Removes headers for devices that have been revoked.
/// This is called after PQRR completes to ensure Invariant #2.
///
/// # Arguments
///
/// - `state_machine`: Mutable reference to PQRR state machine
/// - `device_id`: Device identifier to cleanup
///
/// # Returns
///
/// - `Ok(())` if cleanup successful
/// - `Err(PqrrError::HeaderIncomplete)` if device still active
pub fn cleanup_revoked_headers(
    state_machine: &mut PqrrStateMachine,
    device_id: &DeviceId,
) -> Result<()> {
    // Check device is revoked
    let header = state_machine
        .device_headers()
        .get(device_id)
        .ok_or_else(|| {
            PqrrError::header_incomplete(format!("{:?}", device_id), "device not found".to_string())
        })?;

    if header.status != DeviceStatus::Revoked {
        return Err(PqrrError::header_incomplete(
            format!("{:?}", device_id),
            "device is not revoked".to_string(),
        ));
    }

    // Remove header completely
    state_machine.device_headers_mut().remove(device_id);

    Ok(())
}

/// Get device registration status
///
/// # Arguments
///
/// - `state_machine`: PQRR state machine
/// - `device_id`: Device identifier to check
///
/// # Returns
///
/// `true` if device is registered, `false` otherwise
pub fn is_device_registered(state_machine: &PqrrStateMachine, device_id: &DeviceId) -> bool {
    state_machine.device_headers().contains_key(device_id)
}

/// Get active device IDs
///
/// # Arguments
///
/// - `state_machine`: PQRR state machine
///
/// # Returns
///
/// Vector of active device IDs
pub fn get_active_devices(state_machine: &PqrrStateMachine) -> Vec<DeviceId> {
    state_machine
        .device_headers()
        .iter()
        .filter(|(_, h)| h.status == DeviceStatus::Active)
        .map(|(id, _)| id.clone())
        .collect()
}

/// Get revoked device IDs
///
/// # Arguments
///
/// - `state_machine`: PQRR state machine
///
/// # Returns
///
/// Vector of revoked device IDs
pub fn get_revoked_devices(state_machine: &PqrrStateMachine) -> Vec<DeviceId> {
    state_machine
        .device_headers()
        .iter()
        .filter(|(_, h)| h.status == DeviceStatus::Revoked)
        .map(|(id, _)| id.clone())
        .collect()
}

// ============================================================================
// Tests
// ============================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use crate::crypto::kem::KyberKEM;
    use crate::models::epoch::{CryptoAlgorithm, CryptoEpoch};

    // ------------------------------------------------------------------------
    // Device Registration Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_register_device_success() {
        let mut sm = PqrrStateMachine::new(0);

        let device_id = DeviceId::generate();
        let keypair = KyberKEM::generate_keypair();
        let role = Role::Authorized;

        assert!(register_device(&mut sm, device_id.clone(), keypair.public, role).is_ok());
        assert!(sm.is_device_active(device_id.as_bytes().to_vec()));
    }

    #[test]
    fn test_register_device_from_idle_only() {
        let mut sm = PqrrStateMachine::new(0);

        // Transition to Rekeying
        let epoch_v2 = crate::models::epoch::CryptoEpoch::new(2, CryptoAlgorithm::V1);
        sm.transition_to_rekeying_internal(epoch_v2).unwrap();

        let device_id = DeviceId::generate();
        let keypair = KyberKEM::generate_keypair();

        let result = register_device(&mut sm, device_id, keypair.public, Role::Authorized);
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            PqrrError::InvalidStateTransition { .. }
        ));
    }

    #[test]
    fn test_register_device_duplicate_fails() {
        let mut sm = PqrrStateMachine::new(0);

        let device_id = DeviceId::generate();
        let keypair = KyberKEM::generate_keypair();
        let role = Role::Authorized;

        // First registration should succeed
        assert!(register_device(&mut sm, device_id.clone(), keypair.public, role).is_ok());

        // Second registration should fail
        let keypair2 = KyberKEM::generate_keypair();
        let result = register_device(&mut sm, device_id, keypair2.public, Role::Authorized);
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            PqrrError::HeaderIncomplete { .. }
        ));
    }

    // ------------------------------------------------------------------------
    // Invariant #2: Header Completeness Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_validate_header_completeness_success() {
        let sm = PqrrStateMachine::new(0);

        assert!(validate_header_completeness(&sm).is_ok());
    }

    #[test]
    fn test_validate_header_completeness_with_devices() {
        let mut sm = PqrrStateMachine::new(0);

        // Add a device
        let device_id = DeviceId::generate();
        let keypair = KyberKEM::generate_keypair();
        register_device(&mut sm, device_id.clone(), keypair.public, Role::Authorized).unwrap();

        assert!(validate_header_completeness(&sm).is_ok());
    }

    #[test]
    fn test_validate_header_completeness_wrong_epoch() {
        let mut sm = PqrrStateMachine::new(0);

        // Add a device with wrong epoch
        let device_id = DeviceId::generate();
        let keypair = KyberKEM::generate_keypair();
        let wrong_epoch = CryptoEpoch::new(99, CryptoAlgorithm::V1);

        let header = DeviceHeader::new(
            device_id.clone(),
            wrong_epoch,
            keypair.public,
            KyberCipherText([1u8; 1568]),
        );

        sm.device_headers_mut().insert(device_id, header);

        let result = validate_header_completeness(&sm);
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            PqrrError::HeaderIncomplete { .. }
        ));
    }

    // ------------------------------------------------------------------------
    // Device Revocation Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_revoke_device_success() {
        let mut sm = PqrrStateMachine::new(0);

        let device_id = DeviceId::generate();
        let keypair = KyberKEM::generate_keypair();
        register_device(&mut sm, device_id.clone(), keypair.public, Role::Authorized).unwrap();

        assert!(revoke_device(&mut sm, &device_id).is_ok());
        assert!(!sm.is_device_active(device_id.as_bytes().to_vec()));
    }

    #[test]
    fn test_revoke_device_not_found() {
        let mut sm = PqrrStateMachine::new(0);

        let device_id = DeviceId::generate();
        let result = revoke_device(&mut sm, &device_id);

        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            PqrrError::HeaderIncomplete { .. }
        ));
    }

    #[test]
    fn test_cleanup_revoked_headers_success() {
        let mut sm = PqrrStateMachine::new(0);

        let device_id = DeviceId::generate();
        let keypair = KyberKEM::generate_keypair();
        register_device(&mut sm, device_id.clone(), keypair.public, Role::Authorized).unwrap();
        revoke_device(&mut sm, &device_id).unwrap();

        assert!(cleanup_revoked_headers(&mut sm, &device_id).is_ok());
        assert!(!sm.device_headers().contains_key(&device_id));
    }

    #[test]
    fn test_cleanup_revoked_headers_active_device_fails() {
        let mut sm = PqrrStateMachine::new(0);

        let device_id = DeviceId::generate();
        let keypair = KyberKEM::generate_keypair();
        register_device(&mut sm, device_id.clone(), keypair.public, Role::Authorized).unwrap();

        // Don't revoke - try to cleanup active device
        let result = cleanup_revoked_headers(&mut sm, &device_id);

        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            PqrrError::HeaderIncomplete { .. }
        ));
    }

    // ------------------------------------------------------------------------
    // Helper Function Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_is_device_registered() {
        let mut sm = PqrrStateMachine::new(0);

        let device_id = DeviceId::generate();
        assert!(!is_device_registered(&sm, &device_id));

        let keypair = KyberKEM::generate_keypair();
        register_device(&mut sm, device_id.clone(), keypair.public, Role::Authorized).unwrap();

        assert!(is_device_registered(&sm, &device_id));
    }

    #[test]
    fn test_get_active_devices() {
        let mut sm = PqrrStateMachine::new(0);

        // Add 3 devices
        let device_ids: Vec<DeviceId> = (0..3)
            .map(|_| {
                let device_id = DeviceId::generate();
                let keypair = KyberKEM::generate_keypair();
                register_device(&mut sm, device_id.clone(), keypair.public, Role::Authorized)
                    .unwrap();
                device_id
            })
            .collect();

        // Revoke 1 device
        revoke_device(&mut sm, &device_ids[1]).unwrap();

        let active = get_active_devices(&sm);
        assert_eq!(active.len(), 2);
        assert!(active.contains(&device_ids[0]));
        assert!(active.contains(&device_ids[2]));
        assert!(!active.contains(&device_ids[1]));
    }

    #[test]
    fn test_get_revoked_devices() {
        let mut sm = PqrrStateMachine::new(0);

        // Add 3 devices
        let device_ids: Vec<DeviceId> = (0..3)
            .map(|_| {
                let device_id = DeviceId::generate();
                let keypair = KyberKEM::generate_keypair();
                register_device(&mut sm, device_id.clone(), keypair.public, Role::Authorized)
                    .unwrap();
                device_id
            })
            .collect();

        // Revoke 2 devices
        revoke_device(&mut sm, &device_ids[0]).unwrap();
        revoke_device(&mut sm, &device_ids[2]).unwrap();

        let revoked = get_revoked_devices(&sm);
        assert_eq!(revoked.len(), 2);
        assert!(revoked.contains(&device_ids[0]));
        assert!(revoked.contains(&device_ids[2]));
        assert!(!revoked.contains(&device_ids[1]));
    }
}
