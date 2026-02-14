//! # Protocol Module
//!
//! This module provides the core protocol state machine for Aeternum's
//! PQRR (Post-Quantum Revocation & Re-keying) system.
//!
//! ## Architecture
//!
//! The protocol module sits on top of crypto, models, and storage:
//! - **crypto/** - Cryptographic primitives (KEM, KDF, AEAD)
//! - **models/** - Data models (epochs, devices, vaults)
//! - **storage/** - Shadow writing and crash consistency
//! - **protocol/** - State machine and invariant enforcement ← **THIS MODULE**
//! - **sync/** - Aeternum Wire protocol (device-to-device)
//!
//! ## Modules
//!
//! - `pqrr` - PQRR state machine and epoch upgrade coordination
//! - `error` - Protocol-specific error types
//!
//! ## Four Mathematical Invariants
//!
//! This module enforces four mathematical invariants that must NEVER be violated:
//!
//! ### Invariant #1 — Epoch Monotonicity
//! All device epochs must strictly increase, no rollback allowed.
//! ```text
//! ∀d₁,d₂∈D_active ⇒ epoch(d₁) = epoch(d₂) = S_epoch
//! ```
//!
//! ### Invariant #2 — Header Completeness
//! Each active device must have exactly one valid header to access DEK.
//! ```text
//! ∀d∈D_active ⇔ ∃!h∈V_e.H: unwrap(h,d) = DEK_e
//! ```
//!
//! ### Invariant #3 — Causal Entropy Barrier
//! Decryption authority ≠ Management authority (RECOVERY role cannot execute σ_rotate).
//! ```text
//! Role(S) = RECOVERY ⇒ σ_rotate ∉ P(S)
//! ```
//!
//! ### Invariant #4 — Veto Supremacy
//! Any active device's Veto signal within 48h window must immediately terminate recovery.
//! ```text
//! Status(req) = COMMITTED ⇒ Vetoes(req) = ∅
//! ```
//!
//! ## Safety Guarantees
//!
//! - All invariant violations trigger immediate meltdown (kernel lock)
//! - State machine enforces monotonic epoch progression
//! - Role-based access control prevents privilege escalation
//! - Veto signals have highest priority in recovery protocol

#![warn(missing_docs)]
#![warn(unused_extern_crates)]
#![warn(unused_imports)]

// Sub-modules
pub mod device_mgmt;
pub mod epoch_upgrade;
pub mod error;
pub mod pqrr;
pub mod recovery;

// Re-export common types
pub use device_mgmt::{
    cleanup_revoked_headers, get_active_devices, get_revoked_devices, is_device_registered,
    register_device, revoke_device, validate_header_completeness,
};
pub use epoch_upgrade::EpochUpgradeCoordinator;
pub use error::{PqrrError, Result};
pub use pqrr::{PqrrStateMachine, ProtocolState};
pub use recovery::{
    check_veto_supremacy, RecoveryRequestId, RecoveryWindow, VetoMessage, VETO_WINDOW_MS,
};
