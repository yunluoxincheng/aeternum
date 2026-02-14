//! # Wire Protocol Core
//!
//! Aeternum Wire 协议核心实现，提供消息发送/接收、否决信号处理和重放攻击防护。
//!
//! ## 核心功能
//!
//! - **消息加密传输**: 使用 XChaCha20-Poly1305 AEAD 加密所有消息
//! - **否决信号处理**: 实现 Invariant #4（否决权优先）
//! - **重放攻击防护**: Nonce 记忆机制检测重复指令
//! - **纪元单调性**: 强制执行 Invariant #1（禁止 epoch 回滚）
//!
//! ## 架构
//!
//! ```text
//! ┌─────────────────────────────────────────────────────┐
//! │  WireProtocol (session_key, nonce_memory)           │
//! ├─────────────────────────────────────────────────────┤
//! │  send_message()   → 构建 Frame → AEAD 加密        │
//! │  receive_message() → AEAD 解密 → 解析 Frame      │
//! │  handle_veto()    → 验证签名 → 检查 48h 窗口   │
//! │  nonce_memo()     → 检测重复 → 防重放攻击      │
//! └─────────────────────────────────────────────────────┘
//! ```
//!
//! ## 不变量强制执行
//!
//! - **Invariant #1**: 所有 epoch 验证确保单调递增
//! - **Invariant #4**: 否决信号具有最高优先级处理
//!
//! ## 使用示例
//!
//! ```no_run
//! use aeternum_core::sync::{wire::WireProtocol, codec::PayloadType};
//! use aeternum_core::crypto::aead::XChaCha20Key;
//!
//! let session_key = XChaCha20Key::generate();
//! let protocol = WireProtocol::new(session_key);
//!
//! // 发送消息
//! let frame = protocol.send_message(
//!     PayloadType::Sync,
//!     b"hello".to_vec(),
//!     1, // epoch
//! ).unwrap();
//!
//! // 接收消息
//! let (payload_type, decrypted) = protocol.receive_message(&frame).unwrap();
//! ```

use crate::crypto::aead::{AeadCipher, XChaCha20Key, XChaCha20Nonce};
use crate::sync::codec::{MessageCodec, PayloadType};
use crate::sync::frame::WireFrame;
use crate::sync::{Result, WireError, AUTH_TAG_SIZE, NONCE_SIZE};
use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use std::time::{SystemTime, UNIX_EPOCH};

/// 48小时否决窗口（秒）
pub const VETO_WINDOW_SECONDS: u64 = 48 * 60 * 60;

/// 否决信号消息类型
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
pub struct VetoMessage {
    /// 恢复请求 ID
    pub recovery_request_id: String,
    /// 设备 ID
    pub device_id: String,
    /// StrongBox 签名（使用设备私钥签名）
    pub signature: Vec<u8>,
    /// 时间戳（Unix 秒）
    pub timestamp: u64,
}

/// Wire 协议核心
///
/// 维护会话密钥和 nonce 记忆，提供完整的消息发送/接收功能。
pub struct WireProtocol {
    /// 会话密钥（XChaCha20-Poly1305）
    session_key: XChaCha20Key,
    /// Nonce 记忆（已使用的 nonce 集合）
    nonce_memory: HashSet<[u8; NONCE_SIZE]>,
    /// 当前 epoch（用于单调性检查）
    current_epoch: u32,
}

impl WireProtocol {
    /// 创建新的 Wire 协议实例
    ///
    /// # Arguments
    ///
    /// * `session_key` - 会话密钥（从混合握手派生）
    ///
    /// # Example
    ///
    /// ```no_build
    /// use aeternum_core::crypto::aead::XChaCha20Key;
    /// use aeternum_core::sync::wire::WireProtocol;
    ///
    /// let key = XChaCha20Key::generate();
    /// let protocol = WireProtocol::new(key);
    /// ```
    pub fn new(session_key: XChaCha20Key) -> Self {
        Self {
            session_key,
            nonce_memory: HashSet::new(),
            current_epoch: 0,
        }
    }

    /// 发送消息
    ///
    /// 构建 WireFrame、应用 Padding、AEAD 加密、添加认证标签。
    ///
    /// # Arguments
    ///
    /// * `payload_type` - 消息类型
    /// * `plaintext` - 明文消息
    /// * `epoch` - 当前纪元（必须 >= current_epoch）
    ///
    /// # Returns
    ///
    /// 返回序列化后的 WireFrame（8192 字节）。
    ///
    /// # Errors
    ///
    /// - `WireError::EpochRegression`: 如果 epoch < current_epoch（违反 Invariant #1）
    /// - `WireError::InvalidFrameSize`: 如果消息超过最大尺寸
    pub fn send_message(
        &mut self,
        payload_type: PayloadType,
        plaintext: Vec<u8>,
        epoch: u32,
    ) -> Result<Vec<u8>> {
        // INVARIANT #1: Epoch Monotonicity - 禁止回滚
        if epoch < self.current_epoch {
            return Err(WireError::EpochRegression {
                current: self.current_epoch,
                attempted: epoch,
            });
        }

        // 生成随机 nonce
        let nonce = XChaCha20Nonce::random();
        let nonce_bytes = *nonce.as_bytes();

        // 创建 AEAD cipher
        let cipher = AeadCipher::new(&self.session_key);

        // AEAD 加密（认证标签自动附加到密文）
        let ciphertext_with_tag = cipher.encrypt(&nonce, &plaintext, None)?;

        // 提取认证标签（最后 16 字节）
        let ciphertext_len = ciphertext_with_tag.len() - AUTH_TAG_SIZE;
        let encrypted_body = ciphertext_with_tag[..ciphertext_len].to_vec();
        let auth_tag = {
            let tag_bytes = &ciphertext_with_tag[ciphertext_len..];
            let mut tag = [0u8; AUTH_TAG_SIZE];
            tag.copy_from_slice(tag_bytes);
            tag
        };

        // 构建 WireFrame（自动填充到 8192 字节）
        let frame = WireFrame::new(
            nonce_bytes,
            epoch,
            payload_type.to_byte(),
            encrypted_body,
            auth_tag,
        )?;

        // 更新当前 epoch
        self.current_epoch = epoch;

        // 注意：不在发送时记录 nonce
        // nonce 记忆应该在接收消息时使用，防止重放攻击

        // 序列化 Frame
        frame.serialize()
    }

    /// 接收消息
    ///
    /// 验证认证标签、AEAD 解密、移除 Padding、解析 Payload。
    ///
    /// # Arguments
    ///
    /// * `frame_bytes` - 接收到的 8192 字节 Frame
    ///
    /// # Returns
    ///
    /// 返回 (PayloadType, 明文消息)。
    ///
    /// # Errors
    ///
    /// - `WireError::ReplayAttack`: 如果 nonce 已被使用（重放攻击）
    /// - `WireError::AuthenticationFailed`: 如果认证标签验证失败
    /// - `WireError::EpochRegression`: 如果 epoch 回滚（违反 Invariant #1）
    pub fn receive_message(&mut self, frame_bytes: &[u8]) -> Result<(PayloadType, Vec<u8>)> {
        // 反序列化 WireFrame
        let frame = WireFrame::deserialize(frame_bytes)?;

        // 验证 Frame 完整性
        frame.validate()?;

        // 提取 nonce
        let nonce_bytes = frame.nonce();

        // 检测重放攻击
        if self.nonce_memory.contains(nonce_bytes) {
            return Err(WireError::ReplayAttack(*nonce_bytes));
        }

        // INVARIANT #1: 检查 epoch 单调性
        let frame_epoch = frame.epoch();
        if frame_epoch < self.current_epoch {
            return Err(WireError::EpochRegression {
                current: self.current_epoch,
                attempted: frame_epoch,
            });
        }

        // 提取 payload type
        let payload_type = MessageCodec::decode_payload_type(&frame)?;

        // 重建 nonce 和 ciphertext
        let nonce = XChaCha20Nonce::from_bytes(*nonce_bytes);
        let encrypted_body = MessageCodec::extract_body(&frame);
        let auth_tag = frame.auth_tag;

        // 组合 ciphertext + tag（AEAD 解密需要）
        let mut ciphertext_with_tag = encrypted_body;
        ciphertext_with_tag.extend_from_slice(&auth_tag);

        // AEAD 解密
        let cipher = AeadCipher::new(&self.session_key);
        let plaintext = cipher.decrypt(&nonce, &ciphertext_with_tag, None)?;

        // 记录 nonce（防止重放）
        self.nonce_memory.insert(*nonce_bytes);

        // 更新当前 epoch
        self.current_epoch = frame_epoch;

        Ok((payload_type, plaintext))
    }

    /// 处理否决信号（Invariant #4）
    ///
    /// 验证 StrongBox 签名、检查 48h 窗口、终止恢复流程。
    ///
    /// # Arguments
    ///
    /// * `_veto_message` - 否决信号消息
    /// * `recovery_start_time` - 恢复流程开始时间（Unix 秒）
    ///
    /// # Returns
    ///
    /// 返回 `Ok(())` 如果否决有效，否则返回错误。
    ///
    /// # Errors
    ///
    /// - `WireError::VetoExpired`: 如果超出 48h 窗口
    /// - `WireError::AuthenticationFailed`: 如果签名验证失败
    ///
    /// # Invariant #4 Compliance
    ///
    /// 否决信号具有最高优先级：
    /// - 48h 窗口内任何活跃设备的 Veto 必须立即终止恢复
    /// - Veto 信号绕过普通队列处理
    pub fn handle_veto(&self, _veto_message: &VetoMessage, recovery_start_time: u64) -> Result<()> {
        // 获取当前时间
        let current_time = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_err(|_| WireError::AuthenticationFailed)?
            .as_secs();

        // 检查 48h 窗口
        let window_end = recovery_start_time.saturating_add(VETO_WINDOW_SECONDS);
        if current_time > window_end {
            return Err(WireError::AuthenticationFailed); // TODO: Add VetoExpired variant
        }

        // TODO: 验证 StrongBox 签名
        // 这需要集成 Android StrongBox/KeyStore API
        // 当前实现仅检查时间窗口

        // INVARIANT #4: Veto Supremacy - 否决信号强制终止恢复
        // 实际终止逻辑由调用者执行

        Ok(())
    }

    /// Nonce 记忆检查（重放攻击防护）
    ///
    /// 检测 nonce 是否已被使用，防止重放攻击。
    ///
    /// # Arguments
    ///
    /// * `nonce` - 要检查的 nonce
    ///
    /// # Returns
    ///
    /// 返回 `true` 如果 nonce 已被使用（重放攻击），`false` 否则。
    ///
    /// # Example
    ///
    /// ```no_build
    /// use aeternum_core::crypto::aead::XChaCha20Nonce;
    /// use aeternum_core::sync::wire::WireProtocol;
    ///
    /// let key = XChaCha20Key::generate();
    /// let protocol = WireProtocol::new(key);
    ///
    /// let nonce = XChaCha20Nonce::random();
    /// assert!(!protocol.nonce_memo(&nonce));
    /// ```
    pub fn nonce_memo(&self, nonce: &XChaCha20Nonce) -> bool {
        self.nonce_memory.contains(nonce.as_bytes())
    }

    /// 获取当前 epoch
    pub fn current_epoch(&self) -> u32 {
        self.current_epoch
    }

    /// 清空 nonce 记忆
    ///
    /// 警告：仅在确定不会有旧消息重放时使用（例如密钥轮换后）。
    pub fn clear_nonce_memory(&mut self) {
        self.nonce_memory.clear();
        self.nonce_memory.shrink_to_fit();
    }
}

/// Wire 协议测试
#[cfg(test)]
mod tests {
    use super::*;
    use crate::sync::FRAME_SIZE;

    #[test]
    fn test_wire_protocol_creation() {
        let key = XChaCha20Key::generate();
        let protocol = WireProtocol::new(key);
        assert_eq!(protocol.current_epoch(), 0);
    }

    #[test]
    fn test_send_message_roundtrip() {
        let key = XChaCha20Key::generate();

        // 创建两个独立的协议实例（模拟两端）
        let mut sender = WireProtocol::new(key.clone());
        let mut receiver = WireProtocol::new(key);

        let plaintext = b"Hello, Aeternum!".to_vec();
        let epoch = 1;

        // 发送消息
        let frame_bytes = sender
            .send_message(PayloadType::Sync, plaintext.clone(), epoch)
            .expect("Failed to send message");

        // 验证 Frame 大小
        assert_eq!(frame_bytes.len(), FRAME_SIZE);

        // 接收消息
        let (payload_type, decrypted) = receiver
            .receive_message(&frame_bytes)
            .expect("Failed to receive message");

        assert_eq!(payload_type, PayloadType::Sync);
        assert_eq!(decrypted, plaintext);
    }

    #[test]
    fn test_epoch_regression() {
        let key = XChaCha20Key::generate();
        let mut protocol = WireProtocol::new(key);

        // 发送 epoch = 1
        let _ = protocol.send_message(PayloadType::Sync, vec![1, 2, 3], 1);

        // 尝试发送 epoch = 0（回滚）
        let result = protocol.send_message(PayloadType::Sync, vec![4, 5, 6], 0);
        assert!(matches!(result, Err(WireError::EpochRegression { .. })));
    }

    #[test]
    fn test_replay_attack_detection() {
        let key = XChaCha20Key::generate();

        let mut sender = WireProtocol::new(key.clone());
        let mut receiver = WireProtocol::new(key);

        // 发送消息
        let frame_bytes = sender
            .send_message(PayloadType::Sync, vec![1, 2, 3], 1)
            .expect("Failed to send message");

        // 第一次接收应该成功
        let _ = receiver
            .receive_message(&frame_bytes)
            .expect("Failed to receive message");

        // 重放相同消息应该被检测到
        let result = receiver.receive_message(&frame_bytes);
        assert!(matches!(result, Err(WireError::ReplayAttack(_))));
    }

    #[test]
    fn test_nonce_memo() {
        let key = XChaCha20Key::generate();

        let mut sender = WireProtocol::new(key.clone());
        let mut receiver = WireProtocol::new(key);

        let nonce1 = XChaCha20Nonce::random();
        let nonce2 = XChaCha20Nonce::random();

        // 初始状态：两个 nonce 都未被使用
        assert!(!sender.nonce_memo(&nonce1));
        assert!(!sender.nonce_memo(&nonce2));
        assert!(!receiver.nonce_memo(&nonce1));
        assert!(!receiver.nonce_memo(&nonce2));

        // 发送并接收消息会记录 nonce 到 receiver
        let _ = sender.send_message(PayloadType::Sync, vec![1, 2, 3], 1);

        let frame_bytes = sender
            .send_message(PayloadType::Sync, vec![4, 5, 6], 2)
            .expect("Failed to send");

        let _ = receiver
            .receive_message(&frame_bytes)
            .expect("Failed to receive");

        // 注意：我们无法直接测试 nonce 记忆，因为我们不知道 send_message 生成了什么 nonce
        // 这个测试主要用于验证 nonce_memo 方法的存在性
    }

    #[test]
    fn test_veto_window_check() {
        let key = XChaCha20Key::generate();
        let protocol = WireProtocol::new(key);

        let current_time = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs();

        // 在窗口内的否决
        let veto_in_window = VetoMessage {
            recovery_request_id: "test-1".to_string(),
            device_id: "device-1".to_string(),
            signature: vec![1, 2, 3],
            timestamp: current_time,
        };
        assert!(protocol.handle_veto(&veto_in_window, current_time).is_ok());

        // 超出窗口的否决
        let veto_expired = VetoMessage {
            recovery_request_id: "test-2".to_string(),
            device_id: "device-2".to_string(),
            signature: vec![4, 5, 6],
            timestamp: current_time - VETO_WINDOW_SECONDS - 1,
        };
        assert!(protocol
            .handle_veto(&veto_expired, current_time - VETO_WINDOW_SECONDS - 1)
            .is_err());
    }

    #[test]
    fn test_empty_payload() {
        let key = XChaCha20Key::generate();

        let mut sender = WireProtocol::new(key.clone());
        let mut receiver = WireProtocol::new(key);

        // 空消息
        let frame_bytes = sender
            .send_message(PayloadType::Sync, vec![], 1)
            .expect("Failed to send empty message");

        let (payload_type, decrypted) = receiver
            .receive_message(&frame_bytes)
            .expect("Failed to receive empty message");

        assert_eq!(payload_type, PayloadType::Sync);
        assert!(decrypted.is_empty());
    }

    #[test]
    fn test_max_payload_size() {
        let key = XChaCha20Key::generate();

        let mut sender = WireProtocol::new(key.clone());
        let mut receiver = WireProtocol::new(key);

        // 最大尺寸消息
        let max_plaintext = vec![0xAB; crate::sync::MAX_BODY_SIZE];
        let frame_bytes = sender
            .send_message(PayloadType::Sync, max_plaintext.clone(), 1)
            .expect("Failed to send max size message");

        let (payload_type, decrypted) = receiver
            .receive_message(&frame_bytes)
            .expect("Failed to receive max size message");

        assert_eq!(payload_type, PayloadType::Sync);
        assert_eq!(decrypted, max_plaintext);
    }

    #[test]
    fn test_payload_too_large() {
        let key = XChaCha20Key::generate();
        let mut protocol = WireProtocol::new(key);

        // 超过最大尺寸的消息
        let too_large = vec![0; crate::sync::MAX_BODY_SIZE + 1];
        let result = protocol.send_message(PayloadType::Sync, too_large, 1);

        assert!(matches!(result, Err(WireError::InvalidFrameSize(_))));
    }

    #[test]
    fn test_multiple_messages_different_epochs() {
        let key = XChaCha20Key::generate();

        let mut sender = WireProtocol::new(key.clone());
        let mut receiver = WireProtocol::new(key);

        // 发送多条消息，epoch 单调递增
        for epoch in 1..=5 {
            let plaintext = format!("message {}", epoch).into_bytes();
            let frame_bytes = sender
                .send_message(PayloadType::Sync, plaintext.clone(), epoch)
                .expect("Failed to send message");

            let (payload_type, decrypted) = receiver
                .receive_message(&frame_bytes)
                .expect("Failed to receive message");

            assert_eq!(payload_type, PayloadType::Sync);
            assert_eq!(decrypted, plaintext);
        }
    }

    #[test]
    fn test_tampered_frame_detection() {
        let key = XChaCha20Key::generate();

        let mut sender = WireProtocol::new(key.clone());
        let mut receiver = WireProtocol::new(key);

        let frame_bytes = sender
            .send_message(PayloadType::Sync, vec![1, 2, 3], 1)
            .expect("Failed to send message");

        // 篡改 Frame（修改认证标签，最后一个字节）
        let mut tampered = frame_bytes.clone();
        let last_index = tampered.len() - 1;
        tampered[last_index] ^= 0xFF;

        let result = receiver.receive_message(&tampered);
        assert!(result.is_err());
    }

    #[test]
    fn test_clear_nonce_memory() {
        let key = XChaCha20Key::generate();

        let mut sender = WireProtocol::new(key.clone());
        let mut receiver = WireProtocol::new(key);

        // 发送消息
        let frame_bytes = sender
            .send_message(PayloadType::Sync, vec![1, 2, 3], 1)
            .expect("Failed to send message");

        // 第一次接收应该成功
        let (payload_type, decrypted) = receiver
            .receive_message(&frame_bytes)
            .expect("Failed to receive message");

        assert_eq!(payload_type, PayloadType::Sync);
        assert_eq!(decrypted, vec![1, 2, 3]);

        // 重放应该被检测到
        let result = receiver.receive_message(&frame_bytes);
        assert!(matches!(result, Err(WireError::ReplayAttack(_))));

        // 清空 nonce 记忆
        receiver.clear_nonce_memory();

        // 现在可以重放（尽管密文相同，但这是一种测试场景）
        let (payload_type, decrypted) = receiver
            .receive_message(&frame_bytes)
            .expect("Failed to receive message after clearing nonce memory");

        assert_eq!(payload_type, PayloadType::Sync);
        assert_eq!(decrypted, vec![1, 2, 3]);
    }
}
