//! # Bridge Types
//!
//! Bridge-specific types for UniFFI interface.

use crate::models::device::DeviceId;
use crate::protocol::ProtocolState;
use std::time::{SystemTime, UNIX_EPOCH};

/// Device information - Sanitized for UI layer
///
/// Contains only non-sensitive device metadata.
#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq)]
pub struct DeviceInfo {
    /// Device identifier (16 bytes)
    pub device_id: Vec<u8>,

    /// Device name (user-friendly)
    pub device_name: String,

    /// Epoch version
    pub epoch: u32,

    /// Protocol state
    pub state: ProtocolState,

    /// Last seen timestamp (Unix milliseconds)
    pub last_seen_timestamp: i64,

    /// Whether this is the current device
    pub is_this_device: bool,
}

impl DeviceInfo {
    /// Create DeviceInfo from DeviceId and metadata
    pub fn new(
        device_id: DeviceId,
        device_name: String,
        epoch: u32,
        state: ProtocolState,
        is_this_device: bool,
    ) -> Self {
        Self {
            device_id: device_id.as_bytes().to_vec(),
            device_name,
            epoch,
            state,
            last_seen_timestamp: SystemTime::now()
                .duration_since(UNIX_EPOCH)
                .map(|d| d.as_millis() as i64)
                .unwrap_or(0),
            is_this_device,
        }
    }

    /// Create DeviceInfo with custom last_seen timestamp
    pub fn with_last_seen(
        device_id: DeviceId,
        device_name: String,
        epoch: u32,
        state: ProtocolState,
        last_seen_timestamp: i64,
        is_this_device: bool,
    ) -> Self {
        Self {
            device_id: device_id.as_bytes().to_vec(),
            device_name,
            epoch,
            state,
            last_seen_timestamp,
            is_this_device,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_device_info_creation() {
        let device_id = DeviceId::generate();
        let info = DeviceInfo::new(
            device_id,
            "Test Device".to_string(),
            1,
            ProtocolState::Idle,
            false,
        );

        assert_eq!(info.device_name, "Test Device");
        assert_eq!(info.epoch, 1);
        assert!(!info.is_this_device);
    }

    #[test]
    fn test_device_info_with_last_seen() {
        let device_id = DeviceId::generate();
        let timestamp = 1234567890;
        let info = DeviceInfo::with_last_seen(
            device_id,
            "Test Device".to_string(),
            1,
            ProtocolState::Idle,
            timestamp,
            true,
        );

        assert_eq!(info.last_seen_timestamp, timestamp);
        assert!(info.is_this_device);
    }
}
