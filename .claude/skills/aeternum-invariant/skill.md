# Aeternum Invariant Verification

Aeternum 四大数学不变量验证技能

## User-invocable skill

Use this skill when verifying code compliance with the four mathematical invariants in the Aeternum project.

## Trigger words

- Verify invariants, check security
- Invariant check, epoch monotonicity
- Header completeness, causal entropy barrier
- Veto supremacy, veto priority
- Generate report, security audit

## Context

The Aeternum project enforces four mathematical invariants. Violations trigger circuit breaker: kernel lock → state isolation → user alert.

## The Four Invariants

### Invariant #1 — Epoch Monotonicity

**Statement:** All device epochs must strictly increase, no rollback allowed.

**Formula:** `∀ d1, d2 ∈ D_active ⟹ epoch(d1) = epoch(d2) = S_epoch`

**Checks:**
- `assert!(new_epoch > current_epoch)` assertion
- Epoch comparison logic
- Rollback rejection mechanism

**Example code:**
```rust
// ✅ Correct
if new_epoch <= self.current_epoch {
    return Err(PqrrError::EpochRegression { current, attempted: new_epoch });
}

// ❌ Wrong - Missing check
self.current_epoch = new_epoch;
```

### Invariant #2 — Header Completeness

**Statement:** Each active device must have exactly one valid header to access DEK.

**Formula:** `∀ d ∈ D_active ⟺ ∃! h ∈ Ve.H: unwrap(h, d) = DEK_e`

**Checks:**
- Unwrap probe verification
- Header uniqueness check
- Revoked device header removal

**Example code:**
```rust
// ✅ Correct - With unwrap probe
let header = generate_device_header(&dek, pk)?;
let unwrap_result = unwrap_header(&header, sk)?;
assert!(unwrap_result.is_ok(), "Header must be unwrapable");

// ✅ Correct - Includes Device_0
new_headers.insert("device_0".to_string(), device_0_header);

// ❌ Wrong - Missing Device_0
```

### Invariant #3 — Causal Entropy Barrier

**Statement:** Decryption authority ≠ Management authority (RECOVERY role cannot execute σ_rotate).

**Formula:** `Role(S) = RECOVERY ⟹ σ_rotate ∉ P(S)`

**Checks:**
- Permission separation logic
- RECOVERY role restrictions
- Root rotation dual verification

**Example code:**
```rust
// ✅ Correct - Permission check
pub fn execute_root_rotation(&self, role: Role) -> Result<(), PqrrError> {
    if role == Role::Recovery {
        return Err(PqrrError::InsufficientPrivileges);
    }
}

// ❌ Wrong - Missing permission check
pub fn execute_root_rotation(&self, role: Role) -> Result<(), PqrrError> {
    // Direct execution, no permission verification
}
```

### Invariant #4 — Veto Supremacy

**Statement:** Any active device's Veto signal within 48h window must immediately terminate recovery.

**Formula:** `Status(req) = COMMITTED ⟹ (t_now ≥ T_start + ΔT_window) ∧ (Vetoes(req) = ∅)`

**Checks:**
- Veto signal priority queue
- Time window check
- Conflict collapse handling

**Example code:**
```rust
// ✅ Correct - Veto priority check
pub fn finalize_recovery(&self) -> Result<(), RecoveryError> {
    if !self.vetoes.is_empty() {
        return Err(RecoveryError::Vetoed);
    }
    // ... time window check
}

// ❌ Wrong - No veto check
pub fn finalize_recovery(&self) -> Result<(), RecoveryError> {
    // Direct completion, no veto check
}
```

## Output report format

```markdown
## Aeternum Invariant Compliance Report

Generated: 2025-01-15 10:30:00
Scope: core/src/storage/epoch.rs, core/src/protocol/pqrr.rs

---

### Invariant #1 — Epoch Monotonicity
✅ **Passed**

Checks:
- ✅ epoch.rs:42 - `assert!(header.epoch > self.current_epoch)`
- ✅ pqrr.rs:128 - Rollback rejection logic

---

### Invariant #2 — Header Completeness
⚠️ **Suggested Improvement**

Checks:
- ✅ pqrr.rs:85 - Headers for each active device
- ⚠️ Suggestion: Add unwrap probe verification

---

### Summary
- Passed: 3/4
- Suggested improvements: 1/4
- Critical violations: 0/4
```

## Auto-check patterns

The skill can automatically:
1. Search all `epoch` related code for monotonicity checks
2. Search all `Header` generation for completeness
3. Search `RECOVERY` role usage for permission separation
4. Search `veto` related code for priority handling

## After verification

If violations found:
1. Suggest fix approach
2. Offer to trigger appropriate skill (`aeternum:protocol` or `aeternum:android`)
3. Re-verify after fix
