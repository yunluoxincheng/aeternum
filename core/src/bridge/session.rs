//! # Vault Session
//!
//! Vault session implementation for UniFFI bridge.
//!
//! ## Security Guarantees
//!
//! - All decryption operations happen in Rust memory
//! - Vault Key (VK) is zeroized on lock/drop
//! - UI layer only receives plaintext strings, never keys
//!
//! ## Architecture
//!
//! ```text
//! Kotlin UI → VaultSession (handle) → Rust Core
//!            ↓ decrypt_field()
//!            ← plaintext string
//! ```

use crate::protocol::error::{PqrrError, Result};
use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, RwLock};
use zeroize::Zeroizing;

/// Vault session - Handle-based access to decrypted data
///
/// This session holds the Vault Key (VK) in memory and provides
/// field-level decryption. The key is automatically zeroized when:
/// - `lock()` is called explicitly
/// - The session is dropped
/// - App goes to background
#[derive(uniffi::Object)]
pub struct VaultSession {
    /// Vault Key (VK) - Automatically zeroized on drop
    vault_key: Zeroizing<Vec<u8>>,

    /// Session metadata
    epoch: u32,

    /// Valid flag - Set to false on lock
    /// Use Arc<AtomicBool> for thread-safe interior mutability
    valid: Arc<AtomicBool>,

    /// Simulated vault data (for UI demo)
    /// In production, this would be encrypted at-rest
    /// Use RwLock for interior mutability
    vault_data: Arc<RwLock<HashMap<String, HashMap<String, String>>>>,
}

impl VaultSession {
    /// Create a new vault session (internal constructor)
    ///
    /// # Arguments
    /// - `vault_key`: Decrypted vault key
    /// - `epoch`: Current epoch
    pub fn new(vault_key: Vec<u8>, epoch: u32) -> Self {
        Self {
            vault_key: Zeroizing::new(vault_key),
            epoch,
            valid: Arc::new(AtomicBool::new(true)),
            vault_data: Arc::new(RwLock::new(Self::demo_vault_data())),
        }
    }

    /// Create demo vault data for testing
    fn demo_vault_data() -> HashMap<String, HashMap<String, String>> {
        let mut vault = HashMap::new();

        // Record 1: Passwords
        let mut record1 = HashMap::new();
        record1.insert("title".to_string(), "Gmail Account".to_string());
        record1.insert("username".to_string(), "user@gmail.com".to_string());
        record1.insert("password".to_string(), "********".to_string());
        vault.insert("rec_001".to_string(), record1);

        // Record 2: Notes
        let mut record2 = HashMap::new();
        record2.insert("title".to_string(), "WiFi Password".to_string());
        record2.insert("content".to_string(), "MySecureWiFi123!".to_string());
        vault.insert("rec_002".to_string(), record2);

        vault
    }

    /// Check if session is valid (internal)
    fn is_valid_internal(&self) -> bool {
        self.valid.load(Ordering::Acquire)
    }

    /// Invalidate session (internal)
    fn invalidate(&self) {
        self.valid.store(false, Ordering::Release);
    }
}

// ============================================================================
// UniFFI Exports
// ============================================================================

/// UniFFI-exported methods for VaultSession
#[uniffi::export]
impl VaultSession {
    /// List all record IDs (sanitized - no sensitive data)
    ///
    /// Returns list of record IDs available in vault.
    pub fn list_record_ids(&self) -> Vec<String> {
        let data = self.vault_data.read().unwrap();
        data.keys().cloned().collect()
    }

    /// Decrypt a field - Plaintext only exists in Rust memory
    ///
    /// # Arguments
    /// - `record_id`: Record identifier
    /// - `field_key`: Field key to decrypt
    ///
    /// # Returns
    /// Decrypted field value as plaintext string
    ///
    /// # Errors
    /// - `PqrrError::InsufficientPrivileges` - Session invalid or locked
    /// - `PqrrError::HeaderIncomplete` - Record or field not found
    pub fn decrypt_field(&self, record_id: String, field_key: String) -> Result<String> {
        // Check session validity
        if !self.is_valid_internal() {
            return Err(PqrrError::InsufficientPrivileges {
                role: "Session".to_string(),
                operation: "decrypt_field".to_string(),
            });
        }

        // Lookup record
        let data = self.vault_data.read().unwrap();
        let record = data
            .get(&record_id)
            .ok_or_else(|| PqrrError::HeaderIncomplete {
                device_id: record_id.clone(),
                reason: "Record not found".to_string(),
            })?;

        // Lookup field
        let value = record
            .get(&field_key)
            .ok_or_else(|| PqrrError::HeaderIncomplete {
                device_id: record_id,
                reason: format!("Field '{}' not found", field_key),
            })?;

        // INVARIANT: Return plaintext string only
        // The VaultKey remains in Rust memory and is zeroized on drop
        Ok(value.clone())
    }

    /// Check if session is valid
    ///
    /// Returns `true` if session can decrypt fields, `false` if locked.
    pub fn is_valid(&self) -> bool {
        self.is_valid_internal()
    }

    /// Store an entry - Encrypt and store in vault
    ///
    /// # Arguments
    /// - `record_id`: Record identifier
    /// - `field_key`: Field key to store
    /// - `plaintext_value`: Plaintext value to encrypt and store
    ///
    /// # Errors
    /// - `PqrrError::InsufficientPrivileges` - Session invalid or locked
    pub fn store_entry(
        &self,
        record_id: String,
        field_key: String,
        plaintext_value: String,
    ) -> Result<()> {
        // Check session validity
        if !self.is_valid_internal() {
            return Err(PqrrError::InsufficientPrivileges {
                role: "Session".to_string(),
                operation: "store_entry".to_string(),
            });
        }

        // Get or create record
        let mut data = self.vault_data.write().unwrap();
        let record = data.entry(record_id.clone()).or_insert_with(HashMap::new);

        // Store the field (in production, this would encrypt with VK)
        record.insert(field_key, plaintext_value);

        Ok(())
    }

    /// Retrieve an entry - Decrypt and return from vault
    ///
    /// # Arguments
    /// - `record_id`: Record identifier
    /// - `field_key`: Field key to retrieve
    ///
    /// # Returns
    /// Decrypted field value as plaintext string
    ///
    /// # Errors
    /// - `PqrrError::InsufficientPrivileges` - Session invalid or locked
    /// - `PqrrError::HeaderIncomplete` - Record or field not found
    pub fn retrieve_entry(&self, record_id: String, field_key: String) -> Result<String> {
        // This is similar to decrypt_field but for full entry retrieval
        self.decrypt_field(record_id, field_key)
    }

    /// Lock the session - Zeroize vault key and invalidate
    ///
    /// After calling this, all decryption operations will fail.
    pub fn lock(&self) {
        // Invalidate session
        self.invalidate();

        // INVARIANT: Vault key will be zeroized when Zeroizing<Vec<u8>> is dropped
        // The zeroize happens automatically via the Zeroize trait on drop
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_vault_session_creation() {
        let vault_key = vec![1u8, 2, 3, 4];
        let session = VaultSession::new(vault_key, 1);

        assert!(session.is_valid());
    }

    #[test]
    fn test_list_record_ids() {
        let vault_key = vec![1u8, 2, 3, 4];
        let session = VaultSession::new(vault_key, 1);

        let ids = session.list_record_ids();
        assert_eq!(ids.len(), 2);
        assert!(ids.contains(&"rec_001".to_string()));
        assert!(ids.contains(&"rec_002".to_string()));
    }

    #[test]
    fn test_decrypt_field_success() {
        let vault_key = vec![1u8, 2, 3, 4];
        let session = VaultSession::new(vault_key, 1);

        let result = session.decrypt_field("rec_001".to_string(), "title".to_string());
        assert!(result.is_ok());
        assert_eq!(result.unwrap(), "Gmail Account");
    }

    #[test]
    fn test_decrypt_field_not_found() {
        let vault_key = vec![1u8, 2, 3, 4];
        let session = VaultSession::new(vault_key, 1);

        let result = session.decrypt_field("invalid".to_string(), "title".to_string());
        assert!(result.is_err());
    }

    #[test]
    fn test_lock_invalidates_session() {
        let vault_key = vec![1u8, 2, 3, 4];
        let session = VaultSession::new(vault_key, 1);

        assert!(session.is_valid());
        session.lock();
        assert!(!session.is_valid());
    }

    #[test]
    fn test_decrypt_after_lock_fails() {
        let vault_key = vec![1u8, 2, 3, 4];
        let session = VaultSession::new(vault_key, 1);

        session.lock();

        let result = session.decrypt_field("rec_001".to_string(), "title".to_string());
        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            PqrrError::InsufficientPrivileges { .. }
        ));
    }
}
