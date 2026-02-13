# 设计文档：数据模型模块 (add-models)

## 概述

本文档详细描述 `models/` 模块的技术设计，包括数据结构、API 设计和实现细节。

---

## 1. 模块结构

```
core/src/models/
├── mod.rs              # 模块组织与公共导出
├── key_hierarchy.rs    # 密钥层级 (MRS → VK)
├── epoch.rs           # 纪元与密码学版本
├── device.rs          # 设备标识与 Header
└── vault.rs          # VaultBlob 与加密容器
```

---

## 2. 密钥层级设计 (key_hierarchy.rs)

### 2.1 派生路径

根据 `docs/protocols/Cold-Anchor-Recovery.md` 规范：

```
MRS (24-word 助记词)
    │
    ├─ PBKDF2-SHA512 (2048 iterations) → 种子 S (512-bit)
    │
    ├─ BLAKE3-Derive(S, "Aeternum_Identity_v1") → IK (32 bytes)
    │   └─ Salt 策略: 使用 MasterSeed 本身作为 salt
    │
    ├─ BLAKE3-Derive(S, "Aeternum_Recovery_v1") → RK (32 bytes)
    │   └─ Salt 策略: 使用 MasterSeed 本身作为 salt
    │
    └─ [硬件生成] → DK (Device Key, StrongBox)
            │
            ├─ Kyber-1024 封装 → DEK (Data Encryption Key)
            │
            └─ XChaCha20 加密 → VK (Vault Key)
```

**Salt 策略说明**：

使用 `crypto::hash::DeriveKey` API 进行密钥派生时：
- **Salt**: `MasterSeed` 本身（512-bit 种子）
- **Context**: 域分离字符串（`"Aeternum_Identity_v1"` 或 `"Aeternum_Recovery_v1"`）
- **IKM (Input Key Material)**: 同样是 `MasterSeed`

这种设计确保：
1. IK 和 RK 使用相同的 salt（MasterSeed）保证确定性
2. 不同的 context 字符串提供域分离，防止 IK 和 RK 混淆
3. 与 `DeriveKey::new(salt, context)` API 完全兼容

**派生代码示例**：
```rust
impl MasterSeed {
    pub fn derive_identity_key(&self) -> IdentityKey {
        let dk = DeriveKey::new(&self.0, "Aeternum_Identity_v1");
        let key_bytes = dk.derive(&self.0, 32);
        IdentityKey(key_bytes.try_into().unwrap())
    }

    pub fn derive_recovery_key(&self) -> RecoveryKey {
        let dk = DeriveKey::new(&self.0, "Aeternum_Recovery_v1");
        let key_bytes = dk.derive(&self.0, 32);
        RecoveryKey(key_bytes.try_into().unwrap())
    }
}
```

### 2.2 数据结构

```rust
/// Master Root Seed - 24词助记词的种子表示
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct MasterSeed(pub [u8; 64]);

impl MasterSeed {
    /// 从 BIP-39 助记词派生（需要 PBKDF2-HMAC-SHA512, 2048 iter）
    pub fn from_mnemonic(words: &[&str]) -> Result<Self, CryptoError>;

    /// 派生 Identity Key (IK)
    pub fn derive_identity_key(&self) -> IdentityKey;

    /// 派生 Recovery Key (RK)
    pub fn derive_recovery_key(&self) -> RecoveryKey;
}

/// Identity Key - 身份证明密钥
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct IdentityKey(pub [u8; 32]);

/// Recovery Key - 恢复密钥
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct RecoveryKey(pub [u8; 32]);

/// Device Key - 设备密钥（硬件生成，不导出私钥）
pub struct DeviceKey {
    pub key_id: [u8; 16],
    // 私钥仅在 StrongBox/KeyStore 中，不暴露给 Rust
}

/// Data Encryption Key - 数据加密密钥（包装 VK）
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct DataEncryptionKey(pub [u8; 32]);

/// Vault Key - 实际加密用户数据库的密钥
#[derive(Zeroize, ZeroizeOnDrop)]
pub struct VaultKey(pub [u8; 32]);

// 敏感类型的 Debug 实现（防止密钥泄露）
impl std::fmt::Debug for MasterSeed {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("MasterSeed([REDACTED])")
    }
}

impl std::fmt::Debug for IdentityKey {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("IdentityKey([REDACTED])")
    }
}

impl std::fmt::Debug for RecoveryKey {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("RecoveryKey([REDACTED])")
    }
}

impl std::fmt::Debug for DataEncryptionKey {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("DataEncryptionKey([REDACTED])")
    }
}

impl std::fmt::Debug for VaultKey {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str("VaultKey([REDACTED])")
    }
}
```

---

## 3. 纪元管理设计 (epoch.rs)

### 3.1 密码学算法版本

```rust
/// 密码学算法标识符
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum CryptoAlgorithm {
    /// v1: Kyber-1024 + X25519 + XChaCha20-Poly1305 + Argon2id + BLAKE3
    V1,

    /// 保留给未来升级（如 Kyber-2, 通用后量子算法）
    #[allow(dead_code)]
    Reserved,
}

impl CryptoAlgorithm {
    pub fn version(&self) -> u32;
    pub fn is_supported(&self) -> bool;
}
```

### 3.2 纪元结构

```rust
/// 密码学纪元 - 标识密钥算法的代际
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct CryptoEpoch {
    pub version: u64,
    pub timestamp: u64,
    pub algorithm: CryptoAlgorithm,
}

impl CryptoEpoch {
    pub fn new(version: u64, algorithm: CryptoAlgorithm) -> Self;
    pub fn initial() -> Self;
    pub fn next(&self) -> Self;
    pub fn as_string(&self) -> String;
}
```

---

## 4. 设备与 Header 设计 (device.rs)

### 4.1 设备标识

```rust
/// 设备唯一标识符（16字节 UUID）
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct DeviceId(pub [u8; 16]);

impl DeviceId {
    pub fn from_bytes(bytes: [u8; 16]) -> Self;
    pub fn generate() -> Self;
    pub fn is_shadow_anchor(&self) -> bool;
    pub fn shadow_anchor() -> Self;
}
```

**影子冷锚**: Device_0 使用 `[0u8; 16]` 作为固定标识，使其在服务端与普通设备不可区分。

### 4.2 设备状态

```rust
/// 设备状态
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DeviceStatus {
    /// 设备活跃
    Active,
    /// 设备已撤销
    Revoked,
    /// 设备处于降级模式（完整性验证失败）
    Degraded,
}
```

### 4.3 设备 Header

```rust
/// 设备 Header - 存储在服务端的加密元数据
pub struct DeviceHeader {
    pub device_id: DeviceId,
    pub epoch: CryptoEpoch,
    pub public_key: KyberPublicKeyBytes,
    pub encrypted_dek: KyberCipherText,
    pub status: DeviceStatus,
    pub created_at: u64,
}

impl DeviceHeader {
    pub fn new(
        device_id: DeviceId,
        epoch: CryptoEpoch,
        public_key: KyberPublicKeyBytes,
        encrypted_dek: KyberCipherText,
    ) -> Self;

    pub fn shadow_anchor(epoch: CryptoEpoch, public_key: KyberPublicKeyBytes, encrypted_dek: KyberCipherText) -> Self;
    pub fn revoke(&mut self);
    pub fn belongs_to_epoch(&self, epoch: &CryptoEpoch) -> bool;
}
```

---

## 5. Vault Blob 设计 (vault.rs)

### 5.1 加密容器

```rust
/// Vault Blob - 完整的加密数据容器
pub struct VaultBlob {
    pub blob_version: u32,
    pub epoch: CryptoEpoch,
    pub ciphertext: Vec<u8>,
    pub auth_tag: AuthTag,
    pub nonce: XChaCha20Nonce,
}

impl VaultBlob {
    pub const CURRENT_BLOB_VERSION: u32 = 1;

    pub fn new(
        blob_version: u32,
        epoch: CryptoEpoch,
        ciphertext: Vec<u8>,
        auth_tag: AuthTag,
        nonce: XChaCha20Nonce,
    ) -> Self {
        Self { blob_version, epoch, ciphertext, auth_tag, nonce }
    }

    pub fn serialize(&self) -> Result<Vec<u8>, CryptoError>;
    pub fn deserialize(bytes: &[u8]) -> Result<Self, CryptoError>;
    pub fn validate(&self) -> Result<(), CryptoError>;
    pub fn size(&self) -> usize;
}

**序列化错误处理**：
```rust
pub fn serialize(&self) -> Result<Vec<u8>, CryptoError> {
    bincode::serialize(self)
        .map_err(|e| CryptoError::InternalError(format!("Serialization failed: {}", e)))
}

pub fn deserialize(bytes: &[u8]) -> Result<Self, CryptoError> {
    bincode::deserialize(bytes)
        .map_err(|e| CryptoError::InternalError(format!("Deserialization failed: {}", e)))
}
```

### 5.2 文件头部

```rust
/// Vault Blob 头部元数据（文件头部）
#[derive(Debug, Clone)]
pub struct VaultHeader {
    pub magic: [u8; 8],
    pub blob_version: u32,
    pub epoch_version: u64,
    pub data_length: u64,
}

impl VaultHeader {
    pub const MAGIC: [u8; 8] = *b"AETERNM";

    pub fn new(blob: &VaultBlob) -> Self;
    pub fn to_bytes(&self) -> [u8; 32];
    pub fn from_bytes(bytes: &[u8]) -> Result<Self, CryptoError>;
}
```

### 5.3 文件格式

磁盘上的 Vault 文件格式：

```
+------------------+
| VaultHeader      | 32 bytes (固定)
| - magic (8)      | "AETERNM"
| - blob_version (4) |
| - epoch_version (8)|
| - data_length (8) |
+------------------+
| VaultBlob        | 变长
| - epoch          | (serialized)
| - ciphertext     |
| - auth_tag       |
| - nonce          |
+------------------+
```

### 5.4 版本兼容性策略

| blob_version | 读取支持 | 写入支持 | 算法支持 | 迁移策略 |
|--------------|----------|----------|----------|----------|
| 1            | ✅       | ✅       | V1       | -        |
| 2+           | TBD      | TBD      | TBD      | PQRR    |

**版本升级原则**：
1. **向后兼容读取**：新版本 MUST 能读取所有旧版本 Blob
2. **向前隔离**：旧版本无法读取新版本时 SHALL 返回 `CryptoError::UnsupportedVersion`
3. **算法绑定**：`blob_version` 与 `CryptoAlgorithm` 版本解耦，允许算法升级而不改变 blob 格式
4. **PQRR 迁移**：纪元升级时自动重新封装为最新 blob_version

---

## 6. Android 集成说明

### 6.1 DeviceKey 与 KeyStore 的桥接

**现状**：Android 层 (`AndroidSecurityManager.kt:23`) 使用固定 KeyAlias `"aeternum_dk_hardware"` 存储硬件密钥，但没有 key_id 提取方法。

**集成方案**：
1. **Rust 层**：`DeviceKey` 持有 `key_id: [u8; 16]`，作为 KeyStore 密钥的句柄
2. **Android 层**：需添加 `getKeyId(): ByteArray` 方法：
   ```kotlin
   fun getKeyId(): ByteArray {
       val key = getHardwareKey()
       // 使用 KeyStore.getKey() 获取密钥的唯一标识符
       // 方案 A: 使用 KeyAlias 的 BLAKE3 哈希
       // 方案 B: 使用 KeyStore 内部的 key ID（如果可用）
       return blake3Hash(HARDWARE_KEY_ALIAS.encodeToByteArray())
   }
   ```

3. **UniFFI 桥接**：后续提案中定义 FFI 接口时传递 `key_id` 作为 16 字节数组

### 6.2 密钥生命周期

```
┌─────────────────────────────────────────────────────┐
│ Android KeyStore (StrongBox)                     │
│ ┌─────────────────────────────────────────────┐   │
│ │ DK_hardware (AES-256)                      │   │
│ │ - KeyAlias: "aeternum_dk_hardware"        │   │
│ │ - 永不离开硬件                               │   │
│ └─────────────────────────────────────────────┘   │
│                      ↑                          │
│             key_id: [u8; 16]                  │
│                      ↓                          │
│ ┌─────────────────────────────────────────────┐   │
│ │ Rust Core: DeviceKey                       │   │
│ │ - 持有 key_id 作为句柄                       │   │
│ │ - 私钥仅存在于 KeyStore 中                   │   │
│ └─────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

---

## 7. 设计决策记录

### 7.1 为什么使用强类型 newtype？

**决策**: 每个密钥类型都是独立的 newtype 包装。

**理由**:
- 防止类型混淆（如将 IK 用于 RK 的位置）
- 与现有 `crypto/` 模块风格一致
- 编译时保证类型安全

**替代方案**: 使用泛型 `Key<用途>` 标记类型。
**拒绝原因**: 牺牲类型安全性的清晰度，与现有代码不一致。

### 7.2 为什么数据与验证分离？

**决策**: 数据结构本身不包含验证逻辑，验证由 `protocol/` 模块处理。

**理由**:
- 符合数学不变量文档的要求（验证由 `AeternumState` 模块集中处理）
- 保持数据结构简单，便于序列化
- 验证逻辑集中，便于形式化验证

**替代方案**: 在 `CryptoEpoch` 中内置验证方法。
**拒绝原因**: 违反架构文档中的设计原则。

### 7.3 为什么使用 bincode 序列化？

**决策**: VaultBlob 使用 bincode 进行序列化。

**理由**:
- 二进制格式紧凑（存储效率）
- 支持derive宏（减少样板代码）
- 性能优秀（零拷贝设计）

**替代方案**: 使用 serde + JSON。
**拒绝原因**: JSON 格式冗余，不适合加密数据存储。

---

## 8. 安全考虑

### 8.1 内存安全

- 所有秘密类型实现 `Zeroize` trait
- 使用 `ZeroizeOnDrop` 确保 drop 时自动擦除
- `VaultKey`、`DataEncryptionKey` 等敏感类型禁止 Debug 显示实际内容

### 8.2 类型安全

- `DeviceKey` 仅持有 `key_id`，私钥不暴露给 Rust
- `MasterSeed` 实现 `Zeroize`，防止助记词种子在内存中残留
- 密钥类型间不能隐式转换

### 8.3 持久化安全

- VaultBlob 包含 AEAD 认证标签，防止篡改
- VaultHeader 包含魔数，便于识别和验证
- 纪元版本号确保旧客户端无法读取新格式数据

---

## 9. 测试策略

### 9.1 单元测试

每个模块的 `#[cfg(test)]` 模块包含：
- 构造函数测试
- 边界条件测试
- 错误处理测试

### 9.2 属性测试

使用 `proptest` 验证：
- 密钥派生确定性（相同输入 → 相同输出）
- 纪元单调性（next() 必须递增）
- 序列化往返（serialize → deserialize 恒等）

### 9.3 集成测试

模块间交互测试：
- 密钥派生 → 设备 Header 创建
- 纪元升级 → Vault Blob 创建
- 完整的 Vault 文件写入/读取流程

---

## 10. 性能考虑

### 10.1 序列化性能

- bincode 序列化时间: O(n) 其中 n 是数据大小
- 预期 VaultBlob 大小: < 10 MB（用户数据库）
- 序列化时间: < 100ms

### 10.2 密钥派生性能

- BLAKE3 派生: < 1ms
- PBKDF2 (2048 iterations): ~50-100ms
- 密钥层级完整派生: < 200ms

---

## 11. 未来扩展

### 11.1 密码学纪元升级

当需要升级算法时（如 Kyber-2）：
1. 添加 `CryptoAlgorithm::V2` 变体
2. 实现新的密钥派生逻辑
3. PQRR 协议自动处理纪元迁移

### 11.2 多设备支持

当前设计支持：
- 无限数量的活跃设备
- 设备撤销后立即移除 Header
- 影子冷锚永久存在

---

## 附录 A：依赖项

```toml
[dependencies]
# 现有依赖...
bincode = "1.3"           # 序列化/反序列化
```

---

## 附录 B：与现有模块集成

```rust
// core/src/lib.rs

/// Cryptographic primitives module
pub mod crypto;

/// Data models module
pub mod models;

// Re-export common types
pub use crypto::{error::CryptoError, error::Result};
pub use models::{
    key_hierarchy::{MasterSeed, IdentityKey, RecoveryKey, VaultKey},
    epoch::{CryptoEpoch, CryptoAlgorithm},
    device::{DeviceId, DeviceHeader, DeviceStatus},
    vault::{VaultBlob, VaultHeader},
};
```

---

*设计文档版本: 1.0*
*最后更新: 2026-02-12*
