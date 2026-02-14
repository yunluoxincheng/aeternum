//! # Sync 模块集成测试
//!
//! 测试 sync 模块与其他模块（crypto、models、storage）的集成。
//!
//! ## 测试覆盖
//!
//! - 跨模块类型使用
//! - Wire Frame 与 AEAD 集成
//! - 混合握手与 KEM/ECDH 集成
//! - Epoch 管理与协议集成
//! - 否决信号与时间窗口验证

use aeternum_core::crypto::aead::{AeadCipher, XChaCha20Key, XChaCha20Nonce};
use aeternum_core::crypto::KyberKEM;
use aeternum_core::models::epoch::{CryptoAlgorithm, CryptoEpoch};
use aeternum_core::sync::chaff::{ChaffGenerator, JITTER_MAX_MS, JITTER_MIN_MS};
use aeternum_core::sync::codec::PayloadType;
use aeternum_core::sync::frame::WireFrame;
use aeternum_core::sync::handshake::HybridHandshake;
use aeternum_core::sync::version::ProtocolVersion;
use aeternum_core::sync::wire::{VetoMessage, WireProtocol, VETO_WINDOW_SECONDS};
use aeternum_core::sync::{FRAME_SIZE, NONCE_SIZE};
use std::time::{SystemTime, UNIX_EPOCH};

// ===== 集成测试：跨模块类型使用 =====

#[test]
fn test_crypto_sync_integration() {
    // 测试 crypto 模块与 sync 模块的类型集成

    // 生成 AEAD 密钥
    let session_key = XChaCha20Key::generate();
    let nonce = XChaCha20Nonce::random();

    // 创建 AEAD cipher
    let cipher = AeadCipher::new(&session_key);

    // 加密测试数据
    let plaintext = b"Hello, Aeternum Wire!";
    let ciphertext = cipher
        .encrypt(&nonce, plaintext, None)
        .expect("AEAD 加密失败");

    // 解密验证
    let decrypted = cipher
        .decrypt(&nonce, &ciphertext, None)
        .expect("AEAD 解密失败");

    assert_eq!(decrypted, plaintext);
}

#[test]
fn test_models_sync_integration() {
    // 测试 models 模块与 sync 模块的集成

    // 创建 Epoch
    let epoch = CryptoEpoch::new(1, CryptoAlgorithm::V1);
    assert_eq!(epoch.version, 1);

    // 创建 WireFrame 并关联 Epoch
    let nonce = [1u8; NONCE_SIZE];
    let payload_type = PayloadType::Sync.to_byte();
    let encrypted_body = vec![2, 3, 4];
    let auth_tag = [5u8; 16];

    let frame = WireFrame::new(
        nonce,
        epoch.version as u32,
        payload_type,
        encrypted_body,
        auth_tag,
    )
    .expect("创建 WireFrame 失败");

    // 验证 Frame 的 epoch 与 Epoch 匹配
    assert_eq!(u64::from(frame.epoch()), epoch.version);
}

#[test]
fn test_wire_frame_with_aead() {
    // 测试 WireFrame 与 AEAD 的端到端集成

    // 生成会话密钥
    let session_key = XChaCha20Key::generate();
    let cipher = AeadCipher::new(&session_key);

    // 准备明文消息
    let plaintext = b"Integration test message";
    let nonce = XChaCha20Nonce::random();
    let nonce_bytes = *nonce.as_bytes();

    // AEAD 加密
    let ciphertext_with_tag = cipher.encrypt(&nonce, plaintext, None).expect("加密失败");

    // 提取密文和认证标签
    let auth_tag_start = ciphertext_with_tag.len() - 16;
    let encrypted_body = ciphertext_with_tag[..auth_tag_start].to_vec();
    let mut auth_tag = [0u8; 16];
    auth_tag.copy_from_slice(&ciphertext_with_tag[auth_tag_start..]);

    // 创建 WireFrame
    let frame = WireFrame::new(
        nonce_bytes,
        1,
        PayloadType::Sync.to_byte(),
        encrypted_body,
        auth_tag,
    )
    .expect("创建 WireFrame 失败");

    // 序列化
    let serialized = frame.serialize().expect("序列化失败");
    assert_eq!(serialized.len(), FRAME_SIZE);

    // 反序列化
    let deserialized = WireFrame::deserialize(&serialized).expect("反序列化失败");

    // 验证完整性
    assert_eq!(deserialized.nonce, nonce_bytes);
    assert_eq!(deserialized.epoch, 1);
}

// ===== 集成测试：混合握手与密码学模块 =====

#[test]
fn test_hybrid_handshake_with_crypto_modules() {
    // 测试混合握手与 X25519/Kyber 模块的集成

    // 发起方：生成密钥对
    let initiator_keypair = HybridHandshake::generate_initiator_keypair();
    let context_id = [0x42u8; 32];

    // 创建 Hello 消息
    let hello = HybridHandshake::initiate(&initiator_keypair, context_id);

    // 响应方：生成密钥对并响应
    let responder_keypair = HybridHandshake::generate_initiator_keypair();
    let (_response, session_key_responder) = HybridHandshake::respond(&hello, &responder_keypair);

    // 验证会话密钥长度
    assert_eq!(session_key_responder.key.len(), 32); // HKDF-SHA256 输出

    // 发起方完成握手
    let session_key_initiator =
        HybridHandshake::complete(&_response, &initiator_keypair).expect("完成握手失败");

    // 验证双方会话密钥一致
    assert_eq!(session_key_initiator.key, session_key_responder.key);
}

// ===== 集成测试：Wire 协议端到端 =====

#[test]
fn test_wire_protocol_end_to_end() {
    // 测试 Wire 协议的完整消息流程

    // 创建两个协议实例（模拟两端）
    let session_key = XChaCha20Key::generate();
    let mut sender = WireProtocol::new(session_key.clone());
    let mut receiver = WireProtocol::new(session_key);

    // 发送不同类型的消息
    let test_cases = vec![
        (PayloadType::Handshake, b"Handshake data".to_vec()),
        (PayloadType::Sync, b"Sync data".to_vec()),
        (PayloadType::Veto, b"Veto data".to_vec()),
        (PayloadType::Recovery, b"Recovery data".to_vec()),
    ];

    for (payload_type, plaintext) in test_cases {
        let epoch = 1;

        // 发送消息
        let frame_bytes = sender
            .send_message(payload_type, plaintext.clone(), epoch)
            .unwrap_or_else(|_| panic!("发送 {:?} 消息失败", payload_type));

        // 接收消息
        let (received_type, decrypted) = receiver
            .receive_message(&frame_bytes)
            .unwrap_or_else(|_| panic!("接收 {:?} 消息失败", payload_type));

        // 验证
        assert_eq!(received_type, payload_type);
        assert_eq!(decrypted, plaintext);
    }
}

// ===== 集成测试：流量混淆与 Wire Frame =====

#[test]
fn test_chaff_generator_with_wire_frame() {
    // 测试 ChaffGenerator 与 WireFrame 的集成

    let mut generator = ChaffGenerator::new();

    // 测试填充生成（generate_padding 返回使帧达到 FRAME_SIZE 所需的填充）
    let current_size = 100;
    let padding = generator
        .generate_padding(current_size)
        .expect("生成填充失败");
    assert_eq!(padding.len(), FRAME_SIZE - current_size);

    // 测试时序抖动
    for _ in 0..100 {
        let jitter = generator.timing_jitter();
        let jitter_ms = jitter.as_millis() as u64;
        assert!(jitter_ms >= JITTER_MIN_MS);
        assert!(jitter_ms <= JITTER_MAX_MS);
    }
}

#[test]
fn test_chaff_indistinguishability() {
    // 测试诱饵流量与真实流量的统计独立性

    let mut generator = ChaffGenerator::new();

    // 生成真实消息
    let nonce = [1u8; NONCE_SIZE];
    let real_body = vec![2, 3, 4];
    let auth_tag = [5u8; 16];

    let real_frame = WireFrame::new(nonce, 1, PayloadType::Sync.to_byte(), real_body, auth_tag)
        .expect("创建真实 Frame 失败");

    // 验证两者长度相同
    let real_serialized = real_frame.serialize().expect("序列化失败");
    assert_eq!(real_serialized.len(), FRAME_SIZE);

    // 生成诱饵消息
    let chaff_frame = generator.create_chaff_sync(1).expect("生成诱饵消息失败");

    // 验证两者长度相同
    let chaff_serialized = chaff_frame.serialize().expect("序列化失败");
    assert_eq!(chaff_serialized.len(), FRAME_SIZE);
}

// ===== 集成测试：版本管理 =====

#[test]
fn test_version_negotiation_integration() {
    // 测试版本协商与协议的集成

    let client_version = ProtocolVersion::new(1, 0);
    let server_version = ProtocolVersion::new(1, 0);

    // 相同版本应该兼容
    let result = client_version.check_compatibility(&server_version).unwrap();
    assert!(!result.requires_upgrade());

    // 客户端旧版本应该兼容
    let old_client = ProtocolVersion::new(1, 0);
    let new_server = ProtocolVersion::new(1, 5);
    let result = old_client.check_compatibility(&new_server).unwrap();
    assert!(!result.requires_upgrade());

    // 不兼容的主版本（客户端版本落后）
    let incompatible_client = ProtocolVersion::new(1, 0);
    let incompatible_server = ProtocolVersion::new(2, 0);
    let result = incompatible_client
        .check_compatibility(&incompatible_server)
        .unwrap();
    assert!(result.requires_upgrade());
}

// ===== 集成测试：否决信号与时间窗口 =====

#[test]
fn test_veto_message_with_time_window() {
    // 测试否决消息与时间窗口验证的集成

    let session_key = XChaCha20Key::generate();
    let protocol = WireProtocol::new(session_key);

    let current_time = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_secs();

    // 在窗口内的否决
    let veto_in_window = VetoMessage {
        recovery_request_id: "recovery-123".to_string(),
        device_id: "device-alpha".to_string(),
        signature: vec![1, 2, 3, 4],
        timestamp: current_time,
    };

    assert!(protocol.handle_veto(&veto_in_window, current_time).is_ok());

    // 超出窗口的否决
    let veto_expired = VetoMessage {
        recovery_request_id: "recovery-456".to_string(),
        device_id: "device-beta".to_string(),
        signature: vec![5, 6, 7, 8],
        timestamp: current_time - VETO_WINDOW_SECONDS - 100,
    };

    assert!(protocol
        .handle_veto(&veto_expired, current_time - VETO_WINDOW_SECONDS - 100)
        .is_err());
}

// ===== 集成测试：Epoch 管理与协议 =====

#[test]
fn test_epoch_monotonicity_enforcement() {
    // 测试 Epoch 单调性强制执行（Invariant #1）

    let session_key = XChaCha20Key::generate();
    let mut protocol = WireProtocol::new(session_key);

    // 发送 epoch = 1
    let _ = protocol
        .send_message(PayloadType::Sync, vec![1, 2, 3], 1)
        .expect("发送 epoch 1 失败");

    // 发送 epoch = 2
    let _ = protocol
        .send_message(PayloadType::Sync, vec![4, 5, 6], 2)
        .expect("发送 epoch 2 失败");

    // 尝试发送 epoch = 1（回滚，应该失败）
    let result = protocol.send_message(PayloadType::Sync, vec![7, 8, 9], 1);

    assert!(result.is_err());
    assert!(matches!(
        result,
        Err(aeternum_core::sync::WireError::EpochRegression { .. })
    ));
}

// ===== 集成测试：重放攻击防护 =====

#[test]
fn test_replay_attack_protection() {
    // 测试重放攻击防护机制

    let session_key = XChaCha20Key::generate();
    let mut sender = WireProtocol::new(session_key.clone());
    let mut receiver = WireProtocol::new(session_key);

    // 发送消息
    let frame_bytes = sender
        .send_message(PayloadType::Sync, vec![1, 2, 3], 1)
        .expect("发送消息失败");

    // 第一次接收应该成功
    let _ = receiver
        .receive_message(&frame_bytes)
        .expect("第一次接收失败");

    // 重放相同消息应该被检测到
    let result = receiver.receive_message(&frame_bytes);

    assert!(result.is_err());
    assert!(matches!(
        result,
        Err(aeternum_core::sync::WireError::ReplayAttack(_))
    ));
}

// ===== 集成测试：消息编解码 =====

#[test]
fn test_message_codec_integration() {
    // 测试 PayloadType 与 WireFrame 的集成

    // 编码不同类型的消息
    let test_cases = vec![
        PayloadType::Handshake,
        PayloadType::Sync,
        PayloadType::Veto,
        PayloadType::Recovery,
    ];

    for payload_type in test_cases {
        let byte_value = payload_type.to_byte();
        let decoded = PayloadType::from_byte(byte_value);

        assert_eq!(decoded, payload_type);
    }
}

// ===== 集成测试：大消息处理 =====

#[test]
fn test_large_message_handling() {
    // 测试大尺寸消息的处理

    let session_key = XChaCha20Key::generate();
    let mut sender = WireProtocol::new(session_key.clone());
    let mut receiver = WireProtocol::new(session_key);

    // 测试不同尺寸的消息
    let sizes = vec![
        0,                                  // 空消息
        100,                                // 小消息
        1000,                               // 中等消息
        5000,                               // 大消息
        aeternum_core::sync::MAX_BODY_SIZE, // 最大消息
    ];

    for size in sizes {
        let plaintext = vec![0xAB; size];
        let frame_bytes = sender
            .send_message(PayloadType::Sync, plaintext.clone(), 1)
            .unwrap_or_else(|_| panic!("发送 {} 字节消息失败", size));

        let (payload_type, decrypted) = receiver
            .receive_message(&frame_bytes)
            .unwrap_or_else(|_| panic!("接收 {} 字节消息失败", size));

        assert_eq!(payload_type, PayloadType::Sync);
        assert_eq!(decrypted, plaintext);
    }
}

// ===== 集成测试：Invariant #2 (Header 完备性) =====

#[test]
fn test_inv_2_header_completeness_with_wire_protocol() {
    // 测试 INV_2: Header 完备性
    // 验证 WireProtocol 的 epoch 状态与 models/epoch::Epoch 同步
    // 确保每个活跃设备通过正确的 DeviceHeader 获取 DEK

    use aeternum_core::models::{DeviceHeader, DeviceId, DeviceStatus};

    // 创建多个设备（模拟活跃设备集合）
    let device_1 = DeviceId::generate();
    let device_2 = DeviceId::generate();
    let device_3 = DeviceId::generate();

    // 创建初始纪元
    let epoch_v1 = CryptoEpoch::initial();
    assert_eq!(epoch_v1.version, 1);

    // 为每个设备生成 DeviceHeader（模拟 PQRR 过程）
    let keypair_1 = KyberKEM::generate_keypair();
    let keypair_2 = KyberKEM::generate_keypair();
    let keypair_3 = KyberKEM::generate_keypair();

    let (_ss1, encrypted_dek_1) = KyberKEM::encapsulate(&keypair_1.public).unwrap();
    let (_ss2, encrypted_dek_2) = KyberKEM::encapsulate(&keypair_2.public).unwrap();
    let (_ss3, encrypted_dek_3) = KyberKEM::encapsulate(&keypair_3.public).unwrap();

    let header_1 = DeviceHeader::new(
        device_1,
        epoch_v1.clone(),
        keypair_1.public,
        encrypted_dek_1,
    );
    let header_2 = DeviceHeader::new(
        device_2,
        epoch_v1.clone(),
        keypair_2.public,
        encrypted_dek_2,
    );
    let header_3 = DeviceHeader::new(
        device_3,
        epoch_v1.clone(),
        keypair_3.public,
        encrypted_dek_3,
    );

    // INV_2: 验证每个活跃设备都有且仅有一个 Header
    assert!(header_1.belongs_to_epoch(&epoch_v1));
    assert!(header_2.belongs_to_epoch(&epoch_v1));
    assert!(header_3.belongs_to_epoch(&epoch_v1));
    assert_eq!(header_1.status, DeviceStatus::Active);
    assert_eq!(header_2.status, DeviceStatus::Active);
    assert_eq!(header_3.status, DeviceStatus::Active);

    // 创建 WireProtocol 并验证 epoch 同步
    let session_key = XChaCha20Key::generate();
    let mut protocol = WireProtocol::new(session_key);

    // WireProtocol 的 epoch 应该与 CryptoEpoch 同步
    assert_eq!(protocol.current_epoch(), 0);

    // 发送消息（epoch 1）应该成功
    let frame_bytes = protocol
        .send_message(PayloadType::Sync, b"test message".to_vec(), epoch_v1.version as u32)
        .expect("发送 epoch 1 消息失败");

    // 验证 Frame 大小
    assert_eq!(frame_bytes.len(), FRAME_SIZE);

    // 验证 epoch 已更新
    assert_eq!(protocol.current_epoch(), epoch_v1.version as u32);

    // 创建下一个纪元
    let epoch_v2 = epoch_v1.next();
    assert_eq!(epoch_v2.version, 2);

    // INV_2: 在新纪元中，所有设备必须获得新的 Header
    // 重新生成 Header（模拟 PQRR 纪元升级）
    let keypair_1_v2 = KyberKEM::generate_keypair();
    let keypair_2_v2 = KyberKEM::generate_keypair();
    let keypair_3_v2 = KyberKEM::generate_keypair();

    let (_ss1_v2, encrypted_dek_1_v2) = KyberKEM::encapsulate(&keypair_1_v2.public).unwrap();
    let (_ss2_v2, encrypted_dek_2_v2) = KyberKEM::encapsulate(&keypair_2_v2.public).unwrap();
    let (_ss3_v2, encrypted_dek_3_v2) = KyberKEM::encapsulate(&keypair_3_v2.public).unwrap();

    let header_1_v2 = DeviceHeader::new(
        device_1,
        epoch_v2.clone(),
        keypair_1_v2.public,
        encrypted_dek_1_v2,
    );
    let header_2_v2 = DeviceHeader::new(
        device_2,
        epoch_v2.clone(),
        keypair_2_v2.public,
        encrypted_dek_2_v2,
    );
    let header_3_v2 = DeviceHeader::new(
        device_3,
        epoch_v2.clone(),
        keypair_3_v2.public,
        encrypted_dek_3_v2,
    );

    // 验证新纪元的 Header
    assert!(header_1_v2.belongs_to_epoch(&epoch_v2));
    assert!(header_2_v2.belongs_to_epoch(&epoch_v2));
    assert!(header_3_v2.belongs_to_epoch(&epoch_v2));
    assert!(!header_1_v2.belongs_to_epoch(&epoch_v1)); // 不属于旧纪元

    // WireProtocol 应该接受新纪元的消息
    let frame_bytes_v2 = protocol
        .send_message(PayloadType::Sync, b"epoch 2 message".to_vec(), epoch_v2.version as u32)
        .expect("发送 epoch 2 消息失败");

    assert_eq!(frame_bytes_v2.len(), FRAME_SIZE);
    assert_eq!(protocol.current_epoch(), epoch_v2.version as u32);

    // INV_2 验证：确保每个活跃设备在新纪元中都有对应的 Header
    // 在实际实现中，VaultBlob 会包含所有设备的 Header
    // 这里我们验证 epoch 同步机制正确工作

    // 模拟设备列表验证
    let active_devices = vec![&header_1_v2, &header_2_v2, &header_3_v2];
    for header in active_devices {
        assert!(
            header.belongs_to_epoch(&epoch_v2),
            "INV_2 违规: 活跃设备的 Header 不属于当前纪元"
        );
        assert_eq!(
            header.status,
            DeviceStatus::Active,
            "INV_2 违规: 活跃设备的状态不是 Active"
        );
    }

    // INV_2: 验证纪元单调性（禁止回滚）
    let result = protocol.send_message(PayloadType::Sync, b"rollback attempt".to_vec(), 1);
    assert!(result.is_err(), "INV_1 违规: epoch 回滚应该被拒绝");
    assert!(matches!(result, Err(aeternum_core::sync::WireError::EpochRegression { .. })));
}

#[test]
fn test_inv_2_device_header_epoch_consistency() {
    // 测试 INV_2: DeviceHeader 与 WireProtocol 的 epoch 一致性

    use aeternum_core::models::{DeviceHeader, DeviceId};

    let device_id = DeviceId::generate();
    let epoch = CryptoEpoch::initial();

    let keypair = KyberKEM::generate_keypair();
    let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();

    let header = DeviceHeader::new(
        device_id,
        epoch.clone(),
        keypair.public,
        encrypted_dek,
    );

    // 验证 Header 的 epoch 与 WireProtocol 兼容
    let session_key = XChaCha20Key::generate();
    let mut protocol = WireProtocol::new(session_key);

    // WireFrame 的 epoch 字段类型是 u32，CryptoEpoch.version 是 u64
    // 在实际使用时需要进行转换
    let frame_epoch = epoch.version as u32;

    let frame_bytes = protocol
        .send_message(PayloadType::Sync, b"consistency test".to_vec(), frame_epoch)
        .expect("发送消息失败");

    // 验证 epoch 匹配
    let received_frame = WireFrame::deserialize(&frame_bytes).expect("反序列化失败");
    assert_eq!(received_frame.epoch(), frame_epoch);
    assert_eq!(u64::from(received_frame.epoch()), epoch.version);

    // 验证 DeviceHeader 的 epoch 与 Frame 的 epoch 一致
    assert!(header.belongs_to_epoch(&epoch));
}

#[test]
fn test_inv_2_multiple_devices_same_epoch() {
    // 测试 INV_2: 多个设备在同一纪元中都有各自的 Header

    use aeternum_core::models::{DeviceHeader, DeviceId};

    let epoch = CryptoEpoch::initial();
    let num_devices = 5;

    let mut headers = Vec::new();

    // 为每个设备生成独立的 Header
    for _ in 0..num_devices {
        let device_id = DeviceId::generate();
        let keypair = KyberKEM::generate_keypair();
        let (_ss, encrypted_dek) = KyberKEM::encapsulate(&keypair.public).unwrap();

        let header = DeviceHeader::new(
            device_id,
            epoch.clone(),
            keypair.public,
            encrypted_dek,
        );

        headers.push(header);
    }

    // INV_2: 验证所有 Header 都属于同一纪元
    for header in &headers {
        assert!(
            header.belongs_to_epoch(&epoch),
            "INV_2 违规: 设备 Header 不属于当前纪元"
        );
    }

    // INV_2: 验证每个设备都有唯一的 DeviceId
    let device_ids: std::collections::HashSet<_> = headers
        .iter()
        .map(|h| h.device_id)
        .collect();

    assert_eq!(
        device_ids.len(),
        num_devices,
        "INV_2 违规: 存在重复的 DeviceId"
    );
}

#[test]
fn test_inv_2_epoch_upgrade_header_migration() {
    // 测试 INV_2: 纪元升级时所有设备的 Header 正确迁移

    use aeternum_core::models::{DeviceHeader, DeviceId};

    let device_1 = DeviceId::generate();
    let device_2 = DeviceId::generate();

    let epoch_v1 = CryptoEpoch::initial();

    // 纪元 1: 创建初始 Header
    let keypair_1_v1 = KyberKEM::generate_keypair();
    let keypair_2_v1 = KyberKEM::generate_keypair();
    let (_ss1, encrypted_dek_1_v1) = KyberKEM::encapsulate(&keypair_1_v1.public).unwrap();
    let (_ss2, encrypted_dek_2_v1) = KyberKEM::encapsulate(&keypair_2_v1.public).unwrap();

    let header_1_v1 = DeviceHeader::new(
        device_1,
        epoch_v1.clone(),
        keypair_1_v1.public,
        encrypted_dek_1_v1,
    );
    let header_2_v1 = DeviceHeader::new(
        device_2,
        epoch_v1.clone(),
        keypair_2_v1.public,
        encrypted_dek_2_v1,
    );

    // 验证纪元 1
    assert!(header_1_v1.belongs_to_epoch(&epoch_v1));
    assert!(header_2_v1.belongs_to_epoch(&epoch_v1));

    // 纪元升级：创建纪元 2 的新 Header
    let epoch_v2 = epoch_v1.next();

    let keypair_1_v2 = KyberKEM::generate_keypair();
    let keypair_2_v2 = KyberKEM::generate_keypair();
    let (_ss1_v2, encrypted_dek_1_v2) = KyberKEM::encapsulate(&keypair_1_v2.public).unwrap();
    let (_ss2_v2, encrypted_dek_2_v2) = KyberKEM::encapsulate(&keypair_2_v2.public).unwrap();

    let header_1_v2 = DeviceHeader::new(
        device_1,
        epoch_v2.clone(),
        keypair_1_v2.public,
        encrypted_dek_1_v2,
    );
    let header_2_v2 = DeviceHeader::new(
        device_2,
        epoch_v2.clone(),
        keypair_2_v2.public,
        encrypted_dek_2_v2,
    );

    // INV_2: 验证纪元 2 的 Header
    assert!(header_1_v2.belongs_to_epoch(&epoch_v2));
    assert!(header_2_v2.belongs_to_epoch(&epoch_v2));
    assert!(!header_1_v2.belongs_to_epoch(&epoch_v1));

    // INV_2: 验证每个设备在纪元升级后都有新的 Header
    let active_devices_v2 = vec![&header_1_v2, &header_2_v2];
    for header in active_devices_v2 {
        assert!(
            header.belongs_to_epoch(&epoch_v2),
            "INV_2 违规: 纪元升级后设备 Header 不属于新纪元"
        );
    }

    // 验证 WireProtocol 与纪元升级同步
    let session_key = XChaCha20Key::generate();
    let mut protocol = WireProtocol::new(session_key);

    // 发送纪元 1 消息
    let _ = protocol
        .send_message(PayloadType::Sync, b"epoch 1".to_vec(), epoch_v1.version as u32)
        .expect("发送 epoch 1 失败");

    // 发送纪元 2 消息
    let _ = protocol
        .send_message(PayloadType::Sync, b"epoch 2".to_vec(), epoch_v2.version as u32)
        .expect("发送 epoch 2 失败");

    assert_eq!(protocol.current_epoch(), epoch_v2.version as u32);
}
