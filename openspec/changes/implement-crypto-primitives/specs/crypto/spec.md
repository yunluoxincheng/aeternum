# Cryptographic Primitives Specification Delta

## ADDED Requirements

### Requirement: BLAKE3 Hashing

The system SHALL provide BLAKE3 hash functionality through a type-safe API that prevents misuse and ensures memory safety.

#### Scenario: Hash arbitrary data

- **WHEN** arbitrary bytes are provided to the `hash()` function
- **THEN** a 32-byte `HashOutput` SHALL be returned
- **AND** the output SHALL be deterministic for identical inputs

#### Scenario: Incremental hashing

- **WHEN** data is provided in chunks via `Blake3Hasher::update()`
- **THEN** `finalize()` SHALL return the same hash as hashing the concatenated data

#### Scenario: Key derivation mode

- **WHEN** `DeriveKey::derive()` is called with IKM, context info, and output length
- **THEN** a cryptographically derived key of the requested length SHALL be returned
- **AND** the same inputs SHALL always produce the same output

### Requirement: Argon2id Key Derivation

The system SHALL provide Argon2id KDF with secure default parameters and configurable memory/time costs.

#### Scenario: Derive key with default parameters

- **WHEN** `Argon2idKDF::new()` is used with default configuration
- **THEN** OWASP 2024 recommended parameters SHALL be applied (m_cost=64MB, t_cost=3, p_cost=4)
- **AND** key derivation SHALL complete in under 500ms on typical Android devices

#### Scenario: Derive key with custom parameters

- **WHEN** `Argon2idKDF::with_config()` is called with custom parameters
- **THEN** the system SHALL validate m_cost >= 8192 and t_cost >= 1
- **AND** SHALL return an error if validation fails

#### Scenario: Salt requirement

- **WHEN** `derive_key()` is called with a salt shorter than 16 bytes
- **THEN** the system SHALL return a `CryptoError::InvalidKeyLength`

### Requirement: XChaCha20-Poly1305 AEAD

The system SHALL provide authenticated encryption using XChaCha20-Poly1305 with automatic nonce management support.

#### Scenario: Encrypt and decrypt

- **WHEN** plaintext is encrypted with a unique nonce
- **THEN** the ciphertext can only be decrypted with the same key and nonce
- **AND** decryption SHALL fail if the ciphertext or associated data is modified

#### Scenario: Authenticated data

- **WHEN** associated data (AAD) is provided during encryption
- **THEN** the AAD is authenticated but not encrypted
- **AND** modification of AAD SHALL cause decryption to fail

#### Scenario: In-place encryption

- **WHEN** `encrypt_in_place()` is called on a mutable buffer
- **THEN** the buffer SHALL be overwritten with ciphertext
- **AND** the original plaintext SHALL be securely erased

### Requirement: Kyber-1024 Key Encapsulation

The system SHALL provide post-quantum secure key encapsulation using Kyber-1024 (ML-KEM).

#### Scenario: Generate keypair

- **WHEN** `KyberKEM::generate_keypair()` is called
- **THEN** a new keypair SHALL be generated
- **AND** the public key SHALL be 1184 bytes
- **AND** the secret key SHALL be 2400 bytes

#### Scenario: Encapsulate shared secret

- **WHEN** a sender calls `encapsulate()` with a recipient's public key
- **THEN** a 32-byte shared secret and 1568-byte ciphertext SHALL be returned
- **AND** only the holder of the corresponding secret key can recover the shared secret

#### Scenario: Decapsulate shared secret

- **WHEN** a recipient calls `decapsulate()` with their secret key and the ciphertext
- **THEN** the original 32-byte shared secret SHALL be returned
- **AND** a modified ciphertext SHALL cause decapsulation to fail

### Requirement: X25519 ECDH

The system SHALL provide classic key exchange using X25519 elliptic curve Diffie-Hellman.

#### Scenario: Generate keypair

- **WHEN** `X25519ECDH::generate_keypair()` is called
- **THEN** a new keypair SHALL be generated
- **AND** both public and secret keys SHALL be 32 bytes

#### Scenario: Diffie-Hellman key agreement

- **WHEN** two parties perform `diffie_hellman()` with their secret and the other's public key
- **THEN** both SHALL derive the same shared secret
- **AND** the shared secret SHALL be 32 bytes

#### Scenario: Invalid key handling

- **WHEN** a key is loaded from bytes that is not a valid X25519 key
- **THEN** the system SHALL return a `CryptoError`

### Requirement: Hybrid Key Exchange

The system SHALL provide a hybrid key exchange combining Kyber-1024 and X25519 for defense-in-depth.

#### Scenario: Combine shared secrets

- **WHEN** `HybridKeyExchange::combine_secrets()` is called with Kyber and X25519 shared secrets
- **THEN** a combined 64-byte secret SHALL be returned using BLAKE3 derivation
- **AND** compromise of either individual secret SHALL not reveal the combined secret

### Requirement: Memory Safety

The system SHALL ensure all cryptographic material is securely erased from memory when no longer needed.

#### Scenario: Automatic zeroization

- **WHEN** a key type implementing `Zeroize` goes out of scope
- **THEN** the underlying memory SHALL be overwritten with zeros before deallocation
- **AND** this SHALL apply to all secret keys, shared secrets, and derived keys

#### Scenario: Public keys not zeroized

- **WHEN** a public key type is dropped
- **THEN** the memory SHALL NOT be zeroized (public keys are not sensitive)

### Requirement: Error Handling

The system SHALL provide detailed, type-safe error reporting for all cryptographic operations.

#### Scenario: Invalid input length

- **WHEN** a key or nonce is provided with incorrect length
- **THEN** `CryptoError::InvalidKeyLength` SHALL be returned with expected and actual lengths

#### Scenario: Cryptographic operation failure

- **WHEN** a cryptographic operation fails (decryption, verification, etc.)
- **THEN** an appropriate error variant SHALL be returned with a descriptive message
- **AND** sensitive data SHALL be zeroized before returning the error

### Requirement: Testing Requirements

All cryptographic primitives SHALL be validated against standard test vectors and property-based tests.

#### Scenario: Standard test vectors

- **WHEN** test vectors from Wycheproof or RFC standards are provided
- **THEN** the implementation SHALL produce identical outputs
- **AND** all test vectors MUST pass

#### Scenario: Property-based testing

- **WHEN** proptest runs with random inputs
- **THEN** cryptographic properties (e.g., decrypt(encrypt(x)) == x) SHALL hold for all valid inputs
- **AND** edge cases (empty input, large input) SHALL be covered
