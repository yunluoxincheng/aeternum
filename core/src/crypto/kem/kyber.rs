//! # Kyber-1024 KEM Implementation
//!
//! Provides post-quantum key encapsulation using Kyber-1024 (ML-KEM)
//! via the PQClean reference implementation.
//!
//! ## Security Properties
//!
//! - **NIST Level 5** security (equivalent to AES-256)
//! - 1568-byte public key, 3168-byte secret key
//! - 1568-byte ciphertext, 32-byte shared secret
//! - IND-CCA2 secure key encapsulation
//! - All secret keys implement `Zeroize` for automatic memory cleanup
//!
//! ## Usage
//!
//! ```
//! use aeternum_core::crypto::kem::{KyberKEM, KyberKeyPair};
//!
//! // Generate a keypair
//! let keypair = KyberKEM::generate_keypair();
//!
//! // Sender encapsulates a shared secret
//! let (shared_secret, ciphertext) = KyberKEM::encapsulate(&keypair.public).unwrap();
//!
//! // Recipient decapsulates
//! let recovered = KyberKEM::decapsulate(&keypair.secret, &ciphertext).unwrap();
//! assert_eq!(shared_secret.as_bytes(), recovered.as_bytes());
//! ```

use super::{
    KyberCipherText, KyberKEM, KyberKeyPair, KyberPublicKeyBytes, KyberSecretKeyBytes,
    KyberSharedSecret,
};
use crate::crypto::error::{CryptoError, Result};
use pqcrypto_kyber::kyber1024;
use pqcrypto_traits::kem::{
    Ciphertext as CiphertextTrait, PublicKey as PublicKeyTrait, SecretKey as SecretKeyTrait,
    SharedSecret as SharedSecretTrait,
};

/// Kyber-1024 public key size in bytes
pub const PUBLIC_KEY_SIZE: usize = 1568;

/// Kyber-1024 secret key size in bytes
pub const SECRET_KEY_SIZE: usize = 3168;

/// Kyber-1024 ciphertext size in bytes
pub const CIPHERTEXT_SIZE: usize = 1568;

/// Kyber-1024 shared secret size in bytes
pub const SHARED_SECRET_SIZE: usize = 32;

impl KyberKEM {
    /// Generate a new Kyber-1024 keypair using the system CSPRNG.
    ///
    /// This is the recommended way to create Kyber keypairs. The underlying
    /// implementation uses PQClean's reference C code compiled from source.
    ///
    /// # Returns
    ///
    /// A `KyberKeyPair` containing the public and secret keys.
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::crypto::kem::KyberKEM;
    ///
    /// let keypair = KyberKEM::generate_keypair();
    /// ```
    pub fn generate_keypair() -> KyberKeyPair {
        let (pk, sk) = kyber1024::keypair();

        let pk_bytes = PublicKeyTrait::as_bytes(&pk);
        let sk_bytes = SecretKeyTrait::as_bytes(&sk);

        let mut pub_arr = [0u8; 1568];
        pub_arr.copy_from_slice(pk_bytes);

        let mut sec_arr = [0u8; 3168];
        sec_arr.copy_from_slice(sk_bytes);

        KyberKeyPair {
            public: KyberPublicKeyBytes(pub_arr),
            secret: KyberSecretKeyBytes(sec_arr),
        }
    }

    /// Encapsulate a shared secret using the recipient's public key.
    ///
    /// The sender calls this function with the recipient's public key to
    /// produce a shared secret and a ciphertext. The ciphertext is sent
    /// to the recipient who can recover the shared secret using their
    /// secret key.
    ///
    /// # Arguments
    ///
    /// - `public_key`: The recipient's Kyber-1024 public key
    ///
    /// # Returns
    ///
    /// A tuple of `(KyberSharedSecret, KyberCipherText)` on success.
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::KemError` if encapsulation fails.
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::crypto::kem::KyberKEM;
    ///
    /// let keypair = KyberKEM::generate_keypair();
    /// let (shared_secret, ciphertext) = KyberKEM::encapsulate(&keypair.public).unwrap();
    /// ```
    pub fn encapsulate(
        public_key: &KyberPublicKeyBytes,
    ) -> Result<(KyberSharedSecret, KyberCipherText)> {
        let pk = PublicKeyTrait::from_bytes(&public_key.0).map_err(|e| {
            CryptoError::kem(format!("Invalid public key for encapsulation: {}", e))
        })?;

        let (ss, ct) = kyber1024::encapsulate(&pk);

        let ss_bytes = SharedSecretTrait::as_bytes(&ss);
        let ct_bytes = CiphertextTrait::as_bytes(&ct);

        let mut secret_arr = [0u8; 32];
        secret_arr.copy_from_slice(ss_bytes);

        let mut ct_arr = [0u8; 1568];
        ct_arr.copy_from_slice(ct_bytes);

        Ok((KyberSharedSecret(secret_arr), KyberCipherText(ct_arr)))
    }

    /// Decapsulate a shared secret from a ciphertext using the secret key.
    ///
    /// The recipient calls this function with their secret key and the
    /// ciphertext received from the sender to recover the shared secret.
    ///
    /// # Arguments
    ///
    /// - `secret_key`: The recipient's Kyber-1024 secret key
    /// - `ciphertext`: The ciphertext produced by `encapsulate()`
    ///
    /// # Returns
    ///
    /// The recovered `KyberSharedSecret` on success.
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::KemError` if decapsulation fails.
    ///
    /// # Security Note
    ///
    /// Kyber's IND-CCA2 security means that a modified ciphertext will
    /// produce an implicit rejection (a random-looking shared secret that
    /// does not match the sender's). The caller should use the shared
    /// secret in a key-confirmation step to detect tampering.
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::crypto::kem::KyberKEM;
    ///
    /// let keypair = KyberKEM::generate_keypair();
    /// let (ss1, ct) = KyberKEM::encapsulate(&keypair.public).unwrap();
    /// let ss2 = KyberKEM::decapsulate(&keypair.secret, &ct).unwrap();
    /// assert_eq!(ss1.as_bytes(), ss2.as_bytes());
    /// ```
    pub fn decapsulate(
        secret_key: &KyberSecretKeyBytes,
        ciphertext: &KyberCipherText,
    ) -> Result<KyberSharedSecret> {
        let sk = SecretKeyTrait::from_bytes(&secret_key.0).map_err(|e| {
            CryptoError::kem(format!("Invalid secret key for decapsulation: {}", e))
        })?;

        let ct = CiphertextTrait::from_bytes(&ciphertext.0).map_err(|e| {
            CryptoError::kem(format!("Invalid ciphertext for decapsulation: {}", e))
        })?;

        let ss = kyber1024::decapsulate(&ct, &sk);

        let ss_bytes = SharedSecretTrait::as_bytes(&ss);
        let mut secret_arr = [0u8; 32];
        secret_arr.copy_from_slice(ss_bytes);

        Ok(KyberSharedSecret(secret_arr))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // ── Basic functionality tests ────────────────────────────────────

    #[test]
    fn test_keypair_generation() {
        let kp = KyberKEM::generate_keypair();
        assert_eq!(kp.public.as_bytes().len(), 1568);
        assert_eq!(kp.secret.as_bytes().len(), 3168);
    }

    #[test]
    fn test_keypair_uniqueness() {
        let kp1 = KyberKEM::generate_keypair();
        let kp2 = KyberKEM::generate_keypair();
        assert_ne!(
            kp1.public.as_bytes(),
            kp2.public.as_bytes(),
            "Two keypairs should have different public keys"
        );
    }

    #[test]
    fn test_encapsulate_decapsulate_roundtrip() {
        let kp = KyberKEM::generate_keypair();
        let (ss_sender, ct) = KyberKEM::encapsulate(&kp.public).unwrap();
        let ss_recipient = KyberKEM::decapsulate(&kp.secret, &ct).unwrap();
        assert_eq!(
            ss_sender.as_bytes(),
            ss_recipient.as_bytes(),
            "Shared secrets must match after encapsulate/decapsulate roundtrip"
        );
    }

    #[test]
    fn test_shared_secret_size() {
        let kp = KyberKEM::generate_keypair();
        let (ss, _ct) = KyberKEM::encapsulate(&kp.public).unwrap();
        assert_eq!(ss.as_bytes().len(), 32);
    }

    #[test]
    fn test_ciphertext_size() {
        let kp = KyberKEM::generate_keypair();
        let (_ss, ct) = KyberKEM::encapsulate(&kp.public).unwrap();
        assert_eq!(ct.as_bytes().len(), 1568);
    }

    // ── Different keypair isolation ──────────────────────────────────

    #[test]
    fn test_wrong_secret_key_produces_different_secret() {
        let kp1 = KyberKEM::generate_keypair();
        let kp2 = KyberKEM::generate_keypair();

        let (ss_sender, ct) = KyberKEM::encapsulate(&kp1.public).unwrap();
        // Decapsulate with the wrong secret key — Kyber's implicit rejection
        // will produce a valid but different shared secret
        let ss_wrong = KyberKEM::decapsulate(&kp2.secret, &ct).unwrap();

        assert_ne!(
            ss_sender.as_bytes(),
            ss_wrong.as_bytes(),
            "Decapsulating with wrong secret key must yield different shared secret"
        );
    }

    #[test]
    fn test_different_encapsulations_yield_different_secrets() {
        let kp = KyberKEM::generate_keypair();
        let (ss1, _ct1) = KyberKEM::encapsulate(&kp.public).unwrap();
        let (ss2, _ct2) = KyberKEM::encapsulate(&kp.public).unwrap();
        assert_ne!(
            ss1.as_bytes(),
            ss2.as_bytes(),
            "Each encapsulation should produce a unique shared secret"
        );
    }

    // ── Tampered ciphertext (implicit rejection) ─────────────────────

    #[test]
    fn test_tampered_ciphertext_implicit_rejection() {
        let kp = KyberKEM::generate_keypair();
        let (ss_original, ct) = KyberKEM::encapsulate(&kp.public).unwrap();

        // Flip a byte in the ciphertext
        let mut tampered_bytes = *ct.as_bytes();
        tampered_bytes[0] ^= 0xFF;
        let tampered_ct = KyberCipherText(tampered_bytes);

        // Kyber uses implicit rejection: decapsulation succeeds but
        // produces a pseudorandom shared secret that doesn't match
        let ss_tampered = KyberKEM::decapsulate(&kp.secret, &tampered_ct).unwrap();
        assert_ne!(
            ss_original.as_bytes(),
            ss_tampered.as_bytes(),
            "Tampered ciphertext should yield different shared secret (implicit rejection)"
        );
    }

    #[test]
    fn test_tampered_ciphertext_multiple_positions() {
        let kp = KyberKEM::generate_keypair();
        let (ss_original, ct) = KyberKEM::encapsulate(&kp.public).unwrap();

        // Test tampering at various positions
        for &pos in &[0, 100, 500, 1000, 1567] {
            let mut tampered_bytes = *ct.as_bytes();
            tampered_bytes[pos] ^= 0x01;
            let tampered_ct = KyberCipherText(tampered_bytes);

            let ss_tampered = KyberKEM::decapsulate(&kp.secret, &tampered_ct).unwrap();
            assert_ne!(
                ss_original.as_bytes(),
                ss_tampered.as_bytes(),
                "Tampered ciphertext at position {} should yield different shared secret",
                pos
            );
        }
    }

    // ── Type construction from bytes ─────────────────────────────────

    #[test]
    fn test_public_key_from_bytes_roundtrip() {
        let kp = KyberKEM::generate_keypair();
        let pk_bytes = kp.public.as_bytes();
        let pk_restored = KyberPublicKeyBytes::from_bytes(pk_bytes).unwrap();
        assert_eq!(pk_restored.as_bytes(), pk_bytes);
    }

    #[test]
    fn test_secret_key_from_bytes_roundtrip() {
        let kp = KyberKEM::generate_keypair();
        let sk_bytes = kp.secret.as_bytes();
        let sk_restored = KyberSecretKeyBytes::from_bytes(sk_bytes).unwrap();
        assert_eq!(sk_restored.as_bytes(), sk_bytes);
    }

    #[test]
    fn test_ciphertext_from_bytes_roundtrip() {
        let kp = KyberKEM::generate_keypair();
        let (_ss, ct) = KyberKEM::encapsulate(&kp.public).unwrap();
        let ct_bytes = ct.as_bytes();
        let ct_restored = KyberCipherText::from_bytes(ct_bytes).unwrap();
        assert_eq!(ct_restored.as_bytes(), ct_bytes);
    }

    #[test]
    fn test_shared_secret_from_bytes_roundtrip() {
        let kp = KyberKEM::generate_keypair();
        let (ss, _ct) = KyberKEM::encapsulate(&kp.public).unwrap();
        let ss_bytes = ss.as_bytes();
        let ss_restored = KyberSharedSecret::from_bytes(ss_bytes).unwrap();
        assert_eq!(ss_restored.as_bytes(), ss_bytes);
    }

    // ── Serialization roundtrip: generate → serialize → deserialize → use ──

    #[test]
    fn test_full_serialization_roundtrip() {
        // Generate original keypair
        let kp = KyberKEM::generate_keypair();

        // Serialize and deserialize public key
        let pk_bytes = kp.public.as_bytes().to_vec();
        let pk_restored = KyberPublicKeyBytes::from_bytes(&pk_bytes).unwrap();

        // Serialize and deserialize secret key
        let sk_bytes = kp.secret.as_bytes().to_vec();
        let sk_restored = KyberSecretKeyBytes::from_bytes(&sk_bytes).unwrap();

        // Use restored keys for encapsulation/decapsulation
        let (ss1, ct) = KyberKEM::encapsulate(&pk_restored).unwrap();
        let ss2 = KyberKEM::decapsulate(&sk_restored, &ct).unwrap();
        assert_eq!(ss1.as_bytes(), ss2.as_bytes());
    }

    // ── Multiple rounds test ─────────────────────────────────────────

    #[test]
    fn test_multiple_encapsulations_same_keypair() {
        let kp = KyberKEM::generate_keypair();
        for _ in 0..10 {
            let (ss_sender, ct) = KyberKEM::encapsulate(&kp.public).unwrap();
            let ss_recipient = KyberKEM::decapsulate(&kp.secret, &ct).unwrap();
            assert_eq!(ss_sender.as_bytes(), ss_recipient.as_bytes());
        }
    }

    // ── KAT-style deterministic validation ───────────────────────────
    // Note: pqcrypto-kyber uses internal CSPRNG, so we validate structural
    // properties and consistency rather than exact KAT vectors.

    #[test]
    fn test_kat_structural_properties() {
        // Verify the key sizes match NIST FIPS 203 ML-KEM-1024 parameters
        assert_eq!(kyber1024::public_key_bytes(), 1568);
        assert_eq!(kyber1024::secret_key_bytes(), 3168);
        assert_eq!(kyber1024::ciphertext_bytes(), 1568);
        assert_eq!(kyber1024::shared_secret_bytes(), 32);
    }

    #[test]
    fn test_kat_encapsulate_decapsulate_consistency() {
        // Run 20 rounds to provide statistical confidence
        for i in 0..20 {
            let kp = KyberKEM::generate_keypair();
            let (ss1, ct) = KyberKEM::encapsulate(&kp.public).unwrap();
            let ss2 = KyberKEM::decapsulate(&kp.secret, &ct).unwrap();
            assert_eq!(
                ss1.as_bytes(),
                ss2.as_bytes(),
                "KAT consistency failed at round {}",
                i
            );
        }
    }
}

// ── Property-based tests (proptest) ──────────────────────────────

#[cfg(test)]
mod proptest_tests {
    use super::*;
    use proptest::prelude::*;

    proptest! {
        /// Encapsulate-decapsulate roundtrip must always succeed with matching secrets
        #[test]
        fn prop_encapsulate_decapsulate_roundtrip(_seed in 0u64..1000) {
            let kp = KyberKEM::generate_keypair();
            let (ss_sender, ct) = KyberKEM::encapsulate(&kp.public).unwrap();
            let ss_recipient = KyberKEM::decapsulate(&kp.secret, &ct).unwrap();
            prop_assert_eq!(ss_sender.as_bytes(), ss_recipient.as_bytes());
        }

        /// Shared secret is always exactly 32 bytes
        #[test]
        fn prop_shared_secret_length(_seed in 0u64..100) {
            let kp = KyberKEM::generate_keypair();
            let (ss, _ct) = KyberKEM::encapsulate(&kp.public).unwrap();
            prop_assert_eq!(ss.as_bytes().len(), 32);
        }

        /// Public key is always exactly 1568 bytes
        #[test]
        fn prop_public_key_length(_seed in 0u64..100) {
            let kp = KyberKEM::generate_keypair();
            prop_assert_eq!(kp.public.as_bytes().len(), 1568);
        }

        /// Secret key is always exactly 3168 bytes
        #[test]
        fn prop_secret_key_length(_seed in 0u64..100) {
            let kp = KyberKEM::generate_keypair();
            prop_assert_eq!(kp.secret.as_bytes().len(), 3168);
        }

        /// Ciphertext is always exactly 1568 bytes
        #[test]
        fn prop_ciphertext_length(_seed in 0u64..100) {
            let kp = KyberKEM::generate_keypair();
            let (_ss, ct) = KyberKEM::encapsulate(&kp.public).unwrap();
            prop_assert_eq!(ct.as_bytes().len(), 1568);
        }

        /// Tampered ciphertext must produce a different shared secret (implicit rejection)
        #[test]
        fn prop_tampered_ciphertext_rejection(flip_pos in 0usize..1568) {
            let kp = KyberKEM::generate_keypair();
            let (ss_original, ct) = KyberKEM::encapsulate(&kp.public).unwrap();

            let mut tampered_bytes = *ct.as_bytes();
            tampered_bytes[flip_pos] ^= 0x01;
            let tampered_ct = KyberCipherText(tampered_bytes);

            let ss_tampered = KyberKEM::decapsulate(&kp.secret, &tampered_ct).unwrap();
            prop_assert_ne!(
                ss_original.as_bytes(),
                ss_tampered.as_bytes(),
                "Tampered ciphertext at position {} should yield different shared secret",
                flip_pos
            );
        }

        /// Wrong secret key must produce a different shared secret
        #[test]
        fn prop_wrong_key_rejection(_seed in 0u64..100) {
            let kp1 = KyberKEM::generate_keypair();
            let kp2 = KyberKEM::generate_keypair();

            let (ss_sender, ct) = KyberKEM::encapsulate(&kp1.public).unwrap();
            let ss_wrong = KyberKEM::decapsulate(&kp2.secret, &ct).unwrap();
            prop_assert_ne!(ss_sender.as_bytes(), ss_wrong.as_bytes());
        }
    }
}
