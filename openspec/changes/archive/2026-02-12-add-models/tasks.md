# 实现任务清单 (add-models)

## 概览

实现 `models/` 模块的完整任务列表，按优先级和依赖关系排序。

---

## 阶段 1：基础设施

### 1.1 添加依赖
- [x] 在 `core/Cargo.toml` 中添加以下依赖：
  - [x] `bincode = "1.3"` - VaultBlob 序列化
  - [x] `pbkdf2 = "0.12"` - PBKDF2-HMAC-SHA512 (MRS 派生)
  - [x] `bip39 = { version = "2.0", default-features = false, features = ["std"] }` - BIP-39 助记词 (使用最新稳定版)
  - [x] `serde = { version = "1.0", features = ["derive"] }` - 序列化支持
- [x] 运行 `cargo check` 确认依赖解析成功

### 1.2 创建模块结构
- [x] 创建 `core/src/models/` 目录
- [x] 创建 `mod.rs` 模块文件
- [x] 在 `core/src/lib.rs` 中添加 `pub mod models;`

---

## 阶段 2：密钥层级实现

### 2.1 实现 `key_hierarchy.rs`
- [x] 定义 `MasterSeed` 结构体（512-bit, 实现 Zeroize）
  - [x] 实现 `from_mnemonic()` 方法（需要 PBKDF2-HMAC-SHA512, 2048 iter）
  - [x] 实现 `derive_identity_key()` 方法
  - [x] 实现 `derive_recovery_key()` 方法
  - [x] 实现 `as_bytes()` 访问方法
- [x] 定义 `IdentityKey` 结构体（32-byte, 实现 Zeroize）
- [x] 定义 `RecoveryKey` 结构体（32-byte, 实现 Zeroize）
- [x] 定义 `DeviceKey` 结构体（仅持有 key_id）
- [x] 定义 `DataEncryptionKey` 结构体（32-byte, 实现 Zeroize）
- [x] 定义 `VaultKey` 结构体（32-byte, 实现 Zeroize）

### 2.2 密钥层级测试
- [x] 测试 MRS → IK 派生
- [x] 测试 MRS → RK 派生
- [x] 测试上下文隔离（不同上下文产生不同密钥）
- [x] 测试所有密钥类型的 Zeroize 功能
- [x] 测试无效助记词被拒绝（BIP-39 校验和验证）
- [x] 测试有效助记词确定性派生
- [x] **测试 BIP-39 标准测试向量**（使用 BIP-39 规范中的已知助记词和种子）
- [x] **测试 BIP-39 校验和验证**（无效校验和应被拒绝，有效校验和应通过）

---

## 阶段 3：纪元管理实现

### 3.1 实现 `epoch.rs`
- [x] 定义 `CryptoAlgorithm` 枚举
  - [x] 实现 `V1` 变体
  - [x] 实现 `version()` 方法
  - [x] 实现 `is_supported()` 方法
- [x] 定义 `CryptoEpoch` 结构体
  - [x] 实现 `new()` 构造函数
  - [x] 实现 `initial()` 工厂方法
  - [x] 实现 `next()` 方法（纪元升级）
  - [x] 实现 `as_string()` 格式化方法

### 3.2 纪元测试
- [x] 测试初始纪元创建
- [x] 测试纪元单调性（next() 必须递增）
- [x] 测试算法版本号
- [x] 测试纪元序列化
- [x] 测试纪元回滚检测（InvariantViolation 错误）

---

## 阶段 4：设备与 Header 实现

### 4.1 实现 `device.rs`
- [x] 定义 `DeviceId` 结构体（16-byte UUID）
  - [x] 实现 `from_bytes()` 方法
  - [x] 实现 `generate()` 随机生成方法
  - [x] 实现 `is_shadow_anchor()` 方法
  - [x] 实现 `shadow_anchor()` 工厂方法
- [x] 定义 `DeviceStatus` 枚举
  - [x] `Active` 变体
  - [x] `Revoked` 变体
  - [x] `Degraded` 变体
- [x] 定义 `DeviceHeader` 结构体
  - [x] 实现 `new()` 构造函数
  - [x] 实现 `shadow_anchor()` 工厂方法
  - [x] 实现 `revoke()` 方法
  - [x] 实现 `belongs_to_epoch()` 方法

### 4.2 设备测试
- [x] 测试影子冷锚识别
- [x] 测试设备 ID 生成唯一性
- [x] 测试 Header 创建
- [x] 测试设备撤销
- [x] 测试纪元关联检查

---

## 阶段 5：Vault Blob 实现

### 5.1 实现 `vault.rs`
- [x] 定义 `VaultBlob` 结构体
  - [x] 实现 `new()` 构造函数
  - [x] 实现 `serialize()` 方法（使用 bincode）
  - [x] 实现 `deserialize()` 方法
  - [x] 实现 `validate()` 方法
  - [x] 实现 `size()` 方法
- [x] 定义 `VaultHeader` 结构体
  - [x] 定义 `MAGIC` 常量（`*b"AETERNM"`）
  - [x] 实现 `new()` 方法（从 Blob 创建）
  - [x] 实现 `to_bytes()` 方法（固定 32 字节）
  - [x] 实现 `from_bytes()` 方法（解析头部）

### 5.2 Vault 测试
- [x] 测试头部魔数识别
- [x] 测试头部序列化往返
- [x] 测试 Blob 验证
- [x] 测试 Blob 序列化
- [x] 测试 Blob 反序列化

---

## 阶段 6：模块集成

### 6.1 模块导出
- [x] 在 `models/mod.rs` 中添加所有子模块
- [x] 重新导出常用类型到 crate root

### 6.2 集成测试
- [x] 测试跨模块类型使用
- [x] 测试模块可见性
- [x] 测试文档完整性

---

## 阶段 7：文档与发布

### 7.1 代码文档
- [x] 确保所有公共 API 有文档注释
- [x] 添加使用示例到关键类型

### 7.2 更新变更状态
- [x] 所有任务完成后，将此文件的 `- [ ]` 改为 `- [x]`
- [x] 标记提案状态为 ✅ 已完成 (Completed)

---

## 检查清单

在完成所有任务前：

- [x] 运行 `cargo test --all-targets` 确保所有测试通过
- [x] 运行 `cargo clippy --all-targets` 检查代码质量
- [x] 运行 `./scripts/format-check.sh` 检查代码格式
- [x] 确保所有秘密类型实现 `Zeroize`
- [x] 确保测试覆盖率符合要求

---

## 总计

- **总任务数**: 63
- **已完成**: 63 (阶段 1: 7, 阶段 2: 15, 阶段 3: 8, 阶段 4: 11, 阶段 5: 16, 阶段 6: 4, 阶段 7: 2)
- **待完成**: 0
- **进度**: 100% ✅
