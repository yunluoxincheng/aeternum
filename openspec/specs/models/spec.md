# models Specification

## Purpose
TBD - created by archiving change add-models. Update Purpose after archive.
## Requirements
### Requirement: 密钥层级派生

The system SHALL provide a hierarchical key derivation system that transforms a master seed into specialized keys for different purposes.

#### Scenario: Derive MRS from mnemonic

- **WHEN** 24 词 BIP-39 助记词被提供
- **THEN** 系统 SHALL 使用 PBKDF2-HMAC-SHA512 (2048 iterations) 派生 512-bit MasterSeed
- **AND** 输出 SHALL 确定性的（相同助记词产生相同种子）

#### Scenario: Derive Identity Key (IK)

- **WHEN** `MasterSeed::derive_identity_key()` 被调用
- **THEN** 系统 SHALL 使用 BLAKE3-Derive with context "Aeternum_Identity_v1"
- **AND** SHALL 返回 32-byte IdentityKey
- **AND** 相同输入 SHALL 始终产生相同输出

#### Scenario: Derive Recovery Key (RK)

- **WHEN** `MasterSeed::derive_recovery_key()` 被调用
- **THEN** 系统 SHALL 使用 BLAKE3-Derive with context "Aeternum_Recovery_v1"
- **AND** SHALL 返回 32-byte RecoveryKey
- **AND** IK 和 RK SHALL 不同（域分离）

#### Scenario: Key memory safety

- **WHEN** 任何密钥类型（MasterSeed, IdentityKey, RecoveryKey, VaultKey）被 drop
- **THEN** 底层内存 SHALL 被零覆盖（通过 Zeroize）
- **AND** 密钥材料 SHALL 不在堆栈跟踪或日志中暴露

#### Scenario: Invalid mnemonic rejected

- **WHEN** BIP-39 校验和无效的助记词被提供
- **THEN** 系统 SHALL 返回 `CryptoError`
- **AND** 不生成任何密钥材料
- **AND** 内存中不残留部分派生的数据

### Requirement: 密码学纪元管理

The system SHALL provide epoch management to track cryptographic algorithm versions and ensure temporal consistency.

#### Scenario: Create initial epoch

- **WHEN** `CryptoEpoch::initial()` 被调用
- **THEN** 系统 SHALL 创建 version=0, algorithm=V1 的纪元
- **AND** SHALL 设置当前时间戳

#### Scenario: Epoch advancement

- **WHEN** `CryptoEpoch::next()` 被调用
- **THEN** 系统 SHALL 创建 version+1 的新纪元
- **AND** SHALL 继承相同的 algorithm
- **AND** version SHALL 单调递增

#### Scenario: Algorithm version identification

- **WHEN** `CryptoAlgorithm::version()` 被调用
- **THEN** V1 SHALL 返回版本号 1
- **AND** 系统 SHALL 支持 is_supported() 查询

#### Scenario: Epoch rollback detection

- **WHEN** 系统检测到 epoch.version 小于当前版本
- **THEN** SHALL 触发 `CryptoError::InvariantViolation`
- **AND** SHALL 阻止回滚操作
- **AND** SHALL 记录安全事件日志

### Requirement: 设备标识与管理

The system SHALL provide device identification with support for shadow anchor (recovery) devices.

#### Scenario: Generate device ID

- **WHEN** `DeviceId::generate()` 被调用
- **THEN** 系统 SHALL 生成 16-byte 随机标识符
- **AND** 不同调用 SHALL 产生不同 ID

#### Scenario: Identify shadow anchor

- **WHEN** `DeviceId::is_shadow_anchor()` 被调用
- **THEN** Device_0 (全零标识) SHALL 返回 true
- **AND** 任何其他设备 ID SHALL 返回 false

#### Scenario: Create device header

- **WHEN** `DeviceHeader::new()` 被调用
- **THEN** 系统 SHALL 创建包含 device_id, epoch, public_key, encrypted_dek 的 Header
- **AND** 初始状态 SHALL 为 Active

#### Scenario: Device revocation

- **WHEN** `DeviceHeader::revoke()` 被调用
- **THEN** 设备状态 SHALL 更改为 Revoked
- **AND** 被撤销的设备 SHALL 无法解密新纪元的数据

### Requirement: Vault Blob 持久化

The system SHALL provide a serialized data structure for encrypted vault storage with integrity protection.

#### Scenario: Create Vault Blob

- **WHEN** `VaultBlob::new()` 被调用
- **THEN** 系统 SHALL 创建包含 epoch, ciphertext, auth_tag, nonce 的 Blob
- **AND** SHALL 设置 blob_version 为当前版本

#### Scenario: Serialization and deserialization

- **WHEN** `VaultBlob::serialize()` 被调用
- **THEN** 系统 SHALL 返回字节表示（使用 bincode）
- **AND** `VaultBlob::deserialize()` SHALL 能够还原原始数据
- **AND** 序列化往返 SHALL 保持数据完整性

#### Scenario: Blob integrity validation

- **WHEN** `VaultBlob::validate()` 被调用
- **THEN** 系统 SHALL 检查 blob_version 是否受支持
- **AND** SHALL 验证 ciphertext 不为空
- **AND** SHALL 返回验证结果

#### Scenario: File header identification

- **WHEN** `VaultHeader::from_bytes()` 被调用
- **THEN** 系统 SHALL 验证魔数为 "AETERNM"
- **AND** SHALL 解析版本、纪元和长度信息
- **AND** 无效魔数 SHALL 导致错误返回

### Requirement: 类型安全与防止误用

The system SHALL prevent accidental misuse of key types through strong typing.

#### Scenario: Key types cannot be confused

- **WHEN** 代码尝试将 IdentityKey 传递给期望 VaultKey 的函数
- **THEN** 编译器 SHALL 报告类型错误
- **AND** 不存在隐式类型转换

#### Scenario: Device ID type safety

- **WHEN** DeviceId 被创建
- **THEN** 它 SHALL 是 16 字节的固定大小类型
- **AND** SHALL 不与 [u8; 32] 或其他大小混淆

### Requirement: 密码学纪元支持

The system SHALL support cryptographic algorithm evolution through epoch versioning.

#### Scenario: Epoch version string

- **WHEN** `CryptoEpoch::as_string()` 被调用
- **THEN** 系统 SHALL 返回格式 "epoch_N_algo_vM" 的字符串
- **AND** N 为纪元版本号，M 为算法版本号

#### Scenario: Future algorithm reservation

- **WHEN** 新的密码学算法被添加
- **THEN** 系统 SHALL 添加新的 CryptoAlgorithm 变体
- **AND** 旧纪元的数据 SHALL 仍然可读
- **AND** 纪元升级 SHALL 通过 PQRR 协议无缝迁移

---

