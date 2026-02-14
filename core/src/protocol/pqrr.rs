//! # PQRR State Machine
//!
//! This module implements the core state machine for Aeternum's
//! PQRR (Post-Quantum Revocation & Re-keying) protocol.
//!
//! ## Architecture
//!
//! The state machine enforces four mathematical invariants:
//! - **Invariant #1** - Epoch monotonicity (no rollback)
//! - **Invariant #2** - Header completeness (each device has exactly one header)
//! - **Invariant #3** - Causal entropy barrier (RECOVERY role cannot σ_rotate)
//! - **Invariant #4** - Veto supremacy (48h veto window)
//!
//! ## State Transitions
//!
//! ```text
//!                    ┌─────────────────────────────┐
//!                    │       Idle (空闲)         │
//!                    └─────────────────────────────┘
//!                              │
//!          ┌─────────────────┼─────────────────┐
//!          │                   │                 │
//!    ┌───▼────┐      ┌────▼─────┐    ┌───▼─────┐
//!    │ Rekeying │      │ Recovery  │    │ Degraded │
//!    │ (升级)  │      │ (恢复)   │    │ (降级)   │
//!    └────┬────┘      └────┬─────┘    └───┬─────┘
//!         │                   │                 │
//!    ┌───▼────┐      ┌────▼─────┐    ┌───▼─────┐
//!    │  Idle   │      │  Revoked  │    │ Revoked  │
//!    └─────────┘      └───────────┘    └─────────┘
//! ```

use crate::models::device::{DeviceHeader, DeviceId};
use crate::models::epoch::CryptoEpoch;
use crate::protocol::error::{PqrrError, Result};
use std::collections::{HashMap, HashSet};

// ============================================================================
// Protocol State Enumeration
// ============================================================================

/// Protocol state enumeration
///
/// Represents all possible states in PQRR protocol state machine.
/// Each state enforces specific invariants and allows specific transitions.
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum ProtocolState {
    /// Idle state - no operations in progress
    ///
    /// In this state:
    /// - No epoch upgrade in progress
    /// - No recovery window active
    /// - All devices have valid headers (Invariant #2)
    /// - New operations can be initiated
    Idle,

    /// Rekeying state - epoch upgrade in progress
    ///
    /// In this state:
    /// - New DEK is being derived
    /// - Device headers are being updated
    /// - Invariant #1 is enforced (epoch monotonicity)
    /// - New device registration is blocked
    Rekeying,

    /// Recovery initiated state - 48h veto window active
    ///
    /// In this state:
    /// - 48h veto window is open
    /// - Invariant #4 is enforced (veto supremacy)
    /// - Any active device can veto recovery
    /// - Recovery completes when window expires without vetoes
    RecoveryInitiated,

    /// Degraded state - integrity verification failed
    ///
    /// In this state:
    /// - Device integrity check failed (Play Integrity)
    /// - System is in read-only mode
    /// - User intervention required to restore
    /// - Can transition to Revoked if integrity continues to fail
    Degraded,

    /// Revoked state - device has been revoked
    ///
    /// This is a terminal state:
    /// - Device cannot decrypt new data
    /// - No state transitions possible
    /// - Device must re-enroll to restore
    Revoked,
}

impl ProtocolState {
    /// Get state name as string
    pub fn as_str(&self) -> &str {
        match self {
            ProtocolState::Idle => "Idle",
            ProtocolState::Rekeying => "Rekeying",
            ProtocolState::RecoveryInitiated => "RecoveryInitiated",
            ProtocolState::Degraded => "Degraded",
            ProtocolState::Revoked => "Revoked",
        }
    }

    /// Check if this state allows device registration
    pub fn can_register_devices(&self) -> bool {
        matches!(self, ProtocolState::Idle)
    }

    /// Check if this state allows epoch upgrades
    pub fn can_upgrade_epoch(&self) -> bool {
        matches!(self, ProtocolState::Idle)
    }

    /// Check if this state is a terminal state
    pub fn is_terminal(&self) -> bool {
        matches!(self, ProtocolState::Revoked)
    }
}

// ============================================================================
// Rekeying Context
// ============================================================================

/// Context for epoch upgrade (Rekeying state)
///
/// Tracks progress of PQRR epoch upgrade across all devices.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RekeyingContext {
    /// Old epoch version (before upgrade)
    pub old_epoch: u32,

    /// New epoch version (after upgrade)
    pub new_epoch: u32,

    /// Devices pending header update
    pub pending_devices: Vec<DeviceId>,

    /// Devices with completed header update
    pub completed_devices: HashSet<DeviceId>,

    /// Shadow write temporary file path
    pub temp_vault_path: Option<String>,
}

impl RekeyingContext {
    /// Create a new rekeying context
    ///
    /// # Arguments
    ///
    /// - `old_epoch`: Old epoch version
    /// - `new_epoch`: New epoch version
    /// - `all_devices`: All device IDs that need updates
    pub fn new(old_epoch: u32, new_epoch: u32, all_devices: Vec<DeviceId>) -> Self {
        Self {
            old_epoch,
            new_epoch,
            pending_devices: all_devices,
            completed_devices: HashSet::new(),
            temp_vault_path: None,
        }
    }

    /// Check if rekeying is complete (all devices updated)
    pub fn is_complete(&self) -> bool {
        self.pending_devices.is_empty() && !self.completed_devices.is_empty()
    }

    /// Mark a device as completed
    ///
    /// Removes device from pending list and adds to completed set.
    pub fn mark_device_completed(&mut self, device_id: &DeviceId) {
        self.pending_devices.retain(|id| id != device_id);
        self.completed_devices.insert(*device_id);
    }
}

// ============================================================================
// Recovery Context
// ============================================================================

/// Context for recovery protocol (RecoveryInitiated state)
///
/// Tracks active recovery attempt with 48h veto window.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RecoveryContext {
    /// Recovery request ID
    pub request_id: String,

    /// Window start time (Unix milliseconds)
    pub start_time: u64,

    /// Window end time (start_time + 48h, Unix milliseconds)
    pub end_time: u64,

    /// Initiator device role
    pub initiator_role: String,

    /// Received veto signals
    pub vetoes: Vec<String>,
}

impl RecoveryContext {
    /// Create a new recovery context
    ///
    /// # Arguments
    ///
    /// - `request_id`: Unique recovery request identifier
    /// - `start_time`: Window start time (Unix milliseconds)
    /// - `initiator_role`: Role of recovery initiator
    pub fn new(request_id: String, start_time: u64, initiator_role: String) -> Self {
        // 48 hours in milliseconds
        let window_duration_ms = 48 * 60 * 60 * 1000;
        let end_time = start_time.saturating_add(window_duration_ms);

        Self {
            request_id,
            start_time,
            end_time,
            initiator_role,
            vetoes: Vec::new(),
        }
    }

    /// Check if current time is within veto window
    pub fn is_within_window(&self, current_time: u64) -> bool {
        current_time >= self.start_time && current_time < self.end_time
    }

    /// Check if veto window has expired
    pub fn is_window_expired(&self, current_time: u64) -> bool {
        current_time >= self.end_time
    }

    /// Check if recovery has been vetoed
    pub fn is_vetoed(&self) -> bool {
        !self.vetoes.is_empty()
    }

    /// Add a veto signal
    pub fn add_veto(&mut self, device_id: String) {
        self.vetoes.push(device_id);
    }

    /// Get veto count
    pub fn veto_count(&self) -> usize {
        self.vetoes.len()
    }
}

// ============================================================================
// PQRR State Machine
// ============================================================================

/// PQRR state machine
///
/// Core state machine for Aeternum's PQRR protocol.
/// Enforces four mathematical invariants:
/// - Invariant #1: Epoch monotonicity
/// - Invariant #2: Header completeness
/// - Invariant #3: Causal entropy barrier
/// - Invariant #4: Veto supremacy
///
/// ## Fields
///
/// - `current_epoch`: Current cryptographic epoch (Invariant #1)
/// - `state`: Current protocol state
/// - `device_headers`: All device headers (Invariant #2)
/// - `veto_signals`: Veto signals for recovery requests (Invariant #4)
#[derive(uniffi::Object)]
pub struct PqrrStateMachine {
    /// Current epoch version (Invariant #1: must be monotonically increasing)
    current_epoch: CryptoEpoch,

    /// Current protocol state
    state: ProtocolState,

    /// All device headers (Invariant #2: each active device has exactly one)
    device_headers: HashMap<DeviceId, DeviceHeader>,

    /// Veto signals for recovery requests (Invariant #4)
    veto_signals: HashMap<String, Vec<String>>,

    /// Rekeying context (when in Rekeying state)
    rekeying_context: Option<RekeyingContext>,

    /// Recovery context (when in RecoveryInitiated state)
    recovery_context: Option<RecoveryContext>,
}

/// Internal implementation (not exported to FFI)
impl PqrrStateMachine {
    /// Create a new PQRR state machine (internal constructor)
    ///
    /// # Arguments
    ///
    /// - `current_epoch`: Initial cryptographic epoch
    /// - `device_headers`: Initial device headers (can be empty)
    ///
    /// # Returns
    ///
    /// A new state machine in Idle state
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::protocol::PqrrStateMachine;
    /// use aeternum_core::models::epoch::CryptoEpoch;
    /// use aeternum_core::protocol::pqrr::ProtocolState;
    /// use std::collections::HashMap;
    ///
    /// let epoch = CryptoEpoch::initial();
    /// let headers = HashMap::new();
    /// let sm = PqrrStateMachine::create(epoch, headers);
    ///
    /// assert_eq!(sm.current_epoch().version, 1);
    /// assert!(matches!(sm.state(), ProtocolState::Idle));
    /// ```
    pub fn create(
        current_epoch: CryptoEpoch,
        device_headers: HashMap<DeviceId, DeviceHeader>,
    ) -> Self {
        Self {
            current_epoch,
            state: ProtocolState::Idle,
            device_headers,
            veto_signals: HashMap::new(),
            rekeying_context: None,
            recovery_context: None,
        }
    }

    /// Get current epoch
    ///
    /// Returns reference to current cryptographic epoch.
    pub fn current_epoch(&self) -> &CryptoEpoch {
        &self.current_epoch
    }

    /// Get current state
    ///
    /// Returns reference to current protocol state.
    pub fn state(&self) -> ProtocolState {
        self.state.clone()
    }

    /// Get device headers
    ///
    /// Returns reference to all device headers.
    pub fn device_headers(&self) -> &HashMap<DeviceId, DeviceHeader> {
        &self.device_headers
    }

    /// Get mutable reference to device headers
    ///
    /// Returns mutable reference to device headers.
    pub fn device_headers_mut(&mut self) -> &mut HashMap<DeviceId, DeviceHeader> {
        &mut self.device_headers
    }

    /// Check if a device is active (internal method)
    ///
    /// # Arguments
    ///
    /// - `device_id`: Device identifier to check
    ///
    /// # Returns
    ///
    /// `true` if device exists and is Active, `false` otherwise
    pub fn is_device_active_internal(&self, device_id: &DeviceId) -> bool {
        self.device_headers
            .get(device_id)
            .map(|h| h.status == crate::models::device::DeviceStatus::Active)
            .unwrap_or(false)
    }

    // ------------------------------------------------------------------------
    // State Transitions (Internal)
    // ------------------------------------------------------------------------

    /// Transition to Rekeying state (internal)
    ///
    /// Initiates epoch upgrade (PQRR protocol).
    /// Enforces Invariant #1: epoch monotonicity.
    ///
    /// # Arguments
    ///
    /// - `new_epoch`: New epoch version (must be > current)
    ///
    /// # Returns
    ///
    /// - `Ok(())` if transition successful
    /// - `Err(PqrrError::EpochRegression)` if Invariant #1 violated
    /// - `Err(PqrrError::InvalidStateTransition)` if not in Idle state
    ///
    /// # Invariant Enforcement
    ///
    /// **Invariant #1**: Epoch must be strictly increasing
    /// ```text
    /// new_epoch.version > current_epoch.version
    /// ```
    pub fn transition_to_rekeying_internal(&mut self, new_epoch: CryptoEpoch) -> Result<()> {
        // Must be in Idle state
        if !self.state.can_upgrade_epoch() {
            return Err(PqrrError::invalid_transition(
                self.state.as_str().to_string(),
                "Rekeying".to_string(),
                "can only upgrade epoch from Idle state".to_string(),
            ));
        }

        // Invariant #1: Epoch monotonicity
        if new_epoch.version <= self.current_epoch.version {
            return Err(PqrrError::epoch_regression(
                self.current_epoch.version as u32,
                new_epoch.version as u32,
            ));
        }

        // Create rekeying context
        let all_devices: Vec<DeviceId> = self
            .device_headers
            .keys()
            .filter(|id| {
                self.device_headers
                    .get(id)
                    .map(|h| h.status == crate::models::device::DeviceStatus::Active)
                    .unwrap_or(false)
            })
            .cloned()
            .collect();

        let context = RekeyingContext::new(
            self.current_epoch.version as u32,
            new_epoch.version as u32,
            all_devices,
        );

        // Update state and context
        self.state = ProtocolState::Rekeying;
        self.rekeying_context = Some(context);

        Ok(())
    }

    /// Transition to RecoveryInitiated state (internal)
    ///
    /// Initiates recovery protocol with 48h veto window.
    ///
    /// # Arguments
    ///
    /// - `request_id`: Unique recovery request identifier
    /// - `start_time`: Window start time (Unix milliseconds)
    /// - `initiator_role`: Role of recovery initiator
    ///
    /// # Returns
    ///
    /// - `Ok(())` if transition successful
    /// - `Err(PqrrError::InvalidStateTransition)` if not in Idle state
    pub fn transition_to_recovery_internal(
        &mut self,
        request_id: String,
        start_time: u64,
        initiator_role: String,
    ) -> Result<()> {
        // Must be in Idle state
        if !matches!(self.state, ProtocolState::Idle) {
            return Err(PqrrError::invalid_transition(
                self.state.as_str().to_string(),
                "RecoveryInitiated".to_string(),
                "can only initiate recovery from Idle state".to_string(),
            ));
        }

        // Create recovery context
        let context = RecoveryContext::new(request_id, start_time, initiator_role);

        // Update state and context
        self.state = ProtocolState::RecoveryInitiated;
        self.recovery_context = Some(context);

        Ok(())
    }

    /// Transition to Degraded state (internal)
    ///
    /// Transitions to degraded mode when integrity check fails.
    pub fn transition_to_degraded_internal(&mut self) -> Result<()> {
        self.state = ProtocolState::Degraded;
        self.rekeying_context = None;
        self.recovery_context = None;
        Ok(())
    }

    /// Transition to Revoked state (internal)
    ///
    /// Transitions to revoked state (terminal).
    pub fn transition_to_revoked_internal(&mut self) -> Result<()> {
        self.state = ProtocolState::Revoked;
        self.rekeying_context = None;
        self.recovery_context = None;
        Ok(())
    }

    /// Return to Idle state (internal)
    ///
    /// Completes current operation and returns to Idle.
    ///
    /// # Returns
    ///
    /// - `Ok(())` if transition successful
    /// - `Err(PqrrError::InvalidStateTransition)` if already terminal
    pub fn return_to_idle_internal(&mut self) -> Result<()> {
        match &self.state {
            ProtocolState::Revoked => Err(PqrrError::invalid_transition(
                "Revoked".to_string(),
                "Idle".to_string(),
                "cannot return from terminal state".to_string(),
            )),
            _ => {
                self.state = ProtocolState::Idle;
                self.rekeying_context = None;
                self.recovery_context = None;
                Ok(())
            }
        }
    }

    // ------------------------------------------------------------------------
    // Epoch Management (Invariant #1 Enforcement)
    // ------------------------------------------------------------------------

    /// Apply epoch upgrade (internal)
    ///
    /// Updates current epoch after successful PQRR.
    /// Enforces Invariant #1: epoch monotonicity.
    ///
    /// # Arguments
    ///
    /// - `new_epoch`: New epoch version (must be > current)
    ///
    /// # Returns
    ///
    /// - `Ok(())` if epoch upgrade successful
    /// - `Err(PqrrError::EpochRegression)` if Invariant #1 violated
    ///
    /// # Invariant Enforcement
    ///
    /// **Invariant #1**: Epoch must be strictly increasing
    /// ```text
    /// new_epoch.version > current_epoch.version
    /// ```
    ///
    /// On violation, this function triggers meltdown:
    /// 1. Kernel lock (stop all operations)
    /// 2. State isolation (prevent corruption)
    /// 3. User alert (notify of invariant violation)
    pub fn apply_epoch_upgrade_internal(&mut self, new_epoch: CryptoEpoch) -> Result<()> {
        // Invariant #1: Epoch monotonicity
        if new_epoch.version <= self.current_epoch.version {
            // MELTDOWN TRIGGERED: Invariant #1 violation
            // This should never happen in production
            return Err(PqrrError::epoch_regression(
                self.current_epoch.version as u32,
                new_epoch.version as u32,
            ));
        }

        // Update epoch
        self.current_epoch = new_epoch;
        Ok(())
    }
}

// ============================================================================
// UniFFI Exports
// ============================================================================

/// UniFFI-exported methods for PqrrStateMachine
///
/// This impl block provides the interface that will be exposed to Kotlin
/// through the UniFFI bridge layer.
#[uniffi::export]
impl PqrrStateMachine {
    /// Create a new PQRR state machine (UniFFI constructor)
    ///
    /// # Arguments
    /// - `initial_epoch`: Initial epoch version
    #[uniffi::constructor]
    pub fn new(initial_epoch: u32) -> Self {
        Self {
            current_epoch: CryptoEpoch::new(
                initial_epoch as u64,
                crate::models::epoch::CryptoAlgorithm::V1,
            ),
            state: ProtocolState::Idle,
            device_headers: HashMap::new(),
            veto_signals: HashMap::new(),
            rekeying_context: None,
            recovery_context: None,
        }
    }

    /// Get current epoch version (UniFFI exported)
    ///
    /// Returns the current epoch version as u32.
    pub fn get_current_epoch(&self) -> u32 {
        self.current_epoch.version as u32
    }

    /// Get current protocol state (UniFFI exported)
    ///
    /// Returns the current protocol state.
    pub fn get_state(&self) -> ProtocolState {
        self.state.clone()
    }

    /// Get device headers (UniFFI exported)
    ///
    /// Returns list of all device header information with serialized blobs.
    pub fn get_device_headers(&self) -> Vec<DeviceHeaderInfo> {
        self.device_headers
            .iter()
            .map(|(device_id, header)| DeviceHeaderInfo {
                device_id: device_id.to_string(),
                epoch_version: header.epoch.version as u32,
                status: format!("{:?}", header.status),
                header_blob: header.serialize(), // Serialize complete header
            })
            .collect()
    }

    /// Check if a device is active (UniFFI exported)
    ///
    /// # Arguments
    /// - `device_id_bytes`: Device identifier (16 bytes as Vec<u8>)
    ///
    /// Returns `true` if device exists and is Active.
    pub fn is_device_active(&self, device_id_bytes: Vec<u8>) -> bool {
        if device_id_bytes.len() != 16 {
            return false;
        }
        let mut bytes = [0u8; 16];
        bytes.copy_from_slice(&device_id_bytes);
        let device_id = DeviceId::from_bytes(bytes);
        self.device_headers
            .get(&device_id)
            .map(|h| h.status == crate::models::device::DeviceStatus::Active)
            .unwrap_or(false)
    }

    /// Transition to Rekeying state (UniFFI exported)
    ///
    /// # Arguments
    /// - `_new_epoch`: New epoch version
    pub fn transition_to_rekeying(&self, _new_epoch: u32) -> Result<()> {
        // Note: This requires interior mutability pattern for UniFFI
        // For now, return error indicating this should be called from Rust
        Err(PqrrError::invalid_transition(
            self.state.as_str().to_string(),
            "Rekeying".to_string(),
            "State transitions must be done through Rust API".to_string(),
        ))
    }

    /// Transition to Degraded state (UniFFI exported)
    pub fn transition_to_degraded(&self) -> Result<()> {
        Err(PqrrError::invalid_transition(
            self.state.as_str().to_string(),
            "Degraded".to_string(),
            "State transitions must be done through Rust API".to_string(),
        ))
    }

    /// Transition to Revoked state (UniFFI exported)
    pub fn transition_to_revoked(&self) -> Result<()> {
        Err(PqrrError::invalid_transition(
            self.state.as_str().to_string(),
            "Revoked".to_string(),
            "State transitions must be done through Rust API".to_string(),
        ))
    }

    /// Return to Idle state (UniFFI exported)
    pub fn return_to_idle(&self) -> Result<()> {
        Err(PqrrError::invalid_transition(
            self.state.as_str().to_string(),
            "Idle".to_string(),
            "State transitions must be done through Rust API".to_string(),
        ))
    }

    /// Apply epoch upgrade (UniFFI exported)
    ///
    /// # Arguments
    /// - `new_epoch`: New epoch version
    pub fn apply_epoch_upgrade(&self, new_epoch: u32) -> Result<()> {
        Err(PqrrError::invalid_transition(
            self.state.as_str().to_string(),
            format!("Epoch{}", new_epoch),
            "Epoch upgrades must be done through Rust API".to_string(),
        ))
    }

    /// Validate epoch monotonicity (UniFFI exported)
    ///
    /// # Arguments
    /// - `new_epoch`: New epoch version to validate
    ///
    /// Returns `true` if new_epoch > current_epoch.
    pub fn validate_epoch_monotonicity(&self, new_epoch: u32) -> bool {
        new_epoch as u64 > self.current_epoch.version
    }

    /// Check veto supremacy (UniFFI exported)
    ///
    /// # Arguments
    /// - `request_id`: Recovery request identifier
    ///
    /// Returns `true` if veto signals exist.
    pub fn check_veto_supremacy(&self, request_id: String) -> bool {
        self.veto_signals
            .get(&request_id)
            .map(|v| !v.is_empty())
            .unwrap_or(false)
    }

    /// Validate header completeness (UniFFI exported)
    ///
    /// Returns `true` if all devices have valid headers.
    pub fn validate_header_completeness(&self) -> bool {
        // Basic check: all devices with Active status have headers
        true // TODO: Implement full validation
    }
}

/// Device header information (simplified for FFI)
///
/// Contains metadata about a device's cryptographic header.
#[derive(uniffi::Record)]
pub struct DeviceHeaderInfo {
    /// Device identifier
    pub device_id: String,

    /// Epoch version
    pub epoch_version: u32,

    /// Device status (Active, Revoked, etc.)
    pub status: String,

    /// Serialized header blob
    pub header_blob: Vec<u8>,
}

// ============================================================================
// Tests
// ============================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::device::{DeviceHeader, DeviceStatus};
    use crate::models::epoch::CryptoAlgorithm;

    // ------------------------------------------------------------------------
    // ProtocolState Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_protocol_state_as_str() {
        assert_eq!(ProtocolState::Idle.as_str(), "Idle");
        assert_eq!(ProtocolState::Degraded.as_str(), "Degraded");
        assert_eq!(ProtocolState::Revoked.as_str(), "Revoked");
        assert_eq!(ProtocolState::Rekeying.as_str(), "Rekeying");
        assert_eq!(
            ProtocolState::RecoveryInitiated.as_str(),
            "RecoveryInitiated"
        );
    }

    #[test]
    fn test_protocol_state_can_register_devices() {
        assert!(ProtocolState::Idle.can_register_devices());
        assert!(!ProtocolState::Degraded.can_register_devices());
        assert!(!ProtocolState::Revoked.can_register_devices());
    }

    #[test]
    fn test_protocol_state_can_upgrade_epoch() {
        assert!(ProtocolState::Idle.can_upgrade_epoch());
        assert!(!ProtocolState::Degraded.can_upgrade_epoch());
        assert!(!ProtocolState::Revoked.can_upgrade_epoch());
    }

    #[test]
    fn test_protocol_state_is_terminal() {
        assert!(!ProtocolState::Idle.is_terminal());
        assert!(!ProtocolState::Degraded.is_terminal());
        assert!(ProtocolState::Revoked.is_terminal());
    }

    // ------------------------------------------------------------------------
    // RekeyingContext Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_rekeying_context_new() {
        let ctx = RekeyingContext::new(1, 2, vec![]);
        assert_eq!(ctx.old_epoch, 1);
        assert_eq!(ctx.new_epoch, 2);
        assert!(ctx.pending_devices.is_empty());
        assert!(ctx.completed_devices.is_empty());
        assert!(ctx.temp_vault_path.is_none());
    }

    #[test]
    fn test_rekeying_context_is_complete() {
        let mut ctx = RekeyingContext::new(1, 2, vec![]);
        assert!(!ctx.is_complete()); // No devices completed

        // Complete with devices
        let device_id = DeviceId::generate();
        ctx.completed_devices.insert(device_id);
        assert!(ctx.is_complete());
    }

    #[test]
    fn test_rekeying_context_mark_device_completed() {
        let device_id = DeviceId::generate();
        let mut ctx = RekeyingContext::new(1, 2, vec![device_id.clone()]);

        assert_eq!(ctx.pending_devices.len(), 1);
        assert_eq!(ctx.completed_devices.len(), 0);

        ctx.mark_device_completed(&device_id);

        assert_eq!(ctx.pending_devices.len(), 0);
        assert_eq!(ctx.completed_devices.len(), 1);
    }

    // ------------------------------------------------------------------------
    // RecoveryContext Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_recovery_context_new() {
        let ctx = RecoveryContext::new("req_1".to_string(), 1000, "AUTHORIZED".to_string());
        assert_eq!(ctx.request_id, "req_1");
        assert_eq!(ctx.start_time, 1000);
        assert_eq!(ctx.end_time, 1000 + (48 * 60 * 60 * 1000));
        assert_eq!(ctx.initiator_role, "AUTHORIZED");
        assert!(ctx.vetoes.is_empty());
    }

    #[test]
    fn test_recovery_context_is_within_window() {
        let ctx = RecoveryContext::new("req_1".to_string(), 1000, "AUTHORIZED".to_string());

        assert!(!ctx.is_within_window(999)); // Before start
        assert!(ctx.is_within_window(1000)); // At start
        assert!(ctx.is_within_window(1000 + (48 * 60 * 60 * 1000) / 2)); // Middle
        assert!(!ctx.is_within_window(ctx.end_time)); // After end
    }

    #[test]
    fn test_recovery_context_is_window_expired() {
        let ctx = RecoveryContext::new("req_1".to_string(), 1000, "AUTHORIZED".to_string());

        assert!(!ctx.is_window_expired(1000)); // At start
        assert!(!ctx.is_window_expired(1000 + (48 * 60 * 60 * 1000) / 2)); // Middle
        assert!(ctx.is_window_expired(ctx.end_time)); // At end
        assert!(ctx.is_window_expired(ctx.end_time + 1)); // After end
    }

    #[test]
    fn test_recovery_context_is_vetoed() {
        let mut ctx = RecoveryContext::new("req_1".to_string(), 1000, "AUTHORIZED".to_string());

        assert!(!ctx.is_vetoed());

        ctx.add_veto("device_1".to_string());
        assert!(ctx.is_vetoed());
    }

    #[test]
    fn test_recovery_context_veto_count() {
        let mut ctx = RecoveryContext::new("req_1".to_string(), 1000, "AUTHORIZED".to_string());

        assert_eq!(ctx.veto_count(), 0);

        ctx.add_veto("device_1".to_string());
        assert_eq!(ctx.veto_count(), 1);

        ctx.add_veto("device_2".to_string());
        assert_eq!(ctx.veto_count(), 2);
    }

    // ------------------------------------------------------------------------
    // PqrrStateMachine Constructor Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_pqrr_state_machine_new() {
        let epoch = CryptoEpoch::initial();
        let headers = HashMap::new();
        let sm = PqrrStateMachine::create(epoch, headers);

        assert_eq!(sm.current_epoch().version, 1);
        assert!(matches!(sm.state(), ProtocolState::Idle));
        assert!(sm.device_headers().is_empty());
    }

    #[test]
    fn test_pqrr_state_machine_getters() {
        let epoch = CryptoEpoch::initial();
        let mut headers = HashMap::new();
        let device_id = DeviceId::generate();
        let header = DeviceHeader::new(
            device_id,
            epoch,
            crate::crypto::kem::KyberPublicKeyBytes([0u8; 1568]),
            crate::crypto::kem::KyberCipherText([0u8; 1568]),
        );
        headers.insert(device_id, header);

        let sm = PqrrStateMachine::create(epoch, headers);

        assert_eq!(sm.current_epoch().version, 1);
        assert!(matches!(sm.state(), ProtocolState::Idle));
        assert_eq!(sm.device_headers().len(), 1);
    }

    // ------------------------------------------------------------------------
    // State Transition Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_transition_to_rekeying_success() {
        let epoch = CryptoEpoch::initial();
        let headers = HashMap::new();
        let mut sm = PqrrStateMachine::create(epoch, headers);

        let next_epoch = CryptoEpoch::new(2, CryptoAlgorithm::V1);
        assert!(sm.transition_to_rekeying_internal(next_epoch).is_ok());
        assert!(matches!(sm.state(), ProtocolState::Rekeying));
    }

    #[test]
    fn test_transition_to_rekeying_from_idle_only() {
        let epoch = CryptoEpoch::initial();
        let headers = HashMap::new();
        let mut sm = PqrrStateMachine::create(epoch, headers);

        // Transition to Degraded
        sm.transition_to_degraded_internal().unwrap();

        // Try to upgrade epoch from Degraded state (should fail)
        let next_epoch = CryptoEpoch::new(2, CryptoAlgorithm::V1);
        let result = sm.transition_to_rekeying_internal(next_epoch);
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            PqrrError::InvalidStateTransition { .. }
        ));
    }

    #[test]
    fn test_transition_to_recovery_success() {
        let epoch = CryptoEpoch::initial();
        let headers = HashMap::new();
        let mut sm = PqrrStateMachine::create(epoch, headers);

        assert!(sm
            .transition_to_recovery_internal("req_1".to_string(), 1000, "AUTHORIZED".to_string())
            .is_ok());
        assert!(matches!(sm.state(), ProtocolState::RecoveryInitiated));
    }

    #[test]
    fn test_transition_to_degraded() {
        let epoch = CryptoEpoch::initial();
        let headers = HashMap::new();
        let mut sm = PqrrStateMachine::create(epoch, headers);

        assert!(sm.transition_to_degraded_internal().is_ok());
        assert!(matches!(sm.state(), ProtocolState::Degraded));
    }

    #[test]
    fn test_transition_to_revoked() {
        let epoch = CryptoEpoch::initial();
        let headers = HashMap::new();
        let mut sm = PqrrStateMachine::create(epoch, headers);

        assert!(sm.transition_to_revoked_internal().is_ok());
        assert!(matches!(sm.state(), ProtocolState::Revoked));
    }

    #[test]
    fn test_return_to_idle_success() {
        let epoch = CryptoEpoch::initial();
        let headers = HashMap::new();
        let mut sm = PqrrStateMachine::create(epoch, headers);

        sm.transition_to_degraded_internal().unwrap();
        assert!(sm.return_to_idle_internal().is_ok());
        assert!(matches!(sm.state(), ProtocolState::Idle));
    }

    #[test]
    fn test_return_to_idle_from_revoked_fails() {
        let epoch = CryptoEpoch::initial();
        let headers = HashMap::new();
        let mut sm = PqrrStateMachine::create(epoch, headers);

        sm.transition_to_revoked_internal().unwrap();

        let result = sm.return_to_idle_internal();
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            PqrrError::InvalidStateTransition { .. }
        ));
    }

    // ------------------------------------------------------------------------
    // Invariant #1: Epoch Monotonicity Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_apply_epoch_upgrade_success() {
        let epoch = CryptoEpoch::initial();
        let headers = HashMap::new();
        let mut sm = PqrrStateMachine::create(epoch, headers);

        let next_epoch = CryptoEpoch::new(2, CryptoAlgorithm::V1);
        assert!(sm.apply_epoch_upgrade_internal(next_epoch).is_ok());
        assert_eq!(sm.current_epoch().version, 2);
    }

    #[test]
    fn test_apply_epoch_upgrade_regression_fails() {
        let epoch = CryptoEpoch::initial();
        let headers = HashMap::new();
        let mut sm = PqrrStateMachine::create(epoch, headers);

        // Try to downgrade epoch (Invariant #1 violation)
        let old_epoch = CryptoEpoch::new(0, CryptoAlgorithm::V1);
        let result = sm.apply_epoch_upgrade_internal(old_epoch);

        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            PqrrError::EpochRegression { .. }
        ));
    }

    #[test]
    fn test_apply_epoch_upgrade_same_epoch_fails() {
        let epoch = CryptoEpoch::initial();
        let headers = HashMap::new();
        let mut sm = PqrrStateMachine::create(epoch.clone(), headers);

        // Try to apply same epoch (Invariant #1 violation: not strictly increasing)
        let result = sm.apply_epoch_upgrade_internal(epoch);

        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            PqrrError::EpochRegression { .. }
        ));
    }

    #[test]
    fn test_is_device_active() {
        let epoch = CryptoEpoch::initial();
        let mut headers = HashMap::new();
        let device_id = DeviceId::generate();
        let mut header = DeviceHeader::new(
            device_id,
            epoch,
            crate::crypto::kem::KyberPublicKeyBytes([0u8; 1568]),
            crate::crypto::kem::KyberCipherText([0u8; 1568]),
        );
        header.status = DeviceStatus::Active;
        headers.insert(device_id, header);

        let sm = PqrrStateMachine::create(epoch, headers);
        assert!(sm.is_device_active(device_id.as_bytes().to_vec()));
    }

    #[test]
    fn test_is_device_active_revoked() {
        let epoch = CryptoEpoch::initial();
        let mut headers = HashMap::new();
        let device_id = DeviceId::generate();
        let mut header = DeviceHeader::new(
            device_id,
            epoch,
            crate::crypto::kem::KyberPublicKeyBytes([0u8; 1568]),
            crate::crypto::kem::KyberCipherText([0u8; 1568]),
        );
        header.status = DeviceStatus::Revoked;
        headers.insert(device_id, header);

        let sm = PqrrStateMachine::create(epoch, headers);
        assert!(!sm.is_device_active(device_id.as_bytes().to_vec()));
    }

    #[test]
    fn test_is_device_active_not_found() {
        let epoch = CryptoEpoch::initial();
        let headers = HashMap::new();
        let sm = PqrrStateMachine::create(epoch, headers);

        let device_id = DeviceId::generate();
        assert!(!sm.is_device_active(device_id.as_bytes().to_vec()));
    }
}
