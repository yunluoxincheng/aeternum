# Proposal: 添加数据模型模块 (add-models)

## 元数据

- **ID**: `add-models`
- **状态**: ✅ 已完成 (Completed)
- **完成日期**: 2026-02-13
- **创建日期**: 2026-02-12
- **审查日期**: 2026-02-13
- **作者**: Claude (Aeternum AI Assistant)
- **影响范围**: `core/src/models/` (新增模块)

---

## 概述

为 Aeternum Rust Core 添加 `models/` 模块，定义密钥层级、纪元管理、设备标识和加密容器等核心数据结构。这是实现 PQRR 协议和其他高层功能的基础。

## 背景

当前 Aeternum 的密码学原语层（`crypto/`）已经完成（173个测试全通过），但缺少定义密钥派生路径、纪元版本管理和设备元数据的抽象层。这些数据结构在架构文档中有明确定义，但尚未实现。

### 问题陈述

1. **密钥层级未形式化**：MRS → IK/RK → DK → DEK → VK 的派生路径仅存在于文档描述中
2. **纪元管理缺失**：无法表示和验证密码学纪元的单调性
3. **设备 Header 未定义**：PQRR 协议需要的设备元数据结构不存在
4. **Vault Blob 未实现**：缺少持久化存储的数据结构

### 参考文档

- `docs/arch/Aeternum-architecture.md` - 密钥层级定义
- `docs/protocols/Cold-Anchor-Recovery.md` - 派生算法规范
- `docs/math/Formal-Invariants.md` - 数学不变量定义
- `docs/protocols/Persistence-Crash-Consistency.md` - 存储一致性要求

---

## 提议的变更

### 1. 新增模块结构

```
core/src/models/
├── mod.rs              # 模块组织与公共导出
├── key_hierarchy.rs    # 密钥层级 (MRS → VK)
├── epoch.rs           # 纪元与密码学版本
├── device.rs          # 设备标识与 Header
└── vault.rs          # VaultBlob 与加密容器
```

### 2. 密钥层级类型

- **MasterSeed**: 24词助记词的 PBKDF2 派生种子 (512-bit)
- **IdentityKey**: 身份证明密钥 (32-byte, 从 MRS 派生)
- **RecoveryKey**: 恢复密钥 (32-byte, 从 MRS 派生)
- **DeviceKey**: 设备密钥（硬件生成，不暴露私钥）
- **DataEncryptionKey**: 数据加密密钥 (32-byte)
- **VaultKey**: 库加密密钥 (32-byte)

派生路径遵循 `Cold-Anchor-Recovery.md` 规范：
- IK = BLAKE3-Derive(S, "Aeternum_Identity_v1")
- RK = BLAKE3-Derive(S, "Aeternum_Recovery_v1")

### 3. 纪元管理

- **CryptoEpoch**: 纪元标识 (version, timestamp, algorithm)
- **CryptoAlgorithm**: 算法版本枚举 (V1 = Kyber-1024 + X25519 + ...)

### 4. 设备与 Header

- **DeviceId**: 16字节 UUID，Device_0 为全零（影子冷锚）
- **DeviceHeader**: 设备的加密元数据（公钥、封装的 DEK、状态）
- **DeviceStatus**: 设备状态枚举 (Active/Revoked/Degraded)

### 5. Vault Blob

- **VaultBlob**: 完整的加密数据容器（密文、认证标签、nonce）
- **VaultHeader**: 文件头部（魔数、版本、长度）

### 6. 依赖项

```
[dependencies]
# 现有依赖...
bincode = "1.3"           # VaultBlob 序列化
pbkdf2 = "0.12"           # PBKDF2-HMAC-SHA512 (MRS 派生)
bip39 = { version = "0.12", default-features = false, features = ["std"] }  # BIP-39 助记词
```

**依赖说明**：
- `bincode`: 二进制序列化，紧凑高效的编码格式
- `pbkdf2`: PBKDF2-HMAC-SHA512 实现，用于从助记词派生 MasterSeed
- `bip39`: BIP-39 标准库，用于助记词校验和种子计算

---

## 设计原则

1. **类型安全**：使用强类型 newtype，防止密钥类型混淆
2. **内存安全**：所有秘密类型实现 `Zeroize + ZeroizeOnDrop`
3. **代码一致性**：遵循 `crypto/` 模块的现有设计模式
4. **验证分离**：数据结构本身不包含验证逻辑，验证由 `protocol/` 模块处理

---

## 非目标

以下内容**不在**本提案范围内：

- UniFFI 桥接代码（留待后续提案）
- PQRR 协议逻辑（将在 `protocol/` 模块实现）
- 不变量验证引擎（将在 `protocol/` 模块实现）
- SQLCipher 集成（Android 层）

---

## 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| 密钥派生实现错误 | 用户无法恢复数据 | 使用标准测试向量验证 BLAKE3 派生 |
| 内存泄漏 | 密钥材料残留 | 强制使用 `Zeroize` trait，添加测试验证 |
| 序列化不兼容 | 无法读取旧数据 | 定义版本化的 VaultBlob 格式 |

---

## 替代方案

### 方案 A：使用泛化密钥类型
使用 `Key<用途>` 标记类型减少重复代码。

**拒绝原因**：与现有 `crypto/` 模块风格不一致，且牺牲类型安全性的清晰度。

### 方案 B：Epoch 自带验证方法
在 `CryptoEpoch` 结构中内置验证方法。

**拒绝原因**：数学不变量文档要求验证由 `AeternumState` 模块集中处理，保持数据与验证分离。

---

## 时间线

- **预计实现时间**: 1-2 天
- **测试时间**: 0.5 天
- **总计**: 约 2 天

---

## 审查清单

在批准前请确认：

- [ ] 密钥派生路径与 `Cold-Anchor-Recovery.md` 一致
- [ ] 所有秘密类型实现 `Zeroize`
- [ ] 代码风格与 `crypto/` 模块一致
- [ ] 测试覆盖所有新类型
- [ ] bincode 依赖版本兼容性检查

---

## 后续工作（可选）

以下建议可在实现过程中或后续提案中处理：

### 1. CryptoError 扩展

当前 `core/src/crypto/error.rs` 缺少 `InvariantViolation` 变体。建议在 models/ 模块实现时添加：

```rust
/// 数学不变量违反
///
/// 当检测到四大数学不变量被违反时触发：
/// - Invariant #1: 纪元单调性
/// - Invariant #2: Header 完备性
/// - Invariant #3: 因果熵障
/// - Invariant #4: 否决权优先
#[error("Invariant violation: {0}")]
InvariantViolation(String),
```

### 2. Android 层 getKeyId() 实现

当前 `android/.../security/AndroidSecurityManager.kt` 没有 `getKeyId()` 方法。建议在后续 UniFFI 桥接提案中实现：

```kotlin
/**
 * 获取硬件密钥的唯一标识符
 * 用于 Rust 层 DeviceKey 作为句柄
 */
fun getKeyId(): ByteArray {
    val key = getHardwareKey()
    // 方案 A: 使用 KeyAlias 的 BLAKE3 哈希
    return blake3Hash(HARDWARE_KEY_ALIAS.encodeToByteArray())
}
```

---

## 参考资料

- [架构白皮书 v5.0](../docs/arch/Aeternum-architecture.md)
- [数学不变量](../docs/math/Formal-Invariants.md)
- [冷锚恢复协议](../docs/protocols/Cold-Anchor-Recovery.md)
- [持久化与崩溃一致性](../docs/protocols/Persistence-Crash-Consistency.md)
