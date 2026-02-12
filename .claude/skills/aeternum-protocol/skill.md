# Aeternum Protocol Development

Aeternum 核心协议与状态机开发技能

## User-invocable skill

Use this skill when implementing core protocols or state machine logic in the Aeternum project.

## Trigger words

- PQRR, revocation, rekeying, epoch upgrade
- Shadow Wrapping, Device_0
- Veto mechanism, 48h veto
- Device management, device sync
- Crypto epoch, algorithm migration

## Context

The Aeternum project uses a state machine model for vault management:

```
Uninitialized → Initializing → Active (Idle/Decrypting/Rekeying)
                                    ↓         ↓
                               Degraded ← Revoked
```

**Active sub-states:**
- **Idle**: No keys in memory
- **Decrypting**: DEK/VK in Rust memory only (mlock + zeroize)
- **Rekeying**: Executing PQRR

## Four Mathematical Invariants

### Invariant #1 — Epoch Monotonicity
All device epochs must strictly increase, no rollback allowed.

```rust
// INVARIANT#1: Epoch Monotonicity - new_epoch MUST be > current_epoch
if new_epoch <= self.current_epoch {
    return Err(PqrrError::EpochRegression {
        current: self.current_epoch,
        attempted: new_epoch,
    });
}
```

### Invariant #2 — Header Completeness
Each active device must have exactly one valid header to access DEK.

```rust
// INVARIANT#2: Header Completeness - Each active device has exactly one header
for device_id in &self.active_devices {
    let header = self.generate_device_header(&new_dek, pk)?;
    new_headers.insert(device_id.clone(), header);
}
// Include Device_0 shadow wrapper
new_headers.insert("device_0".to_string(), device_0_header);
```

### Invariant #3 — Causal Entropy Barrier
Decryption authority ≠ Management authority (RECOVERY role cannot execute σ_rotate).

```rust
// INVARIANT#3: Causal Entropy Barrier - RECOVERY role cannot rotate
if session.role == Role::Recovery {
    return Err(PqrrError::InsufficientPrivileges);
}
```

### Invariant #4 — Veto Supremacy
Any active device's Veto signal within 48h window must immediately terminate recovery.

```rust
// INVARIANT#4: Veto Supremacy - Veto signals have highest priority
if !vetoes.is_empty() {
    return Err(PqrrError::Vetoed);
}
```

## PQRR Protocol Steps

1. **Epoch upgrade preparation**: Derive new DEK
2. **Rewrap VK**: Re-encapsulate Vault Key with new DEK
3. **Generate new Headers**: For all active devices (including Device_0)
4. **Atomic commit**: Shadow write → Atomic rename → Metadata update

## Output location

- `core/src/protocol/pqrr.rs` - PQRR protocol
- `core/src/protocol/epoch.rs` - Epoch management
- `core/src/protocol/shadow.rs` - Shadow wrapping
- `core/src/protocol/veto.rs` - Veto mechanism

## Core data structures

```rust
pub struct Epoch {
    pub version: u32,
    pub dek: Zeroizing<Vec<u8>>,
}

pub struct DeviceHeader {
    pub device_id: String,
    pub epoch: u32,
    pub wrapped_dek: Vec<u8>,
}

pub struct VaultBlob {
    pub crypto_epoch: u32,
    pub headers: BTreeMap<String, DeviceHeader>,
    pub wrapped_vk: Vec<u8>,
}
```

## Testing requirements

- Verify epoch monotonicity (no rollback)
- Verify Header completeness (each device has header)
- Verify atomicity (no hybrid state)
- Verify Device_0 shadow wrapping indistinguishability

## After code generation

Ask the user:
1. Run `cargo test`?
2. If exposing to Kotlin layer, suggest `aeternum-bridge` skill
3. If UI support needed, suggest `aeternum-android` skill
