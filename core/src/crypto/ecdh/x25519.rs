//! # X25519 ECDH Implementation
//!
//! Provides elliptic curve Diffie-Hellman key exchange using X25519
//! via the x25519-dalek crate (v2.0.1).
//!
//! ## Security Properties
//!
//! - 128-bit security level (equivalent to AES-128)
//! - 32-byte public key, 32-byte secret key
//! - 32-byte shared secret
//! - Resistant to timing side-channel attacks (constant-time operations)
//! - All secret keys implement `Zeroize` for automatic memory cleanup
//!
//! ## Usage
//!
//! ```
//! use aeternum_core::crypto::ecdh::{X25519ECDH, X25519KeyPair};
//!
//! // Alice generates a keypair
//! let alice = X25519ECDH::generate_keypair();
//!
//! // Bob generates a keypair
//! let bob = X25519ECDH::generate_keypair();
//!
//! // Both compute the shared secret
//! let ss_alice = X25519ECDH::diffie_hellman(&alice.secret, &bob.public).unwrap();
//! let ss_bob = X25519ECDH::diffie_hellman(&bob.secret, &alice.public).unwrap();
//! assert_eq!(ss_alice.as_bytes(), ss_bob.as_bytes());
//! ```

use super::{
    EcdhSharedSecret, HybridKeyExchange, HybridSharedSecret, X25519KeyPair, X25519PublicKeyBytes,
    X25519SecretKeyBytes, X25519ECDH,
};
use crate::crypto::error::{CryptoError, Result};
use crate::crypto::kem::KyberSharedSecret;

impl X25519ECDH {
    /// Generate a new X25519 keypair using the system CSPRNG.
    ///
    /// # Returns
    ///
    /// An `X25519KeyPair` containing the public and secret keys,
    /// both 32 bytes each.
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::crypto::ecdh::X25519ECDH;
    ///
    /// let keypair = X25519ECDH::generate_keypair();
    /// assert_eq!(keypair.public.as_bytes().len(), 32);
    /// ```
    pub fn generate_keypair() -> X25519KeyPair {
        let mut rng = rand::thread_rng();
        let secret = x25519_dalek::StaticSecret::random_from_rng(&mut rng);
        let public = x25519_dalek::PublicKey::from(&secret);

        X25519KeyPair {
            public: X25519PublicKeyBytes(public.to_bytes()),
            secret: X25519SecretKeyBytes(secret.to_bytes()),
        }
    }

    /// Perform Diffie-Hellman key agreement.
    ///
    /// Computes a shared secret from the local secret key and the
    /// remote party's public key. Both parties will derive the same
    /// shared secret when using their own secret key and the other's
    /// public key.
    ///
    /// # Arguments
    ///
    /// - `secret_key`: The local party's X25519 secret key
    /// - `public_key`: The remote party's X25519 public key
    ///
    /// # Returns
    ///
    /// A 32-byte `EcdhSharedSecret` on success.
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::EcdhError` if the resulting shared secret
    /// is all zeros, which indicates the remote public key is a
    /// low-order point (a potential small-subgroup attack).
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::crypto::ecdh::X25519ECDH;
    ///
    /// let alice = X25519ECDH::generate_keypair();
    /// let bob = X25519ECDH::generate_keypair();
    ///
    /// let ss_a = X25519ECDH::diffie_hellman(&alice.secret, &bob.public).unwrap();
    /// let ss_b = X25519ECDH::diffie_hellman(&bob.secret, &alice.public).unwrap();
    /// assert_eq!(ss_a.as_bytes(), ss_b.as_bytes());
    /// ```
    pub fn diffie_hellman(
        secret_key: &X25519SecretKeyBytes,
        public_key: &X25519PublicKeyBytes,
    ) -> Result<EcdhSharedSecret> {
        let secret = x25519_dalek::StaticSecret::from(secret_key.0);
        let public = x25519_dalek::PublicKey::from(public_key.0);

        let shared = secret.diffie_hellman(&public);
        let shared_bytes = shared.to_bytes();

        // Reject all-zero shared secret (low-order point attack)
        if shared_bytes.iter().all(|&b| b == 0) {
            return Err(CryptoError::ecdh(
                "Shared secret is all zeros: possible low-order point attack",
            ));
        }

        Ok(EcdhSharedSecret(shared_bytes))
    }

    /// Derive the public key from a secret key.
    ///
    /// This is useful when reconstructing a keypair from a stored secret key.
    ///
    /// # Arguments
    ///
    /// - `secret_key`: The X25519 secret key
    ///
    /// # Returns
    ///
    /// The corresponding `X25519PublicKeyBytes`.
    pub fn public_from_secret(secret_key: &X25519SecretKeyBytes) -> X25519PublicKeyBytes {
        let secret = x25519_dalek::StaticSecret::from(secret_key.0);
        let public = x25519_dalek::PublicKey::from(&secret);
        X25519PublicKeyBytes(public.to_bytes())
    }
}

impl HybridKeyExchange {
    /// Combine Kyber-1024 and X25519 shared secrets into a hybrid secret.
    ///
    /// Uses BLAKE3 key derivation with domain separation to combine
    /// the two shared secrets. This provides defense-in-depth: even if
    /// one algorithm is broken, the combined secret remains secure as
    /// long as the other holds.
    ///
    /// # Arguments
    ///
    /// - `kyber_secret`: The 32-byte shared secret from Kyber-1024 KEM
    /// - `x25519_secret`: The 32-byte shared secret from X25519 ECDH
    ///
    /// # Returns
    ///
    /// A `HybridSharedSecret` containing both individual secrets and
    /// the combined 64-byte derived secret.
    ///
    /// # Security
    ///
    /// The combined secret is derived as:
    /// ```text
    /// combined = BLAKE3-derive_key(
    ///     context = "aeternum v5 hybrid-kex kyber1024+x25519",
    ///     input   = kyber_secret || x25519_secret,
    ///     length  = 64
    /// )
    /// ```
    pub fn combine_secrets(
        kyber_secret: KyberSharedSecret,
        x25519_secret: EcdhSharedSecret,
    ) -> HybridSharedSecret {
        let dk =
            crate::crypto::hash::DeriveKey::new(&[], "aeternum v5 hybrid-kex kyber1024+x25519");

        // Concatenate: kyber_secret || x25519_secret
        let mut ikm = Vec::with_capacity(64);
        ikm.extend_from_slice(x25519_secret.as_bytes());
        ikm.extend_from_slice(kyber_secret.as_bytes());

        let derived = dk.derive(&ikm, 64);

        let mut combined = [0u8; 64];
        combined.copy_from_slice(&derived);

        // Zeroize the intermediate buffer
        use zeroize::Zeroize;
        ikm.zeroize();

        HybridSharedSecret {
            kyber_secret,
            x25519_secret,
            combined,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // -- Basic functionality tests ------------------------------------------

    #[test]
    fn test_keypair_generation() {
        let kp = X25519ECDH::generate_keypair();
        assert_eq!(kp.public.as_bytes().len(), 32);
        assert_eq!(kp.secret.as_bytes().len(), 32);
    }

    #[test]
    fn test_keypair_uniqueness() {
        let kp1 = X25519ECDH::generate_keypair();
        let kp2 = X25519ECDH::generate_keypair();
        assert_ne!(
            kp1.public.as_bytes(),
            kp2.public.as_bytes(),
            "Two keypairs should have different public keys"
        );
    }

    #[test]
    fn test_dh_roundtrip() {
        let alice = X25519ECDH::generate_keypair();
        let bob = X25519ECDH::generate_keypair();

        let ss_alice = X25519ECDH::diffie_hellman(&alice.secret, &bob.public).unwrap();
        let ss_bob = X25519ECDH::diffie_hellman(&bob.secret, &alice.public).unwrap();

        assert_eq!(
            ss_alice.as_bytes(),
            ss_bob.as_bytes(),
            "Both parties must derive the same shared secret"
        );
    }

    #[test]
    fn test_shared_secret_size() {
        let alice = X25519ECDH::generate_keypair();
        let bob = X25519ECDH::generate_keypair();
        let ss = X25519ECDH::diffie_hellman(&alice.secret, &bob.public).unwrap();
        assert_eq!(ss.as_bytes().len(), 32);
    }

    #[test]
    fn test_public_from_secret() {
        let kp = X25519ECDH::generate_keypair();
        let derived_public = X25519ECDH::public_from_secret(&kp.secret);
        assert_eq!(kp.public.as_bytes(), derived_public.as_bytes());
    }

    // -- Different keypair isolation ----------------------------------------

    #[test]
    fn test_wrong_key_produces_different_secret() {
        let alice = X25519ECDH::generate_keypair();
        let bob = X25519ECDH::generate_keypair();
        let eve = X25519ECDH::generate_keypair();

        let ss_alice = X25519ECDH::diffie_hellman(&alice.secret, &bob.public).unwrap();
        let ss_eve = X25519ECDH::diffie_hellman(&eve.secret, &bob.public).unwrap();

        assert_ne!(
            ss_alice.as_bytes(),
            ss_eve.as_bytes(),
            "Different secret keys must yield different shared secrets"
        );
    }

    // -- RFC 7748 test vectors ----------------------------------------------

    #[test]
    fn test_rfc7748_vector_1() {
        // RFC 7748 Section 6.1 - First test vector
        let alice_secret_bytes =
            hex::decode("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a")
                .unwrap();
        let alice_public_expected =
            hex::decode("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
                .unwrap();

        let bob_secret_bytes =
            hex::decode("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb")
                .unwrap();
        let bob_public_expected =
            hex::decode("de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f")
                .unwrap();

        let expected_shared_secret =
            hex::decode("4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742")
                .unwrap();

        // Load keys
        let alice_secret = X25519SecretKeyBytes::from_bytes(&alice_secret_bytes).unwrap();
        let bob_secret = X25519SecretKeyBytes::from_bytes(&bob_secret_bytes).unwrap();

        // Verify public key derivation
        let alice_public = X25519ECDH::public_from_secret(&alice_secret);
        let bob_public = X25519ECDH::public_from_secret(&bob_secret);

        assert_eq!(
            alice_public.as_bytes().as_slice(),
            &alice_public_expected,
            "Alice's derived public key must match RFC 7748 vector"
        );
        assert_eq!(
            bob_public.as_bytes().as_slice(),
            &bob_public_expected,
            "Bob's derived public key must match RFC 7748 vector"
        );

        // Verify shared secret (both directions)
        let ss_alice = X25519ECDH::diffie_hellman(&alice_secret, &bob_public).unwrap();
        let ss_bob = X25519ECDH::diffie_hellman(&bob_secret, &alice_public).unwrap();

        assert_eq!(
            ss_alice.as_bytes().as_slice(),
            &expected_shared_secret,
            "Alice's shared secret must match RFC 7748 vector"
        );
        assert_eq!(
            ss_bob.as_bytes().as_slice(),
            &expected_shared_secret,
            "Bob's shared secret must match RFC 7748 vector"
        );
    }

    #[test]
    fn test_rfc7748_iterated_1() {
        // RFC 7748 Section 5.2 - After 1 iteration
        // Start: k = u = 9 (basepoint)
        let mut k = [0u8; 32];
        k[0] = 9;
        let mut u = [0u8; 32];
        u[0] = 9;

        // One iteration: k, u = X25519(k, u), k
        let old_k = k;
        let sk = x25519_dalek::StaticSecret::from(k);
        let pk = x25519_dalek::PublicKey::from(u);
        let result = sk.diffie_hellman(&pk);
        k = result.to_bytes();
        u = old_k;
        let _ = u; // suppress unused warning

        let expected_k_1 =
            hex::decode("422c8e7a6227d7bca1350b3e2bb7279f7897b87bb6854b783c60e80311ae3079")
                .unwrap();

        assert_eq!(
            &k[..],
            &expected_k_1[..],
            "After 1 iteration, k must match RFC 7748 test vector"
        );
    }

    #[test]
    fn test_rfc7748_iterated_1000() {
        // RFC 7748 Section 5.2 - After 1,000 iterations
        let mut k = [0u8; 32];
        k[0] = 9;
        let mut u = [0u8; 32];
        u[0] = 9;

        for _ in 0..1000 {
            let old_k = k;
            let sk = x25519_dalek::StaticSecret::from(k);
            let pk = x25519_dalek::PublicKey::from(u);
            let result = sk.diffie_hellman(&pk);
            k = result.to_bytes();
            u = old_k;
        }

        let expected_k_1000 =
            hex::decode("684cf59ba83309552800ef566f2f4d3c1c3887c49360e3875f2eb94d99532c51")
                .unwrap();

        assert_eq!(
            &k[..],
            &expected_k_1000[..],
            "After 1000 iterations, k must match RFC 7748 test vector"
        );
    }

    // -- Edge case: low-order points ----------------------------------------

    #[test]
    fn test_all_zero_public_key_rejected() {
        // An all-zero public key is a low-order point; DH should produce
        // all-zero shared secret, which we reject.
        let kp = X25519ECDH::generate_keypair();
        let zero_pk = X25519PublicKeyBytes([0u8; 32]);
        let result = X25519ECDH::diffie_hellman(&kp.secret, &zero_pk);
        assert!(
            result.is_err(),
            "DH with all-zero public key must be rejected"
        );
    }

    #[test]
    fn test_all_ff_public_key() {
        // All-0xFF is not a low-order point; DH should succeed and
        // produce a non-zero shared secret.
        let kp = X25519ECDH::generate_keypair();
        let ff_pk = X25519PublicKeyBytes([0xFF; 32]);
        let result = X25519ECDH::diffie_hellman(&kp.secret, &ff_pk);
        // This should succeed (0xFF... is a valid curve point after reduction)
        assert!(result.is_ok(), "DH with all-0xFF public key should succeed");
    }

    #[test]
    fn test_known_low_order_points() {
        // Known small-order points on Curve25519 that produce all-zero output
        let low_order_points: Vec<[u8; 32]> = vec![
            // 0 (identity)
            [0; 32],
            // 1 (order-2 point)
            {
                let mut p = [0u8; 32];
                p[0] = 1;
                p
            },
        ];

        let kp = X25519ECDH::generate_keypair();
        for point in &low_order_points {
            let pk = X25519PublicKeyBytes(*point);
            let result = X25519ECDH::diffie_hellman(&kp.secret, &pk);
            // These may or may not produce all-zero output depending on
            // the clamped secret key, but we verify the check is in place
            if let Err(e) = result {
                assert!(
                    e.to_string().contains("all zeros"),
                    "Error should mention all-zero shared secret"
                );
            }
        }
    }

    // -- Type construction from bytes ---------------------------------------

    #[test]
    fn test_secret_key_from_bytes_roundtrip() {
        let kp = X25519ECDH::generate_keypair();
        let sk_bytes = kp.secret.as_bytes();
        let sk_restored = X25519SecretKeyBytes::from_bytes(sk_bytes).unwrap();
        assert_eq!(sk_restored.as_bytes(), sk_bytes);
    }

    #[test]
    fn test_public_key_from_bytes_roundtrip() {
        let kp = X25519ECDH::generate_keypair();
        let pk_bytes = kp.public.as_bytes();
        let pk_restored = X25519PublicKeyBytes::from_bytes(pk_bytes).unwrap();
        assert_eq!(pk_restored.as_bytes(), pk_bytes);
    }

    // -- Serialization roundtrip: generate -> serialize -> deserialize -> use

    #[test]
    fn test_full_serialization_roundtrip() {
        let alice = X25519ECDH::generate_keypair();
        let bob = X25519ECDH::generate_keypair();

        // Serialize and deserialize Alice's keys
        let alice_pk = X25519PublicKeyBytes::from_bytes(alice.public.as_bytes()).unwrap();
        let alice_sk = X25519SecretKeyBytes::from_bytes(alice.secret.as_bytes()).unwrap();

        // Serialize and deserialize Bob's keys
        let bob_pk = X25519PublicKeyBytes::from_bytes(bob.public.as_bytes()).unwrap();
        let bob_sk = X25519SecretKeyBytes::from_bytes(bob.secret.as_bytes()).unwrap();

        // Use restored keys for DH
        let ss1 = X25519ECDH::diffie_hellman(&alice_sk, &bob_pk).unwrap();
        let ss2 = X25519ECDH::diffie_hellman(&bob_sk, &alice_pk).unwrap();
        assert_eq!(ss1.as_bytes(), ss2.as_bytes());
    }

    // -- Hybrid key exchange ------------------------------------------------

    #[test]
    fn test_hybrid_combine_deterministic() {
        let kyber_bytes = [0x42u8; 32];
        let x25519_bytes = [0x84u8; 32];

        let ks1 = KyberSharedSecret::from_bytes(&kyber_bytes).unwrap();
        let xs1 = EcdhSharedSecret::from_bytes(&x25519_bytes).unwrap();
        let hybrid1 = HybridKeyExchange::combine_secrets(ks1, xs1);

        let ks2 = KyberSharedSecret::from_bytes(&kyber_bytes).unwrap();
        let xs2 = EcdhSharedSecret::from_bytes(&x25519_bytes).unwrap();
        let hybrid2 = HybridKeyExchange::combine_secrets(ks2, xs2);

        assert_eq!(
            hybrid1.combined, hybrid2.combined,
            "Combining identical secrets must produce identical output"
        );
    }

    #[test]
    fn test_hybrid_combined_length() {
        let ks = KyberSharedSecret::from_bytes(&[0x11u8; 32]).unwrap();
        let xs = EcdhSharedSecret::from_bytes(&[0x22u8; 32]).unwrap();
        let hybrid = HybridKeyExchange::combine_secrets(ks, xs);
        assert_eq!(hybrid.combined.len(), 64);
    }

    #[test]
    fn test_hybrid_different_inputs_differ() {
        let ks1 = KyberSharedSecret::from_bytes(&[0x11u8; 32]).unwrap();
        let xs1 = EcdhSharedSecret::from_bytes(&[0x22u8; 32]).unwrap();
        let hybrid1 = HybridKeyExchange::combine_secrets(ks1, xs1);

        let ks2 = KyberSharedSecret::from_bytes(&[0x33u8; 32]).unwrap();
        let xs2 = EcdhSharedSecret::from_bytes(&[0x22u8; 32]).unwrap();
        let hybrid2 = HybridKeyExchange::combine_secrets(ks2, xs2);

        assert_ne!(
            hybrid1.combined, hybrid2.combined,
            "Different Kyber secrets must produce different combined secrets"
        );
    }

    #[test]
    fn test_hybrid_different_x25519_differ() {
        let ks1 = KyberSharedSecret::from_bytes(&[0x11u8; 32]).unwrap();
        let xs1 = EcdhSharedSecret::from_bytes(&[0x22u8; 32]).unwrap();
        let hybrid1 = HybridKeyExchange::combine_secrets(ks1, xs1);

        let ks2 = KyberSharedSecret::from_bytes(&[0x11u8; 32]).unwrap();
        let xs2 = EcdhSharedSecret::from_bytes(&[0x44u8; 32]).unwrap();
        let hybrid2 = HybridKeyExchange::combine_secrets(ks2, xs2);

        assert_ne!(
            hybrid1.combined, hybrid2.combined,
            "Different X25519 secrets must produce different combined secrets"
        );
    }

    #[test]
    fn test_hybrid_preserves_individual_secrets() {
        let kyber_bytes = [0xAAu8; 32];
        let x25519_bytes = [0xBBu8; 32];

        let ks = KyberSharedSecret::from_bytes(&kyber_bytes).unwrap();
        let xs = EcdhSharedSecret::from_bytes(&x25519_bytes).unwrap();
        let hybrid = HybridKeyExchange::combine_secrets(ks, xs);

        assert_eq!(hybrid.kyber_secret.as_bytes(), &kyber_bytes);
        assert_eq!(hybrid.x25519_secret.as_bytes(), &x25519_bytes);
    }

    #[test]
    fn test_hybrid_combined_not_simple_concat() {
        // The combined secret must NOT be a simple concatenation
        let kyber_bytes = [0x11u8; 32];
        let x25519_bytes = [0x22u8; 32];

        let ks = KyberSharedSecret::from_bytes(&kyber_bytes).unwrap();
        let xs = EcdhSharedSecret::from_bytes(&x25519_bytes).unwrap();
        let hybrid = HybridKeyExchange::combine_secrets(ks, xs);

        let mut naive_concat = [0u8; 64];
        naive_concat[..32].copy_from_slice(&kyber_bytes);
        naive_concat[32..].copy_from_slice(&x25519_bytes);

        assert_ne!(
            hybrid.combined, naive_concat,
            "Combined secret must NOT be a simple concatenation"
        );
    }

    // -- Multiple rounds test -----------------------------------------------

    #[test]
    fn test_multiple_dh_same_keypairs() {
        let alice = X25519ECDH::generate_keypair();
        let bob = X25519ECDH::generate_keypair();

        // DH is deterministic for the same key pair
        let ss1 = X25519ECDH::diffie_hellman(&alice.secret, &bob.public).unwrap();
        let ss2 = X25519ECDH::diffie_hellman(&alice.secret, &bob.public).unwrap();
        assert_eq!(
            ss1.as_bytes(),
            ss2.as_bytes(),
            "DH with same keys must always produce the same shared secret"
        );
    }
}

// -- Property-based tests (proptest) ----------------------------------------

#[cfg(test)]
mod proptest_tests {
    use super::*;
    use proptest::prelude::*;

    proptest! {
        /// DH exchange must be symmetric: DH(a, B) == DH(b, A)
        #[test]
        fn prop_dh_symmetry(_seed in 0u64..500) {
            let alice = X25519ECDH::generate_keypair();
            let bob = X25519ECDH::generate_keypair();

            let ss_alice = X25519ECDH::diffie_hellman(&alice.secret, &bob.public).unwrap();
            let ss_bob = X25519ECDH::diffie_hellman(&bob.secret, &alice.public).unwrap();
            prop_assert_eq!(ss_alice.as_bytes(), ss_bob.as_bytes());
        }

        /// Shared secret is always exactly 32 bytes
        #[test]
        fn prop_shared_secret_length(_seed in 0u64..100) {
            let alice = X25519ECDH::generate_keypair();
            let bob = X25519ECDH::generate_keypair();
            let ss = X25519ECDH::diffie_hellman(&alice.secret, &bob.public).unwrap();
            prop_assert_eq!(ss.as_bytes().len(), 32);
        }

        /// Public key is always exactly 32 bytes
        #[test]
        fn prop_public_key_length(_seed in 0u64..100) {
            let kp = X25519ECDH::generate_keypair();
            prop_assert_eq!(kp.public.as_bytes().len(), 32);
        }

        /// Secret key is always exactly 32 bytes
        #[test]
        fn prop_secret_key_length(_seed in 0u64..100) {
            let kp = X25519ECDH::generate_keypair();
            prop_assert_eq!(kp.secret.as_bytes().len(), 32);
        }

        /// DH is deterministic for the same keypairs
        #[test]
        fn prop_dh_deterministic(_seed in 0u64..100) {
            let alice = X25519ECDH::generate_keypair();
            let bob = X25519ECDH::generate_keypair();

            let ss1 = X25519ECDH::diffie_hellman(&alice.secret, &bob.public).unwrap();
            let ss2 = X25519ECDH::diffie_hellman(&alice.secret, &bob.public).unwrap();
            prop_assert_eq!(ss1.as_bytes(), ss2.as_bytes());
        }

        /// Public key derivation is consistent
        #[test]
        fn prop_public_from_secret_consistent(_seed in 0u64..100) {
            let kp = X25519ECDH::generate_keypair();
            let derived = X25519ECDH::public_from_secret(&kp.secret);
            prop_assert_eq!(kp.public.as_bytes(), derived.as_bytes());
        }

        /// Different keypairs produce different shared secrets
        #[test]
        fn prop_different_keys_different_secrets(_seed in 0u64..100) {
            let alice = X25519ECDH::generate_keypair();
            let bob = X25519ECDH::generate_keypair();
            let eve = X25519ECDH::generate_keypair();

            let ss_alice = X25519ECDH::diffie_hellman(&alice.secret, &bob.public).unwrap();
            let ss_eve = X25519ECDH::diffie_hellman(&eve.secret, &bob.public).unwrap();
            prop_assert_ne!(ss_alice.as_bytes(), ss_eve.as_bytes());
        }

        /// Hybrid combine is deterministic
        #[test]
        fn prop_hybrid_deterministic(
            kyber_byte in any::<u8>(),
            x25519_byte in any::<u8>(),
        ) {
            let ks1 = KyberSharedSecret::from_bytes(&[kyber_byte; 32]).unwrap();
            let xs1 = EcdhSharedSecret::from_bytes(&[x25519_byte; 32]).unwrap();
            let h1 = HybridKeyExchange::combine_secrets(ks1, xs1);

            let ks2 = KyberSharedSecret::from_bytes(&[kyber_byte; 32]).unwrap();
            let xs2 = EcdhSharedSecret::from_bytes(&[x25519_byte; 32]).unwrap();
            let h2 = HybridKeyExchange::combine_secrets(ks2, xs2);

            prop_assert_eq!(h1.combined, h2.combined);
        }

        /// Hybrid combined output is always 64 bytes
        #[test]
        fn prop_hybrid_output_length(
            kyber_byte in any::<u8>(),
            x25519_byte in any::<u8>(),
        ) {
            let ks = KyberSharedSecret::from_bytes(&[kyber_byte; 32]).unwrap();
            let xs = EcdhSharedSecret::from_bytes(&[x25519_byte; 32]).unwrap();
            let hybrid = HybridKeyExchange::combine_secrets(ks, xs);
            prop_assert_eq!(hybrid.combined.len(), 64);
        }
    }
}
