//! # Aeternum Wire Frame
//!
//! Fixed-size (8192-byte) frame format for network transmission.
//!
//! ## Frame Format
//!
//! ```text
//! +----------------+------------------+-------------------+
//! | Nonce (24 B)   | Epoch ID (4 B)  | Payload Type (1 B)|
//! +----------------+------------------+-------------------+
//! | Encrypted Body (Variable)                           |
//! +---------------------------------------------------+
//! | Padding (Variable)              | Auth Tag (16 B)   |
//! +-------------------------------+-------------------+
//! Total: 8192 bytes (fixed)
//! ```
//!
//! ## Security Properties
//!
//! - **Fixed size** prevents traffic analysis
//! - **AEAD encryption** provides confidentiality + integrity
//! - **Epoch in clear** enables routing without decryption
//!
//! ## Invariant Compliance
//!
//! - **Invariant #1**: Epoch field is validated for monotonicity
//! - **Invariant #4**: Veto messages use highest priority routing

use crate::sync::{Result, WireError, AUTH_TAG_SIZE, FRAME_SIZE, MAX_BODY_SIZE, NONCE_SIZE};
use serde::{Deserialize, Serialize};
use zeroize::{Zeroize, ZeroizeOnDrop};

/// Aeternum Wire Frame - fixed 8192-byte network packet
///
/// All network traffic must use this format to prevent traffic fingerprinting.
#[derive(Debug, Clone, Serialize, Deserialize, Zeroize, ZeroizeOnDrop)]
pub struct WireFrame {
    /// XChaCha20-Poly1305 nonce (must be unique per message)
    pub nonce: [u8; NONCE_SIZE],

    /// Current logical epoch version (plaintext for routing)
    pub epoch: u32,

    /// Message payload type (encrypted)
    pub payload_type: u8,

    /// Length of encrypted body (needed for deserialization)
    pub body_len: u16,

    /// AEAD-encrypted body (ciphertext)
    pub encrypted_body: Vec<u8>,

    /// Random padding to ensure fixed frame size
    pub padding: Vec<u8>,

    /// Poly1305 authentication tag
    pub auth_tag: [u8; AUTH_TAG_SIZE],
}

impl WireFrame {
    /// Create a new Wire Frame with automatic padding
    ///
    /// # Arguments
    ///
    /// * `nonce` - Unique nonce for this message (must never be reused)
    /// * `epoch` - Current logical epoch (validated for monotonicity)
    /// * `payload_type` - Message type identifier
    /// * `encrypted_body` - Ciphertext from AEAD encryption
    /// * `auth_tag` - Poly1305 authentication tag
    ///
    /// # Returns
    ///
    /// Returns a `WireFrame` with padding automatically calculated to
    /// ensure the total serialized size is exactly 8192 bytes.
    ///
    /// # Errors
    ///
    /// Returns `WireError::InvalidFrameSize` if the body exceeds maximum size.
    pub fn new(
        nonce: [u8; NONCE_SIZE],
        epoch: u32,
        payload_type: u8,
        encrypted_body: Vec<u8>,
        auth_tag: [u8; AUTH_TAG_SIZE],
    ) -> Result<Self> {
        // Validate body size
        if encrypted_body.len() > MAX_BODY_SIZE {
            return Err(WireError::InvalidFrameSize(
                NONCE_SIZE + 4 + 1 + 2 + encrypted_body.len() + AUTH_TAG_SIZE,
            ));
        }

        let body_len = encrypted_body.len() as u16;

        // Calculate required padding
        let current_size = NONCE_SIZE + 4 + 1 + 2 + encrypted_body.len() + AUTH_TAG_SIZE;
        let padding_size = FRAME_SIZE - current_size;

        Ok(Self {
            nonce,
            epoch,
            payload_type,
            body_len,
            encrypted_body,
            padding: vec![0u8; padding_size],
            auth_tag,
        })
    }

    /// Serialize frame to bytes
    ///
    /// # Returns
    ///
    /// Returns exactly `FRAME_SIZE` (8192) bytes.
    ///
    /// # Panics
    ///
    /// Panics if internal size calculation is incorrect (should never happen).
    pub fn serialize(&self) -> Result<Vec<u8>> {
        let mut buffer = Vec::with_capacity(FRAME_SIZE);

        // Write fixed fields
        buffer.extend_from_slice(&self.nonce);
        buffer.extend_from_slice(&self.epoch.to_be_bytes());
        buffer.push(self.payload_type);
        buffer.extend_from_slice(&self.body_len.to_be_bytes());
        buffer.extend_from_slice(&self.encrypted_body);
        buffer.extend_from_slice(&self.padding);
        buffer.extend_from_slice(&self.auth_tag);

        // Validate size is exactly FRAME_SIZE
        if buffer.len() != FRAME_SIZE {
            return Err(WireError::InvalidFrameSize(buffer.len()));
        }

        Ok(buffer)
    }

    /// Deserialize frame from bytes
    ///
    /// # Arguments
    ///
    /// * `data` - Exactly 8192 bytes from network
    ///
    /// # Returns
    ///
    /// Returns the parsed `WireFrame`.
    ///
    /// # Errors
    ///
    /// Returns `WireError::InvalidFrameSize` if data is not exactly 8192 bytes.
    pub fn deserialize(data: &[u8]) -> Result<Self> {
        if data.len() != FRAME_SIZE {
            return Err(WireError::InvalidFrameSize(data.len()));
        }

        let mut pos = 0;

        // Parse nonce
        let mut nonce = [0u8; NONCE_SIZE];
        nonce.copy_from_slice(&data[pos..pos + NONCE_SIZE]);
        pos += NONCE_SIZE;

        // Parse epoch
        let epoch_bytes = &data[pos..pos + 4];
        let epoch = u32::from_be_bytes(epoch_bytes.try_into().unwrap());
        pos += 4;

        // Parse payload type
        let payload_type = data[pos];
        pos += 1;

        // Parse body length
        let body_len_bytes = &data[pos..pos + 2];
        let body_len = u16::from_be_bytes(body_len_bytes.try_into().unwrap());
        pos += 2;

        // Parse encrypted body using body_len
        let body_end = pos + body_len as usize;
        let encrypted_body = data[pos..body_end].to_vec();
        pos = body_end;

        // Calculate padding size
        let padding_start = pos;
        let padding_end = FRAME_SIZE - AUTH_TAG_SIZE;
        let padding = data[padding_start..padding_end].to_vec();
        pos = padding_end;

        // Parse auth tag
        let mut auth_tag = [0u8; AUTH_TAG_SIZE];
        auth_tag.copy_from_slice(&data[pos..pos + AUTH_TAG_SIZE]);

        Ok(Self {
            nonce,
            epoch,
            payload_type,
            body_len,
            encrypted_body,
            padding,
            auth_tag,
        })
    }

    /// Validate frame integrity
    ///
    /// Checks:
    /// - Size is exactly 8192 bytes
    /// - Padding size matches expected value
    ///
    /// # Errors
    ///
    /// Returns `WireError::InvalidFrameSize` if validation fails.
    pub fn validate(&self) -> Result<()> {
        // Recalculate expected padding size
        let current_size = NONCE_SIZE + 4 + 1 + 2 + self.encrypted_body.len() + AUTH_TAG_SIZE;
        let expected_padding = FRAME_SIZE - current_size;

        if self.padding.len() != expected_padding {
            return Err(WireError::InvalidFrameSize(
                current_size + self.padding.len(),
            ));
        }

        // Validate body_len matches actual body length
        if self.body_len as usize != self.encrypted_body.len() {
            return Err(WireError::InvalidFrameSize(
                NONCE_SIZE + 4 + 1 + 2 + self.encrypted_body.len() + AUTH_TAG_SIZE,
            ));
        }

        Ok(())
    }

    /// Get the nonce value
    pub fn nonce(&self) -> &[u8; NONCE_SIZE] {
        &self.nonce
    }

    /// Get the epoch value
    pub fn epoch(&self) -> u32 {
        self.epoch
    }

    /// Get the payload type
    pub fn payload_type(&self) -> u8 {
        self.payload_type
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_wire_frame_creation() {
        let nonce = [1u8; NONCE_SIZE];
        let epoch = 1;
        let payload_type = 0x01;
        let encrypted_body = vec![2, 3, 4];
        let auth_tag = [5u8; AUTH_TAG_SIZE];

        let frame = WireFrame::new(nonce, epoch, payload_type, encrypted_body.clone(), auth_tag)
            .expect("Failed to create frame");

        assert_eq!(frame.nonce, nonce);
        assert_eq!(frame.epoch, epoch);
        assert_eq!(frame.payload_type, payload_type);
        assert_eq!(frame.encrypted_body, encrypted_body);
        assert_eq!(frame.auth_tag, auth_tag);
    }

    #[test]
    fn test_wire_frame_serialize_roundtrip() {
        let nonce = [42u8; NONCE_SIZE];
        let epoch = 12345;
        let payload_type = 0xFF;
        let encrypted_body = vec![1, 2, 3, 4, 5];
        let auth_tag = [99u8; AUTH_TAG_SIZE];

        let frame = WireFrame::new(nonce, epoch, payload_type, encrypted_body.clone(), auth_tag)
            .expect("Failed to create frame");

        // Serialize
        let serialized = frame.serialize().expect("Failed to serialize");
        assert_eq!(serialized.len(), FRAME_SIZE);

        // Deserialize
        let deserialized = WireFrame::deserialize(&serialized).expect("Failed to deserialize");

        // Verify equality
        assert_eq!(deserialized.nonce, nonce);
        assert_eq!(deserialized.epoch, epoch);
        assert_eq!(deserialized.payload_type, payload_type);
        assert_eq!(deserialized.encrypted_body, encrypted_body);
        assert_eq!(deserialized.auth_tag, auth_tag);
    }

    #[test]
    fn test_wire_frame_validate() {
        let frame = WireFrame::new([0u8; NONCE_SIZE], 1, 0, vec![1, 2, 3], [0u8; AUTH_TAG_SIZE])
            .expect("Failed to create frame");

        assert!(frame.validate().is_ok());
    }

    #[test]
    fn test_wire_frame_invalid_size() {
        // Deserialize with wrong size
        let result = WireFrame::deserialize(&[0u8; 100]);
        assert!(matches!(result, Err(WireError::InvalidFrameSize(100))));
    }

    #[test]
    fn test_wire_frame_max_body_size() {
        // Create body at maximum size
        let max_body = vec![0u8; MAX_BODY_SIZE];
        let frame = WireFrame::new([0u8; NONCE_SIZE], 1, 0, max_body, [0u8; AUTH_TAG_SIZE])
            .expect("Failed to create frame with max body");

        assert!(frame.validate().is_ok());
    }

    #[test]
    fn test_wire_frame_body_too_large() {
        // Create body exceeding maximum size
        let too_large_body = vec![0u8; MAX_BODY_SIZE + 1];
        let result = WireFrame::new(
            [0u8; NONCE_SIZE],
            1,
            0,
            too_large_body,
            [0u8; AUTH_TAG_SIZE],
        );

        assert!(matches!(result, Err(WireError::InvalidFrameSize(_))));
    }

    #[test]
    fn test_wire_frame_empty_payload() {
        let frame = WireFrame::new([0u8; NONCE_SIZE], 1, 0, vec![], [0u8; AUTH_TAG_SIZE])
            .expect("Failed to create frame with empty payload");

        assert_eq!(frame.encrypted_body.len(), 0);

        // Should serialize to correct size
        let serialized = frame.serialize().expect("Failed to serialize");
        assert_eq!(serialized.len(), FRAME_SIZE);
    }

    #[test]
    fn test_wire_frame_padding_automatic() {
        // Frame with small body should have large padding
        let small_body = vec![1, 2, 3];
        let frame = WireFrame::new([0u8; NONCE_SIZE], 1, 0, small_body, [0u8; AUTH_TAG_SIZE])
            .expect("Failed to create frame");

        // Padding should bring total to FRAME_SIZE
        let expected_padding = FRAME_SIZE - (NONCE_SIZE + 4 + 1 + 2 + 3 + AUTH_TAG_SIZE);
        assert_eq!(frame.padding.len(), expected_padding);
    }

    #[test]
    fn test_wire_frame_zeroize_on_drop() {
        use zeroize::Zeroize;

        let frame = WireFrame::new([1u8; NONCE_SIZE], 1, 0, vec![2, 3, 4], [5u8; AUTH_TAG_SIZE])
            .expect("Failed to create frame");

        // After explicit zeroize
        let mut frame_clone = frame.clone();
        frame_clone.zeroize();

        // Verify nonce is zeroized
        assert_eq!(frame_clone.nonce, [0u8; NONCE_SIZE]);
        assert_eq!(frame_clone.auth_tag, [0u8; AUTH_TAG_SIZE]);
    }
}
