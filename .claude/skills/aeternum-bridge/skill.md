# Aeternum Bridge Management

Aeternum UniFFI 桥接层管理技能

## User-invocable skill

Use this skill when managing the Rust ↔ Kotlin UniFFI bridge layer in the Aeternum project.

## Trigger words

- UDL, interface definition, UniFFI
- Bridge code, FFI interface, Kotlin generation
- Add interface method, modify UDL
- Rust-Kotlin bridge, cross-language binding

## Context

The Aeternum project uses UniFFI to bridge Rust cryptographic core with Kotlin Android layer.

**Core principles:**

**Handle-based access:**
- Kotlin must NOT hold plaintext key `ByteArray`
- All decryption operations in Rust
- Return sanitized data or handles only

**Two-phase commit:**
- Rust generates data stream
- Kotlin executes atomic rename

## UDL interface location

`core/uniffi/aeternum.udl`

## Standard UDL template

```idl
namespace aeternum {
    [Error]
    interface AeternumError {
        CryptoError(string reason);
        EpochError(string reason);
        StorageError(string reason);
        IntegrityError(string reason);
    };

    dictionary RekeyResult {
        sequence<u8> new_vault_blob;
        u32 new_epoch;
    };

    interface VaultSession {
        [Throws=AeternumError]
        string decrypt_field(string record_id, string field_key);
        void lock();
    };

    interface AeternumEngine {
        [Throws=AeternumError]
        constructor();

        [Throws=AeternumError]
        VaultSession unlock(sequence<u8> header_blob);
    };
}
```

## Workflow

### 1. Modify UDL interface

Add or modify interface in `core/uniffi/aeternum.udl`:

```idl
interface VaultSession {
    // Add new method
    [Throws=AeternumError]
    string get_record_metadata(string record_id);
}
```

### 2. Implement in Rust

In `core/src/lib.rs`:

```rust
uniffi::include_interface!("aeternum");

#[derive(uniffi::Object)]
pub struct VaultSession {
    // ...
}

#[uniffi::export]
impl VaultSession {
    pub fn get_record_metadata(&self, record_id: String) -> Result<String, AeternumError> {
        // INVARIANT: Return sanitized data only
        Ok("...".to_string())
    }
}
```

### 3. Generate bridge code

Run `./scripts/generate-bridge.sh`:

```bash
#!/bin/bash
set -e
echo "=== Generating UniFFI bridge code ==="
cd core
cargo run --bin uniffi-bindgen -- \
    uniffi/aeternum.udl \
    --language kotlin \
    --out-dir ../android/app/src/main/kotlin/aeternum/
echo "=== Bridge code generated ==="
```

### 4. Use in Kotlin

```kotlin
// android/app/src/main/kotlin/io/aeternum/bridge/AeternumBridge.kt
import aeternum.AeternumEngine

object AeternumBridge {
    private lateinit var engine: AeternumEngine

    fun initialize() {
        engine = AeternumEngine()
    }

    fun unlockVault(headerBlob: ByteArray): VaultSessionHandle {
        val session = engine.unlock(headerBlob.toList())
        return VaultSessionHandle(session)
    }
}
```

## Type mapping (Rust → Kotlin)

| Rust | Kotlin |
|------|--------|
| `String` | `String` |
| `Vec<u8>` | `List<Byte>` (pass as `toList()`) |
| `i32/u32` | `Int` |
| `i64/u64` | `Long` |
| `bool` | `Boolean` |
| `Option<T>` | `T?` |
| `Result<T, E>` | `T` (throws exception) |

## Dependencies

### Cargo.toml

```toml
[dependencies]
uniffi = "0.28"

[build-dependencies]
uniffi = { version = "0.28", features = ["build"] }
```

### build.gradle.kts

```kotlin
dependencies {
    implementation("org.mozilla.uniffi:uniffi-bindgen:0.28.0")
    implementation("org.mozilla.uniffi:uniffi-kotlin:0.28.0")
}
```

## After code generation

Ask the user:
1. Build Rust core? (run `./scripts/build-core.sh`)
2. Verify Kotlin code synced
