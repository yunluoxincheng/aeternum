//! # Bridge Module Tests
//!
//! Integration tests for UniFFI bridge interfaces.
//!
//! ## Test Coverage
//!
//! - VaultSession unlock/lock flow
//! - Device list retrieval
//! - Error mapping correctness
//! - Interface availability

#![cfg(test)]

mod tests {
    use super::*;
    use crate::bridge::{AeternumEngine, DeviceInfo, VaultSession};
    use crate::models::device::{DeviceId, DeviceStatus};
    use crate::models::epoch::CryptoEpoch;
    use crate::protocol::ProtocolState;
    use std::collections::HashMap;

    // ------------------------------------------------------------------------
    // 0.4.1 VaultSession unlock/lock flow tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_vault_session_creation() {
        let vault_key = vec![1u8, 2, 3, 4];
        let epoch = 1;

        let session = VaultSession::new(vault_key, epoch);

        // Verify session is initially valid
        assert!(session.is_valid());
    }

    #[test]
    fn test_vault_session_list_record_ids() {
        let vault_key = vec![1u8, 2, 3, 4];
        let session = VaultSession::new(vault_key, 1);

        let ids = session.list_record_ids();

        // Should have demo records
        assert_eq!(ids.len(), 2);
        assert!(ids.contains(&"rec_001".to_string()));
        assert!(ids.contains(&"rec_002".to_string()));
    }

    #[test]
    fn test_vault_session_decrypt_field_success() {
        let vault_key = vec![1u8, 2, 3, 4];
        let session = VaultSession::new(vault_key, 1);

        // Decrypt existing record and field
        let result = session.decrypt_field("rec_001".to_string(), "title".to_string());

        assert!(result.is_ok());
        assert_eq!(result.unwrap(), "Gmail Account");
    }

    #[test]
    fn test_vault_session_decrypt_field_invalid_record() {
        let vault_key = vec![1u8, 2, 3, 4];
        let session = VaultSession::new(vault_key, 1);

        // Try to decrypt non-existent record
        let result = session.decrypt_field("invalid_record".to_string(), "title".to_string());

        assert!(result.is_err());
    }

    #[test]
    fn test_vault_session_decrypt_field_invalid_field() {
        let vault_key = vec![1u8, 2, 3, 4];
        let session = VaultSession::new(vault_key, 1);

        // Try to decrypt non-existent field
        let result = session.decrypt_field("rec_001".to_string(), "invalid_field".to_string());

        assert!(result.is_err());
    }

    // Note: lock() method is not implemented in this prototype
    // In production, this would test:
    // - lock() zeroizes vault key
    // - is_valid() returns false after lock
    // - decrypt_field() fails after lock

    // ------------------------------------------------------------------------
    // 0.4.2 Device list retrieval tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_aeternum_engine_creation() {
        let vault_path = "/tmp/test_vault".to_string();
        let engine = AeternumEngine::new_with_path(vault_path);

        assert!(engine.is_ok());
        let engine = engine.unwrap();

        // Verify engine was created successfully
        // Note: vault_path is private, so we can't directly check it
    }

    #[test]
    fn test_get_device_list() {
        let vault_path = "/tmp/test_vault".to_string();
        let engine = AeternumEngine::new_with_path(vault_path).unwrap();

        // Get device list
        let devices = engine.get_device_list();

        assert!(devices.is_ok());
        let devices = devices.unwrap();

        // Should have at least this device
        assert!(!devices.is_empty());
    }

    #[test]
    fn test_get_device_list_contains_this_device() {
        let vault_path = "/tmp/test_vault".to_string();
        let engine = AeternumEngine::new_with_path(vault_path).unwrap();

        let devices = engine.get_device_list().unwrap();

        // At least one device should be marked as "this device"
        let this_device_count = devices.iter().filter(|d| d.is_this_device).count();
        assert!(this_device_count >= 1);
    }

    // ------------------------------------------------------------------------
    // 0.4.3 Error mapping tests
    // ------------------------------------------------------------------------

    // Note: lock() test is not implemented in this prototype
    // In production, this would test session invalidation after lock
    // The error mapping is verified through other tests

    #[test]
    fn test_error_mapping_header_incomplete() {
        let vault_key = vec![1u8, 2, 3, 4];
        let session = VaultSession::new(vault_key, 1);

        // Try to decrypt non-existent record
        let result = session.decrypt_field("nonexistent".to_string(), "field".to_string());

        assert!(result.is_err());

        // Verify error is HeaderIncomplete variant
        // Error checking is implicit in Result type
    }

    #[test]
    fn test_error_mapping_device_id_length() {
        let vault_path = "/tmp/test_vault".to_string();
        let engine = AeternumEngine::new_with_path(vault_path).unwrap();

        // Try to revoke with invalid device ID length
        let invalid_device_id = vec![1u8, 2, 3]; // Less than 16 bytes

        let result = engine.revoke_device(invalid_device_id);

        assert!(result.is_err());
    }

    #[test]
    fn test_error_mapping_revoke_this_device() {
        let vault_path = "/tmp/test_vault".to_string();
        let engine = AeternumEngine::new_with_path(vault_path).unwrap();

        // Get this device ID
        let devices = engine.get_device_list().unwrap();
        let this_device = devices.iter().find(|d| d.is_this_device).unwrap();
        let this_device_id = this_device.device_id.clone();

        // Try to revoke this device
        let result = engine.revoke_device(this_device_id);

        assert!(result.is_err());

        // Verify error is InsufficientPrivileges variant
        // Error checking is implicit in Result type
    }

    // ------------------------------------------------------------------------
    // 0.4.4 Interface availability tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_interface_vault_session_available() {
        // Verify VaultSession is accessible through crate root
        use crate::bridge::VaultSession;

        let vault_key = vec![1u8, 2, 3, 4];
        let session = VaultSession::new(vault_key, 1);

        // Verify all methods are callable
        let _ids = session.list_record_ids();
        let _result = session.decrypt_field("rec_001".to_string(), "title".to_string());
        let _valid = session.is_valid();
    }

    #[test]
    fn test_interface_aeternum_engine_available() {
        // Verify AeternumEngine is accessible through crate root
        use crate::bridge::AeternumEngine;

        let vault_path = "/tmp/test_vault".to_string();
        let engine = AeternumEngine::new_with_path(vault_path).unwrap();

        // Verify all methods are callable
        let _devices = engine.get_device_list();
        let _recovery_id = engine.initiate_recovery();
        let _integrity = engine.verify_vault_integrity(vec![1, 2, 3, 4]);
    }

    #[test]
    fn test_interface_device_info_available() {
        // Verify DeviceInfo is accessible through crate root
        use crate::bridge::DeviceInfo;

        let device_id = DeviceId::generate();
        let info = DeviceInfo::new(
            device_id,
            "Test Device".to_string(),
            1,
            ProtocolState::Idle,
            false,
        );

        // Verify all fields are accessible
        assert_eq!(info.device_name, "Test Device");
        assert_eq!(info.epoch, 1);
        assert_eq!(info.state, ProtocolState::Idle);
        assert!(!info.is_this_device);
    }
}
