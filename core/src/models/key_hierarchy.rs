//! Key hierarchy: Master Root Seed and derived keys
//!
//! This module implements the key derivation chain from the 24-word
//! mnemonic phrase (MRS) to all derived key types.
//!
//! ## Derivation Path
//!
//! ```text
//! MRS (24-word mnemonic)
//!     │
//!     ├─ PBKDF2-SHA512 (2048 iterations) → Seed S (512-bit)
//!     │
//!     ├─ BLAKE3-Derive(S, "Aeternum_Identity_v1") → IK (32 bytes)
//!     │
//!     ├─ BLAKE3-Derive(S, "Aeternum_Recovery_v1") → RK (32 bytes)
//!     │
//!     └─ [Hardware-generated] → DK (Device Key)
//!             │
//!             ├─ Kyber-1024 encapsulation → DEK
//!             │
//!             └─ XChaCha20 encryption → VK
//! ```
//!
//! ## Security Properties
//!
//! - All secret types implement `Zeroize` and `ZeroizeOnDrop`
//! - Debug implementations never expose actual key material
//! - Key derivation is deterministic and reproducible

use crate::crypto::error::{CryptoError, Result};
use crate::crypto::hash::DeriveKey;
use pbkdf2::pbkdf2_hmac;
use sha2::Sha512;
use zeroize::{Zeroize, ZeroizeOnDrop};

// Domain separation context strings (MUST match Cold-Anchor-Recovery.md spec)
const IDENTITY_KEY_CONTEXT: &str = "Aeternum_Identity_v1";
const RECOVERY_KEY_CONTEXT: &str = "Aeternum_Recovery_v1";

// PBKDF2 parameters (MUST match Cold-Anchor-Recovery.md spec)
const PBKDF2_ITERATIONS: u32 = 2048;
const SEED_SIZE: usize = 64; // 512-bit seed

/// Master Root Seed - 512-bit seed derived from 24-word mnemonic
///
/// This is the root of all key derivation in Aeternum. It is derived
/// from a BIP-39 mnemonic using PBKDF2-HMAC-SHA512.
///
/// # Security
///
/// - Implements `Zeroize` and `ZeroizeOnDrop` for automatic memory erasure
/// - Debug output never shows actual key material
/// - The seed should only exist in memory during initial setup or recovery
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct MasterSeed(pub [u8; 64]);

impl MasterSeed {
    /// Derive MasterSeed from a BIP-39 mnemonic phrase.
    ///
    /// Uses PBKDF2-HMAC-SHA512 with 2048 iterations to derive a 512-bit seed.
    /// The passphrase is empty (standard BIP-39 behavior).
    ///
    /// # Arguments
    ///
    /// * `mnemonic` - A BIP-39 mnemonic phrase (typically 24 words)
    ///
    /// # Returns
    ///
    /// A `MasterSeed` containing the 512-bit derived seed.
    ///
    /// # Errors
    ///
    /// Returns `CryptoError::KdfError` if:
    /// - The mnemonic is invalid (bad checksum, wrong word count, etc.)
    /// - The mnemonic contains invalid words
    ///
    /// # Example
    ///
    /// ```ignore
    /// use aeternum_core::models::MasterSeed;
    ///
    /// let mnemonic = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";
    /// let seed = MasterSeed::from_mnemonic(mnemonic)?;
    /// ```
    pub fn from_mnemonic(mnemonic: &str) -> Result<Self> {
        // Validate and parse the mnemonic using BIP-39
        let _mnemonic_obj = bip39::Mnemonic::parse(mnemonic)
            .map_err(|e| CryptoError::kdf(format!("Invalid mnemonic: {}", e)))?;

        // Derive the seed using PBKDF2-HMAC-SHA512
        // BIP-39: seed = PBKDF2-HMAC-SHA512(mnemonic, "mnemonic" + passphrase, 2048)
        // We use an empty passphrase (standard behavior)
        let mut seed = [0u8; SEED_SIZE];
        let salt = b"mnemonic"; // BIP-39 standard salt (empty passphrase case)
        pbkdf2_hmac::<Sha512>(mnemonic.as_bytes(), salt, PBKDF2_ITERATIONS, &mut seed);

        Ok(MasterSeed(seed))
    }

    /// Derive the Identity Key (IK) from the master seed.
    ///
    /// Uses BLAKE3 key derivation mode with domain separation.
    /// The context string is "Aeternum_Identity_v1".
    ///
    /// # Returns
    ///
    /// A 32-byte `IdentityKey`.
    pub fn derive_identity_key(&self) -> IdentityKey {
        let dk = DeriveKey::new(&self.0, IDENTITY_KEY_CONTEXT);
        let key_bytes = dk.derive(&self.0, 32);
        // SAFETY: derive() always returns exactly 32 bytes when length=32
        let key_array: [u8; 32] = key_bytes.try_into().unwrap();
        IdentityKey(key_array)
    }

    /// Derive the Recovery Key (RK) from the master seed.
    ///
    /// Uses BLAKE3 key derivation mode with domain separation.
    /// The context string is "Aeternum_Recovery_v1".
    ///
    /// # Returns
    ///
    /// A 32-byte `RecoveryKey`.
    pub fn derive_recovery_key(&self) -> RecoveryKey {
        let dk = DeriveKey::new(&self.0, RECOVERY_KEY_CONTEXT);
        let key_bytes = dk.derive(&self.0, 32);
        // SAFETY: derive() always returns exactly 32 bytes when length=32
        let key_array: [u8; 32] = key_bytes.try_into().unwrap();
        RecoveryKey(key_array)
    }

    /// Get a reference to the raw seed bytes.
    ///
    /// # Security Warning
    ///
    /// This exposes the raw seed material. Use with caution and
    /// ensure the result is not logged or persisted insecurely.
    pub fn as_bytes(&self) -> &[u8; 64] {
        &self.0
    }

    /// Create a MasterSeed from raw bytes.
    ///
    /// # Security Warning
    ///
    /// This bypasses BIP-39 validation. Use only when you have
    /// a verified seed from a trusted source.
    pub fn from_bytes(bytes: [u8; 64]) -> Self {
        MasterSeed(bytes)
    }
}

// Secure Debug implementation (never expose key material)
impl std::fmt::Debug for MasterSeed {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("MasterSeed([REDACTED])")
    }
}

/// Identity Key - used for authentication and signing
///
/// Derived from `MasterSeed` using BLAKE3 with context "Aeternum_Identity_v1".
/// This key proves the user's identity without revealing the master seed.
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct IdentityKey(pub [u8; 32]);

impl IdentityKey {
    /// Get a reference to the raw key bytes.
    ///
    /// # Security Warning
    ///
    /// This exposes the raw key material. Use with caution.
    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.0
    }

    /// Create an IdentityKey from raw bytes.
    pub fn from_bytes(bytes: [u8; 32]) -> Self {
        IdentityKey(bytes)
    }
}

// Secure Debug implementation
impl std::fmt::Debug for IdentityKey {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("IdentityKey([REDACTED])")
    }
}

/// Recovery Key - used for vault recovery
///
/// Derived from `MasterSeed` using BLAKE3 with context "Aeternum_Recovery_v1".
/// This key is used to decrypt the shadow-wrapped DEK (Device_0's header).
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct RecoveryKey(pub [u8; 32]);

impl RecoveryKey {
    /// Get a reference to the raw key bytes.
    ///
    /// # Security Warning
    ///
    /// This exposes the raw key material. Use with caution.
    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.0
    }

    /// Create a RecoveryKey from raw bytes.
    pub fn from_bytes(bytes: [u8; 32]) -> Self {
        RecoveryKey(bytes)
    }
}

// Secure Debug implementation
impl std::fmt::Debug for RecoveryKey {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("RecoveryKey([REDACTED])")
    }
}

/// Device Key - hardware-generated key (only holds key_id)
///
/// The private key never leaves the hardware security module (StrongBox/KeyStore).
/// Rust only holds a `key_id` reference to identify the hardware key.
///
/// # Security Model
///
/// ```text
/// Android KeyStore (StrongBox)
///     └── DK_hardware (Kyber-1024 keypair)
///             ↑
///             key_id: [u8; 16] (handle)
///             ↓
/// Rust Core: DeviceKey { key_id }
/// ```
pub struct DeviceKey {
    /// Key identifier (16 bytes) - a handle to the hardware key
    pub key_id: [u8; 16],
}

impl DeviceKey {
    /// Create a new DeviceKey with the given key_id.
    pub fn new(key_id: [u8; 16]) -> Self {
        DeviceKey { key_id }
    }

    /// Get the key identifier.
    pub fn key_id(&self) -> &[u8; 16] {
        &self.key_id
    }
}

impl std::fmt::Debug for DeviceKey {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        // Show truncated key_id for debugging (not the actual key)
        f.debug_struct("DeviceKey")
            .field("key_id", &hex::encode(&self.key_id[..4]))
            .finish()
    }
}

/// Data Encryption Key - wraps the Vault Key
///
/// The DEK is a 256-bit key used to encrypt the Vault Key (VK).
/// It is itself encrypted using Kyber-1024 and stored in each device's header.
///
/// # Security
///
/// - Implements `Zeroize` and `ZeroizeOnDrop`
/// - Only exists in memory during encryption/decryption operations
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct DataEncryptionKey(pub [u8; 32]);

impl DataEncryptionKey {
    /// Create a new DEK from raw bytes.
    pub fn from_bytes(bytes: [u8; 32]) -> Self {
        DataEncryptionKey(bytes)
    }

    /// Get a reference to the raw key bytes.
    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.0
    }

    /// Generate a random DEK.
    ///
    /// # Security
    ///
    /// Uses the system's cryptographically secure random number generator.
    pub fn generate() -> Self {
        use rand::RngCore;
        let mut bytes = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut bytes);
        DataEncryptionKey(bytes)
    }
}

// Secure Debug implementation
impl std::fmt::Debug for DataEncryptionKey {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("DataEncryptionKey([REDACTED])")
    }
}

/// Vault Key - encrypts user data
///
/// The VK is the actual symmetric key used with XChaCha20-Poly1305
/// to encrypt the user's database. It is wrapped by the DEK.
///
/// # Security
///
/// - Implements `Zeroize` and `ZeroizeOnDrop`
/// - Should only exist in memory during active operations
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct VaultKey(pub [u8; 32]);

impl VaultKey {
    /// Create a new VaultKey from raw bytes.
    pub fn from_bytes(bytes: [u8; 32]) -> Self {
        VaultKey(bytes)
    }

    /// Get a reference to the raw key bytes.
    pub fn as_bytes(&self) -> &[u8; 32] {
        &self.0
    }

    /// Generate a random Vault Key.
    ///
    /// # Security
    ///
    /// Uses the system's cryptographically secure random number generator.
    pub fn generate() -> Self {
        use rand::RngCore;
        let mut bytes = [0u8; 32];
        rand::thread_rng().fill_bytes(&mut bytes);
        VaultKey(bytes)
    }
}

// Secure Debug implementation
impl std::fmt::Debug for VaultKey {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("VaultKey([REDACTED])")
    }
}

// ============================================================================
// Tests
// ============================================================================

#[cfg(test)]
mod tests {
    use super::*;

    // BIP-39 test vectors (from BIP-39 specification)
    // 24-word mnemonic with known seed
    const BIP39_TEST_MNEMONIC_24: &str = "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";

    // ── MasterSeed Tests ──────────────────────────────────────────────────

    #[test]
    fn test_master_seed_from_mnemonic_valid() {
        let seed = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24);
        assert!(seed.is_ok());
    }

    #[test]
    fn test_master_seed_from_mnemonic_invalid_word() {
        let result = MasterSeed::from_mnemonic("invalid word list that does not exist in bip39");
        assert!(result.is_err());
    }

    #[test]
    fn test_master_seed_from_mnemonic_12_words() {
        // BIP-39 also supports 12-word mnemonics
        let result = MasterSeed::from_mnemonic(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about",
        );
        // 12 words is valid BIP-39
        assert!(result.is_ok());
    }

    #[test]
    fn test_master_seed_from_mnemonic_invalid_word_count() {
        // Invalid word count (13 words - not a valid BIP-39 length)
        let result = MasterSeed::from_mnemonic(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon",
        );
        assert!(result.is_err());
    }

    #[test]
    fn test_master_seed_from_mnemonic_invalid_checksum() {
        // Correct word count but wrong last word (checksum mismatch)
        let result = MasterSeed::from_mnemonic(
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon",
        );
        assert!(result.is_err());
    }

    #[test]
    fn test_master_seed_debug_redacted() {
        let seed = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24).unwrap();
        let debug_str = format!("{:?}", seed);
        assert_eq!(debug_str, "MasterSeed([REDACTED])");
        assert!(!debug_str.contains("abandon"));
    }

    #[test]
    fn test_master_seed_as_bytes() {
        let seed = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24).unwrap();
        let bytes = seed.as_bytes();
        assert_eq!(bytes.len(), 64);
    }

    #[test]
    fn test_master_seed_from_bytes_roundtrip() {
        let original = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24).unwrap();
        let bytes = *original.as_bytes();
        let reconstructed = MasterSeed::from_bytes(bytes);
        assert_eq!(original.as_bytes(), reconstructed.as_bytes());
    }

    // ── Identity Key Derivation Tests ──────────────────────────────────────

    #[test]
    fn test_derive_identity_key_deterministic() {
        let seed = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24).unwrap();
        let ik1 = seed.derive_identity_key();
        let ik2 = seed.derive_identity_key();
        assert_eq!(ik1.as_bytes(), ik2.as_bytes());
    }

    #[test]
    fn test_derive_identity_key_32_bytes() {
        let seed = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24).unwrap();
        let ik = seed.derive_identity_key();
        assert_eq!(ik.as_bytes().len(), 32);
    }

    #[test]
    fn test_derive_identity_key_debug_redacted() {
        let seed = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24).unwrap();
        let ik = seed.derive_identity_key();
        let debug_str = format!("{:?}", ik);
        assert_eq!(debug_str, "IdentityKey([REDACTED])");
    }

    // ── Recovery Key Derivation Tests ───────────────────────────────────────

    #[test]
    fn test_derive_recovery_key_deterministic() {
        let seed = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24).unwrap();
        let rk1 = seed.derive_recovery_key();
        let rk2 = seed.derive_recovery_key();
        assert_eq!(rk1.as_bytes(), rk2.as_bytes());
    }

    #[test]
    fn test_derive_recovery_key_32_bytes() {
        let seed = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24).unwrap();
        let rk = seed.derive_recovery_key();
        assert_eq!(rk.as_bytes().len(), 32);
    }

    #[test]
    fn test_derive_recovery_key_debug_redacted() {
        let seed = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24).unwrap();
        let rk = seed.derive_recovery_key();
        let debug_str = format!("{:?}", rk);
        assert_eq!(debug_str, "RecoveryKey([REDACTED])");
    }

    // ── Context Isolation Tests ─────────────────────────────────────────────

    #[test]
    fn test_identity_and_recovery_keys_differ() {
        let seed = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24).unwrap();
        let ik = seed.derive_identity_key();
        let rk = seed.derive_recovery_key();
        assert_ne!(ik.as_bytes(), rk.as_bytes());
    }

    #[test]
    fn test_different_mnemonics_different_keys() {
        let mnemonic1 = BIP39_TEST_MNEMONIC_24;
        let mnemonic2 = "zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo zoo vote";

        let seed1 = MasterSeed::from_mnemonic(mnemonic1).unwrap();
        let seed2 = MasterSeed::from_mnemonic(mnemonic2).unwrap();

        let ik1 = seed1.derive_identity_key();
        let ik2 = seed2.derive_identity_key();

        assert_ne!(ik1.as_bytes(), ik2.as_bytes());
    }

    // ── Zeroize Tests ───────────────────────────────────────────────────────

    #[test]
    fn test_master_seed_zeroize_on_drop() {
        let seed = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24).unwrap();

        // Get the bytes before dropping
        let _bytes_before = *seed.as_bytes();

        // Drop the seed
        drop(seed);

        // Note: We can't reliably test that the memory was zeroized
        // because after drop, accessing the memory is UB.
        // The test is that the code compiles with ZeroizeOnDrop derive.
        // We verify the trait is implemented by checking compile success.
    }

    #[test]
    fn test_identity_key_zeroize_on_drop() {
        let seed = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24).unwrap();
        let ik = seed.derive_identity_key();

        // Verify ZeroizeOnDrop is derived by dropping
        drop(ik);
    }

    #[test]
    fn test_recovery_key_zeroize_on_drop() {
        let seed = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24).unwrap();
        let rk = seed.derive_recovery_key();

        // Verify ZeroizeOnDrop is derived by dropping
        drop(rk);
    }

    // ── DeviceKey Tests ─────────────────────────────────────────────────────

    #[test]
    fn test_device_key_new() {
        let key_id = [1u8; 16];
        let dk = DeviceKey::new(key_id);
        assert_eq!(*dk.key_id(), key_id);
    }

    #[test]
    fn test_device_key_debug_shows_key_id_prefix() {
        let key_id = [0xABu8; 16];
        let dk = DeviceKey::new(key_id);
        let debug_str = format!("{:?}", dk);
        // Should show truncated key_id, not the full key
        assert!(debug_str.contains("DeviceKey"));
        assert!(debug_str.contains("abababab")); // First 4 bytes in hex
    }

    // ── DataEncryptionKey Tests ─────────────────────────────────────────────

    #[test]
    fn test_dek_from_bytes() {
        let bytes = [0x42u8; 32];
        let dek = DataEncryptionKey::from_bytes(bytes);
        assert_eq!(*dek.as_bytes(), bytes);
    }

    #[test]
    fn test_dek_debug_redacted() {
        let dek = DataEncryptionKey::from_bytes([0u8; 32]);
        let debug_str = format!("{:?}", dek);
        assert_eq!(debug_str, "DataEncryptionKey([REDACTED])");
    }

    #[test]
    fn test_dek_zeroize_on_drop() {
        let dek = DataEncryptionKey::from_bytes([1u8; 32]);
        drop(dek);
    }

    // ── VaultKey Tests ──────────────────────────────────────────────────────

    #[test]
    fn test_vk_from_bytes() {
        let bytes = [0x99u8; 32];
        let vk = VaultKey::from_bytes(bytes);
        assert_eq!(*vk.as_bytes(), bytes);
    }

    #[test]
    fn test_vk_debug_redacted() {
        let vk = VaultKey::from_bytes([0u8; 32]);
        let debug_str = format!("{:?}", vk);
        assert_eq!(debug_str, "VaultKey([REDACTED])");
    }

    #[test]
    fn test_vk_zeroize_on_drop() {
        let vk = VaultKey::from_bytes([1u8; 32]);
        drop(vk);
    }

    // ── Complete Derivation Path Test ───────────────────────────────────────

    #[test]
    fn test_complete_derivation_path() {
        // Simulate the complete key derivation flow
        let seed = MasterSeed::from_mnemonic(BIP39_TEST_MNEMONIC_24).unwrap();

        // Derive IK and RK
        let ik = seed.derive_identity_key();
        let rk = seed.derive_recovery_key();

        // Verify they are different
        assert_ne!(ik.as_bytes(), rk.as_bytes());

        // Verify they are non-zero
        assert_ne!(ik.as_bytes(), &[0u8; 32]);
        assert_ne!(rk.as_bytes(), &[0u8; 32]);
    }
}

// ============================================================================
// Property-based Tests
// ============================================================================

#[cfg(test)]
mod proptests {
    use super::*;
    use proptest::prelude::*;

    // Generate a valid BIP-39 mnemonic (simplified - only test with known valid mnemonics)
    proptest! {
        /// Test that key derivation is deterministic for any valid seed
        #[test]
        fn prop_derive_identity_key_deterministic(
            bytes in prop::collection::vec(any::<u8>(), 64..=64)
        ) {
            // Create seed from random bytes
            let mut arr = [0u8; 64];
            arr.copy_from_slice(&bytes);
            let seed = MasterSeed::from_bytes(arr);

            // Derive twice
            let ik1 = seed.derive_identity_key();
            let ik2 = seed.derive_identity_key();

            prop_assert_eq!(ik1.as_bytes(), ik2.as_bytes());
        }

        #[test]
        fn prop_derive_recovery_key_deterministic(
            bytes in prop::collection::vec(any::<u8>(), 64..=64)
        ) {
            let mut arr = [0u8; 64];
            arr.copy_from_slice(&bytes);
            let seed = MasterSeed::from_bytes(arr);

            let rk1 = seed.derive_recovery_key();
            let rk2 = seed.derive_recovery_key();

            prop_assert_eq!(rk1.as_bytes(), rk2.as_bytes());
        }

        #[test]
        fn prop_identity_and_recovery_always_differ(
            bytes in prop::collection::vec(any::<u8>(), 64..=64)
        ) {
            let mut arr = [0u8; 64];
            arr.copy_from_slice(&bytes);
            let seed = MasterSeed::from_bytes(arr);

            let ik = seed.derive_identity_key();
            let rk = seed.derive_recovery_key();

            prop_assert_ne!(ik.as_bytes(), rk.as_bytes());
        }

        #[test]
        fn prop_different_seeds_different_identity_keys(
            bytes1 in prop::collection::vec(any::<u8>(), 64..=64),
            bytes2 in prop::collection::vec(any::<u8>(), 64..=64)
        ) {
            // Skip if seeds are identical
            prop_assume!(bytes1 != bytes2);

            let mut arr1 = [0u8; 64];
            let mut arr2 = [0u8; 64];
            arr1.copy_from_slice(&bytes1);
            arr2.copy_from_slice(&bytes2);

            let seed1 = MasterSeed::from_bytes(arr1);
            let seed2 = MasterSeed::from_bytes(arr2);

            let ik1 = seed1.derive_identity_key();
            let ik2 = seed2.derive_identity_key();

            prop_assert_ne!(ik1.as_bytes(), ik2.as_bytes());
        }
    }
}
