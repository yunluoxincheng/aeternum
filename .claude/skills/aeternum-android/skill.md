# Aeternum Android Development

Aeternum Android 安全层与 UI 开发技能

## User-invocable skill

Use this skill when implementing Android security layer or UI components for the Aeternum project.

## Trigger words

- Jetpack Compose, UI, screen, interface
- BiometricPrompt, fingerprint, face unlock
- StrongBox, KeyStore, hardware key
- Play Integrity, device integrity, security verification
- Degraded mode, secure mode
- Vault screen, unlock screen

## Context

The Aeternum Android app has a three-layer security architecture:

| Layer | Trusted | Holds plaintext keys? |
|-------|---------|---------------------|
| UI Layer (Compose) | ❌ | ❌ |
| Security Layer (Kotlin) | ✅ (partial) | ❌ (hardware handles only) |
| Rust Core | ✅ | ✅ |

## Security boundaries

**PROHIBITED:**
- ❌ Implement any cryptographic logic in Kotlin
- ❌ Log keys in any form
- ❌ Hold plaintext keys as `ByteArray`
- ❌ Use Flutter, React Native, WebView as main UI

**REQUIRED:**
- ✅ Only hold Rust instance handles
- ✅ Plaintext "ephemeral" in memory only
- ✅ Use Class 3 biometric authentication
- ✅ Use Play Integrity API for device verification

## Output locations

- UI components: `android/app/src/main/kotlin/io/aeternum/ui/`
- Security layer: `android/app/src/main/kotlin/io/aeternum/security/`
- Data layer: `android/app/src/main/kotlin/io/aeternum/data/`
- Bridge: `android/app/src/main/kotlin/io/aeternum/bridge/`

## Code templates

### Biometric authenticator

```kotlin
class BiometricAuthenticator(
    private val activity: FragmentActivity,
    private val bridge: AeternumBridge,
) {
    suspend fun authenticate(): BiometricResult {
        // INVARIANT: UI Layer - Handles auth flow only, no keys
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("验证身份以访问 Aeternum")
            .setAllowedAuthenticators(BiometricPrompt.AUTHENTICATOR_BIOMETRIC_STRONG)
            .build()

        // After biometric success, request Rust core to unlock
        val session = bridge.unlockVault()
        return BiometricResult.Success(session)
    }
}

sealed class BiometricResult {
    data class Success(val session: VaultSessionHandle) : BiometricResult()
    data class Failed(val reason: String) : BiometricResult()
    data object Cancelled : BiometricResult()
}
```

### VaultSession handle

```kotlin
/**
 * VaultSession Handle
 *
 * INVARIANT: Handle-based Access - Kotlin holds handle, plaintext in Rust
 */
class VaultSessionHandle internal constructor(
    internal val nativePtr: Long,
) {
    fun decryptField(recordId: String, fieldKey: String): String {
        // INVARIANT: Security Boundary - Decryption in Rust, plaintext not in Kotlin
        return AeternumBridge.nativeDecryptField(nativePtr, recordId, fieldKey)
    }

    fun lock() {
        // INVARIANT: Memory Safety - Triggers Rust zeroize
        AeternumBridge.nativeLockSession(nativePtr)
    }
}
```

### Compose UI template

```kotlin
@Composable
fun VaultScreen(
    viewModel: VaultViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // INVARIANT: UI Layer - Display sanitized data only
    when (val state = uiState) {
        is VaultUiState.Locked -> {
            LockedVaultContent(onUnlockRequest = { viewModel.requestUnlock() })
        }
        is VaultUiState.Unlocked -> {
            UnlockedVaultContent(
                session = state.session,
                recordIds = state.recordIds,
                onDecryptField = { recordId, fieldKey ->
                    viewModel.decryptField(state.session, recordId, fieldKey)
                },
            )
        }
    }
}

sealed class VaultUiState {
    data object Locked : VaultUiState()
    data class Unlocked(val session: VaultSessionHandle, val recordIds: List<String>) : VaultUiState()
    data class Error(val message: String) : VaultUiState()
}
```

## Android state machine

```
Uninitialized → Initializing → Active (Idle/Decrypting/Rekeying)
                                    ↓         ↓
                               Degraded ← Revoked
```

## After code generation

Ask the user:
1. Add unit tests?
2. If new Rust interface needed, suggest `aeternum-bridge` skill
3. Build APK?
