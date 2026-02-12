//! # Cryptographic Primitives Module
//!
//! This module provides type-safe, memory-safe cryptographic primitives
//! for the Aeternum key management system.
//!
//! ## Design Principles
//!
//! 1. **Type Safety**: All key types are newtype wrappers preventing misuse
//! 2. **Memory Safety**: All secret keys implement `Zeroize`
//! 3. **Minimal Explicit**: Complex parameters have safe defaults
//! 4. **Testability**: Every primitive has comprehensive test vectors
//!
//! ## Module Structure
//!
//! - `error` - Unified error types for all crypto operations
//! - `hash` - BLAKE3 hashing and key derivation
//! - `kdf` - Argon2id key derivation function
//! - `aead` - XChaCha20-Poly1305 authenticated encryption
//! - `kem` - Kyber-1024 post-quantum key encapsulation
//! - `ecdh` - X25519 elliptic curve Diffie-Hellman

// Error handling
pub mod error;

// Cryptographic primitives
pub mod aead;
pub mod ecdh;
pub mod hash;
pub mod kdf;
pub mod kem;

// Re-export common types at the crypto module level
pub use error::{CryptoError, Result};

// Re-export hash types
pub use hash::{hash as blake3_hash, Blake3Hasher, DeriveKey, HashOutput};

// Re-export KDF types
pub use kdf::{Argon2idConfig, Argon2idKDF, DerivedKey};

// Re-export AEAD types
pub use aead::{AeadCipher, AuthTag, XChaCha20Key, XChaCha20Nonce};

// Re-export KEM types
pub use kem::{
    KyberCipherText, KyberKEM, KyberKeyPair, KyberPublicKeyBytes, KyberSecretKeyBytes,
    KyberSharedSecret,
};

// Re-export ECDH types
pub use ecdh::{
    EcdhSharedSecret, HybridKeyExchange, HybridSharedSecret, X25519KeyPair, X25519PublicKeyBytes,
    X25519SecretKeyBytes, X25519ECDH,
};
