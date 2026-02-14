//! # Recovery Protocol & Veto Mechanism
//!
//! This module implements the recovery protocol with 48h veto window,
//! enforcing Invariant #4 (Veto Supremacy).
//!
//! ## Architecture
//!
//! The recovery protocol provides:
//! - **48h Veto Window** - Any active device can veto recovery
//! - **Invariant #4 Enforcement** - Veto signals have highest priority
//! - **Recovery Window Tracking** - Manages active recovery attempts
//! - **Time Drift Tolerance** - ±5min tolerance for clock skew
//!
//! ## Invariant #4: Veto Supremacy
//!
//! ```text
//! Status(req) = COMMITTED ⇒ Vetoes(req) = ∅
//! ```
//!
//! Any veto signal within the 48h window immediately terminates recovery.

use crate::models::device::{DeviceId, Role};
use crate::protocol::error::{PqrrError, Result};
use std::time::SystemTime;

// ============================================================================
// Constants
// ============================================================================

/// 48 hours in milliseconds (48 * 60 * 60 * 1000)
pub const VETO_WINDOW_MS: u64 = 172_800_000;

/// Time drift tolerance: ±5 minutes in milliseconds
pub const TIME_DRIFT_TOLERANCE_MS: u64 = 300_000;

// ============================================================================
// Veto Message
// ============================================================================

/// Veto signal from a device
///
/// Represents a veto message sent by an active device to terminate
/// a recovery request.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct VetoMessage {
    /// Device ID that sent this veto
    pub device_id: DeviceId,

    /// Reason for veto (optional)
    pub reason: Option<String>,

    /// Timestamp when veto was sent (Unix milliseconds)
    pub timestamp: u64,
}

impl VetoMessage {
    /// Create a new veto message
    ///
    /// # Arguments
    ///
    /// - `device_id`: Device sending the veto
    /// - `reason`: Optional reason for veto
    ///
    /// # Returns
    ///
    /// A new VetoMessage with current timestamp
    pub fn new(device_id: DeviceId, reason: Option<String>) -> Self {
        let timestamp = current_timestamp_ms();
        Self {
            device_id,
            reason,
            timestamp,
        }
    }

    /// Create a veto message with custom timestamp
    ///
    /// Used primarily for testing scenarios.
    pub fn with_timestamp(device_id: DeviceId, reason: Option<String>, timestamp: u64) -> Self {
        Self {
            device_id,
            reason,
            timestamp,
        }
    }
}

// ============================================================================
// Recovery Request ID
// ============================================================================

/// Unique identifier for a recovery request
///
/// Recovery request IDs are generated using CSPRNG to ensure
/// unpredictability and prevent request hijacking.
#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct RecoveryRequestId(String);

impl RecoveryRequestId {
    /// Generate a new recovery request ID
    ///
    /// Uses CSPRNG to generate a cryptographically secure random ID
    /// with format: "req_<32-hex-chars>"
    pub fn generate() -> Self {
        use getrandom::getrandom;
        let mut bytes = [0u8; 16];
        getrandom(&mut bytes).expect("CSPRNG failure");
        let hex = hex::encode(bytes);
        Self(format!("req_{}", hex))
    }

    /// Create a recovery request ID from string
    ///
    /// # Arguments
    ///
    /// - `id`: String representation of request ID
    ///
    /// # Returns
    ///
    /// A new RecoveryRequestId
    pub fn from_string(id: String) -> Self {
        Self(id)
    }

    /// Get request ID as string
    pub fn as_str(&self) -> &str {
        &self.0
    }

    /// Clone as string
    #[allow(clippy::inherent_to_string)]
    pub fn to_string(&self) -> String {
        self.0.clone()
    }
}

// ============================================================================
// Recovery Window
// ============================================================================

/// Recovery window tracking
///
/// Manages a single recovery attempt with its 48h veto window.
/// Enforces Invariant #4: Veto Supremacy.
#[derive(Debug, Clone)]
pub struct RecoveryWindow {
    /// Unique recovery request identifier
    pub request_id: RecoveryRequestId,

    /// Window start time (Unix milliseconds)
    pub start_time: u64,

    /// Window end time (start_time + 48h, Unix milliseconds)
    pub end_time: u64,

    /// Initiator role (must be AUTHORIZED)
    pub initiator_role: Role,

    /// Received veto signals
    pub vetoes: Vec<VetoMessage>,
}

impl RecoveryWindow {
    /// Create a new recovery window
    ///
    /// # Arguments
    ///
    /// - `request_id`: Unique recovery request identifier
    /// - `start_time`: Window start time (Unix milliseconds)
    /// - `initiator_role`: Role of recovery initiator
    ///
    /// # Returns
    ///
    /// A new RecoveryWindow with 48h veto window
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::protocol::recovery::{RecoveryWindow, RecoveryRequestId};
    /// use aeternum_core::models::device::Role;
    ///
    /// let request_id = RecoveryRequestId::generate();
    /// let window = RecoveryWindow::new(
    ///     request_id.clone(),
    ///     1000,
    ///     Role::Authorized
    /// );
    ///
    /// assert_eq!(window.end_time, 1000 + 172_800_000);
    /// assert!(!window.is_vetoed());
    /// ```
    pub fn new(request_id: RecoveryRequestId, start_time: u64, initiator_role: Role) -> Self {
        let end_time = start_time.saturating_add(VETO_WINDOW_MS);

        Self {
            request_id,
            start_time,
            end_time,
            initiator_role,
            vetoes: Vec::new(),
        }
    }

    /// Check if current time is within veto window
    ///
    /// Uses time drift tolerance (±5min) for boundary checks.
    ///
    /// # Arguments
    ///
    /// - `current_time`: Current time (Unix milliseconds)
    ///
    /// # Returns
    ///
    /// `true` if within window (with tolerance), `false` otherwise
    pub fn is_within_window(&self, current_time: u64) -> bool {
        // Apply time drift tolerance
        let effective_start = self.start_time.saturating_sub(TIME_DRIFT_TOLERANCE_MS);
        let effective_end = self.end_time.saturating_add(TIME_DRIFT_TOLERANCE_MS);

        current_time >= effective_start && current_time < effective_end
    }

    /// Check if veto window has expired
    ///
    /// Window is considered expired when current_time >= end_time.
    /// Uses time drift tolerance (±5min) for boundary checks.
    ///
    /// Note: Due to time drift tolerance, the window will only be
    /// reported as expired when current_time >= end_time + tolerance.
    ///
    /// # Arguments
    ///
    /// - `current_time`: Current time (Unix milliseconds)
    ///
    /// # Returns
    ///
    /// `true` if window has expired, `false` otherwise
    pub fn is_window_expired(&self, current_time: u64) -> bool {
        // Apply time drift tolerance
        let effective_end = self.end_time.saturating_add(TIME_DRIFT_TOLERANCE_MS);

        current_time >= effective_end
    }

    /// Check if recovery has been vetoed
    ///
    /// # Returns
    ///
    /// `true` if any veto signals have been received, `false` otherwise
    pub fn is_vetoed(&self) -> bool {
        !self.vetoes.is_empty()
    }

    /// Get veto count
    ///
    /// # Returns
    ///
    /// Number of veto signals received
    pub fn veto_count(&self) -> usize {
        self.vetoes.len()
    }

    /// Add a veto signal
    ///
    /// # Arguments
    ///
    /// - `veto`: Veto message to add
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::protocol::recovery::{RecoveryWindow, RecoveryRequestId, VetoMessage};
    /// use aeternum_core::models::device::{DeviceId, Role};
    ///
    /// let request_id = RecoveryRequestId::generate();
    /// let mut window = RecoveryWindow::new(
    ///     request_id.clone(),
    ///     1000,
    ///     Role::Authorized
    /// );
    ///
    /// let device_id = DeviceId::generate();
    /// let veto = VetoMessage::new(device_id, Some("Suspicious activity".to_string()));
    ///
    /// window.add_veto(veto);
    ///
    /// assert!(window.is_vetoed());
    /// assert_eq!(window.veto_count(), 1);
    /// ```
    pub fn add_veto(&mut self, veto: VetoMessage) {
        self.vetoes.push(veto);
    }

    /// Check if recovery can complete
    ///
    /// Recovery can complete when:
    /// - Window has expired (current_time >= end_time)
    /// - No veto signals have been received
    ///
    /// # Arguments
    ///
    /// - `current_time`: Current time (Unix milliseconds)
    ///
    /// # Returns
    ///
    /// `true` if recovery can complete, `false` otherwise
    pub fn can_complete(&self, current_time: u64) -> bool {
        self.is_window_expired(current_time) && !self.is_vetoed()
    }

    /// Get remaining time in window (milliseconds)
    ///
    /// Returns 0 if window has expired.
    ///
    /// # Arguments
    ///
    /// - `current_time`: Current time (Unix milliseconds)
    ///
    /// # Returns
    ///
    /// Remaining milliseconds in veto window, or 0 if expired
    pub fn remaining_time(&self, current_time: u64) -> u64 {
        if current_time >= self.end_time {
            return 0;
        }
        self.end_time.saturating_sub(current_time)
    }
}

// ============================================================================
// Veto Checker (Invariant #4 Enforcement Point)
// ============================================================================

/// Check veto supremacy (Invariant #4)
///
/// This is the primary enforcement point for Invariant #4:
/// "Any active device's Veto signal within 48h window must
/// immediately terminate recovery."
///
/// # Arguments
///
/// - `window`: Recovery window to check
/// - `current_time`: Current time (Unix milliseconds)
///
/// # Returns
///
/// - `Ok(())` if recovery can proceed (no vetoes or window expired)
/// - `Err(PqrrError::Vetoed)` if Invariant #4 is violated
pub fn check_veto_supremacy(window: &RecoveryWindow, current_time: u64) -> Result<()> {
    // Invariant #4: Veto Supremacy
    // Any veto signal immediately terminates recovery
    if window.is_vetoed() {
        return Err(PqrrError::vetoed(
            window.request_id.to_string(),
            window.veto_count() as u32,
        ));
    }

    // If within window and no vetoes, recovery is pending
    if window.is_within_window(current_time) {
        return Ok(());
    }

    // Window expired and no vetoes - recovery can complete
    Ok(())
}

// ============================================================================
// Helper Functions
// ============================================================================

/// Get current Unix timestamp in milliseconds
fn current_timestamp_ms() -> u64 {
    SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

// ============================================================================
// Tests
// ============================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::device::DeviceId;

    // ------------------------------------------------------------------------
    // VetoMessage Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_veto_message_new() {
        let device_id = DeviceId::generate();
        let veto = VetoMessage::new(device_id, Some("Test".to_string()));

        assert_eq!(veto.device_id, device_id);
        assert_eq!(veto.reason, Some("Test".to_string()));
        assert!(veto.timestamp > 0);
    }

    #[test]
    fn test_veto_message_with_timestamp() {
        let device_id = DeviceId::generate();
        let timestamp = 1234567890;
        let veto = VetoMessage::with_timestamp(device_id, Some("Test".to_string()), timestamp);

        assert_eq!(veto.timestamp, timestamp);
    }

    // ------------------------------------------------------------------------
    // RecoveryRequestId Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_recovery_request_id_generate() {
        let id1 = RecoveryRequestId::generate();
        let id2 = RecoveryRequestId::generate();

        // IDs should be different
        assert_ne!(id1.0, id2.0);

        // IDs should start with "req_"
        assert!(id1.0.starts_with("req_"));
        assert!(id2.0.starts_with("req_"));
    }

    #[test]
    fn test_recovery_request_id_from_string() {
        let id_str = "req_abc123".to_string();
        let id = RecoveryRequestId::from_string(id_str.clone());

        assert_eq!(id.as_str(), &id_str);
        assert_eq!(id.to_string(), id_str);
    }

    // ------------------------------------------------------------------------
    // RecoveryWindow Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_recovery_window_new() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let window = RecoveryWindow::new(request_id.clone(), start_time, Role::Authorized);

        assert_eq!(window.request_id.as_str(), request_id.as_str());
        assert_eq!(window.start_time, start_time);
        assert_eq!(window.end_time, start_time + VETO_WINDOW_MS);
        assert_eq!(window.initiator_role, Role::Authorized);
        assert!(window.vetoes.is_empty());
    }

    #[test]
    fn test_recovery_window_is_within_window() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Before window (within tolerance)
        assert!(window.is_within_window(start_time - 1));

        // At start
        assert!(window.is_within_window(start_time));

        // Middle of window
        let mid_time = start_time + (VETO_WINDOW_MS / 2);
        assert!(window.is_within_window(mid_time));

        // Near end (within tolerance)
        assert!(window.is_within_window(window.end_time - 1));

        // At end (exclusive - still within tolerance)
        assert!(window.is_within_window(window.end_time));

        // After end (past tolerance)
        assert!(!window.is_within_window(window.end_time + TIME_DRIFT_TOLERANCE_MS + 1));
    }

    #[test]
    fn test_recovery_window_is_window_expired() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Not expired at start
        assert!(!window.is_window_expired(start_time));

        // Not expired in middle
        let mid_time = start_time + (VETO_WINDOW_MS / 2);
        assert!(!window.is_window_expired(mid_time));

        // Not expired at end
        assert!(!window.is_window_expired(window.end_time));

        // Expired well after end (past tolerance)
        assert!(window.is_window_expired(window.end_time + TIME_DRIFT_TOLERANCE_MS + 1));
    }

    #[test]
    fn test_recovery_window_is_vetoed() {
        let request_id = RecoveryRequestId::generate();
        let mut window = RecoveryWindow::new(request_id, 1000, Role::Authorized);

        // No vetoes initially
        assert!(!window.is_vetoed());
        assert_eq!(window.veto_count(), 0);

        // Add veto
        let device_id = DeviceId::generate();
        let veto = VetoMessage::new(device_id, None);
        window.add_veto(veto);

        // Now vetoed
        assert!(window.is_vetoed());
        assert_eq!(window.veto_count(), 1);
    }

    #[test]
    fn test_recovery_window_can_complete() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let mut window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Cannot complete during window (not expired)
        let mid_time = start_time + (VETO_WINDOW_MS / 2);
        assert!(!window.can_complete(mid_time));

        // Cannot complete if vetoed (even if expired)
        let device_id = DeviceId::generate();
        let veto = VetoMessage::new(device_id, None);
        window.add_veto(veto);
        assert!(!window.can_complete(window.end_time + TIME_DRIFT_TOLERANCE_MS + 1000));

        // Can complete if expired and no vetoes
        let window2 =
            RecoveryWindow::new(RecoveryRequestId::generate(), start_time, Role::Authorized);
        assert!(window2.can_complete(window2.end_time + TIME_DRIFT_TOLERANCE_MS + 1000));
    }

    #[test]
    fn test_recovery_window_remaining_time() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // At start
        assert_eq!(window.remaining_time(start_time), VETO_WINDOW_MS);

        // Middle
        let mid_time = start_time + (VETO_WINDOW_MS / 2);
        assert_eq!(window.remaining_time(mid_time), VETO_WINDOW_MS / 2);

        // Near end
        assert_eq!(window.remaining_time(window.end_time - 100), 100);

        // After end
        assert_eq!(window.remaining_time(window.end_time + 1000), 0);
    }

    // ------------------------------------------------------------------------
    // Invariant #4: Veto Supremacy Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_check_veto_supremacy_no_veto_within_window() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Within window, no vetoes - should succeed
        let current_time = start_time + (VETO_WINDOW_MS / 2);
        assert!(check_veto_supremacy(&window, current_time).is_ok());
    }

    #[test]
    fn test_check_veto_supremacy_veto_within_window() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let mut window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Add veto
        let device_id = DeviceId::generate();
        let veto = VetoMessage::new(device_id, None);
        window.add_veto(veto);

        // Within window with veto - should fail
        let current_time = start_time + (VETO_WINDOW_MS / 2);
        let result = check_veto_supremacy(&window, current_time);
        assert!(result.is_err());
        assert!(matches!(result.unwrap_err(), PqrrError::Vetoed { .. }));
    }

    #[test]
    fn test_check_veto_supremacy_expired_no_veto() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Expired, no vetoes - should succeed
        let current_time = window.end_time + 1000;
        assert!(check_veto_supremacy(&window, current_time).is_ok());
    }

    #[test]
    fn test_check_veto_supremacy_expired_with_veto() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let mut window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Add veto
        let device_id = DeviceId::generate();
        let veto = VetoMessage::new(device_id, None);
        window.add_veto(veto);

        // Expired with veto - should still fail (Invariant #4)
        let current_time = window.end_time + 1000;
        let result = check_veto_supremacy(&window, current_time);
        assert!(result.is_err());
        assert!(matches!(result.unwrap_err(), PqrrError::Vetoed { .. }));
    }

    #[test]
    fn test_check_veto_supremacy_multiple_vetoes() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let mut window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Add multiple vetoes
        for _ in 0..5 {
            let device_id = DeviceId::generate();
            let veto = VetoMessage::new(device_id, None);
            window.add_veto(veto);
        }

        // Should fail with veto count = 5
        let current_time = start_time + (VETO_WINDOW_MS / 2);
        let result = check_veto_supremacy(&window, current_time);
        assert!(result.is_err());
        match result.unwrap_err() {
            PqrrError::Vetoed { veto_count, .. } => {
                assert_eq!(veto_count, 5);
            }
            _ => panic!("Expected Vetoed error"),
        }
    }

    // ------------------------------------------------------------------------
    // Time Drift Tolerance Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_time_drift_tolerance_before_window() {
        let request_id = RecoveryRequestId::generate();
        let start_time = TIME_DRIFT_TOLERANCE_MS + 1000; // Ensure no underflow
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Just before window (within tolerance)
        let current_time = start_time - (TIME_DRIFT_TOLERANCE_MS / 2);
        assert!(window.is_within_window(current_time));
    }

    #[test]
    fn test_time_drift_tolerance_after_window() {
        let request_id = RecoveryRequestId::generate();
        let start_time = TIME_DRIFT_TOLERANCE_MS + 1000; // Ensure no underflow
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Just after window (within tolerance)
        let current_time = window.end_time + (TIME_DRIFT_TOLERANCE_MS / 2);
        assert!(window.is_within_window(current_time));
    }

    #[test]
    fn test_time_drift_tolerance_outside_range() {
        let request_id = RecoveryRequestId::generate();
        let start_time = TIME_DRIFT_TOLERANCE_MS + 1000; // Ensure no underflow
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Well before window (outside tolerance)
        let current_time = start_time - TIME_DRIFT_TOLERANCE_MS - 1000;
        assert!(!window.is_within_window(current_time));

        // Well after window (outside tolerance)
        let current_time = window.end_time + TIME_DRIFT_TOLERANCE_MS + 1000;
        assert!(!window.is_within_window(current_time));
    }

    // ------------------------------------------------------------------------
    // Integration Tests: Full Recovery Flow Lifecycle
    // ------------------------------------------------------------------------

    #[test]
    fn test_recovery_flow_full_lifecycle_success() {
        // Phase 1: Initialize recovery window
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let initiator_role = Role::Authorized;
        let window = RecoveryWindow::new(request_id.clone(), start_time, initiator_role);

        // Verify initial state
        assert!(!window.is_vetoed());
        assert_eq!(window.veto_count(), 0);
        assert!(window.is_within_window(start_time));

        // Phase 2: Check veto supremacy during window (no vetoes)
        let mid_time = start_time + (VETO_WINDOW_MS / 2);
        assert!(check_veto_supremacy(&window, mid_time).is_ok());

        // Phase 3: Window expires without vetoes
        let expired_time = window.end_time + TIME_DRIFT_TOLERANCE_MS + 1000;
        assert!(window.is_window_expired(expired_time));
        assert!(window.can_complete(expired_time));

        // Phase 4: Verify recovery can complete
        assert!(check_veto_supremacy(&window, expired_time).is_ok());
    }

    #[test]
    fn test_recovery_flow_vetoed_during_window() {
        // Phase 1: Initialize recovery window
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let mut window = RecoveryWindow::new(request_id.clone(), start_time, Role::Authorized);

        // Phase 2: Device sends veto signal
        let device_id = DeviceId::generate();
        let veto = VetoMessage::new(device_id, Some("Unauthorized recovery".to_string()));
        window.add_veto(veto);

        // Phase 3: Verify veto supremacy enforced
        let mid_time = start_time + (VETO_WINDOW_MS / 2);
        let result = check_veto_supremacy(&window, mid_time);
        assert!(result.is_err());
        assert!(matches!(result.unwrap_err(), PqrrError::Vetoed { .. }));

        // Phase 4: Verify recovery cannot complete (even after window expires)
        let expired_time = window.end_time + 1000;
        assert!(!window.can_complete(expired_time)); // Still vetoed
        assert!(check_veto_supremacy(&window, expired_time).is_err());
    }

    // ------------------------------------------------------------------------
    // Integration Tests: Cross-Device Veto Scenarios
    // ------------------------------------------------------------------------

    #[test]
    fn test_cross_device_veto_multiple_devices() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let mut window = RecoveryWindow::new(request_id.clone(), start_time, Role::Authorized);

        // Simulate 3 active devices
        let device_1 = DeviceId::generate();
        let device_2 = DeviceId::generate();
        let device_3 = DeviceId::generate();

        // All three devices send veto
        window.add_veto(VetoMessage::new(device_1, None));
        window.add_veto(VetoMessage::new(
            device_2,
            Some("Suspicious activity".to_string()),
        ));
        window.add_veto(VetoMessage::new(device_3, None));

        // Verify all vetoes recorded
        assert_eq!(window.veto_count(), 3);
        assert!(window.is_vetoed());

        // Verify veto supremacy enforced
        let result = check_veto_supremacy(&window, start_time + 1000);
        assert!(result.is_err());
        match result.unwrap_err() {
            PqrrError::Vetoed { veto_count, .. } => {
                assert_eq!(veto_count, 3);
            }
            _ => panic!("Expected Vetoed error"),
        }
    }

    #[test]
    fn test_cross_device_veto_single_device_blocks() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let mut window = RecoveryWindow::new(request_id.clone(), start_time, Role::Authorized);

        // Simulate 10 active devices, only 1 vetoes
        for _ in 0..9 {
            let _device_id = DeviceId::generate();
            // These devices do NOT veto
        }

        // Single device vetoes
        let vetoing_device = DeviceId::generate();
        window.add_veto(VetoMessage::new(
            vetoing_device,
            Some("I did not authorize this".to_string()),
        ));

        // Verify single veto blocks entire recovery
        assert!(window.is_vetoed());
        assert_eq!(window.veto_count(), 1);
        assert!(check_veto_supremacy(&window, start_time + 1000).is_err());

        // Even after window expires, single veto still blocks
        let expired_time = window.end_time + 1000;
        assert!(!window.can_complete(expired_time));
    }

    #[test]
    fn test_cross_device_veto_with_timestamps() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let mut window = RecoveryWindow::new(request_id.clone(), start_time, Role::Authorized);

        // Simulate vetoes at different times
        let device_1 = DeviceId::generate();
        let device_2 = DeviceId::generate();

        // First veto early in window
        window.add_veto(VetoMessage::with_timestamp(
            device_1,
            None,
            start_time + 1000,
        ));

        // Second veto late in window
        window.add_veto(VetoMessage::with_timestamp(
            device_2,
            None,
            window.end_time - 1000,
        ));

        // Verify both vetoes recorded
        assert_eq!(window.veto_count(), 2);

        // Verify order: first veto should be earliest
        assert!(window.vetoes[0].timestamp < window.vetoes[1].timestamp);
    }

    // ------------------------------------------------------------------------
    // Integration Tests: Window Expiration Boundary Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_window_expiration_boundary_exact_end_time() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // At exact end time (within tolerance)
        assert!(window.is_within_window(window.end_time));
        assert!(!window.is_window_expired(window.end_time));

        // Past end time + tolerance
        let expired_time = window.end_time + TIME_DRIFT_TOLERANCE_MS + 1;
        assert!(!window.is_within_window(expired_time));
        assert!(window.is_window_expired(expired_time));
    }

    #[test]
    fn test_window_expiration_boundary_recovery_blocked_during_window() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Just before window ends (within tolerance)
        let near_end = window.end_time - 1;

        // Recovery should be blocked (window still active)
        let result = check_veto_supremacy(&window, near_end);
        // Result should be Ok (no vetoes) but recovery must wait
        assert!(result.is_ok());
        assert!(!window.can_complete(near_end)); // Can't complete yet
    }

    #[test]
    fn test_window_expiration_boundary_recovery_allowed_after_window() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Just after window ends (past tolerance)
        let just_past = window.end_time + TIME_DRIFT_TOLERANCE_MS + 1;

        // Recovery should be allowed (no vetoes, window expired)
        assert!(window.can_complete(just_past));
        assert!(check_veto_supremacy(&window, just_past).is_ok());
    }

    #[test]
    fn test_window_expiration_with_veto_just_before_deadline() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let mut window = RecoveryWindow::new(request_id.clone(), start_time, Role::Authorized);

        // Veto comes in just before deadline
        let device_id = DeviceId::generate();
        window.add_veto(VetoMessage::with_timestamp(
            device_id,
            None,
            window.end_time - 100, // 100ms before deadline
        ));

        // Verify recovery blocked (Invariant #4)
        let at_deadline = window.end_time;
        assert!(check_veto_supremacy(&window, at_deadline).is_err());

        // Even after deadline, veto still blocks
        let past_deadline = window.end_time + 1000;
        assert!(check_veto_supremacy(&window, past_deadline).is_err());
    }

    // ------------------------------------------------------------------------
    // Integration Tests: Time Drift Tolerance Boundary Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_time_drift_tolerance_early_boundary() {
        let request_id = RecoveryRequestId::generate();
        let start_time = TIME_DRIFT_TOLERANCE_MS + 1000; // Ensure no underflow
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Exactly at tolerance boundary (early)
        let early_boundary = start_time - TIME_DRIFT_TOLERANCE_MS;
        assert!(window.is_within_window(early_boundary));

        // Just outside tolerance (too early)
        let too_early = early_boundary - 1;
        assert!(!window.is_within_window(too_early));
    }

    #[test]
    fn test_time_drift_tolerance_late_boundary() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Just before tolerance boundary (late)
        let just_before_late = window.end_time + TIME_DRIFT_TOLERANCE_MS - 1;
        assert!(window.is_within_window(just_before_late));

        // At tolerance boundary - window is now expired (exclusive upper bound)
        let late_boundary = window.end_time + TIME_DRIFT_TOLERANCE_MS;
        assert!(!window.is_within_window(late_boundary));
        assert!(window.is_window_expired(late_boundary));

        // Just outside tolerance (too late)
        let too_late = late_boundary + 1;
        assert!(!window.is_within_window(too_late));
    }

    #[test]
    fn test_time_drift_tolerance_with_recovery_completion() {
        let request_id = RecoveryRequestId::generate();
        let start_time = TIME_DRIFT_TOLERANCE_MS + 1000; // Ensure no underflow
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // At early boundary - should allow completion if no vetoes
        let early_boundary = start_time - TIME_DRIFT_TOLERANCE_MS;
        // Within window but can't complete yet
        assert!(!window.can_complete(early_boundary));

        // At late boundary - should allow completion
        let late_boundary = window.end_time + TIME_DRIFT_TOLERANCE_MS;
        assert!(window.can_complete(late_boundary));

        // Just outside late boundary - should still allow completion
        let just_past = late_boundary + 1;
        assert!(window.can_complete(just_past));
    }

    // ------------------------------------------------------------------------
    // Integration Tests: Edge Cases
    // ------------------------------------------------------------------------

    #[test]
    fn test_recovery_window_zero_duration() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;

        // Create window with zero duration (edge case, not normal)
        let mut window = RecoveryWindow::new(request_id.clone(), start_time, Role::Authorized);
        window.end_time = start_time; // Zero duration

        // At start time - should be within tolerance
        assert!(window.is_within_window(start_time));

        // Just after start + tolerance - should be expired
        let just_after = start_time + TIME_DRIFT_TOLERANCE_MS + 1;
        assert!(window.is_window_expired(just_after));
    }

    #[test]
    fn test_recovery_window_maximum_vetoes() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let mut window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Add many vetoes (stress test)
        let num_vetoes = 100;
        for _ in 0..num_vetoes {
            let device_id = DeviceId::generate();
            window.add_veto(VetoMessage::new(device_id, None));
        }

        // Verify all vetoes recorded
        assert_eq!(window.veto_count() as usize, num_vetoes);

        // Verify veto supremacy still enforced
        let result = check_veto_supremacy(&window, start_time + 1000);
        assert!(result.is_err());
        match result.unwrap_err() {
            PqrrError::Vetoed { veto_count, .. } => {
                assert_eq!(veto_count as usize, num_vetoes);
            }
            _ => panic!("Expected Vetoed error"),
        }
    }

    #[test]
    fn test_recovery_window_remaining_time_edge_cases() {
        let request_id = RecoveryRequestId::generate();
        let start_time = 1000;
        let window = RecoveryWindow::new(request_id, start_time, Role::Authorized);

        // Before start
        assert_eq!(
            window.remaining_time(start_time - 100),
            VETO_WINDOW_MS + 100
        );

        // At start
        assert_eq!(window.remaining_time(start_time), VETO_WINDOW_MS);

        // At end
        assert_eq!(window.remaining_time(window.end_time), 0);

        // After end
        assert_eq!(window.remaining_time(window.end_time + 1000), 0);
    }
}
