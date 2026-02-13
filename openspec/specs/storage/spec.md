# storage Specification

## Purpose
TBD - created by archiving change add-storage-engine. Update Purpose after archive.
## Requirements
### Requirement: 影子写入机制

The system SHALL provide atomic file update operations using shadow writing to prevent data corruption from crashes.

#### Scenario: Begin shadow write

- **WHEN** `ShadowWriter::begin_shadow_write()` is called
- **THEN** system SHALL create a temporary file `{base_path}.tmp`
- **AND** temporary file SHALL be in the same directory as base path
- **AND** temporary file SHALL NOT replace the original file until explicitly committed

#### Scenario: Write and sync

- **WHEN** `ShadowFile::write_and_sync(data)` is called
- **THEN** system SHALL write data to the temporary file
- **AND** system SHALL call `File::sync_all()` to force physical disk write
- **AND** system SHALL return error if fsync fails
- **AND** data SHALL NOT be considered committed until fsync succeeds

#### Scenario: Atomic commit

- **WHEN** `ShadowWriter::commit_shadow_write(temp_file)` is called
- **THEN** system SHALL call `std::fs::rename()` to atomically replace original file
- **AND** on POSIX systems (Android/Linux), rename SHALL be atomic
- **AND** system SHALL ensure either old file or new file exists, never an intermediate state
- **AND** system SHALL return error if rename fails

#### Scenario: Temporary file cleanup on drop

- **WHEN** `ShadowFile` is dropped without explicit commit
- **THEN** system SHALL automatically delete the temporary file
- **AND** system SHALL NOT leave residual `.tmp` files in the filesystem

### Requirement: 崩溃恢复逻辑

The system SHALL detect and repair inconsistent storage states that result from crashes during atomic upgrade operations.

#### Scenario: Consistency check - State A (Consistent)

- **WHEN** `CrashRecovery::check_consistency()` is called
- **AND** metadata_epoch equals blob_header.epoch
- **THEN** system SHALL return `ConsistencyState::Consistent`
- **AND** system SHALL allow normal startup

#### Scenario: Consistency check - State B (BlobAhead)

- **WHEN** `CrashRecovery::check_consistency()` is called
- **AND** blob_header.epoch is greater than metadata_epoch
- **THEN** system SHALL return `ConsistencyState::BlobAhead`
- **AND** system SHALL indicate automatic healing is available

#### Scenario: Consistency check - State C (MetadataAhead)

- **WHEN** `CrashRecovery::check_consistency()` is called
- **AND** blob_header.epoch is less than metadata_epoch
- **THEN** system SHALL return `ConsistencyState::MetadataAhead`
- **AND** system SHALL indicate potential rollback attack or corruption

#### Scenario: Auto-heal BlobAhead state

- **WHEN** `CrashRecovery::heal_blob_ahead(blob_epoch)` is called
- **THEN** system SHALL update metadata database to match blob epoch
- **AND** system SHALL commit the transaction
- **AND** system SHALL return success after healing

#### Scenario: Handle MetadataAhead meltdown

- **WHEN** `CrashRecovery::handle_metadata_ahead()` is called
- **THEN** system SHALL triggerFatalError::StorageInconsistency
- **AND** system SHALL invoke meltdown procedure (lockdown → isolation → alert)
- **AND** system SHALL NOT allow normal operation until physical anchor recovery

#### Scenario: Cleanup residual temporary files

- **WHEN** system starts up
- **THEN** system SHALL scan for residual `.tmp` files
- **AND** system SHALL delete any leftover temporary files
- **AND** system SHALL log cleanup actions

### Requirement: 不变量验证

The system SHALL enforce the four mathematical invariants to maintain system security and consistency.

#### Scenario: Epoch monotonicity check (Invariant #1)

- **WHEN** `InvariantValidator::check_epoch_monotonicity(current, new)` is called
- **AND** new_epoch is strictly greater than current_epoch
- **THEN** system SHALL return success
- **AND** system SHALL allow epoch advancement

#### Scenario: Epoch monotonicity violation (Invariant #1)

- **WHEN** `InvariantValidator::check_epoch_monotonicity(current, new)` is called
- **AND** new_epoch is less than or equal to current_epoch
- **THEN** system SHALL return `InvariantViolation::EpochMonotonicity`
- **AND** system SHALL prevent epoch rollback
- **AND** system SHALL log security event

#### Scenario: Header completeness check (Invariant #2)

- **WHEN** `InvariantValidator::check_header_completeness(headers, devices)` is called
- **AND** every active device has exactly one valid header
- **THEN** system SHALL return success
- **AND** system SHALL confirm all devices can decrypt their data

#### Scenario: Header incompleteness violation (Invariant #2)

- **WHEN** `InvariantValidator::check_header_completeness(headers, devices)` is called
- **AND** any active device has zero or more than one valid header
- **THEN** system SHALL return `InvariantViolation::HeaderIncomplete`
- **AND** system SHALL identify the affected device
- **AND** system SHALL prevent decrypt operations

#### Scenario: Causal barrier check (Invariant #3)

- **WHEN** `InvariantValidator::check_causal_barrier(role, operation)` is called
- **AND** role is RECOVERY
- **AND** operation is σ_rotate (root authority modification)
- **THEN** system SHALL return `InvariantViolation::CausalBarrier`
- **AND** system SHALL prevent permission elevation
- **AND** system SHALL enforce decryption permission ≠ management permission

#### Scenario: Causal barrier pass

- **WHEN** `InvariantValidator::check_causal_barrier(role, operation)` is called
- **AND** role is AUTHORIZED
- **OR** operation is NOT σ_rotate
- **THEN** system SHALL return success
- **AND** system SHALL allow the operation

#### Scenario: Veto supremacy check (Invariant #4)

- **WHEN** `InvariantValidator::check_veto_supremacy(status, vetoes, window_start, window_duration)` is called
- **AND** current time is within veto window (typically 48 hours)
- **AND** vetoes collection is not empty
- **THEN** system SHALL return `InvariantViolation::VetoSupremacy`
- **AND** system SHALL prevent recovery completion
- **AND** system SHALL prioritize active device vetoes

#### Scenario: Veto supremacy pass

- **WHEN** `InvariantValidator::check_veto_supremacy(status, vetoes, window_start, window_duration)` is called
- **AND** current time is outside veto window
- **OR** vetoes collection is empty
- **THEN** system SHALL return success
- **AND** system SHALL allow recovery to proceed

### Requirement: 完整性审计

The system SHALL provide integrity verification for stored vault data using cryptographic authentication.

#### Scenario: Verify vault integrity

- **WHEN** `IntegrityAudit::verify_vault_integrity()` is called
- **AND** vault blob AEAD authentication tag is valid
- **AND** vault blob BLAKE3 MAC is valid
- **THEN** system SHALL return `Ok(true)`
- **AND** system SHALL confirm data integrity

#### Scenario: Integrity verification failure

- **WHEN** `IntegrityAudit::verify_vault_integrity()` is called
- **AND** AEAD tag or BLAKE3 MAC is invalid
- **THEN** system SHALL return `StorageError::IntegrityCheckFailed`
- **AND** system SHALL prevent use of corrupted data
- **AND** system SHALL log integrity violation

#### Scenario: Compute vault MAC

- **WHEN** `IntegrityAudit::compute_vault_mac()` is called
- **THEN** system SHALL compute BLAKE3 hash of entire vault blob
- **AND** system SHALL return 32-byte hash
- **AND** same input SHALL always produce same output (deterministic)

### Requirement: 原子纪元升级协议 (AUP)

The system SHALL provide a three-phase atomic upgrade protocol to ensure epoch upgrades never leave data in inconsistent state.

#### Scenario: AUP Phase 1 - Preparation

- **WHEN** AUP preparation is initiated
- **THEN** system SHALL unwrap current vault key (VK_n) in memory
- **AND** system SHALL derive new data encryption key (DEK_n+1) for epoch n+1
- **AND** system SHALL prepare new header (H_n+1) with encapsulated DEK
- **AND** system SHALL NOT modify any persistent storage yet

#### Scenario: AUP Phase 2 - Shadow writing

- **WHEN** AUP shadow writing is initiated
- **THEN** system SHALL create temporary file `vault.tmp`
- **AND** system SHALL write H_n+1 and re-encrypted VK to temporary file
- **AND** system SHALL call `File::sync_all()` to force physical disk write
- **AND** system SHALL ensure data is physically committed before proceeding

#### Scenario: AUP Phase 3 - Atomic commit

- **WHEN** AUP atomic commit is initiated
- **THEN** system SHALL call `std::fs::rename("vault.tmp", "vault.db")`
- **AND** system SHALL verify rename is atomic on POSIX systems
- **AND** system SHALL update metadata database (Local_Epoch = n+1)
- **AND** system SHALL commit the database transaction

#### Scenario: AUP crash recovery - Phase 2 failure

- **WHEN** system crashes during Phase 2 (shadow writing)
- **THEN** original `vault.db` SHALL remain intact
- **AND** `vault.tmp` MAY be incomplete or corrupted
- **AND** system SHALL automatically delete residual `vault.tmp` on startup
- **AND** system SHALL remain at epoch n (no upgrade occurred)

#### Scenario: AUP crash recovery - Phase 3.1 success

- **WHEN** system crashes during Phase 3.1 (rename instant)
- **THEN** filesystem SHALL guarantee either old file or new file exists
- **AND** system SHALL be in consistent state (epoch n or epoch n+1)
- **AND** crash recovery SHALL handle both cases correctly

#### Scenario: AUP crash recovery - Phase 3.2 failure

- **WHEN** system crashes after Phase 3.1 but during Phase 3.2 (SQL update)
- **THEN** blob SHALL be upgraded to epoch n+1
- **AND** metadata SHALL still record epoch n
- **AND** system SHALL detect BlobAhead state on startup
- **AND** system SHALL automatically heal by updating metadata to n+1

#### Scenario: AUP insufficient disk space

- **WHEN** AUP encounters insufficient disk space
- **THEN** system SHALL return I/O error
- **AND** system SHALL NOT modify original vault file
- **AND** system SHALL clean up temporary file
- **AND** system SHALL remain in Idle state (no data loss)

### Requirement: 类型安全与防止误用

The system SHALL prevent accidental misuse through strong typing and clear API boundaries.

#### Scenario: Shadow file cannot be used after commit

- **WHEN** `ShadowWriter::commit_shadow_write()` is called
- **THEN** ownership of `ShadowFile` SHALL be consumed
- **AND** committed file SHALL NOT be writable anymore
- **AND** double-commit SHALL be prevented by type system

#### Scenario: Invariant violations are not recoverable errors

- **WHEN** an `InvariantViolation` is returned
- **THEN** caller SHALL NOT catch and ignore the error
- **AND** system SHALL treat invariant violation as fatal condition
- **AND** system SHALL trigger meltdown procedure if violation persists

### Requirement: 零信任文件系统

The system SHALL not trust filesystem reports and shall only trust cryptographically verified data integrity.

#### Scenario: Trust AEAD verification over file existence

- **WHEN** checking if vault file is valid
- **THEN** system SHALL verify AEAD authentication tag
- **AND** system SHALL NOT rely on file existence or size alone
- **AND** system SHALL reject files with invalid authentication tags

#### Scenario: Trust MAC verification over metadata

- **WHEN** checking if vault data is consistent
- **THEN** system SHALL verify BLAKE3 MAC matches expected value
- **AND** system SHALL NOT trust metadata epoch alone
- **AND** system SHALL detect tampered or corrupted data

---

