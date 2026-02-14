//! # Aeternum Wire Protocol Layer
//!
//! This module implements the synchronization protocol for device-to-device communication.
//!
//! ## Architecture
//!
//! The sync layer provides:
//! - **Device Pairing** - Secure onboarding of new devices via QR code + BLE
//! - **Global Epoch Sync** - Coordinated PQRR protocol execution across devices
//! - **Veto Signaling** - 48h window recovery interception mechanism
//! - **Traffic Obfuscation** - Statistical indistinguishability via Padding and Chaff Sync
//!
//! ## Wire Frame Format
//!
//! All network messages are encapsulated in fixed-size **8192-byte** Aeternum-Frames:
//!
//! ```text
//! +----------------+------------------+------------------+-------------------+
//! | Nonce (24 B)   | Epoch ID (4 B)   | Payload Type     | Encrypted Body   |
//! | XChaCha20 rand | Current epoch    | (1 B)            | (Variable)       |
//! +----------------+------------------+------------------+-------------------+
//! | Padding (Variable)            | Auth Tag (16 B)                      |
//! | Random CSPRNG                 | Poly1305 MAC                         |
//! +------------------------------+--------------------------------------+
//! Total: 8192 bytes (fixed)
//! ```
//!
//! ## Security Guarantees
//!
//! - **Fixed-size frames** prevent traffic fingerprinting attacks
//! - **Hybrid handshake** (X25519 + Kyber-1024) provides post-quantum security
//! - **Nonce-based replay protection** prevents duplicate command execution
//! - **Veto supremacy** ensures Invariant #4 is enforced at protocol level
//!
//! ## Modules
//!
//! - `frame` - Aeternum-Frame encapsulation format
//! - `wire` - Wire protocol implementation
//! - `codec` - Message encoding/decoding
//! - `chaff` - Traffic obfuscation and chaff generation
//! - `handshake` - Hybrid encryption handshake protocol
//!
//! ## Protocol Versioning
//!
//! This implementation follows [AET-WIRE-SPEC-004](../../../docs/protocols/Sync-Wire-Protocol.md).

// Public module exports
pub mod chaff;
pub mod codec;
pub mod frame;
pub mod handshake;
pub mod version;
pub mod wire;

// Re-export common types
pub use chaff::{ChaffGenerator, ChaffSyncMessage, TimingMetadata, JITTER_MAX_MS, JITTER_MIN_MS};
pub use codec::{MessageCodec, PayloadType};
pub use frame::WireFrame;
pub use version::{
    CapabilityFlags, ProtocolVersion, VersionNegotiation, VersionNegotiationMessage,
};
pub use wire::{VetoMessage, WireProtocol, VETO_WINDOW_SECONDS};

/// Current Wire protocol version
pub const PROTOCOL_VERSION: (u8, u8) = (1, 0);

/// Fixed frame size in bytes (prevents traffic fingerprinting)
pub const FRAME_SIZE: usize = 8192;

/// Nonce size for XChaCha20-Poly1305
pub const NONCE_SIZE: usize = 24;

/// Auth tag size for Poly1305
pub const AUTH_TAG_SIZE: usize = 16;

/// Maximum encrypted body size (excluding headers, body_len, padding, auth tag)
pub const MAX_BODY_SIZE: usize = FRAME_SIZE - NONCE_SIZE - 4 - 1 - 2 - AUTH_TAG_SIZE;

/// Protocol-level error type
#[derive(Debug, thiserror::Error)]
pub enum WireError {
    /// Invalid frame size (must be exactly FRAME_SIZE)
    #[error("Invalid frame size: expected {FRAME_SIZE}, got {0}")]
    InvalidFrameSize(usize),

    /// Authentication tag verification failed
    #[error("Authentication tag verification failed")]
    AuthenticationFailed,

    /// Invalid payload type
    #[error("Invalid payload type: {0}")]
    InvalidPayloadType(u8),

    /// Frame deserialization failed
    #[error("Frame deserialization failed: {0}")]
    DeserializationFailed(String),

    /// Replay attack detected (duplicate nonce)
    #[error("Replay attack detected: nonce {0:?} already used")]
    ReplayAttack([u8; NONCE_SIZE]),

    /// Epoch mismatch (Invariant #1 violation)
    #[error("Epoch regression: current {current}, attempted {attempted}")]
    EpochRegression {
        /// Current epoch value
        current: u32,
        /// Attempted epoch value (must be > current)
        attempted: u32,
    },

    /// Version negotiation failed
    #[error("Version negotiation failed: client {client:?}, server {server:?}")]
    VersionNegotiationFailed {
        /// Client protocol version
        client: (u8, u8),
        /// Server protocol version
        server: (u8, u8),
    },

    /// I/O error during frame processing
    #[error("I/O error: {0}")]
    Io(#[from] std::io::Error),

    /// Cryptographic error during AEAD encryption/decryption
    #[error("Crypto error: {0}")]
    Crypto(#[from] crate::crypto::error::CryptoError),
}

/// Result type for Wire protocol operations
pub type Result<T> = std::result::Result<T, WireError>;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_frame_size_constants() {
        // Verify frame layout calculations
        assert_eq!(FRAME_SIZE, 8192);
        assert_eq!(NONCE_SIZE, 24);
        assert_eq!(AUTH_TAG_SIZE, 16);

        // Verify max body size calculation
        // FRAME_SIZE - NONCE - Epoch - Type - BodyLen - AuthTag
        // 8192 - 24 - 4 - 1 - 2 - 16 = 8145
        assert_eq!(MAX_BODY_SIZE, 8145);
    }

    #[test]
    fn test_protocol_version() {
        assert_eq!(PROTOCOL_VERSION, (1, 0));
    }

    #[test]
    fn test_wire_error_display() {
        let err = WireError::InvalidFrameSize(100);
        assert!(err.to_string().contains("8192"));
        assert!(err.to_string().contains("100"));

        let err = WireError::EpochRegression {
            current: 5,
            attempted: 3,
        };
        assert!(err.to_string().contains("regression"));
        assert!(err.to_string().contains("5"));
        assert!(err.to_string().contains("3"));
    }
}
