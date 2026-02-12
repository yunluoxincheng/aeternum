# Aeternum Crypto Development

Aeternum 密码学原语开发技能

## User-invocable skill

Use this skill when implementing cryptographic primitives or memory safety features in the Aeternum project.

## Trigger words

- Kyber, KEM, encapsulation, decapsulation
- X25519, ECDH, key exchange
- XChaCha20, AEAD, encryption, decryption
- Argon2, KDF, key derivation
- BLAKE3, hash
- zeroize, memory safety, secure memory

## Context

The Aeternum project is a post-quantum secure mobile key management system with a Rust cryptographic core.

**Architecture:**
```
Android UI (Jetpack Compose) - Non-trust domain
    ↓
Android Security Layer - Trust domain (hardware key handles only)
    ↓
Rust Core - Root trust domain (all key operations here)
```

**Key principles:**
1. All sensitive data structures must implement `zeroize::Zeroize`
2. Use `Zeroizing<Vec<u8>>` for temporary keys
3. Keys must never leave Rust memory in plaintext
4. All cryptographic code must be tested (100% coverage target)

## Output location

- Code: `core/src/crypto/`
- Tests: `core/src/crypto/tests/`

## Dependencies to add in core/Cargo.toml

- `pqcrypto-kyber` - ML-KEM (Kyber-1024)
- `x25519-dalek` - X25519 key exchange
- `chacha20poly1305` - XChaCha20-Poly1305 AEAD
- `argon2` - Argon2id KDF
- `blake3` - BLAKE3 hash
- `zeroize` - Memory erasure
- `thiserror` - Error handling

## Code template

### Basic KEM implementation

```rust
use zeroize::{Zeroize, Zeroizing};

#[derive(Debug, Zeroize)]
pub struct EncapsulationKey {
    pub ciphertext: Vec<u8>,
    pub shared_secret: Zeroizing<Vec<u8>>,
}

pub fn encapsulate(public_key: &[u8]) -> Result<EncapsulationKey, CryptoError> {
    // INVARIANT#1: Input validation
    if public_key.is_empty() {
        return Err(CryptoError::InvalidInput("Public key cannot be empty".into()));
    }

    // Perform encapsulation...
    Ok(EncapsulationKey {
        ciphertext,
        shared_secret: Zeroizing::new(shared_secret),
    })
}
```

## Invariant annotations

Always add invariant comments:
```rust
// INVARIANT: Memory Safety - All sensitive structs implement Zeroize
// INVARIANT: No key leakage - Keys never leave Rust memory boundary
```

## Testing requirements

- Unit tests for all functions
- Property tests using proptest
- Verify Zeroize trait implementation

## After code generation

Ask the user:
1. Run `cargo test` to verify?
2. If exposing to Kotlin layer, suggest using `aeternum-bridge` skill
