//! # Traffic Obfuscation & Chaff Generation
//!
//! This module implements traffic fingerprinting protection through:
//! - **Padding Generation** - Ensures all frames are exactly 8192 bytes
//! - **Chaff Sync** - Generates decoy synchronization messages
//! - **Timing Jitter** - Random delays to prevent timing attacks
//!
//! ## Security Properties
//!
//! - **Statistical Indistinguishability** - Chaff frames are identical to real frames
//! - **Timing Obfuscation** - Random delays prevent correlation attacks
//! - **Entropy Maximization** - CSPRNG ensures maximum entropy in padding
//!
//! ## Protocol Compliance
//!
//! Implements [AET-WIRE-SPEC-004 §4](../../../docs/protocols/Sync-Wire-Protocol.md):
//! - Header Padding (real vs chaff indistinguishability)
//! - Chaff Sync (decoy epoch upgrades)
//! - Timing Obfuscation (50ms-200ms jitter)

use crate::sync::{
    codec::PayloadType, frame::WireFrame, Result, WireError, AUTH_TAG_SIZE, FRAME_SIZE,
    MAX_BODY_SIZE, NONCE_SIZE,
};
use rand::{rngs::StdRng, Rng, SeedableRng};
use serde::{Deserialize, Serialize};
use std::time::{Duration, Instant};
use zeroize::{Zeroize, ZeroizeOnDrop};

/// Minimum timing jitter in milliseconds
pub const JITTER_MIN_MS: u64 = 50;

/// Maximum timing jitter in milliseconds
pub const JITTER_MAX_MS: u64 = 200;

/// Chaff synchronization message
///
/// This struct represents a decoy sync message that is indistinguishable
/// from real sync messages at the network level.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct ChaffSyncMessage {
    /// Fake epoch version (randomized)
    pub fake_epoch: u32,

    /// Decoy device count (randomized between 2-10)
    pub device_count: u8,

    /// Random timestamp for decoy activity
    pub timestamp: u64,

    /// Random checksum (prevents compression attacks)
    pub checksum: u32,
}

impl Default for ChaffSyncMessage {
    fn default() -> Self {
        Self::new()
    }
}

impl ChaffSyncMessage {
    /// Create a new chaff sync message with randomized values
    pub fn new() -> Self {
        let mut rng = StdRng::from_entropy();

        Self {
            fake_epoch: rng.gen(),
            device_count: rng.gen_range(2..=10),
            timestamp: rng.gen(),
            checksum: rng.gen(),
        }
    }

    /// Create a chaff sync message with a specific fake epoch
    ///
    /// This is useful when testing specific epoch values.
    pub fn with_epoch(fake_epoch: u32) -> Self {
        let mut rng = StdRng::from_entropy();

        Self {
            fake_epoch,
            device_count: rng.gen_range(2..=10),
            timestamp: rng.gen(),
            checksum: rng.gen(),
        }
    }
}

/// Traffic obfuscation generator
///
/// Generates padding, chaff messages, and timing jitter to prevent
/// traffic analysis attacks.
#[derive(Debug, Clone)]
pub struct ChaffGenerator {
    /// CSPRNG for generating random padding
    rng: StdRng,
}

impl Default for ChaffGenerator {
    fn default() -> Self {
        Self::new()
    }
}

impl ChaffGenerator {
    /// Create a new chaff generator with a fresh CSPRNG
    pub fn new() -> Self {
        Self {
            rng: StdRng::from_entropy(),
        }
    }

    /// Create a new chaff generator with a specific seed
    ///
    /// # Arguments
    ///
    /// * `seed` - Seed for the CSPRNG (useful for deterministic testing)
    ///
    /// # Note
    ///
    /// In production, always use `ChaffGenerator::new()` to get
    /// cryptographically secure randomness.
    pub fn with_seed(seed: [u8; 32]) -> Self {
        Self {
            rng: StdRng::from_seed(seed),
        }
    }

    /// Generate random padding to ensure frame size is exactly 8192 bytes
    ///
    /// # Arguments
    ///
    /// * `current_size` - Current size of the frame (without padding)
    ///
    /// # Returns
    ///
    /// Returns a vector of random bytes that will pad the frame to `FRAME_SIZE`.
    ///
    /// # Errors
    ///
    /// Returns `WireError::InvalidFrameSize` if `current_size` exceeds `FRAME_SIZE`.
    ///
    /// # Example
    ///
    /// ```ignore
    /// let generator = ChaffGenerator::new();
    /// let padding = generator.generate_padding(100)?;
    /// assert_eq!(padding.len(), FRAME_SIZE - 100);
    /// ```
    pub fn generate_padding(&mut self, current_size: usize) -> Result<Vec<u8>> {
        if current_size > FRAME_SIZE {
            return Err(WireError::InvalidFrameSize(current_size));
        }

        let padding_size = FRAME_SIZE - current_size;
        let mut padding = vec![0u8; padding_size];

        // Fill with cryptographically secure random data
        self.rng.fill(&mut padding[..]);

        Ok(padding)
    }

    /// Generate padding for an existing frame
    ///
    /// This method recalculates and replaces the padding in a frame
    /// to ensure it contains random data rather than zeros.
    ///
    /// # Arguments
    ///
    /// * `frame` - The frame to add padding to
    ///
    /// # Returns
    ///
    /// Returns a new `WireFrame` with randomized padding.
    pub fn apply_padding_to_frame(&mut self, mut frame: WireFrame) -> Result<WireFrame> {
        // Calculate size without padding
        let size_without_padding =
            NONCE_SIZE + 4 + 1 + 2 + frame.encrypted_body.len() + AUTH_TAG_SIZE;

        // Generate new random padding
        frame.padding = self.generate_padding(size_without_padding)?;

        Ok(frame)
    }

    /// Create a chaff (decoy) synchronization message
    ///
    /// Generates a Wire Frame that is indistinguishable from a real
    /// synchronization message at the network level.
    ///
    /// # Arguments
    ///
    /// * `epoch` - The current epoch to use for the chaff message
    /// * `session_key` - Session key for encryption (simulated in chaff)
    ///
    /// # Returns
    ///
    /// Returns a `WireFrame` that looks exactly like a real sync message.
    ///
    /// # Note
    ///
    /// The encrypted body in chaff messages is randomly generated but
    /// properly formatted to prevent statistical analysis.
    pub fn create_chaff_sync(&mut self, epoch: u32) -> Result<WireFrame> {
        // Create a chaff sync message
        let chaff_msg = ChaffSyncMessage::new();

        // Serialize the chaff message
        let serialized = bincode::serialize(&chaff_msg)
            .map_err(|e| WireError::DeserializationFailed(e.to_string()))?;

        // Simulate encryption with random data (in real implementation, this would use AEAD)
        let encrypted_body = self.generate_encrypted_chaff(serialized.len())?;

        // Generate random nonce
        let mut nonce = [0u8; NONCE_SIZE];
        self.rng.fill(&mut nonce);

        // Generate random auth tag
        let mut auth_tag = [0u8; AUTH_TAG_SIZE];
        self.rng.fill(&mut auth_tag);

        // Create the frame
        WireFrame::new(
            nonce,
            epoch,
            PayloadType::Sync.to_byte(),
            encrypted_body,
            auth_tag,
        )
    }

    /// Generate random encrypted body for chaff
    ///
    /// This creates ciphertext-sized random data that matches the
    /// statistical properties of real encrypted messages.
    fn generate_encrypted_chaff(&mut self, size: usize) -> Result<Vec<u8>> {
        if size > MAX_BODY_SIZE {
            return Err(WireError::InvalidFrameSize(size));
        }

        let mut encrypted = vec![0u8; size];
        self.rng.fill(&mut encrypted[..]);

        Ok(encrypted)
    }

    /// Generate timing jitter for network operations
    ///
    /// Returns a random duration between `JITTER_MIN_MS` and `JITTER_MAX_MS`
    /// to prevent timing correlation attacks.
    ///
    /// # Returns
    ///
    /// Returns a `Duration` for the delay.
    ///
    /// # Example
    ///
    /// ```ignore
    /// let generator = ChaffGenerator::new();
    /// let delay = generator.timing_jitter();
    /// std::thread::sleep(delay);
    /// ```
    pub fn timing_jitter(&mut self) -> Duration {
        let jitter_ms = self.rng.gen_range(JITTER_MIN_MS..=JITTER_MAX_MS);
        Duration::from_millis(jitter_ms)
    }

    /// Apply timing jitter and measure the delay
    ///
    /// This is a convenience method that sleeps for the jitter duration
    /// and returns the actual time slept.
    ///
    /// # Returns
    ///
    /// Returns the `Duration` that was actually slept.
    pub fn apply_timing_jitter(&mut self) -> Duration {
        let delay = self.timing_jitter();
        let start = Instant::now();
        std::thread::sleep(delay);
        start.elapsed()
    }

    /// Generate a batch of chaff frames
    ///
    /// Creates multiple chaff frames with varying payload types to
    /// simulate realistic network traffic patterns.
    ///
    /// # Arguments
    ///
    /// * `count` - Number of chaff frames to generate
    /// * `epoch` - Current epoch for the chaff frames
    ///
    /// # Returns
    ///
    /// Returns a vector of `WireFrame` chaff messages.
    pub fn generate_chaff_batch(&mut self, count: usize, epoch: u32) -> Result<Vec<WireFrame>> {
        let mut batch = Vec::with_capacity(count);

        for _ in 0..count {
            batch.push(self.create_chaff_sync(epoch)?);
        }

        Ok(batch)
    }

    /// Calculate entropy of padding (for testing)
    ///
    /// Measures the Shannon entropy of the padding to ensure
    /// it has sufficient randomness.
    ///
    /// # Arguments
    ///
    /// * `padding` - The padding to analyze
    ///
    /// # Returns
    ///
    /// Returns the entropy in bits per byte (0.0 to 8.0).
    #[cfg(test)]
    fn calculate_entropy(padding: &[u8]) -> f64 {
        if padding.is_empty() {
            return 0.0;
        }

        let mut freq = [0usize; 256];
        for &byte in padding {
            freq[byte as usize] += 1;
        }

        let len = padding.len() as f64;
        let mut entropy = 0.0;

        for &count in &freq {
            if count > 0 {
                let p = count as f64 / len;
                entropy -= p * p.log2();
            }
        }

        entropy
    }
}

/// Zeroizing wrapper for sensitive timing data
///
/// This ensures that any timing information stored in memory
/// is properly wiped when no longer needed.
#[derive(Debug, Zeroize, ZeroizeOnDrop)]
pub struct TimingMetadata {
    /// Actual delay applied
    pub actual_delay_ms: u64,

    /// Expected minimum delay
    pub expected_min_ms: u64,

    /// Expected maximum delay
    pub expected_max_ms: u64,
}

impl TimingMetadata {
    /// Create new timing metadata
    pub fn new(actual_delay_ms: u64, expected_min_ms: u64, expected_max_ms: u64) -> Self {
        Self {
            actual_delay_ms,
            expected_min_ms,
            expected_max_ms,
        }
    }

    /// Check if the delay is within expected range
    pub fn is_within_range(&self) -> bool {
        self.actual_delay_ms >= self.expected_min_ms && self.actual_delay_ms <= self.expected_max_ms
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_chaff_generator_new() {
        let mut generator = ChaffGenerator::new();
        // Just verify it can be created and used
        let _val = generator.rng.gen_range(0..1);
    }

    #[test]
    fn test_chaff_generator_with_seed() {
        let seed = [42u8; 32];
        let mut gen1 = ChaffGenerator::with_seed(seed);
        let mut gen2 = ChaffGenerator::with_seed(seed);

        // Same seed should produce same first value
        let val1 = gen1.rng.gen::<u32>();
        let val2 = gen2.rng.gen::<u32>();
        assert_eq!(val1, val2);
    }

    #[test]
    fn test_generate_padding_correct_size() {
        let mut generator = ChaffGenerator::new();

        // Test various sizes
        for size in [0, 100, 1000, 5000, 8000] {
            let padding = generator.generate_padding(size).unwrap();
            assert_eq!(padding.len(), FRAME_SIZE - size);
        }
    }

    #[test]
    fn test_generate_padding_too_large() {
        let mut generator = ChaffGenerator::new();
        let result = generator.generate_padding(FRAME_SIZE + 1);
        assert!(matches!(result, Err(WireError::InvalidFrameSize(_))));
    }

    #[test]
    fn test_generate_padding_maximum_size() {
        let mut generator = ChaffGenerator::new();
        let padding = generator.generate_padding(FRAME_SIZE).unwrap();
        assert_eq!(padding.len(), 0);
    }

    #[test]
    fn test_generate_padding_entropy() {
        let mut generator = ChaffGenerator::new();
        let padding = generator.generate_padding(100).unwrap();

        // Calculate entropy - should be close to 8.0 for good randomness
        let entropy = ChaffGenerator::calculate_entropy(&padding);

        // Require at least 7 bits of entropy per byte (very conservative threshold)
        assert!(entropy > 7.0, "Entropy too low: {}", entropy);
    }

    #[test]
    fn test_apply_padding_to_frame() {
        let mut generator = ChaffGenerator::new();

        let frame =
            WireFrame::new([0u8; NONCE_SIZE], 1, 0, vec![1, 2, 3], [0u8; AUTH_TAG_SIZE]).unwrap();

        let padded_frame = generator.apply_padding_to_frame(frame).unwrap();

        // Verify padding is non-zero (random)
        let has_non_zero = padded_frame.padding.iter().any(|&b| b != 0);
        assert!(has_non_zero);
    }

    #[test]
    fn test_chaff_sync_message_new() {
        let msg = ChaffSyncMessage::new();

        // Verify fields are within expected ranges
        assert!(msg.device_count >= 2 && msg.device_count <= 10);
    }

    #[test]
    fn test_chaff_sync_message_with_epoch() {
        let msg = ChaffSyncMessage::with_epoch(42);
        assert_eq!(msg.fake_epoch, 42);
        assert!(msg.device_count >= 2 && msg.device_count <= 10);
    }

    #[test]
    fn test_create_chaff_sync() {
        let mut generator = ChaffGenerator::new();
        let chaff_frame = generator.create_chaff_sync(5).unwrap();

        // Verify frame properties
        assert_eq!(chaff_frame.epoch, 5);
        assert_eq!(chaff_frame.payload_type, PayloadType::Sync.to_byte());
        assert_eq!(chaff_frame.nonce.len(), NONCE_SIZE);
        assert_eq!(chaff_frame.auth_tag.len(), AUTH_TAG_SIZE);
    }

    #[test]
    fn test_create_chaff_sync_frame_size() {
        let mut generator = ChaffGenerator::new();
        let chaff_frame = generator.create_chaff_sync(1).unwrap();

        // Serialize and verify size
        let serialized = chaff_frame.serialize().unwrap();
        assert_eq!(serialized.len(), FRAME_SIZE);
    }

    #[test]
    fn test_timing_jitter_range() {
        let mut generator = ChaffGenerator::new();

        // Generate multiple jitter values and verify they're in range
        for _ in 0..100 {
            let jitter = generator.timing_jitter();
            let jitter_ms = jitter.as_millis() as u64;

            assert!(
                (JITTER_MIN_MS..=JITTER_MAX_MS).contains(&jitter_ms),
                "Jitter out of range: {}",
                jitter_ms
            );
        }
    }

    #[test]
    fn test_timing_jitter_distribution() {
        let mut generator = ChaffGenerator::new();
        let mut count = [0usize; 4];

        // Generate many samples and check distribution
        for _ in 0..1000 {
            let jitter_ms = generator.timing_jitter().as_millis() as u64;
            let bucket =
                ((jitter_ms - JITTER_MIN_MS) / ((JITTER_MAX_MS - JITTER_MIN_MS) / 4)) as usize;
            let bucket = bucket.min(3);
            count[bucket] += 1;
        }

        // Each bucket should have roughly 25% of samples
        // Allow significant deviation for randomness, but ensure all buckets have some samples
        for bucket in &count {
            assert!(*bucket > 100, "Distribution seems uneven: {:?}", count);
        }
    }

    #[test]
    fn test_apply_timing_jitter() {
        let mut generator = ChaffGenerator::new();
        let delay = generator.apply_timing_jitter();

        let delay_ms = delay.as_millis() as u64;
        assert!(
            (JITTER_MIN_MS..=JITTER_MAX_MS + 10).contains(&delay_ms), // +10 for scheduling overhead
            "Delay out of range: {}",
            delay_ms
        );
    }

    #[test]
    fn test_generate_chaff_batch() {
        let mut generator = ChaffGenerator::new();
        let batch = generator.generate_chaff_batch(5, 10).unwrap();

        assert_eq!(batch.len(), 5);

        for frame in &batch {
            assert_eq!(frame.epoch, 10);
            assert_eq!(frame.payload_type, PayloadType::Sync.to_byte());
        }
    }

    #[test]
    fn test_timing_metadata() {
        let metadata = TimingMetadata::new(100, 50, 200);
        assert!(metadata.is_within_range());

        let metadata_invalid = TimingMetadata::new(250, 50, 200);
        assert!(!metadata_invalid.is_within_range());
    }

    #[test]
    fn test_chaff_indistinguishability() {
        let mut generator = ChaffGenerator::new();

        // Create a "real" frame (simulated)
        let real_frame = WireFrame::new(
            [1u8; NONCE_SIZE],
            5,
            PayloadType::Sync.to_byte(),
            vec![2, 3, 4],
            [5u8; AUTH_TAG_SIZE],
        )
        .unwrap();

        // Create a chaff frame
        let chaff_frame = generator.create_chaff_sync(5).unwrap();

        // Both should serialize to same size
        let real_serialized = real_frame.serialize().unwrap();
        let chaff_serialized = chaff_frame.serialize().unwrap();

        assert_eq!(real_serialized.len(), chaff_serialized.len());
        assert_eq!(real_serialized.len(), FRAME_SIZE);
    }

    #[test]
    fn test_chaff_statistical_independence() {
        let mut generator = ChaffGenerator::with_seed([1u8; 32]);

        // Generate two batches with different seeds
        let batch1 = generator.generate_chaff_batch(10, 1).unwrap();

        generator = ChaffGenerator::with_seed([2u8; 32]);
        let batch2 = generator.generate_chaff_batch(10, 1).unwrap();

        // Verify that different seeds produce different results
        let serialized1 = batch1[0].serialize().unwrap();
        let serialized2 = batch2[0].serialize().unwrap();

        assert_ne!(serialized1, serialized2);
    }

    #[test]
    fn test_chaff_sync_message_serialization() {
        let msg = ChaffSyncMessage {
            fake_epoch: 42,
            device_count: 5,
            timestamp: 12345,
            checksum: 67890,
        };

        let serialized = bincode::serialize(&msg).unwrap();
        let deserialized: ChaffSyncMessage = bincode::deserialize(&serialized).unwrap();

        assert_eq!(msg, deserialized);
    }

    #[test]
    fn test_zeroize_on_drop() {
        use zeroize::Zeroize;

        let metadata = TimingMetadata::new(100, 50, 200);
        let mut metadata_clone = metadata;

        metadata_clone.zeroize();

        assert_eq!(metadata_clone.actual_delay_ms, 0);
        assert_eq!(metadata_clone.expected_min_ms, 0);
        assert_eq!(metadata_clone.expected_max_ms, 0);
    }

    // Property test: Verify padding always produces correct size
    #[test]
    fn test_property_padding_size() {
        let mut generator = ChaffGenerator::new();

        for current_size in 0..=FRAME_SIZE {
            if let Ok(padding) = generator.generate_padding(current_size) {
                assert_eq!(padding.len(), FRAME_SIZE - current_size);
            }
        }
    }

    // Property test: Verify all chaff frames are exactly FRAME_SIZE
    #[test]
    fn test_property_chaff_frame_size() {
        let mut generator = ChaffGenerator::new();

        for epoch in 0u32..10 {
            let frame = generator.create_chaff_sync(epoch).unwrap();
            let serialized = frame.serialize().unwrap();
            assert_eq!(serialized.len(), FRAME_SIZE);
        }
    }
}
