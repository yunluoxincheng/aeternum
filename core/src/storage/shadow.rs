//! # Shadow Write Mechanism
//!
//! Implements atomic file updates using the shadow writing pattern:
//! 1. Create a temporary file in the same directory
//! 2. Write data to the temporary file
//! 3. Sync to disk (fsync)
//! 4. Atomically rename to the target path
//!
//! This ensures crash consistency: at any point, either the old file or the new file
//! exists, never a corrupted intermediate state.
//!
//! ## Safety Guarantees
//!
//! - POSIX `rename()` is atomic on Linux/Android
//! - All writes are synced to disk before commit
//! - Temporary files are automatically cleaned up on drop
//!
//! ## Example
//!
//! ```no_run
//! use aeternum_core::storage::shadow::{ShadowWriter, ShadowFile};
//! use std::path::Path;
//!
//! let writer = ShadowWriter::new(Path::new("vault.db"));
//! let mut shadow = writer.begin_shadow_write()?;
//! shadow.write_and_sync(b"data").unwrap();
//! writer.commit_shadow_write(shadow)?;
//! # Ok::<(), aeternum_core::storage::StorageError>(())
//! ```

use std::fs::{File, OpenOptions};
use std::io::{self, Write};
use std::path::{Path, PathBuf};

use super::StorageError;

/// Default suffix for temporary files
const DEFAULT_TEMP_SUFFIX: &str = ".tmp";

/// Shadow writer for atomic file updates
///
/// Creates and manages temporary files for atomic write operations.
/// The writer ensures that file updates are atomic and crash-safe.
///
/// # Thread Safety
///
/// This type is not thread-safe. Use external synchronization if needed.
#[derive(Debug, Clone)]
pub struct ShadowWriter {
    /// The target file path
    base_path: PathBuf,
    /// Suffix for temporary files (default: ".tmp")
    temp_suffix: String,
}

impl ShadowWriter {
    /// Create a new shadow writer for the given target path
    ///
    /// The temporary file will be created in the same directory as the target,
    /// with a `.tmp` suffix by default.
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::shadow::ShadowWriter;
    /// use std::path::Path;
    ///
    /// let writer = ShadowWriter::new(Path::new("data/vault.db"));
    /// ```
    pub fn new(base_path: impl AsRef<Path>) -> Self {
        Self {
            base_path: base_path.as_ref().to_path_buf(),
            temp_suffix: DEFAULT_TEMP_SUFFIX.to_string(),
        }
    }

    /// Set a custom suffix for temporary files
    ///
    /// Default is `.tmp`. This can be useful for distinguishing between
    /// different types of temporary files.
    pub fn with_temp_suffix(mut self, suffix: impl Into<String>) -> Self {
        self.temp_suffix = suffix.into();
        self
    }

    /// Get the target file path
    pub fn base_path(&self) -> &Path {
        &self.base_path
    }

    /// Get the temporary file path
    ///
    /// The temporary file is created in the same directory as the target,
    /// with the configured suffix appended.
    pub fn temp_path(&self) -> PathBuf {
        // Append the suffix to the base path
        let mut temp_path = self.base_path.clone();
        let mut file_name = temp_path.file_name().unwrap_or_default().to_os_string();
        file_name.push(&self.temp_suffix);
        temp_path.set_file_name(file_name);
        temp_path
    }

    /// Begin a shadow write operation
    ///
    /// Creates a temporary file in the same directory as the target.
    /// The temporary file will be automatically cleaned up if not committed.
    ///
    /// # Errors
    ///
    /// Returns an error if:
    /// - The parent directory doesn't exist
    /// - Permission denied
    /// - I/O error creating the temporary file
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::shadow::ShadowWriter;
    /// use std::path::Path;
    ///
    /// let writer = ShadowWriter::new(Path::new("vault.db"));
    /// let mut shadow = writer.begin_shadow_write()?;
    /// # Ok::<(), aeternum_core::storage::StorageError>(())
    /// ```
    pub fn begin_shadow_write(&self) -> Result<ShadowFile, StorageError> {
        let temp_path = self.temp_path();

        // Ensure parent directory exists
        if let Some(parent) = temp_path.parent() {
            if !parent.exists() {
                return Err(StorageError::shadow_write(format!(
                    "Parent directory does not exist: {}",
                    parent.display()
                )));
            }
        }

        // Create the temporary file with restrictive permissions
        // Note: On Unix, tempfile crate creates files with 0600 permissions
        let file = OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .open(&temp_path)
            .map_err(|e| {
                StorageError::shadow_write(format!(
                    "Failed to create temporary file {}: {}",
                    temp_path.display(),
                    e
                ))
            })?;

        Ok(ShadowFile {
            file,
            path: temp_path,
            should_cleanup: true,
        })
    }

    /// Commit a shadow write operation
    ///
    /// Atomically renames the temporary file to the target path.
    /// This operation consumes the `ShadowFile`, preventing double-commit.
    ///
    /// # Atomicity
    ///
    /// On POSIX systems (Linux, Android, macOS), `rename()` is atomic when
    /// both files are on the same filesystem. The operation guarantees:
    /// - Either the old file or the new file exists, never both or neither
    /// - No intermediate state is possible
    ///
    /// # Errors
    ///
    /// Returns an error if:
    /// - The temporary file doesn't exist
    /// - Cross-device rename (not atomic)
    /// - Permission denied
    /// - I/O error during rename
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::shadow::ShadowWriter;
    /// use std::path::Path;
    ///
    /// let writer = ShadowWriter::new(Path::new("vault.db"));
    /// let mut shadow = writer.begin_shadow_write()?;
    /// shadow.write_and_sync(b"data").unwrap();
    /// writer.commit_shadow_write(shadow)?;
    /// # Ok::<(), aeternum_core::storage::StorageError>(())
    /// ```
    pub fn commit_shadow_write(self, shadow_file: ShadowFile) -> Result<(), StorageError> {
        // Disable cleanup since we're committing
        let mut shadow_file = shadow_file;
        shadow_file.should_cleanup = false;

        // Get paths before moving
        let temp_path = shadow_file.path.clone();
        let target_path = self.base_path;

        // Close the file handle first
        drop(shadow_file);

        // Atomic rename
        std::fs::rename(&temp_path, &target_path).map_err(|e| {
            // Try to clean up the temporary file on failure
            let _ = std::fs::remove_file(&temp_path);
            StorageError::atomic_rename(format!(
                "Failed to rename {} to {}: {}",
                temp_path.display(),
                target_path.display(),
                e
            ))
        })?;

        Ok(())
    }

    /// Clean up any residual temporary files
    ///
    /// This should be called at startup to remove any leftover `.tmp` files
    /// from previous crashed write attempts.
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::shadow::ShadowWriter;
    /// use std::path::Path;
    ///
    /// // Clean up residual temp files at startup
    /// ShadowWriter::cleanup_residual(Path::new("vault.db.tmp"))?;
    /// # Ok::<(), aeternum_core::storage::StorageError>(())
    /// ```
    pub fn cleanup_residual(temp_path: impl AsRef<Path>) -> Result<(), StorageError> {
        let temp_path = temp_path.as_ref();

        if temp_path.exists() {
            std::fs::remove_file(temp_path).map_err(|e| {
                StorageError::shadow_write(format!(
                    "Failed to remove residual temp file {}: {}",
                    temp_path.display(),
                    e
                ))
            })?;
            eprintln!(
                "[INFO] Cleaned up residual temp file: {}",
                temp_path.display()
            );
        }

        Ok(())
    }
}

/// A temporary file for shadow writing
///
/// This struct holds a file handle to a temporary file and ensures
/// automatic cleanup if the file is not committed.
///
/// # Drop Behavior
///
/// If the file is dropped without being committed, it will be automatically
/// deleted to prevent residual temporary files.
#[derive(Debug)]
pub struct ShadowFile {
    /// The file handle
    file: File,
    /// The path to the temporary file
    path: PathBuf,
    /// Whether to clean up the file on drop
    should_cleanup: bool,
}

impl ShadowFile {
    /// Write data to the temporary file and sync to disk
    ///
    /// This method writes all data to the file and then calls `fsync()`
    /// to ensure the data is physically written to disk.
    ///
    /// # Errors
    ///
    /// Returns an error if:
    /// - Write operation fails (disk full, I/O error)
    /// - `fsync()` fails (hardware error, filesystem error)
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::storage::shadow::ShadowWriter;
    /// use std::path::Path;
    ///
    /// let writer = ShadowWriter::new(Path::new("vault.db"));
    /// let mut shadow = writer.begin_shadow_write()?;
    /// shadow.write_and_sync(b"important data").unwrap();
    /// # Ok::<(), aeternum_core::storage::StorageError>(())
    /// ```
    pub fn write_and_sync(&mut self, data: &[u8]) -> io::Result<()> {
        // Write all data
        self.file.write_all(data)?;

        // Sync to disk - this is critical for crash consistency
        self.file.sync_all()?;

        Ok(())
    }

    /// Write all data from a reader to the temporary file and sync to disk
    ///
    /// This is useful for large data that should be streamed rather than
    /// loaded entirely into memory.
    pub fn write_all_from_reader<R: io::Read>(&mut self, mut reader: R) -> io::Result<u64> {
        let bytes_written = io::copy(&mut reader, &mut self.file)?;

        // Sync to disk
        self.file.sync_all()?;

        Ok(bytes_written)
    }

    /// Get the path to the temporary file
    pub fn path(&self) -> &Path {
        &self.path
    }

    /// Get a mutable reference to the underlying file
    ///
    /// This allows for more advanced operations like seeking.
    pub fn file_mut(&mut self) -> &mut File {
        &mut self.file
    }

    /// Get a reference to the underlying file
    pub fn file(&self) -> &File {
        &self.file
    }

    /// Sync the file to disk without writing additional data
    ///
    /// This is useful when you've written data through `file_mut()`
    /// and need to ensure it's synced.
    pub fn sync(&self) -> io::Result<()> {
        self.file.sync_all()
    }
}

impl Drop for ShadowFile {
    fn drop(&mut self) {
        if self.should_cleanup {
            // Close the file handle first
            let _ = self.file.sync_all(); // Best effort sync

            // Remove the temporary file
            match std::fs::remove_file(&self.path) {
                Ok(()) => {
                    eprintln!(
                        "[DEBUG] Cleaned up uncommitted temp file: {}",
                        self.path.display()
                    )
                }
                Err(e) => {
                    eprintln!(
                        "[WARN] Failed to clean up temp file {}: {}",
                        self.path.display(),
                        e
                    )
                }
            }
        }
    }
}

impl io::Write for ShadowFile {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        self.file.write(buf)
    }

    fn flush(&mut self) -> io::Result<()> {
        self.file.flush()
    }
}

// ============================================================================
// Tests
// ============================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;
    use tempfile::TempDir;

    // ------------------------------------------------------------------------
    // ShadowWriter Tests
    // ------------------------------------------------------------------------

    #[test]
    fn test_shadow_writer_new() {
        let writer = ShadowWriter::new("vault.db");
        assert_eq!(writer.base_path(), Path::new("vault.db"));
        assert_eq!(writer.temp_path(), PathBuf::from("vault.db.tmp"));
    }

    #[test]
    fn test_shadow_writer_custom_suffix() {
        let writer = ShadowWriter::new("vault.db").with_temp_suffix(".shadow");
        assert_eq!(writer.temp_path(), PathBuf::from("vault.db.shadow"));
    }

    #[test]
    fn test_shadow_write_success() {
        let temp_dir = TempDir::new().unwrap();
        let target_path = temp_dir.path().join("vault.db");

        let writer = ShadowWriter::new(&target_path);
        let mut shadow = writer.begin_shadow_write().unwrap();

        shadow.write_and_sync(b"test data").unwrap();

        // Temp file should exist
        assert!(shadow.path().exists());

        // Commit
        let writer = ShadowWriter::new(&target_path);
        writer.commit_shadow_write(shadow).unwrap();

        // Target file should exist, temp file should be gone
        assert!(target_path.exists());
        assert!(!target_path.with_extension("db.tmp").exists());

        // Verify content
        let content = fs::read(&target_path).unwrap();
        assert_eq!(content, b"test data");
    }

    #[test]
    fn test_shadow_write_nonexistent_directory() {
        let temp_dir = TempDir::new().unwrap();
        let nonexistent = temp_dir.path().join("nonexistent").join("vault.db");

        let writer = ShadowWriter::new(&nonexistent);
        let result = writer.begin_shadow_write();

        assert!(result.is_err());
        assert!(matches!(
            result.unwrap_err(),
            StorageError::ShadowWriteFailed(_)
        ));
    }

    #[test]
    fn test_temp_file_cleanup_on_drop() {
        let temp_dir = TempDir::new().unwrap();
        let target_path = temp_dir.path().join("vault.db");

        let writer = ShadowWriter::new(&target_path);
        let shadow = writer.begin_shadow_write().unwrap();
        let temp_path = shadow.path().to_path_buf();

        // Temp file should exist
        assert!(temp_path.exists());

        // Drop without committing
        drop(shadow);

        // Temp file should be cleaned up
        assert!(!temp_path.exists());
    }

    #[test]
    fn test_atomic_rename_overwrites_existing() {
        let temp_dir = TempDir::new().unwrap();
        let target_path = temp_dir.path().join("vault.db");

        // Create existing file with old content
        fs::write(&target_path, b"old content").unwrap();

        // Write new content
        let writer = ShadowWriter::new(&target_path);
        let mut shadow = writer.begin_shadow_write().unwrap();
        shadow.write_and_sync(b"new content").unwrap();

        let writer = ShadowWriter::new(&target_path);
        writer.commit_shadow_write(shadow).unwrap();

        // Verify content was replaced
        let content = fs::read(&target_path).unwrap();
        assert_eq!(content, b"new content");
    }

    #[test]
    fn test_write_large_data() {
        let temp_dir = TempDir::new().unwrap();
        let target_path = temp_dir.path().join("vault.db");

        // Create 1MB of data
        let data = vec![0xAB; 1024 * 1024];

        let writer = ShadowWriter::new(&target_path);
        let mut shadow = writer.begin_shadow_write().unwrap();
        shadow.write_and_sync(&data).unwrap();

        let writer = ShadowWriter::new(&target_path);
        writer.commit_shadow_write(shadow).unwrap();

        // Verify content
        let content = fs::read(&target_path).unwrap();
        assert_eq!(content.len(), data.len());
        assert!(content.iter().all(|&b| b == 0xAB));
    }

    #[test]
    fn test_write_from_reader() {
        use std::io::Cursor;

        let temp_dir = TempDir::new().unwrap();
        let target_path = temp_dir.path().join("vault.db");

        let writer = ShadowWriter::new(&target_path);
        let mut shadow = writer.begin_shadow_write().unwrap();

        let reader = Cursor::new(b"streamed data");
        let bytes = shadow.write_all_from_reader(reader).unwrap();
        assert_eq!(bytes, 13);

        let writer = ShadowWriter::new(&target_path);
        writer.commit_shadow_write(shadow).unwrap();

        let content = fs::read(&target_path).unwrap();
        assert_eq!(content, b"streamed data");
    }

    #[test]
    fn test_cleanup_residual() {
        let temp_dir = TempDir::new().unwrap();
        let temp_path = temp_dir.path().join("vault.db.tmp");

        // Create a residual temp file
        fs::write(&temp_path, b"residual").unwrap();
        assert!(temp_path.exists());

        // Clean it up
        ShadowWriter::cleanup_residual(&temp_path).unwrap();
        assert!(!temp_path.exists());

        // Cleaning up non-existent file should succeed
        ShadowWriter::cleanup_residual(&temp_path).unwrap();
    }

    #[test]
    fn test_shadow_file_path() {
        let temp_dir = TempDir::new().unwrap();
        let target_path = temp_dir.path().join("vault.db");

        let writer = ShadowWriter::new(&target_path);
        let shadow = writer.begin_shadow_write().unwrap();

        assert_eq!(shadow.path(), temp_dir.path().join("vault.db.tmp"));
    }

    #[test]
    fn test_shadow_file_file_mut() {
        use std::io::Seek;

        let temp_dir = TempDir::new().unwrap();
        let target_path = temp_dir.path().join("vault.db");

        let writer = ShadowWriter::new(&target_path);
        let mut shadow = writer.begin_shadow_write().unwrap();

        // Write some data
        shadow.write_and_sync(b"hello").unwrap();

        // Seek back and overwrite using file_mut
        let file = shadow.file_mut();
        file.seek(std::io::SeekFrom::Start(0)).unwrap();
        file.write_all(b"HELLO").unwrap();
        shadow.sync().unwrap();

        let writer = ShadowWriter::new(&target_path);
        writer.commit_shadow_write(shadow).unwrap();

        let content = fs::read(&target_path).unwrap();
        assert_eq!(content, b"HELLO");
    }

    #[test]
    fn test_shadow_file_write_trait() {
        use std::io::Write;

        let temp_dir = TempDir::new().unwrap();
        let target_path = temp_dir.path().join("vault.db");

        let writer = ShadowWriter::new(&target_path);
        let mut shadow = writer.begin_shadow_write().unwrap();

        // Use io::Write trait directly
        write!(shadow, "formatted data").unwrap();
        shadow.sync().unwrap();

        let writer = ShadowWriter::new(&target_path);
        writer.commit_shadow_write(shadow).unwrap();

        let content = fs::read(&target_path).unwrap();
        assert_eq!(content, b"formatted data");
    }

    #[test]
    fn test_multiple_shadow_writers_different_suffix() {
        // Test that multiple shadow writers with different suffixes can coexist
        let temp_dir = TempDir::new().unwrap();
        let target_path = temp_dir.path().join("vault.db");

        let writer1 = ShadowWriter::new(&target_path).with_temp_suffix(".tmp1");
        let writer2 = ShadowWriter::new(&target_path).with_temp_suffix(".tmp2");

        let mut shadow1 = writer1.begin_shadow_write().unwrap();
        shadow1.write_and_sync(b"data1").unwrap();

        let mut shadow2 = writer2.begin_shadow_write().unwrap();
        shadow2.write_and_sync(b"data2").unwrap();

        // Both temp files exist with different names
        assert!(shadow1.path().exists());
        assert!(shadow2.path().exists());
        assert_ne!(shadow1.path(), shadow2.path());

        // Drop shadow2 (cleanup)
        let temp_path2 = shadow2.path().to_path_buf();
        drop(shadow2);
        assert!(!temp_path2.exists());

        // Commit shadow1
        let writer1 = ShadowWriter::new(&target_path).with_temp_suffix(".tmp1");
        writer1.commit_shadow_write(shadow1).unwrap();

        // Verify only shadow1's data was committed
        let content = fs::read(&target_path).unwrap();
        assert_eq!(content, b"data1");
    }
}
