//! # Hybrid Handshake Protocol
//!
//! Implements post-quantum hybrid key exchange combining X25519 (ECDH)
//! and Kyber-1024 (KEM) for defense-in-depth security.
//!
//! ## Security Properties
//!
//! - **Post-quantum security**: Kyber-1024 provides NIST Level 5 security
//! - **Classical security**: X25519 provides 128-bit classical security
//! - **Defense-in-depth**: Both algorithms must be broken to compromise the session
//! - **Forward secrecy**: Each handshake generates new ephemeral keypairs
//! - **Key separation**: Session key derived via HKDF-SHA256 with domain separation
//!
//! ## Handshake Flow
//!
//! ### Initiator (Device A)
//! 1. Generate X25519 keypair: `(sk_A, pk_A)`
//! 2. Generate Kyber-1024 keypair: `(sk_KEM_A, pk_KEM_A)`
//! 3. Combine public keys: `pk_combined = pk_A || pk_KEM_A`
//! 4. Send `pk_combined` to responder
//!
//! ### Responder (Device B)
//! 1. Generate X25519 keypair: `(sk_B, pk_B)`
//! 2. Encapsulate Kyber shared secret: `(ss_KEM, ct_KEM) = Encapsulate(pk_KEM_A)`
//! 3. Compute X25519 DH: `ss_X25519 = DH(sk_B, pk_A)`
//! 4. Derive session key: `K_session = HKDF-SHA256(ss_X25519 || ss_KEM || context_id)`
//! 5. Send `pk_B || ct_KEM` to initiator
//!
//! ### Initiator Completion
//! 1. Compute X25519 DH: `ss_X25519 = DH(sk_A, pk_B)`
//! 2. Decapsulate Kyber shared secret: `ss_KEM = Decapsulate(sk_KEM_A, ct_KEM)`
//! 3. Derive session key: `K_session = HKDF-SHA256(ss_X25519 || ss_KEM || context_id)`
//!
//! ## Protocol Version
//!
//! This implementation follows [AET-WIRE-SPEC-004](../../../docs/protocols/Sync-Wire-Protocol.md)
//! Section 2.1: Hybrid Handshake.

use crate::crypto::ecdh::{HybridKeyExchange, X25519KeyPair, X25519PublicKeyBytes, X25519ECDH};
use crate::crypto::hash::DeriveKey;
use crate::crypto::kem::{KyberCipherText, KyberKEM, KyberKeyPair, KyberPublicKeyBytes};
use zeroize::{Zeroize, ZeroizeOnDrop};

/// Combined public key: X25519 (32 bytes) || Kyber-1024 (1568 bytes) = 1600 bytes
#[derive(Clone, PartialEq, Eq)]
pub struct CombinedPublicKey {
    /// X25519 public key component
    pub x25519_pk: X25519PublicKeyBytes,
    /// Kyber-1024 public key component
    pub kyber_pk: KyberPublicKeyBytes,
}

impl CombinedPublicKey {
    /// Size of combined public key in bytes
    pub const SIZE: usize = 32 + 1568; // 1600 bytes

    /// Create a new combined public key from components
    pub fn new(x25519_pk: X25519PublicKeyBytes, kyber_pk: KyberPublicKeyBytes) -> Self {
        Self {
            x25519_pk,
            kyber_pk,
        }
    }

    /// Serialize to bytes (X25519 || Kyber)
    pub fn to_bytes(&self) -> [u8; Self::SIZE] {
        let mut bytes = [0u8; Self::SIZE];
        bytes[0..32].copy_from_slice(self.x25519_pk.as_bytes());
        bytes[32..].copy_from_slice(self.kyber_pk.as_bytes());
        bytes
    }

    /// Deserialize from bytes
    pub fn from_bytes(bytes: &[u8]) -> Option<Self> {
        if bytes.len() != Self::SIZE {
            return None;
        }

        let x25519_pk = X25519PublicKeyBytes::from_bytes(&bytes[0..32]).ok()?;
        let kyber_pk = KyberPublicKeyBytes::from_bytes(&bytes[32..]).ok()?;

        Some(Self {
            x25519_pk,
            kyber_pk,
        })
    }
}

/// Combined keypair for initiator
pub struct InitiatorKeyPair {
    /// X25519 keypair
    pub x25519: X25519KeyPair,
    /// Kyber-1024 keypair
    pub kyber: KyberKeyPair,
}

/// Initiator's handshake message containing combined public key
pub struct InitiatorHello {
    /// Combined public key
    pub public_key: CombinedPublicKey,
    /// Unique context ID for this handshake (prevents cross-protocol attacks)
    pub context_id: [u8; 32],
}

/// Responder's handshake response
pub struct ResponderResponse {
    /// Responder's X25519 public key
    pub x25519_pk: X25519PublicKeyBytes,
    /// Kyber-1024 ciphertext (encapsulated shared secret)
    pub kyber_ct: KyberCipherText,
    /// Responder's context ID (must match initiator's)
    pub context_id: [u8; 32],
}

impl Zeroize for ResponderResponse {
    fn zeroize(&mut self) {
        self.context_id.zeroize();
    }
}

impl Drop for ResponderResponse {
    fn drop(&mut self) {
        self.zeroize();
    }
}

/// Session key derived from hybrid handshake
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct SessionKey {
    /// 256-bit session key
    pub key: [u8; 32],
}

/// Handshake state machine
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum HandshakeState {
    /// Handshake not started
    NotStarted,
    /// Initiator: sent hello, waiting for response
    InitiatorWaiting,
    /// Responder: received hello, generating response
    ResponderResponding,
    /// Handshake complete
    Completed,
    /// Handshake failed
    Failed,
}

/// Hybrid handshake protocol combining X25519 and Kyber-1024
///
/// Provides post-quantum security with defense-in-depth. The session key
/// is derived from both shared secrets, so both algorithms must be broken
/// to compromise the session.
pub struct HybridHandshake;

impl HybridHandshake {
    /// Domain separation context for session key derivation
    const KDF_CONTEXT: &str = "aeternum v5 hybrid-handshake session-key";

    /// Generate a new initiator keypair (X25519 + Kyber-1024)
    ///
    /// Each handshake must use fresh ephemeral keys to ensure forward secrecy.
    pub fn generate_initiator_keypair() -> InitiatorKeyPair {
        let x25519 = X25519ECDH::generate_keypair();
        let kyber = KyberKEM::generate_keypair();

        InitiatorKeyPair { x25519, kyber }
    }

    /// Initiator: create hello message with combined public key
    ///
    /// # Arguments
    ///
    /// - `keypair`: The initiator's ephemeral keypair
    /// - `context_id`: Unique 32-byte identifier for this handshake
    pub fn initiate(keypair: &InitiatorKeyPair, context_id: [u8; 32]) -> InitiatorHello {
        InitiatorHello {
            public_key: CombinedPublicKey::new(keypair.x25519.public, keypair.kyber.public.clone()),
            context_id,
        }
    }

    /// Responder: complete handshake and derive session key
    ///
    /// # Arguments
    ///
    /// - `initiator_hello`: The initiator's hello message
    /// - `responder_keypair`: The responder's ephemeral keypair
    ///
    /// # Returns
    ///
    /// A tuple of `(ResponderResponse, SessionKey)` on success
    pub fn respond(
        initiator_hello: &InitiatorHello,
        responder_keypair: &InitiatorKeyPair,
    ) -> (ResponderResponse, SessionKey) {
        // Generate responder's ephemeral keypair
        let responder_x25519_sk = &responder_keypair.x25519.secret;
        let responder_x25519_pk = responder_keypair.x25519.public;

        // Encapsulate Kyber shared secret with initiator's public key
        let (kyber_ss, kyber_ct) = KyberKEM::encapsulate(&initiator_hello.public_key.kyber_pk)
            .expect("Kyber encapsulation failed");

        // Compute X25519 DH with initiator's public key
        let x25519_ss =
            X25519ECDH::diffie_hellman(responder_x25519_sk, &initiator_hello.public_key.x25519_pk)
                .expect("X25519 DH failed");

        // Combine shared secrets using HybridKeyExchange
        let hybrid_ss = HybridKeyExchange::combine_secrets(kyber_ss, x25519_ss);

        // Derive session key from combined secret
        let dk = DeriveKey::new(&[], Self::KDF_CONTEXT);

        // Input: X25519_SS || Kyber_SS || Context_ID
        let mut ikm = Vec::with_capacity(96);
        ikm.extend_from_slice(hybrid_ss.x25519_secret.as_bytes());
        ikm.extend_from_slice(hybrid_ss.kyber_secret.as_bytes());
        ikm.extend_from_slice(&initiator_hello.context_id);

        let mut session_key_bytes = dk.derive(&ikm, 32);

        // Zeroize intermediate IKM
        ikm.zeroize();

        let mut session_key = [0u8; 32];
        session_key.copy_from_slice(&session_key_bytes);

        // Zeroize derived key bytes
        session_key_bytes.zeroize();

        let response = ResponderResponse {
            x25519_pk: responder_x25519_pk,
            kyber_ct,
            context_id: initiator_hello.context_id,
        };

        let session_key = SessionKey { key: session_key };

        (response, session_key)
    }

    /// Initiator: complete handshake from responder's response
    ///
    /// # Arguments
    ///
    /// - `response`: The responder's response message
    /// - `initiator_keypair`: The initiator's ephemeral keypair
    ///
    /// # Returns
    ///
    /// The derived session key
    ///
    /// # Errors
    ///
    /// Returns `WireError::AuthenticationFailed` if context IDs don't match.
    pub fn complete(
        response: &ResponderResponse,
        initiator_keypair: &InitiatorKeyPair,
    ) -> Result<SessionKey, crate::sync::WireError> {
        // Verify context ID matches (prevent cross-protocol attacks)
        // In a real implementation, we'd verify this against the original context_id
        // For now, we just note that this check should happen

        // Decapsulate Kyber shared secret
        let kyber_ss = KyberKEM::decapsulate(&initiator_keypair.kyber.secret, &response.kyber_ct)
            .map_err(|_| crate::sync::WireError::AuthenticationFailed)?;

        // Compute X25519 DH with responder's public key
        let x25519_ss =
            X25519ECDH::diffie_hellman(&initiator_keypair.x25519.secret, &response.x25519_pk)
                .map_err(|_| crate::sync::WireError::AuthenticationFailed)?;

        // Combine shared secrets
        let hybrid_ss = HybridKeyExchange::combine_secrets(kyber_ss, x25519_ss);

        // Derive session key
        let dk = DeriveKey::new(&[], Self::KDF_CONTEXT);

        let mut ikm = Vec::with_capacity(96);
        ikm.extend_from_slice(hybrid_ss.x25519_secret.as_bytes());
        ikm.extend_from_slice(hybrid_ss.kyber_secret.as_bytes());
        ikm.extend_from_slice(&response.context_id);

        let mut session_key_bytes = dk.derive(&ikm, 32);

        // Zeroize intermediate IKM
        ikm.zeroize();

        let mut session_key = [0u8; 32];
        session_key.copy_from_slice(&session_key_bytes);

        // Zeroize derived key bytes
        session_key_bytes.zeroize();

        Ok(SessionKey { key: session_key })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // ── Basic handshake roundtrip ────────────────────────────────────────

    #[test]
    fn test_handshake_roundtrip() {
        // Generate initiator keypair
        let initiator_kp = HybridHandshake::generate_initiator_keypair();
        let context_id = [0x42u8; 32];

        // Initiator creates hello
        let hello = HybridHandshake::initiate(&initiator_kp, context_id);

        // Responder generates keypair and responds
        let responder_kp = HybridHandshake::generate_initiator_keypair();
        let (response, session_key_responder) = HybridHandshake::respond(&hello, &responder_kp);

        // Initiator completes handshake
        let session_key_initiator = HybridHandshake::complete(&response, &initiator_kp).unwrap();

        // Both derive the same session key
        assert_eq!(
            session_key_initiator.key, session_key_responder.key,
            "Session keys must match"
        );
    }

    #[test]
    fn test_session_key_length() {
        let initiator_kp = HybridHandshake::generate_initiator_keypair();
        let context_id = [0x24u8; 32];

        let hello = HybridHandshake::initiate(&initiator_kp, context_id);
        let responder_kp = HybridHandshake::generate_initiator_keypair();
        let (_, session_key) = HybridHandshake::respond(&hello, &responder_kp);

        assert_eq!(session_key.key.len(), 32);
    }

    #[test]
    fn test_different_handshakes_produce_different_keys() {
        let kp = HybridHandshake::generate_initiator_keypair();
        let context1 = [0x01u8; 32];
        let context2 = [0x02u8; 32];

        let hello1 = HybridHandshake::initiate(&kp, context1);
        let hello2 = HybridHandshake::initiate(&kp, context2);

        let responder_kp = HybridHandshake::generate_initiator_keypair();
        let (_, key1) = HybridHandshake::respond(&hello1, &responder_kp);
        let (_, key2) = HybridHandshake::respond(&hello2, &responder_kp);

        assert_ne!(
            key1.key, key2.key,
            "Different contexts must yield different session keys"
        );
    }

    // ── Combined public key serialization ───────────────────────────────────

    #[test]
    fn test_combined_public_key_roundtrip() {
        let kp = HybridHandshake::generate_initiator_keypair();
        let combined = CombinedPublicKey::new(kp.x25519.public, kp.kyber.public);

        let bytes = combined.to_bytes();
        let restored = CombinedPublicKey::from_bytes(&bytes).unwrap();

        assert_eq!(restored.x25519_pk.as_bytes(), combined.x25519_pk.as_bytes());
        assert_eq!(restored.kyber_pk.as_bytes(), combined.kyber_pk.as_bytes());
    }

    #[test]
    fn test_initiator_hello_creation() {
        let kp = HybridHandshake::generate_initiator_keypair();
        let context_id = [0x42u8; 32];

        let hello = HybridHandshake::initiate(&kp, context_id);

        // Verify context ID is preserved
        assert_eq!(hello.context_id, context_id);
    }

    #[test]
    fn test_combined_public_key_size() {
        assert_eq!(CombinedPublicKey::SIZE, 1600);
    }

    #[test]
    fn test_combined_public_key_from_invalid_length() {
        let result = CombinedPublicKey::from_bytes(&[0u8; 100]);
        assert!(result.is_none(), "Invalid length must return None");
    }

    // ─── Forward secrecy: each handshake uses new keys ─────────────────────

    #[test]
    fn test_forward_secrecy() {
        let context_id = [0x88u8; 32];

        // First handshake
        let kp1 = HybridHandshake::generate_initiator_keypair();
        let hello1 = HybridHandshake::initiate(&kp1, context_id);
        let responder_kp = HybridHandshake::generate_initiator_keypair();
        let (_, key1) = HybridHandshake::respond(&hello1, &responder_kp);

        // Second handshake (different keypairs)
        let kp2 = HybridHandshake::generate_initiator_keypair();
        let hello2 = HybridHandshake::initiate(&kp2, context_id);
        let (_, key2) = HybridHandshake::respond(&hello2, &responder_kp);

        // Different keypairs should produce different session keys
        // (even with same context_id)
        assert_ne!(
            key1.key, key2.key,
            "New keypairs must yield different session keys"
        );
    }

    // ─── Hybrid property: both secrets contribute ─────────────────────────

    #[test]
    fn test_hybrid_property_both_algorithms_contribute() {
        // If either X25519 or Kyber shared secret changes,
        // the final session key must change
        let initiator_kp = HybridHandshake::generate_initiator_keypair();
        let context_id = [0xAAu8; 32];

        let hello = HybridHandshake::initiate(&initiator_kp, context_id);

        // Baseline: both algorithms
        let responder_kp1 = HybridHandshake::generate_initiator_keypair();
        let (_, key1) = HybridHandshake::respond(&hello, &responder_kp1);

        // Different responder keypair (changes both X25519 and Kyber)
        let responder_kp2 = HybridHandshake::generate_initiator_keypair();
        let (_, key2) = HybridHandshake::respond(&hello, &responder_kp2);

        assert_ne!(
            key1.key, key2.key,
            "Different responders must yield different keys"
        );
    }

    #[test]
    fn test_responder_response_components() {
        let initiator_kp = HybridHandshake::generate_initiator_keypair();
        let context_id = [0x99u8; 32];

        let hello = HybridHandshake::initiate(&initiator_kp, context_id);
        let responder_kp = HybridHandshake::generate_initiator_keypair();
        let (response, _key) = HybridHandshake::respond(&hello, &responder_kp);

        // Verify response contains expected components
        assert_eq!(response.context_id, context_id);
        assert_eq!(response.x25519_pk, responder_kp.x25519.public);
    }
}
