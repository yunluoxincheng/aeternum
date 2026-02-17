//! # Protocol Version Management
//!
//! Aeternum Wire 协议版本管理实现，提供版本兼容性检查、协商和强制升级机制。
//!
//! ## 设计原则
//!
//! 根据 [AET-WIRE-SPEC-004](../../../docs/protocols/Sync-Wire-Protocol.md) §6：
//! - **向后兼容**: 旧版本客户端可以读取数据，但禁止发起 PQRR
//! - **强制升级**: 安全漏洞修复时，服务器可强制客户端升级
//! - **纪元保护**: 版本协商不能违反 Invariant #1（纪元单调性）
//!
//! ## 版本号格式
//!
//! ```text
//! major.minor
//! │     │
//! │     └─ 小版本（向后兼容的增强）
//! │
//! └─ 大版本（不兼容的协议变更）
//! ```
//!
//! ## 使用示例
//!
//! ```no_build
//! use aeternum_core::sync::version::{ProtocolVersion, VersionNegotiation};
//!
//! // 客户端版本
//! let client_version = ProtocolVersion::new(1, 0);
//!
//! // 服务器版本
//! let server_version = ProtocolVersion::new(1, 1);
//!
//! // 检查兼容性
//! let negotiation = client_version.check_compatibility(&server_version)?;
//!
//! match negotiation {
//!     VersionNegotiation::Compatible => {
//!         // 正常通信
//!     }
//!     VersionNegotiation::UpgradeRequired => {
//!         // 需要升级客户端
//!     }
//! }
//! ```

use crate::sync::{Result, WireError};
use serde::{Deserialize, Serialize};

/// 协议版本号
///
/// 使用 `major.minor` 格式：
/// - **major**: 大版本号，不兼容的协议变更
/// - **minor**: 小版本号，向后兼容的增强
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord, Serialize, Deserialize)]
pub struct ProtocolVersion {
    /// 大版本号（不兼容变更）
    pub major: u8,
    /// 小版本号（向后兼容）
    pub minor: u8,
}

impl ProtocolVersion {
    /// 创建新的协议版本
    ///
    /// # Arguments
    ///
    /// * `major` - 大版本号（不兼容变更）
    /// * `minor` - 小版本号（向后兼容）
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::sync::version::ProtocolVersion;
    ///
    /// let version = ProtocolVersion::new(1, 0);
    /// assert_eq!(version.major, 1);
    /// assert_eq!(version.minor, 0);
    /// ```
    #[must_use]
    pub const fn new(major: u8, minor: u8) -> Self {
        Self { major, minor }
    }

    /// 获取当前协议版本
    ///
    /// 从 `crate::sync::PROTOCOL_VERSION` 读取。
    #[must_use]
    pub fn current() -> Self {
        let (major, minor) = crate::sync::PROTOCOL_VERSION;
        Self { major, minor }
    }

    /// 检查版本兼容性
    ///
    /// 根据 [AET-WIRE-SPEC-004] §6：
    /// - **向后兼容**: 旧客户端可读取数据（major 相同）
    /// - **强制升级**: major 不匹配或安全漏洞修复
    ///
    /// # Arguments
    ///
    /// * `other` - 要比较的版本（通常是服务器版本）
    ///
    /// # Returns
    ///
    /// 返回 `VersionNegotiation` 结果。
    ///
    /// # Example
    ///
    /// ```no_run
    /// use aeternum_core::sync::version::{ProtocolVersion, VersionNegotiation};
    ///
    /// let v1_0 = ProtocolVersion::new(1, 0);
    /// let v1_1 = ProtocolVersion::new(1, 1);
    /// let v2_0 = ProtocolVersion::new(2, 0);
    ///
    /// // 小版本升级：兼容
    /// assert!(matches!(
    ///     v1_0.check_compatibility(&v1_1),
    ///     Ok(VersionNegotiation::Compatible)
    /// ));
    ///
    /// // 大版本升级：强制升级
    /// assert!(matches!(
    ///     v1_0.check_compatibility(&v2_0),
    ///     Ok(VersionNegotiation::UpgradeRequired { .. })
    /// ));
    /// ```
    pub fn check_compatibility(&self, other: &Self) -> Result<VersionNegotiation> {
        // INVARIANT #1: Epoch Monotonicity
        // 版本协商不应导致纪元回滚，这里仅比较版本号

        if self.major == other.major {
            // 大版本相同：向后兼容
            Ok(VersionNegotiation::Compatible)
        } else if self.major < other.major {
            // 服务器版本更新：客户端需要升级
            Ok(VersionNegotiation::UpgradeRequired {
                client: *self,
                server: *other,
            })
        } else {
            // 客户端版本比服务器新：服务器应升级
            // 但仍可通信（向后兼容）
            Ok(VersionNegotiation::Compatible)
        }
    }

    /// 检查是否允许发起 PQRR
    ///
    /// 根据 [AET-WIRE-SPEC-004] §6：
    /// 旧版本客户端可以读取数据，但禁止发起 PQRR。
    ///
    /// # Arguments
    ///
    /// * `current` - 当前协议版本（通常是服务器版本）
    ///
    /// # Returns
    ///
    /// 返回 `true` 如果允许发起 PQRR。
    ///
    /// # Example
    ///
    /// ```
    /// use aeternum_core::sync::version::ProtocolVersion;
    ///
    /// let v1_0 = ProtocolVersion::new(1, 0);
    /// let v1_1 = ProtocolVersion::new(1, 1);
    /// let v2_0 = ProtocolVersion::new(2, 0);
    ///
    /// // 相同大版本：允许 PQRR
    /// assert!(v1_0.can_initiate_pqrr(&v1_1));
    ///
    /// // 大版本落后：禁止 PQRR
    /// assert!(!v1_0.can_initiate_pqrr(&v2_0));
    /// ```
    #[must_use]
    pub fn can_initiate_pqrr(&self, current: &Self) -> bool {
        // 只有相同大版本才能发起 PQRR
        self.major == current.major
    }

    /// 转换为字节表示（用于序列化）
    #[must_use]
    pub fn to_bytes(self) -> [u8; 2] {
        [self.major, self.minor]
    }

    /// 从字节表示解析
    ///
    /// # Errors
    ///
    /// 如果输入不是 2 字节，返回 `WireError::DeserializationFailed`。
    pub fn from_bytes(bytes: &[u8]) -> Result<Self> {
        if bytes.len() != 2 {
            return Err(WireError::DeserializationFailed(
                "Protocol version must be 2 bytes".to_string(),
            ));
        }
        Ok(Self {
            major: bytes[0],
            minor: bytes[1],
        })
    }
}

impl std::fmt::Display for ProtocolVersion {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}.{}", self.major, self.minor)
    }
}

/// 版本协商结果
///
/// 表示协议版本兼容性检查的结果。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub enum VersionNegotiation {
    /// 版本兼容，可以正常通信
    Compatible,

    /// 需要强制升级
    ///
    /// 当客户端大版本落后于服务器时触发。
    UpgradeRequired {
        /// 客户端版本
        client: ProtocolVersion,
        /// 服务器版本
        server: ProtocolVersion,
    },
}

impl VersionNegotiation {
    /// 检查是否需要升级
    #[must_use]
    pub fn requires_upgrade(&self) -> bool {
        matches!(self, Self::UpgradeRequired { .. })
    }

    /// 获取升级信息（如果需要）
    #[must_use]
    pub fn upgrade_info(&self) -> Option<(ProtocolVersion, ProtocolVersion)> {
        match self {
            Self::UpgradeRequired { client, server } => Some((*client, *server)),
            Self::Compatible => None,
        }
    }
}

/// 版本协商消息
///
/// 用于客户端和服务器之间协商协议版本。
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct VersionNegotiationMessage {
    /// 客户端支持的版本列表
    pub supported_versions: Vec<ProtocolVersion>,
    /// 首选版本
    pub preferred_version: ProtocolVersion,
    /// 客户端能力标志
    pub capabilities: CapabilityFlags,
}

/// 客户端能力标志
///
/// 位掩码，表示客户端支持的协议功能。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct CapabilityFlags(u8);

impl CapabilityFlags {
    /// 无特殊能力
    pub const NONE: u8 = 0b0000_0000;

    /// 支持混合加密握手（X25519 + Kyber-1024）
    pub const HYBRID_HANDSHAKE: u8 = 0b0000_0001;

    /// 支持诱饵流量（Chaff Sync）
    pub const CHAFF_SYNC: u8 = 0b0000_0010;

    /// 支持否决信号（Veto Signaling）
    pub const VETO_SIGNALING: u8 = 0b0000_0100;

    /// 支持影子包装（Shadow Wrapping）
    pub const SHADOW_WRAPPING: u8 = 0b0000_1000;

    /// 创建新的能力标志
    #[must_use]
    pub const fn new(flags: u8) -> Self {
        Self(flags)
    }

    /// 检查是否支持特定能力
    #[must_use]
    pub fn has(&self, flag: u8) -> bool {
        self.0 & flag != 0
    }

    /// 添加能力标志
    #[must_use]
    pub const fn with(mut self, flag: u8) -> Self {
        self.0 |= flag;
        self
    }

    /// 获取原始标志值
    #[must_use]
    pub const fn as_u8(self) -> u8 {
        self.0
    }
}

impl Default for CapabilityFlags {
    fn default() -> Self {
        // 默认支持所有能力
        Self::new(
            Self::HYBRID_HANDSHAKE
                | Self::CHAFF_SYNC
                | Self::VETO_SIGNALING
                | Self::SHADOW_WRAPPING,
        )
    }
}

impl VersionNegotiationMessage {
    /// 创建新的版本协商消息
    ///
    /// # Arguments
    ///
    /// * `supported_versions` - 客户端支持的版本列表
    /// * `preferred_version` - 首选版本
    /// * `capabilities` - 客户端能力标志
    #[must_use]
    pub fn new(
        supported_versions: Vec<ProtocolVersion>,
        preferred_version: ProtocolVersion,
        capabilities: CapabilityFlags,
    ) -> Self {
        Self {
            supported_versions,
            preferred_version,
            capabilities,
        }
    }

    /// 创建默认的协商消息（使用当前版本）
    #[must_use]
    pub fn default_with_version(version: ProtocolVersion) -> Self {
        Self {
            supported_versions: vec![version],
            preferred_version: version,
            capabilities: CapabilityFlags::default(),
        }
    }

    /// 选择最佳匹配版本
    ///
    /// 根据服务器支持的版本和客户端首选版本，选择最佳匹配。
    ///
    /// # Arguments
    ///
    /// * `server_versions` - 服务器支持的版本列表
    ///
    /// # Returns
    ///
    /// 返回最佳匹配的版本，如果没有兼容版本则返回 `None`。
    #[must_use]
    pub fn select_best_version(
        &self,
        server_versions: &[ProtocolVersion],
    ) -> Option<ProtocolVersion> {
        // 查找客户端和服务器都支持的版本
        for client_version in &self.supported_versions {
            if server_versions.contains(client_version) {
                // 找到匹配版本，优先选择客户端首选版本
                if server_versions.contains(&self.preferred_version) {
                    return Some(self.preferred_version);
                }
                return Some(*client_version);
            }
        }
        None
    }

    /// 检查是否有共同支持的版本
    #[must_use]
    pub fn has_common_version(&self, server_versions: &[ProtocolVersion]) -> bool {
        self.supported_versions
            .iter()
            .any(|v| server_versions.contains(v))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_protocol_version_creation() {
        let version = ProtocolVersion::new(1, 0);
        assert_eq!(version.major, 1);
        assert_eq!(version.minor, 0);
    }

    #[test]
    fn test_protocol_version_current() {
        let version = ProtocolVersion::current();
        assert_eq!(version, ProtocolVersion::new(1, 0));
    }

    #[test]
    fn test_protocol_version_to_from_bytes() {
        let version = ProtocolVersion::new(2, 5);
        let bytes = version.to_bytes();
        assert_eq!(bytes, [2, 5]);

        let parsed = ProtocolVersion::from_bytes(&bytes).unwrap();
        assert_eq!(parsed, version);
    }

    #[test]
    fn test_protocol_version_from_bytes_invalid_length() {
        let result = ProtocolVersion::from_bytes(&[1]);
        assert!(result.is_err());
    }

    #[test]
    fn test_version_compatibility_same_major() {
        let v1_0 = ProtocolVersion::new(1, 0);
        let v1_1 = ProtocolVersion::new(1, 1);

        let result = v1_0.check_compatibility(&v1_1).unwrap();
        assert!(matches!(result, VersionNegotiation::Compatible));
    }

    #[test]
    fn test_version_compatibility_upgrade_required() {
        let v1_0 = ProtocolVersion::new(1, 0);
        let v2_0 = ProtocolVersion::new(2, 0);

        let result = v1_0.check_compatibility(&v2_0).unwrap();
        assert!(matches!(
            result,
            VersionNegotiation::UpgradeRequired { client, server }
            if client.major == 1 && server.major == 2
        ));
    }

    #[test]
    fn test_version_compatibility_client_newer() {
        let v2_0 = ProtocolVersion::new(2, 0);
        let v1_0 = ProtocolVersion::new(1, 0);

        // 客户端比服务器新，仍可通信（向后兼容）
        let result = v2_0.check_compatibility(&v1_0).unwrap();
        assert!(matches!(result, VersionNegotiation::Compatible));
    }

    #[test]
    fn test_can_initiate_pqrr() {
        let v1_0 = ProtocolVersion::new(1, 0);
        let v1_1 = ProtocolVersion::new(1, 1);
        let v2_0 = ProtocolVersion::new(2, 0);

        // 相同大版本：允许 PQRR
        assert!(v1_0.can_initiate_pqrr(&v1_1));
        assert!(v1_1.can_initiate_pqrr(&v1_0));

        // 大版本落后：禁止 PQRR
        assert!(!v1_0.can_initiate_pqrr(&v2_0));
        assert!(!v1_1.can_initiate_pqrr(&v2_0));
    }

    #[test]
    fn test_version_display() {
        let version = ProtocolVersion::new(1, 0);
        assert_eq!(format!("{}", version), "1.0");

        let version = ProtocolVersion::new(2, 5);
        assert_eq!(format!("{}", version), "2.5");
    }

    #[test]
    fn test_capability_flags() {
        let flags =
            CapabilityFlags::new(CapabilityFlags::HYBRID_HANDSHAKE | CapabilityFlags::CHAFF_SYNC);

        assert!(flags.has(CapabilityFlags::HYBRID_HANDSHAKE));
        assert!(flags.has(CapabilityFlags::CHAFF_SYNC));
        assert!(!flags.has(CapabilityFlags::VETO_SIGNALING));

        let with_veto = flags.with(CapabilityFlags::VETO_SIGNALING);
        assert!(with_veto.has(CapabilityFlags::VETO_SIGNALING));
    }

    #[test]
    fn test_capability_flags_default() {
        let flags = CapabilityFlags::default();

        assert!(flags.has(CapabilityFlags::HYBRID_HANDSHAKE));
        assert!(flags.has(CapabilityFlags::CHAFF_SYNC));
        assert!(flags.has(CapabilityFlags::VETO_SIGNALING));
        assert!(flags.has(CapabilityFlags::SHADOW_WRAPPING));
    }

    #[test]
    fn test_version_negotiation_message() {
        let version = ProtocolVersion::new(1, 0);
        let flags = CapabilityFlags::default();

        let message = VersionNegotiationMessage::default_with_version(version);

        assert_eq!(message.preferred_version, version);
        assert!(message.supported_versions.contains(&version));
        assert_eq!(message.capabilities.as_u8(), flags.as_u8());
    }

    #[test]
    fn test_select_best_version() {
        let client_msg = VersionNegotiationMessage::new(
            vec![ProtocolVersion::new(1, 0), ProtocolVersion::new(2, 0)],
            ProtocolVersion::new(2, 0),
            CapabilityFlags::default(),
        );

        let server_versions = vec![ProtocolVersion::new(1, 0), ProtocolVersion::new(1, 1)];

        // 服务器不支持 v2.0，应选择 v1.0
        let selected = client_msg.select_best_version(&server_versions);
        assert_eq!(selected, Some(ProtocolVersion::new(1, 0)));
    }

    #[test]
    fn test_select_best_version_preferred() {
        let client_msg = VersionNegotiationMessage::new(
            vec![ProtocolVersion::new(1, 0), ProtocolVersion::new(1, 1)],
            ProtocolVersion::new(1, 1),
            CapabilityFlags::default(),
        );

        let server_versions = vec![
            ProtocolVersion::new(1, 0),
            ProtocolVersion::new(1, 1),
            ProtocolVersion::new(2, 0),
        ];

        // 服务器支持首选版本 v1.1
        let selected = client_msg.select_best_version(&server_versions);
        assert_eq!(selected, Some(ProtocolVersion::new(1, 1)));
    }

    #[test]
    fn test_select_best_version_no_common() {
        let client_msg = VersionNegotiationMessage::new(
            vec![ProtocolVersion::new(3, 0)],
            ProtocolVersion::new(3, 0),
            CapabilityFlags::default(),
        );

        let server_versions = vec![ProtocolVersion::new(1, 0), ProtocolVersion::new(2, 0)];

        // 没有共同版本
        let selected = client_msg.select_best_version(&server_versions);
        assert!(selected.is_none());
    }

    #[test]
    fn test_has_common_version() {
        let client_msg = VersionNegotiationMessage::new(
            vec![ProtocolVersion::new(1, 0), ProtocolVersion::new(2, 0)],
            ProtocolVersion::new(2, 0),
            CapabilityFlags::default(),
        );

        let server_versions = vec![ProtocolVersion::new(1, 0), ProtocolVersion::new(1, 1)];

        assert!(client_msg.has_common_version(&server_versions));

        let server_versions_v2 = vec![ProtocolVersion::new(3, 0)];
        assert!(!client_msg.has_common_version(&server_versions_v2));
    }

    #[test]
    fn test_upgrade_negotiation_requires_upgrade() {
        let client = ProtocolVersion::new(1, 0);
        let server = ProtocolVersion::new(2, 0);

        let result = client.check_compatibility(&server).unwrap();
        assert!(result.requires_upgrade());

        let info = result.upgrade_info();
        assert_eq!(info, Some((client, server)));
    }

    #[test]
    fn test_upgrade_negotiation_compatible() {
        let client = ProtocolVersion::new(1, 0);
        let server = ProtocolVersion::new(1, 1);

        let result = client.check_compatibility(&server).unwrap();
        assert!(!result.requires_upgrade());
        assert!(result.upgrade_info().is_none());
    }

    #[test]
    fn test_version_ord() {
        let v1_0 = ProtocolVersion::new(1, 0);
        let v1_1 = ProtocolVersion::new(1, 1);
        let v2_0 = ProtocolVersion::new(2, 0);

        assert!(v1_0 < v1_1);
        assert!(v1_1 < v2_0);
        assert!(v1_0 < v2_0);

        assert_eq!(v1_0, v1_0);
        assert_ne!(v1_0, v1_1);
    }

    #[test]
    fn test_version_hash() {
        use std::collections::HashSet;

        let v1_0 = ProtocolVersion::new(1, 0);
        let v1_1 = ProtocolVersion::new(1, 1);
        let v2_0 = ProtocolVersion::new(2, 0);

        let mut set = HashSet::new();
        set.insert(v1_0);
        set.insert(v1_1);
        set.insert(v2_0);
        set.insert(v1_0); // 重复

        assert_eq!(set.len(), 3);
    }
}
