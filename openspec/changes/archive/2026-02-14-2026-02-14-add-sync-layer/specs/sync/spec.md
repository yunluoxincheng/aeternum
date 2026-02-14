# sync Specification Deltas (add-sync-layer)

## ADDED Requirements

### Requirement: Wire Frame Format

The system SHALL implement Aeternum-Frame protocol with fixed 8192-byte size for traffic fingerprinting resistance.

#### Scenario: Frame serialization

- **WHEN** a message is serialized into WireFrame
- **THEN** the total size SHALL always be 8192 bytes
- **AND** nonce SHALL be 24 bytes (XChaCha20-Poly1305)
- **AND** epoch SHALL be 4 bytes (plaintext, for routing)
- **AND** payload_type SHALL be 1 byte (encrypted)
- **AND** auth_tag SHALL be 16 bytes (Poly1305)
- **AND** padding SHALL fill remaining space with random bytes

#### Scenario: Frame deserialization

- **WHEN** a WireFrame is deserialized
- **THEN** auth_tag MUST be verified using AEAD
- **AND** frames with invalid authentication MUST be rejected
- **AND** padding MUST be discarded after decryption

### Requirement: Hybrid Handshake Protocol

The system SHALL establish secure channels using X25519 + Kyber-1024 hybrid key exchange for post-quantum security.

#### Scenario: Handshake initiation

- **WHEN** a device initiates pairing
- **THEN** it SHALL generate X25519 keypair
- **AND** it SHALL generate Kyber-1024 keypair
- **AND** it SHALL combine public keys (X25519_PK || Kyber_PK)

#### Scenario: Handshake completion

- **WHEN** both parties complete hybrid handshake
- **THEN** session key SHALL be derived via HKDF-SHA256(SS_X25519 || SS_Kyber || Context_ID)
- **AND** session key SHALL be used for all subsequent AEAD encryption

### Requirement: Traffic Obfuscation

The system SHALL implement traffic fingerprinting protection through padding, chaff sync, and timing jitter.

#### Scenario: Padding enforcement

- **WHEN** any WireFrame is constructed
- **THEN** total length MUST equal 8192 bytes
- **AND** padding MUST use cryptographically secure random bytes
- **AND** empty payload SHALL generate maximum padding

#### Scenario: Chaff sync generation

- **WHEN** chaff traffic is generated
- **THEN** chaff frames MUST be indistinguishable from real frames
- **AND** chaff frequency SHALL be randomized to mask actual sync patterns

#### Scenario: Timing jitter

- **WHEN** server responses are sent
- **THEN** response delay SHALL include 50-200ms random jitter
- **AND** jitter SHALL prevent timing attack analysis

### Requirement: Message Codec

The system SHALL provide message serialization and deserialization with automatic AEAD encryption.

#### Scenario: Message encoding

- **WHEN** a Payload is encoded
- **THEN** it SHALL be serialized to bytes
- **AND** encrypted using XChaCha20-Poly1305
- **AND** encapsulated in WireFrame with padding
- **AND** authenticated with Poly1305 tag

#### Scenario: Message decoding

- **WHEN** a WireFrame is decoded
- **THEN** auth_tag MUST be verified first
- **AND** decryption MUST fail if tag is invalid
- **AND** padding MUST be stripped
- **AND** payload MUST be deserialized

### Requirement: Veto Signaling

The system SHALL implement veto signal broadcasting with highest priority for recovery protection (Invariant #4).

#### Scenario: Veto signal validation

- **WHEN** a veto signal is received
- **THEN** device signature MUST be verified using StrongBox
- **AND** device MUST be in active devices set
- **AND** veto MUST be within 48-hour recovery window
- **AND** valid veto MUST immediately terminate recovery

#### Scenario: Veto priority

- **WHEN** any valid veto signal is present
- **THEN** recovery process MUST be aborted
- **AND** all devices MUST be notified of RECOVERY_ABORTED
- **AND** recovery context MUST be deleted

### Requirement: Protocol Versioning

The system SHALL support protocol version negotiation and backward compatibility.

#### Scenario: Version compatibility check

- **WHEN** two devices communicate
- **THEN** protocol versions MUST be compared
- **AND** same major version SHALL be compatible
- **AND** newer major version MAY require upgrade
- **AND** older client SHALL read data but NOT initiate PQRR

#### Scenario: Forced upgrade

- **WHEN** security fix requires protocol upgrade
- **THEN** server MAY send PROTOCOL_UPGRADE_REQUIRED
- **AND** client MUST block operations until upgrade
