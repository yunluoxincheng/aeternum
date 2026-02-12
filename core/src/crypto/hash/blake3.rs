//! # BLAKE3 Hash Implementation
//!
//! Provides BLAKE3 hashing and key derivation for the Aeternum core.
//!
//! ## Modes
//!
//! - **Hash mode**: One-shot or incremental hashing via [`Blake3Hasher`]
//! - **Key derivation mode**: Domain-separated KDF via [`DeriveKey`]
//!
//! ## Security Properties
//!
//! - 256-bit security level
//! - Deterministic output for identical inputs
//! - [`HashOutput`] implements `Zeroize` for safe memory cleanup

use super::HashOutput;

/// Incremental BLAKE3 hasher.
///
/// Supports feeding data in chunks; the final hash is identical
/// to hashing the concatenation of all chunks in one shot.
///
/// # Example
///
/// ```
/// use aeternum_core::crypto::hash::{Blake3Hasher, hash};
///
/// let mut hasher = Blake3Hasher::new();
/// hasher.update(b"hello ");
/// hasher.update(b"world");
/// let incremental = hasher.finalize();
///
/// let oneshot = hash(b"hello world");
/// assert_eq!(incremental, oneshot);
/// ```
pub struct Blake3Hasher {
    inner: blake3::Hasher,
}

impl Blake3Hasher {
    /// Create a new BLAKE3 hasher.
    pub fn new() -> Self {
        Self {
            inner: blake3::Hasher::new(),
        }
    }

    /// Feed data into the hasher.
    ///
    /// Can be called multiple times to process data incrementally.
    /// Returns `&mut Self` for method chaining.
    pub fn update(&mut self, data: &[u8]) -> &mut Self {
        self.inner.update(data);
        self
    }

    /// Consume the hasher and return the 32-byte hash.
    pub fn finalize(self) -> HashOutput {
        let hash = self.inner.finalize();
        HashOutput::from_bytes(*hash.as_bytes())
    }
}

impl Default for Blake3Hasher {
    fn default() -> Self {
        Self::new()
    }
}

/// Compute the BLAKE3 hash of `data` in one shot.
///
/// This is equivalent to creating a [`Blake3Hasher`], calling
/// `update(data)`, and then `finalize()`.
///
/// # Example
///
/// ```
/// use aeternum_core::crypto::hash::hash;
///
/// let output = hash(b"hello");
/// assert_eq!(output.as_bytes().len(), 32);
/// ```
pub fn hash(data: &[u8]) -> HashOutput {
    let h = blake3::hash(data);
    HashOutput::from_bytes(*h.as_bytes())
}

/// BLAKE3 key derivation context.
///
/// Uses BLAKE3's built-in `derive_key` mode for domain-separated
/// key derivation. The `context` string provides domain separation,
/// while `salt` and input key material (IKM) are fed as input.
///
/// The output can be any length thanks to BLAKE3's XOF (extendable
/// output function) capability.
///
/// # Example
///
/// ```
/// use aeternum_core::crypto::hash::DeriveKey;
///
/// let dk = DeriveKey::new(b"random-salt", "aeternum 2025 vault-key derivation");
/// let key = dk.derive(b"master-secret", 32);
/// assert_eq!(key.len(), 32);
/// ```
pub struct DeriveKey<'a> {
    /// Salt value mixed into the input
    pub salt: &'a [u8],
    /// Domain separation context string (must be globally unique per use case)
    pub context: &'a str,
}

impl<'a> DeriveKey<'a> {
    /// Create a new key derivation context.
    ///
    /// - `salt`: Random or unique bytes mixed into the derivation input
    /// - `context`: A hardcoded, globally unique string describing the use case
    pub fn new(salt: &'a [u8], context: &'a str) -> Self {
        Self { salt, context }
    }

    /// Derive a key of `length` bytes from the given input key material.
    ///
    /// The derivation is: `BLAKE3-derive_key(context, salt || ikm)` with
    /// XOF output truncated/extended to `length` bytes.
    pub fn derive(&self, ikm: &[u8], length: usize) -> Vec<u8> {
        let mut hasher = blake3::Hasher::new_derive_key(self.context);
        hasher.update(self.salt);
        hasher.update(ikm);

        let mut output = vec![0u8; length];
        let mut reader = hasher.finalize_xof();
        reader.fill(&mut output);
        output
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // ── Official BLAKE3 test vectors ────────────────────────────────

    #[test]
    fn test_hash_empty_input() {
        // Official BLAKE3 test vector: empty input
        let output = hash(b"");
        assert_eq!(
            output.to_hex(),
            "af1349b9f5f9a1a6a0404dea36dcc9499bcb25c9adc112b7cc9a93cae41f3262"
        );
    }

    #[test]
    fn test_hash_known_vectors() {
        // Verify against blake3 crate directly for consistency
        let inputs: &[&[u8]] = &[
            b"hello",
            b"hello world",
            b"The quick brown fox jumps over the lazy dog",
            &[0u8; 64],
            &[0xffu8; 128],
        ];

        for input in inputs {
            let expected = blake3::hash(input);
            let actual = hash(input);
            assert_eq!(
                actual.as_bytes(),
                expected.as_bytes(),
                "Mismatch for input of length {}",
                input.len()
            );
        }
    }

    // ── Incremental hashing ─────────────────────────────────────────

    #[test]
    fn test_incremental_equals_oneshot() {
        let data = b"The quick brown fox jumps over the lazy dog";

        let oneshot = hash(data);

        let mut hasher = Blake3Hasher::new();
        hasher.update(&data[..10]);
        hasher.update(&data[10..20]);
        hasher.update(&data[20..]);
        let incremental = hasher.finalize();

        assert_eq!(oneshot, incremental);
    }

    #[test]
    fn test_incremental_single_byte_chunks() {
        let data = b"aeternum";

        let oneshot = hash(data);

        let mut hasher = Blake3Hasher::new();
        for byte in data.iter() {
            hasher.update(std::slice::from_ref(byte));
        }
        let incremental = hasher.finalize();

        assert_eq!(oneshot, incremental);
    }

    #[test]
    fn test_incremental_method_chaining() {
        let mut hasher = Blake3Hasher::new();
        hasher.update(b"hello").update(b" ").update(b"world");
        let chained = hasher.finalize();

        let oneshot = hash(b"hello world");
        assert_eq!(chained, oneshot);
    }

    #[test]
    fn test_incremental_empty_updates() {
        let mut hasher = Blake3Hasher::new();
        hasher.update(b"");
        hasher.update(b"hello");
        hasher.update(b"");
        let result = hasher.finalize();

        assert_eq!(result, hash(b"hello"));
    }

    // ── Key derivation ──────────────────────────────────────────────

    #[test]
    fn test_derive_key_deterministic() {
        let dk = DeriveKey::new(b"test-salt", "aeternum test context");
        let key1 = dk.derive(b"master-key-material", 32);
        let key2 = dk.derive(b"master-key-material", 32);
        assert_eq!(key1, key2);
    }

    #[test]
    fn test_derive_key_different_contexts_differ() {
        let dk1 = DeriveKey::new(b"salt", "context-a");
        let dk2 = DeriveKey::new(b"salt", "context-b");

        let key1 = dk1.derive(b"ikm", 32);
        let key2 = dk2.derive(b"ikm", 32);
        assert_ne!(key1, key2);
    }

    #[test]
    fn test_derive_key_different_salts_differ() {
        let dk1 = DeriveKey::new(b"salt-a", "same-context");
        let dk2 = DeriveKey::new(b"salt-b", "same-context");

        let key1 = dk1.derive(b"ikm", 32);
        let key2 = dk2.derive(b"ikm", 32);
        assert_ne!(key1, key2);
    }

    #[test]
    fn test_derive_key_different_ikm_differ() {
        let dk = DeriveKey::new(b"salt", "context");

        let key1 = dk.derive(b"ikm-a", 32);
        let key2 = dk.derive(b"ikm-b", 32);
        assert_ne!(key1, key2);
    }

    #[test]
    fn test_derive_key_variable_length() {
        let dk = DeriveKey::new(b"salt", "aeternum derive-key length test");

        let key16 = dk.derive(b"ikm", 16);
        let key32 = dk.derive(b"ikm", 32);
        let key64 = dk.derive(b"ikm", 64);

        assert_eq!(key16.len(), 16);
        assert_eq!(key32.len(), 32);
        assert_eq!(key64.len(), 64);

        // The first 16 bytes of key32 should NOT equal key16,
        // because BLAKE3 XOF output depends on the requested length
        // Actually, BLAKE3 XOF IS a prefix-based stream, so
        // the first 32 bytes of key64 SHOULD equal key32
        assert_eq!(&key64[..32], &key32[..]);
        assert_eq!(&key32[..16], &key16[..]);
    }

    #[test]
    fn test_derive_key_matches_blake3_crate() {
        let context = "aeternum 2025 test derivation";
        let salt = b"test-salt-value";
        let ikm = b"input-key-material";

        // Compute expected value directly with blake3 crate
        let mut hasher = blake3::Hasher::new_derive_key(context);
        hasher.update(salt);
        hasher.update(ikm);
        let mut expected = vec![0u8; 48];
        let mut reader = hasher.finalize_xof();
        reader.fill(&mut expected);

        let dk = DeriveKey::new(salt, context);
        let actual = dk.derive(ikm, 48);

        assert_eq!(actual, expected);
    }

    #[test]
    fn test_derive_key_zero_length() {
        let dk = DeriveKey::new(b"salt", "context");
        let key = dk.derive(b"ikm", 0);
        assert!(key.is_empty());
    }

    #[test]
    fn test_derive_key_empty_inputs() {
        let dk = DeriveKey::new(b"", "context");
        let key = dk.derive(b"", 32);
        assert_eq!(key.len(), 32);
        // Should still produce a valid, non-zero output
        assert_ne!(key, vec![0u8; 32]);
    }

    // ── HashOutput properties ───────────────────────────────────────

    #[test]
    fn test_hash_output_length_always_32() {
        let output = hash(b"any input");
        assert_eq!(output.as_bytes().len(), 32);
    }

    #[test]
    fn test_hash_deterministic() {
        let a = hash(b"test data");
        let b = hash(b"test data");
        assert_eq!(a, b);
    }

    #[test]
    fn test_hash_different_inputs_differ() {
        let a = hash(b"input-a");
        let b = hash(b"input-b");
        assert_ne!(a, b);
    }

    #[test]
    fn test_default_trait() {
        let hasher = Blake3Hasher::default();
        let output = hasher.finalize();
        assert_eq!(output, hash(b""));
    }

    // ── Large input ─────────────────────────────────────────────────

    #[test]
    fn test_hash_large_input() {
        let data = vec![0xABu8; 1_000_000];
        let expected = blake3::hash(&data);
        let actual = hash(&data);
        assert_eq!(actual.as_bytes(), expected.as_bytes());
    }
}

// ── Property-based tests (proptest) ─────────────────────────────────

#[cfg(test)]
mod proptests {
    use super::*;
    use proptest::prelude::*;

    proptest! {
        /// Hash output is always exactly 32 bytes
        #[test]
        fn prop_hash_output_is_32_bytes(data in prop::collection::vec(any::<u8>(), 0..4096)) {
            let output = hash(&data);
            prop_assert_eq!(output.as_bytes().len(), 32);
        }

        /// Hashing is deterministic
        #[test]
        fn prop_hash_deterministic(data in prop::collection::vec(any::<u8>(), 0..4096)) {
            let a = hash(&data);
            let b = hash(&data);
            prop_assert_eq!(a, b);
        }

        /// Incremental hashing matches one-shot for arbitrary split points
        #[test]
        fn prop_incremental_equals_oneshot(
            data in prop::collection::vec(any::<u8>(), 0..4096),
            split in 0usize..4097
        ) {
            let split = split.min(data.len());
            let oneshot = hash(&data);

            let mut hasher = Blake3Hasher::new();
            hasher.update(&data[..split]);
            hasher.update(&data[split..]);
            let incremental = hasher.finalize();

            prop_assert_eq!(oneshot, incremental);
        }

        /// Key derivation output has the requested length
        #[test]
        fn prop_derive_key_output_length(
            salt in prop::collection::vec(any::<u8>(), 0..64),
            ikm in prop::collection::vec(any::<u8>(), 0..256),
            length in 0usize..512
        ) {
            let dk = DeriveKey::new(&salt, "proptest context");
            let key = dk.derive(&ikm, length);
            prop_assert_eq!(key.len(), length);
        }

        /// Key derivation is deterministic
        #[test]
        fn prop_derive_key_deterministic(
            salt in prop::collection::vec(any::<u8>(), 0..64),
            ikm in prop::collection::vec(any::<u8>(), 0..256),
        ) {
            let dk = DeriveKey::new(&salt, "proptest determinism");
            let key1 = dk.derive(&ikm, 32);
            let key2 = dk.derive(&ikm, 32);
            prop_assert_eq!(key1, key2);
        }

        /// Derived keys are prefix-consistent (BLAKE3 XOF property)
        #[test]
        fn prop_derive_key_prefix_consistent(
            salt in prop::collection::vec(any::<u8>(), 0..32),
            ikm in prop::collection::vec(any::<u8>(), 1..128),
        ) {
            let dk = DeriveKey::new(&salt, "proptest prefix");
            let short = dk.derive(&ikm, 32);
            let long = dk.derive(&ikm, 64);
            prop_assert_eq!(&long[..32], &short[..]);
        }
    }
}
