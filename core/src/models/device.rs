//! # Device Identity and Headers
//!
//! This module defines device identifiers, status, and headers used in
//! the PQRR protocol for device management and key recovery.
//!
//! ## Components
//!
//! - `DeviceId`: 16-byte UUID for device identification
//! - `DeviceStatus`: Device state (Active/Revoked/Degraded)
//! - `DeviceHeader`: Encrypted metadata stored server-side
//!
//! ## Shadow Anchor (Device_0)
//!
//! Device_0 uses all-zero identifier ([0u8; 16]) to represent the
//! cold anchor, making it indistinguishable from regular devices
//! in the server's view. This preserves privacy by preventing
//! attackers from identifying which device is the recovery anchor.

use crate::crypto::kem::{KyberCipherText, KyberPublicKeyBytes};
use crate::models::epoch::CryptoEpoch;
use serde::{Deserialize, Serialize};

// ============================================================================
// Role & Operation Types (for Invariant #3)
// ============================================================================

/// Device role in permission system
///
/// Defines what operations a device is allowed to perform.
/// This is used to enforce Invariant #3: Causal Barrier.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum Role {
    /// Recovery role - can decrypt vault data only
    ///
    /// RECOVERY role devices:
    /// - Can unwrap DEK to decrypt vault data
    /// - CANNOT perform management operations (σ_rotate, etc.)
    /// - CANNOT request epoch upgrades
    Recovery,

    /// Authorized role - full permissions
    ///
    /// AUTHORIZED role devices:
    /// - Can unwrap DEK to decrypt vault data
    /// - Can perform management operations
    /// - Can request epoch upgrades
    Authorized,
}

/// Management operation type
///
/// Operations that require AUTHORIZED role (RECOVERY role is blocked).
/// This is used to enforce Invariant #3: Causal Barrier.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub enum Operation {
    /// Epoch rotation - creates new DEK for all devices
    SigmaRotate,

    /// Device revocation - removes a device from active set
    RevokeDevice,

    /// Key re-encryption - re-wraps DEK for all devices
    RekeyVault,

    /// Policy update - modifies system security policy
    UpdatePolicy,
}

impl Role {
    /// Check if this role can perform management operations
    ///
    /// Only AUTHORIZED role can perform operations like σ_rotate.
    /// RECOVERY role is blocked by Invariant #3.
    pub fn can_permit_operation(&self, _op: Operation) -> bool {
        match self {
            Role::Recovery => false, // Invariant #3: Causal Barrier
            Role::Authorized => true,
        }
    }

    /// Get role name for error messages
    pub fn as_str(&self) -> &'static str {
        match self {
            Role::Recovery => "RECOVERY",
            Role::Authorized => "AUTHORIZED",
        }
    }
}

impl Operation {
    /// Get operation name for error messages
    pub fn as_str(&self) -> &'static str {
        match self {
            Operation::SigmaRotate => "σ_rotate",
            Operation::RevokeDevice => "revoke_device",
            Operation::RekeyVault => "rekey_vault",
            Operation::UpdatePolicy => "update_policy",
        }
    }
}

// ============================================================================
// Device Identifier
// ============================================================================

/// Device unique identifier (16-byte UUID)
///
/// Each device in the Aeternum system has a unique 16-byte identifier.
/// Device_0 (shadow anchor) uses all zeros as a fixed identifier.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
pub struct DeviceId(pub [u8; 16]);

impl DeviceId {
    /// Create a DeviceId from a 16-byte array
    ///
    /// # Arguments
    ///
    /// - `bytes`: A 16-byte array representing the device ID
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::models::DeviceId;
    ///
    /// let bytes = [1u8; 16];
    /// let device_id = DeviceId::from_bytes(bytes);
    /// ```
    pub fn from_bytes(bytes: [u8; 16]) -> Self {
        Self(bytes)
    }

    /// Generate a random device ID using CSPRNG
    ///
    /// This is the recommended method for creating new device identifiers.
    /// Uses the operating system's cryptographically secure random number
    /// generator to ensure uniqueness and unpredictability.
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::models::DeviceId;
    ///
    /// let device_id = DeviceId::generate();
    /// assert!(!device_id.is_shadow_anchor());
    /// ```
    pub fn generate() -> Self {
        let mut bytes = [0u8; 16];
        getrandom::getrandom(&mut bytes).expect("CSPRNG failure");
        Self(bytes)
    }

    /// Check if this is the shadow anchor (Device_0)
    ///
    /// Device_0 uses all-zero identifier to represent the cold anchor,
    /// making it indistinguishable from regular devices in the server's view.
    ///
    /// # Returns
    ///
    /// `true` if this device ID is all zeros (shadow anchor), `false` otherwise
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::models::DeviceId;
    ///
    /// let shadow = DeviceId::shadow_anchor();
    /// assert!(shadow.is_shadow_anchor());
    ///
    /// let normal = DeviceId::generate();
    /// assert!(!normal.is_shadow_anchor());
    /// ```
    pub fn is_shadow_anchor(&self) -> bool {
        self.0 == [0u8; 16]
    }

    /// Create the shadow anchor identifier (Device_0)
    ///
    /// The shadow anchor is a special device with fixed all-zero identifier,
    /// used for cold recovery with mnemonic phrases.
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::models::DeviceId;
    ///
    /// let anchor = DeviceId::shadow_anchor();
    /// assert!(anchor.is_shadow_anchor());
    /// ```
    pub fn shadow_anchor() -> Self {
        Self([0u8; 16])
    }

    /// Get the device ID as a byte array
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::models::DeviceId;
    ///
    /// let device_id = DeviceId::generate();
    /// let bytes = device_id.as_bytes();
    /// assert_eq!(bytes.len(), 16);
    /// ```
    pub fn as_bytes(&self) -> &[u8; 16] {
        &self.0
    }
}

impl std::fmt::Display for DeviceId {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        // Display as hex string
        for byte in &self.0 {
            write!(f, "{:02x}", byte)?;
        }
        Ok(())
    }
}

// ============================================================================
// Device Status
// ============================================================================

/// Device status enumeration
///
/// Represents the current state of a device in the PQRR protocol.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum DeviceStatus {
    /// Device is active and trusted
    ///
    /// Active devices can:
    /// - Decrypt vault data using their DEK
    /// - Participate in PQRR protocol operations
    /// - Request epoch upgrades
    Active,

    /// Device has been revoked
    ///
    /// Revoked devices:
    /// - Cannot decrypt vault data
    /// - Are removed from the active device set
    /// - Cannot be reactivated (must re-enroll)
    Revoked,

    /// Device is in degraded mode (integrity check failed)
    ///
    /// Degraded devices:
    /// - Have limited functionality (read-only pending verification)
    /// - May have failed Play Integrity verification
    /// - Require user intervention to restore full functionality
    Degraded,
}

// ============================================================================
// Device Header
// ============================================================================

/// Device header containing encrypted metadata
///
/// Device headers are stored server-side and contain the encrypted
/// Data Encryption Key (DEK) for each device. This allows the vault
/// to be decrypted by any active device.
///
/// ## Serialization
///
/// Headers can be serialized for network transmission or storage:
/// - `serialize()`: Convert to bytes using bincode
/// - `deserialize()`: Reconstruct from bytes
///
/// ## Invariant Enforcement
///
/// - **Invariant #2**: Each active device must have exactly one header
/// - Headers are immutable once created (except status changes)
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct DeviceHeader {
    /// Device identifier
    pub device_id: DeviceId,

    /// Cryptographic epoch for this device
    pub epoch: CryptoEpoch,

    /// Device's Kyber-1024 public key (1568 bytes)
    ///
    /// NOTE: KEM serialization will be added when VaultBlob is implemented
    pub public_key: KyberPublicKeyBytes,

    /// Encapsulated DEK for this device (1568 bytes)
    ///
    /// NOTE: KEM serialization will be added when VaultBlob is implemented
    pub encrypted_dek: KyberCipherText,

    /// Current device status
    pub status: DeviceStatus,

    /// Creation timestamp (Unix milliseconds)
    pub created_at: u64,
}

impl DeviceHeader {
    /// Create a new device header
    ///
    /// # Arguments
    ///
    /// - `device_id`: Unique device identifier
    /// - `epoch`: Cryptographic epoch for this device
    /// - `public_key`: Device's Kyber-1024 public key
    /// - `encrypted_dek`: Encapsulated DEK for this device
    ///
    /// # Returns
    ///
    /// A new `DeviceHeader` with `Active` status and current timestamp
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::models::{DeviceId, DeviceHeader};
    /// use aeternum_core::models::epoch::CryptoEpoch;
    /// use aeternum_core::crypto::kem::{KyberKEM, KyberCipherText};
    ///
    /// let device_id = DeviceId::generate();
    /// let epoch = CryptoEpoch::initial();
    /// let keypair = KyberKEM::generate_keypair();
    /// let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();
    ///
    /// let header = DeviceHeader::new(
    ///     device_id,
    ///     epoch,
    ///     keypair.public,
    ///     encrypted_dek
    /// );
    /// ```
    pub fn new(
        device_id: DeviceId,
        epoch: CryptoEpoch,
        public_key: KyberPublicKeyBytes,
        encrypted_dek: KyberCipherText,
    ) -> Self {
        Self {
            device_id,
            epoch,
            public_key,
            encrypted_dek,
            status: DeviceStatus::Active,
            created_at: current_timestamp_ms(),
        }
    }

    /// Create a shadow anchor header (Device_0)
    ///
    /// Device_0 is the cold recovery anchor with fixed all-zero device ID.
    /// This is created during initial vault setup using the mnemonic phrase.
    ///
    /// # Arguments
    ///
    /// - `epoch`: Cryptographic epoch for the anchor
    /// - `public_key`: Anchor's Kyber-1024 public key
    /// - `encrypted_dek`: Encapsulated DEK for the anchor
    ///
    /// # Returns
    ///
    /// A new `DeviceHeader` for Device_0 (shadow anchor)
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::models::{DeviceHeader, DeviceStatus};
    /// use aeternum_core::models::epoch::CryptoEpoch;
    /// use aeternum_core::crypto::kem::{KyberKEM, KyberCipherText};
    ///
    /// let epoch = CryptoEpoch::initial();
    /// let keypair = KyberKEM::generate_keypair();
    /// let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();
    ///
    /// let header = DeviceHeader::shadow_anchor(epoch, keypair.public, encrypted_dek);
    /// assert!(header.device_id.is_shadow_anchor());
    /// assert_eq!(header.status, DeviceStatus::Active);
    /// ```
    pub fn shadow_anchor(
        epoch: CryptoEpoch,
        public_key: KyberPublicKeyBytes,
        encrypted_dek: KyberCipherText,
    ) -> Self {
        Self {
            device_id: DeviceId::shadow_anchor(),
            epoch,
            public_key,
            encrypted_dek,
            status: DeviceStatus::Active,
            created_at: current_timestamp_ms(),
        }
    }

    /// Revoke this device
    ///
    /// Changes the device status to `Revoked`, preventing it from
    /// decrypting vault data or participating in protocol operations.
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::models::{DeviceId, DeviceHeader, DeviceStatus};
    /// use aeternum_core::models::epoch::CryptoEpoch;
    /// use aeternum_core::crypto::kem::{KyberKEM, KyberCipherText};
    ///
    /// let device_id = DeviceId::generate();
    /// let epoch = CryptoEpoch::initial();
    /// let keypair = KyberKEM::generate_keypair();
    /// let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();
    ///
    /// let mut header = DeviceHeader::new(device_id, epoch, keypair.public, encrypted_dek);
    /// assert_eq!(header.status, DeviceStatus::Active);
    ///
    /// header.revoke();
    /// assert_eq!(header.status, DeviceStatus::Revoked);
    /// ```
    pub fn revoke(&mut self) {
        self.status = DeviceStatus::Revoked;
    }

    /// Check if this header belongs to the given epoch
    ///
    /// Used during epoch validation to ensure device headers are
    /// from the expected epoch.
    ///
    /// # Arguments
    ///
    /// - `epoch`: The epoch to check against
    ///
    /// # Returns
    ///
    /// `true` if this header's epoch matches the given epoch
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::models::{DeviceId, DeviceHeader};
    /// use aeternum_core::models::epoch::CryptoEpoch;
    /// use aeternum_core::crypto::kem::{KyberKEM, KyberCipherText};
    ///
    /// let device_id = DeviceId::generate();
    /// let epoch = CryptoEpoch::initial();
    /// let keypair = KyberKEM::generate_keypair();
    /// let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();
    ///
    /// let header = DeviceHeader::new(device_id, epoch.clone(), keypair.public, encrypted_dek);
    /// assert!(header.belongs_to_epoch(&epoch));
    ///
    /// let next_epoch = epoch.next();
    /// assert!(!header.belongs_to_epoch(&next_epoch));
    /// ```
    pub fn belongs_to_epoch(&self, epoch: &CryptoEpoch) -> bool {
        self.epoch.version == epoch.version
    }

    // ------------------------------------------------------------------------
    // Serialization Methods
    // ------------------------------------------------------------------------

    /// Serialize this header to bytes
    ///
    /// Uses bincode for efficient binary serialization.
    ///
    /// # Returns
    ///
    /// Serialized bytes representing this header.
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::models::{DeviceId, DeviceHeader};
    /// use aeternum_core::models::epoch::CryptoEpoch;
    /// use aeternum_core::crypto::kem::{KyberKEM, KyberCipherText};
    ///
    /// let device_id = DeviceId::generate();
    /// let epoch = CryptoEpoch::initial();
    /// let keypair = KyberKEM::generate_keypair();
    /// let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();
    ///
    /// let header = DeviceHeader::new(device_id, epoch, keypair.public, encrypted_dek);
    /// let serialized = header.serialize();
    /// assert!(!serialized.is_empty());
    /// ```
    pub fn serialize(&self) -> Vec<u8> {
        bincode::serialize(self).expect("DeviceHeader serialization should never fail")
    }

    /// Deserialize a header from bytes
    ///
    /// # Arguments
    ///
    /// - `bytes`: Serialized header data
    ///
    /// # Returns
    ///
    /// Deserialized `DeviceHeader`.
    ///
    /// # Panics
    ///
    /// Panics if deserialization fails (corrupted data).
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::models::{DeviceId, DeviceHeader};
    /// use aeternum_core::models::epoch::CryptoEpoch;
    /// use aeternum_core::crypto::kem::{KyberKEM, KyberCipherText};
    ///
    /// let device_id = DeviceId::generate();
    /// let epoch = CryptoEpoch::initial();
    /// let keypair = KyberKEM::generate_keypair();
    /// let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();
    ///
    /// let header = DeviceHeader::new(device_id, epoch, keypair.public, encrypted_dek);
    /// let serialized = header.serialize();
    ///
    /// let deserialized = DeviceHeader::deserialize(&serialized);
    /// assert_eq!(deserialized.device_id, header.device_id);
    /// ```
    pub fn deserialize(bytes: &[u8]) -> Self {
        bincode::deserialize(bytes).expect("DeviceHeader deserialization failed - corrupted data")
    }
}

// ============================================================================
// Helper Functions
// ============================================================================

/// Get current Unix timestamp in milliseconds
fn current_timestamp_ms() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

// ============================================================================
// Tests
// ============================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use crate::crypto::kem::KyberKEM;

    // ------------------------------------------------------------------------
    // DeviceId Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_device_id_from_bytes() {
        let bytes = [1u8; 16];
        let device_id = DeviceId::from_bytes(bytes);
        assert_eq!(device_id.0, bytes);
    }

    #[test]
    fn test_device_id_generate() {
        let id1 = DeviceId::generate();
        let id2 = DeviceId::generate();

        // Two generated IDs should be different (probability of collision is negligible)
        assert_ne!(id1.0, id2.0, "Generated device IDs must be unique");

        // Neither should be shadow anchor
        assert!(!id1.is_shadow_anchor());
        assert!(!id2.is_shadow_anchor());
    }

    #[test]
    fn test_device_id_shadow_anchor() {
        let shadow = DeviceId([0u8; 16]);
        assert!(shadow.is_shadow_anchor());

        let anchor = DeviceId::shadow_anchor();
        assert!(anchor.is_shadow_anchor());
        assert_eq!(anchor.0, [0u8; 16]);
    }

    #[test]
    fn test_device_id_as_bytes() {
        let bytes = [42u8; 16];
        let device_id = DeviceId::from_bytes(bytes);
        assert_eq!(device_id.as_bytes(), &bytes);
    }

    #[test]
    fn test_device_id_hash_uniqueness() {
        use std::collections::HashSet;

        let mut ids = HashSet::new();
        for _ in 0..100 {
            let id = DeviceId::generate();
            ids.insert(id);
        }

        // All 100 generated IDs should be unique
        assert_eq!(ids.len(), 100, "All generated device IDs must be unique");
    }

    // ------------------------------------------------------------------------
    // DeviceStatus Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_device_status_debug() {
        assert_eq!(format!("{:?}", DeviceStatus::Active), "Active");
        assert_eq!(format!("{:?}", DeviceStatus::Revoked), "Revoked");
        assert_eq!(format!("{:?}", DeviceStatus::Degraded), "Degraded");
    }

    #[test]
    fn test_device_status_equality() {
        assert_eq!(DeviceStatus::Active, DeviceStatus::Active);
        assert_ne!(DeviceStatus::Active, DeviceStatus::Revoked);
        assert_ne!(DeviceStatus::Revoked, DeviceStatus::Degraded);
    }

    // ------------------------------------------------------------------------
    // DeviceHeader Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_device_header_new() {
        let device_id = DeviceId::generate();
        let epoch = CryptoEpoch::initial();
        let keypair = KyberKEM::generate_keypair();
        let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();

        let header = DeviceHeader::new(device_id, epoch.clone(), keypair.public, encrypted_dek);

        assert_eq!(header.device_id, device_id);
        assert_eq!(header.epoch.version, epoch.version);
        assert_eq!(header.status, DeviceStatus::Active);
        assert!(header.created_at > 0);
    }

    #[test]
    fn test_device_header_shadow_anchor() {
        let epoch = CryptoEpoch::initial();
        let keypair = KyberKEM::generate_keypair();
        let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();

        let header = DeviceHeader::shadow_anchor(epoch.clone(), keypair.public, encrypted_dek);

        assert!(header.device_id.is_shadow_anchor());
        assert_eq!(header.epoch.version, epoch.version);
        assert_eq!(header.status, DeviceStatus::Active);
    }

    #[test]
    fn test_device_header_revoke() {
        let device_id = DeviceId::generate();
        let epoch = CryptoEpoch::initial();
        let keypair = KyberKEM::generate_keypair();
        let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();

        let mut header = DeviceHeader::new(device_id, epoch, keypair.public, encrypted_dek);

        assert_eq!(header.status, DeviceStatus::Active);

        header.revoke();
        assert_eq!(header.status, DeviceStatus::Revoked);
    }

    #[test]
    fn test_device_header_belongs_to_epoch() {
        let device_id = DeviceId::generate();
        let epoch = CryptoEpoch::initial();
        let keypair = KyberKEM::generate_keypair();
        let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();

        let header = DeviceHeader::new(device_id, epoch.clone(), keypair.public, encrypted_dek);

        // Same epoch should match
        assert!(header.belongs_to_epoch(&epoch));

        // Next epoch should not match
        let next_epoch = epoch.next();
        assert!(!header.belongs_to_epoch(&next_epoch));
    }

    // ------------------------------------------------------------------------
    // Serialization Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_device_header_serialize_roundtrip() {
        let device_id = DeviceId::generate();
        let epoch = CryptoEpoch::initial();
        let keypair = KyberKEM::generate_keypair();
        let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();

        let header = DeviceHeader::new(device_id, epoch.clone(), keypair.public, encrypted_dek);

        // Serialize
        let serialized = header.serialize();
        assert!(
            !serialized.is_empty(),
            "Serialized data should not be empty"
        );

        // Deserialize
        let deserialized = DeviceHeader::deserialize(&serialized);

        // Verify all fields match
        assert_eq!(deserialized.device_id, header.device_id);
        assert_eq!(deserialized.epoch.version, header.epoch.version);
        assert_eq!(deserialized.status, header.status);
        assert_eq!(deserialized.created_at, header.created_at);
        assert_eq!(deserialized.public_key.0, header.public_key.0);
        assert_eq!(deserialized.encrypted_dek.0, header.encrypted_dek.0);
    }

    #[test]
    fn test_device_header_serialize_preserves_epoch() {
        // Test serialization with different epochs (Invariant #1)
        let device_id = DeviceId::generate();
        let epoch1 = CryptoEpoch::new(5, crate::models::epoch::CryptoAlgorithm::V1);
        let keypair = KyberKEM::generate_keypair();
        let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();

        let header = DeviceHeader::new(device_id, epoch1.clone(), keypair.public, encrypted_dek);

        let serialized = header.serialize();
        let deserialized = DeviceHeader::deserialize(&serialized);

        // Epoch must be preserved exactly
        assert_eq!(deserialized.epoch.version, 5);
        assert_eq!(deserialized.epoch.version, epoch1.version);
    }

    #[test]
    fn test_device_header_serialize_shadow_anchor() {
        // Test serialization of Device_0 (shadow anchor)
        let epoch = CryptoEpoch::initial();
        let keypair = KyberKEM::generate_keypair();
        let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();

        let header = DeviceHeader::shadow_anchor(epoch.clone(), keypair.public, encrypted_dek);

        let serialized = header.serialize();
        let deserialized = DeviceHeader::deserialize(&serialized);

        // Shadow anchor must preserve all-zero device ID
        assert!(deserialized.device_id.is_shadow_anchor());
        assert_eq!(deserialized.device_id.0, [0u8; 16]);
    }

    #[test]
    #[should_panic]
    fn test_device_header_deserialize_invalid_data() {
        // Test that deserializing invalid data panics
        let invalid_data = vec![0xFF, 0xFF, 0xFF];
        DeviceHeader::deserialize(&invalid_data);
    }

    #[test]
    fn test_device_header_serialize_with_revoked_status() {
        // Test that status changes are preserved through serialization
        let device_id = DeviceId::generate();
        let epoch = CryptoEpoch::initial();
        let keypair = KyberKEM::generate_keypair();
        let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();

        let mut header = DeviceHeader::new(device_id, epoch, keypair.public, encrypted_dek);

        // Revoke the device
        header.revoke();
        assert_eq!(header.status, DeviceStatus::Revoked);

        // Serialize and deserialize
        let serialized = header.serialize();
        let deserialized = DeviceHeader::deserialize(&serialized);

        // Status must be preserved
        assert_eq!(deserialized.status, DeviceStatus::Revoked);
    }
}
