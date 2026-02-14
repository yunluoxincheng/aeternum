//! # Message Codec
//!
//! Encoding and decoding of protocol messages with AEAD encryption.
//!
//! ## Payload Types
//!
//! - `Handshake` - Device pairing protocol
//! - `Sync` - Global epoch synchronization
//! - `Veto` - Recovery veto signal (highest priority)
//! - `Recovery` - Cold anchor recovery flow
//!
//! ## Security
//!
//! - All payloads are encrypted with XChaCha20-Poly1305
//! - Replay protection via nonce tracking
//! - Veto messages bypass normal queue processing

use crate::sync::{frame::WireFrame, Result, WireError, NONCE_SIZE};
use serde::{Deserialize, Serialize};

/// Message payload type identifiers
///
/// These values are stored encrypted in the Wire Frame.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[repr(u8)]
pub enum PayloadType {
    /// Device pairing handshake (new device onboarding)
    Handshake = 0x01,

    /// Global epoch synchronization (PQRR coordination)
    Sync = 0x02,

    /// Veto signal (48h window recovery interception)
    ///
    /// **Invariant #4**: Veto has highest priority - must bypass normal queue.
    Veto = 0x03,

    /// Cold anchor recovery flow
    Recovery = 0x04,

    /// Protocol version negotiation
    VersionNegotiation = 0x05,

    /// Unknown/invalid payload type
    #[serde(other)]
    Unknown = 0xFF,
}

impl PayloadType {
    /// Convert from byte representation
    pub fn from_byte(value: u8) -> Self {
        match value {
            0x01 => PayloadType::Handshake,
            0x02 => PayloadType::Sync,
            0x03 => PayloadType::Veto,
            0x04 => PayloadType::Recovery,
            0x05 => PayloadType::VersionNegotiation,
            _ => PayloadType::Unknown,
        }
    }

    /// Convert to byte representation
    pub fn to_byte(self) -> u8 {
        self as u8
    }

    /// Check if this payload type requires immediate processing
    ///
    /// Veto signals must bypass normal queue processing.
    pub fn requires_immediate_processing(self) -> bool {
        matches!(self, PayloadType::Veto)
    }

    /// Check if this payload type can be processed in degraded mode
    pub fn allowed_in_degraded_mode(self) -> bool {
        matches!(self, PayloadType::Veto | PayloadType::Recovery)
    }
}

/// Protocol message codec
///
/// Handles encoding and decoding of messages with AEAD encryption.
pub struct MessageCodec;

impl MessageCodec {
    /// Encode a message into a Wire Frame
    ///
    /// # Arguments
    ///
    /// * `payload_type` - Type of message being encoded
    /// * `payload` - Serialized message bytes (plaintext)
    /// * `epoch` - Current logical epoch
    /// * `nonce` - Unique nonce for this message
    /// * `auth_tag` - Poly1305 authentication tag
    ///
    /// # Returns
    ///
    /// Returns a `WireFrame` ready for network transmission.
    ///
    /// # Note
    ///
    /// This method assumes encryption has been performed externally.
    /// The `encrypted_body` parameter should already be ciphertext.
    pub fn encode(
        payload_type: PayloadType,
        encrypted_body: Vec<u8>,
        epoch: u32,
        nonce: [u8; NONCE_SIZE],
        auth_tag: [u8; crate::sync::AUTH_TAG_SIZE],
    ) -> Result<WireFrame> {
        WireFrame::new(
            nonce,
            epoch,
            payload_type.to_byte(),
            encrypted_body,
            auth_tag,
        )
    }

    /// Decode a Wire Frame and extract payload type
    ///
    /// # Arguments
    ///
    /// * `frame` - Received Wire Frame
    ///
    /// # Returns
    ///
    /// Returns the `PayloadType` and encrypted body.
    ///
    /// # Note
    ///
    /// Decryption must be performed externally after extracting the body.
    pub fn decode_payload_type(frame: &WireFrame) -> Result<PayloadType> {
        let payload_type = PayloadType::from_byte(frame.payload_type);

        if matches!(payload_type, PayloadType::Unknown) {
            return Err(WireError::InvalidPayloadType(frame.payload_type));
        }

        Ok(payload_type)
    }

    /// Extract encrypted body from frame
    ///
    /// # Arguments
    ///
    /// * `frame` - Received Wire Frame
    ///
    /// # Returns
    ///
    /// Returns the ciphertext (encrypted body).
    pub fn extract_body(frame: &WireFrame) -> Vec<u8> {
        frame.encrypted_body.clone()
    }

    /// Extract nonce from frame
    ///
    /// # Arguments
    ///
    /// * `frame` - Received Wire Frame
    ///
    /// # Returns
    ///
    /// Returns the nonce as a byte array.
    pub fn extract_nonce(frame: &WireFrame) -> [u8; NONCE_SIZE] {
        frame.nonce
    }
}

/// Generic message trait for serializable payloads
pub trait Message: Serialize + for<'de> Deserialize<'de> + Send + Sync {
    /// Get the payload type for this message
    fn payload_type() -> PayloadType;

    /// Serialize message to bytes
    fn serialize_message(&self) -> Result<Vec<u8>> {
        bincode::serialize(self).map_err(|e| WireError::DeserializationFailed(e.to_string()))
    }

    /// Deserialize message from bytes
    fn deserialize_message(data: &[u8]) -> Result<Self>
    where
        Self: Sized,
    {
        bincode::deserialize(data).map_err(|e| WireError::DeserializationFailed(e.to_string()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
    struct TestMessage {
        data: String,
        value: u32,
    }

    impl Message for TestMessage {
        fn payload_type() -> PayloadType {
            PayloadType::Sync
        }
    }

    #[test]
    fn test_payload_type_conversion() {
        assert_eq!(PayloadType::from_byte(0x01), PayloadType::Handshake);
        assert_eq!(PayloadType::from_byte(0x02), PayloadType::Sync);
        assert_eq!(PayloadType::from_byte(0x03), PayloadType::Veto);
        assert_eq!(PayloadType::from_byte(0x04), PayloadType::Recovery);
        assert_eq!(
            PayloadType::from_byte(0x05),
            PayloadType::VersionNegotiation
        );
        assert_eq!(PayloadType::from_byte(0xFF), PayloadType::Unknown);
    }

    #[test]
    fn test_payload_type_to_byte() {
        assert_eq!(PayloadType::Handshake.to_byte(), 0x01);
        assert_eq!(PayloadType::Sync.to_byte(), 0x02);
        assert_eq!(PayloadType::Veto.to_byte(), 0x03);
        assert_eq!(PayloadType::Recovery.to_byte(), 0x04);
        assert_eq!(PayloadType::VersionNegotiation.to_byte(), 0x05);
    }

    #[test]
    fn test_payload_type_immediate_processing() {
        // Only Veto requires immediate processing
        assert!(PayloadType::Veto.requires_immediate_processing());
        assert!(!PayloadType::Sync.requires_immediate_processing());
        assert!(!PayloadType::Handshake.requires_immediate_processing());
    }

    #[test]
    fn test_payload_type_degraded_mode() {
        // Veto and Recovery are allowed in degraded mode
        assert!(PayloadType::Veto.allowed_in_degraded_mode());
        assert!(PayloadType::Recovery.allowed_in_degraded_mode());
        assert!(!PayloadType::Sync.allowed_in_degraded_mode());
        assert!(!PayloadType::Handshake.allowed_in_degraded_mode());
    }

    #[test]
    fn test_message_codec_encode() {
        let nonce = [1u8; NONCE_SIZE];
        let auth_tag = [2u8; crate::sync::AUTH_TAG_SIZE];
        let encrypted_body = vec![3, 4, 5];

        let frame = MessageCodec::encode(
            PayloadType::Sync,
            encrypted_body.clone(),
            1,
            nonce,
            auth_tag,
        )
        .expect("Failed to encode");

        assert_eq!(frame.nonce, nonce);
        assert_eq!(frame.epoch, 1);
        assert_eq!(frame.payload_type, 0x02);
        assert_eq!(frame.encrypted_body, encrypted_body);
    }

    #[test]
    fn test_message_codec_decode_payload_type() {
        let frame = WireFrame::new(
            [0u8; NONCE_SIZE],
            1,
            PayloadType::Veto.to_byte(),
            vec![],
            [0u8; crate::sync::AUTH_TAG_SIZE],
        )
        .expect("Failed to create frame");

        let payload_type =
            MessageCodec::decode_payload_type(&frame).expect("Failed to decode payload type");

        assert_eq!(payload_type, PayloadType::Veto);
    }

    #[test]
    fn test_message_codec_decode_invalid_payload() {
        let frame = WireFrame::new(
            [0u8; NONCE_SIZE],
            1,
            0xFF, // Unknown payload type
            vec![],
            [0u8; crate::sync::AUTH_TAG_SIZE],
        )
        .expect("Failed to create frame");

        let result = MessageCodec::decode_payload_type(&frame);
        assert!(matches!(result, Err(WireError::InvalidPayloadType(0xFF))));
    }

    #[test]
    fn test_message_serialization() {
        let msg = TestMessage {
            data: "hello".to_string(),
            value: 42,
        };

        let serialized = msg.serialize_message().expect("Failed to serialize");
        assert!(!serialized.is_empty());

        let deserialized =
            TestMessage::deserialize_message(&serialized).expect("Failed to deserialize");

        assert_eq!(deserialized, msg);
    }
}
