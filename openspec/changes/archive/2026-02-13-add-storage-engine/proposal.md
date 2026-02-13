# Proposal: 添加存储引擎模块 (add-storage-engine)

## 元数据

- **ID**: `add-storage-engine`
- **状态**: ✅ 已完成
- **创建日期**: 2026-02-13
- **完成日期**: 2026-02-14
- **作者**: Claude (Aeternum AI Assistant)
- **影响范围**: `core/src/storage/` (新增模块)

---

## 概述

为 Aeternum Rust Core 添加 `storage/` 模块，实现影子写入和崩溃一致性引擎，确保持久化层满足"无中间态"原则和四大数学不变量。

## 背景

当前 Aeternum 的密码学原语层（`crypto/`）和数据模型层（`models/`）已经完成，但缺少持久化层的实现。根据 `Persistence-Crash-Consistency.md` 规范，存储引擎必须：

1. 遵循"无中间态"原则 - 任何时刻 `(Header, VaultBlob)` 对必须是全纪元一致的
2. 实现原子纪元升级协议 (AUP) - 影子写入 + fsync + 原子 rename
3. 提供崩溃恢复与自愈逻辑 - 启动时检查并修复不一致状态
4. 强制执行四大数学不变量 - 防止纪元回滚、Header 不完备等违规行为

### 问题陈述

1. **无持久化能力**：当前无法将 VaultBlob 持久化到磁盘
2. **无崩溃恢复**：系统崩溃后无法检测和修复不一致状态
3. **无不变量保护**：无法在运行时验证数学不变量
4. **无完整性审计**：无法验证存储数据的完整性和真实性

### 参考文档

- `docs/protocols/Persistence-Crash-Consistency.md` - 持久化与崩溃一致性规范
- `docs/math/Formal-Invariants.md` - 四大数学不变量定义
- `docs/bridge/UniFFI-Bridge-Contract.md` - 桥接契约和二阶段提交

---

## 建议的变更

### 1. 新增模块结构

```
core/src/storage/
├── mod.rs              # 模块组织与公共导出
├── shadow.rs          # 影子写入机制（临时文件、fsync、原子 rename）
├── recovery.rs        # 崩溃恢复逻辑（一致性检查、自动修复）
├── invariant.rs       # 不变量验证（四大数学不变量强制执行）
├── integrity.rs       # 完整性审计（MAC 校验）
└── error.rs           # 存储错误类型（StorageError、InvariantViolation）
```

### 2. 影子写入机制

**核心 API**：
- `ShadowWriter::new()` - 创建影子写入器
- `ShadowWriter::begin_shadow_write()` - 创建临时文件
- `ShadowFile::write_and_sync()` - 写入并强制刷盘（fsync）
- `ShadowWriter::commit_shadow_write()` - POSIX 原子重命名

**安全保证**：
- 使用 `tempfile` crate 创建安全临时文件
- 所有写入操作后调用 `File::sync_all()` 强制物理落盘
- `std::fs::rename()` 在 POSIX 系统上是原子操作

### 3. 崩溃恢复逻辑

**核心 API**：
- `CrashRecovery::check_consistency()` - 启动时一致性检查
- `CrashRecovery::heal_blob_ahead()` - 自动修复 Blob 超前状态
- `CrashRecovery::handle_metadata_ahead()` - 处理非法的元数据超前状态

**一致性状态机**：
- **状态 A (Consistent)**: `metadata_epoch == blob_epoch` → 正常启动
- **状态 B (BlobAhead)**: `blob_epoch > metadata_epoch` → 自动修复
- **状态 C (MetadataAhead)**: `blob_epoch < metadata_epoch` → 触发熔断

### 4. 不变量验证

**核心 API**：
- `InvariantValidator::check_epoch_monotonicity()` - 验证纪元单调递增（Invariant #1）
- `InvariantValidator::check_header_completeness()` - 验证 Header 完备性（Invariant #2）
- `InvariantValidator::check_causal_barrier()` - 验证因果熵障（Invariant #3）
- `InvariantValidator::check_veto_supremacy()` - 验证否决权优先（Invariant #4）

**违规处理**：
- 触发 `FatalError::InvariantViolationTriggered`
- 立即停止所有 DEK 解密操作
- 清除内存中的所有明文密钥
- 标记同步状态为"异常分叉"
- 强制弹出高优先级风险警告

### 5. 完整性审计

**核心 API**：
- `IntegrityAudit::verify_vault_integrity()` - 验证 Vault 完整性（AEAD 标签 + MAC）
- `IntegrityAudit::compute_vault_mac()` - 计算整个 Vault 的 BLAKE3 哈希

### 6. 原子纪元升级协议 (AUP) 实现

**完整流程**：
1. **预备** - 在内存中解封当前 VK，派生新纪元的 DEK
2. **影子写入** - 创建 `vault.tmp`，写入 Header 和 Blob，强制 fsync
3. **原子替换** - POSIX rename + 更新 SQLCipher 元数据

### 7. 依赖项更新

```toml
[dependencies]
# 现有依赖...
tempfile = "3.10"           # 安全临时文件创建
parking_lot = "0.12"        # 高效 RwLock（可选，用于并发控制）

[dev-dependencies]
tempfile = "3.10"           # 测试临时目录
proptest = "1.4"            # 属性测试
```

---

## Impact

### Affected specs
- **新增**: `storage` - 全新 capability，定义持久化与崩溃一致性需求

### Affected code
- **新增模块**:
  - `core/src/storage/mod.rs` - 模块组织与公共导出
  - `core/src/storage/shadow.rs` - 影子写入机制
  - `core/src/storage/recovery.rs` - 崩溃恢复逻辑
  - `core/src/storage/invariant.rs` - 不变量验证
  - `core/src/storage/integrity.rs` - 完整性审计
  - `core/src/storage/error.rs` - 存储错误类型
- **修改文件**:
  - `core/src/lib.rs` - 添加 `pub mod storage;`
  - `core/Cargo.toml` - 添加依赖项

### Dependencies
**新增运行时依赖**:
- `tempfile = "3.10"` - 安全临时文件创建
- `parking_lot = "0.12"` - 高效 RwLock（可选，用于并发控制）

**新增开发依赖**:
- `proptest = "1.4"` - 属性测试框架

### Breaking changes
无破坏性变更。这是全新的独立模块，不影响现有 API 或数据格式。

### Migration
无需迁移步骤。模块首次实现时无现有数据需要迁移。

### Performance impact
- **启动时**: 增加 ~50ms 一致性检查（一次性）
- **纪元升级**: 增加 ~100ms 影子写入开销（包含 fsync）
- **运行时**: 无影响（不变量验证为纯内存操作）

---

## 设计原则

1. **零信任文件系统** - 只信任经过 AEAD 校验的数据完整性
2. **原子性优先** - 所有更新操作使用影子写入 + 原子 rename
3. **自愈能力** - 启动时自动检测并修复不一致状态
4. **熔断机制** - 检测到严重违规时立即触发安全停机

---

## 非目标

以下内容**不在**本提案范围内：

- SQLCipher 集成（Android 层，由 Kotlin 层实现）
- UniFFI 桥接接口（留待后续 `bridge/` 提案）
- PQRR 协议逻辑（留待后续 `protocol/` 提案）
- 网络同步功能（留待后续 `sync/` 提案）

---

## 风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| fsync 失败未处理 | 数据丢失 | 所有 fsync 调用后检查返回值，失败时返回错误 |
| 临时文件残留 | 磁盘空间泄漏 | 启动时清理残留 `.tmp` 文件，ShadowFile 实现 Drop 清理 |
| 原子 rename 不原子 | 数据损坏 | 使用标准 POSIX `rename()`，在 Android (Linux) 上保证原子性 |
| 不变量验证遗漏 | 安全漏洞 | 使用属性测试（proptest）覆盖所有边界情况 |

---

## 替代方案

### 方案 A：使用 sled 纯 Rust 数据库

**拒绝原因**：引入额外的依赖和复杂性，不符合架构文档中"SQLCipher (Android) + 独立加密文件"的设计。

### 方案 B：在 Kotlin 层实现所有存储逻辑

**拒绝原因**：违反"Rust 根信任域"原则，密钥材料不能跨越 FFI 边界。

---

## 时间线

- **预计实现时间**: 2-3 天
- **测试时间**: 1 天
- **总计**: 约 3 天

---

## 审查清单

在批准前请确认：

- [ ] 影子写入机制符合 `Persistence-Crash-Consistency.md` 规范
- [ ] 崩溃恢复逻辑覆盖所有三种一致性状态
- [ ] 四大数学不变量强制执行逻辑完整
- [ ] 所有敏感操作正确处理错误和返回值
- [ ] 测试覆盖率达到 ≥ 95%

---

## 参考资料

- [持久化与崩溃一致性规范](../../docs/protocols/Persistence-Crash-Consistency.md)
- [形式化数学不变量](../../docs/math/Formal-Invariants.md)
- [UniFFI 桥接契约](../../docs/bridge/UniFFI-Bridge-Contract.md)
- [架构白皮书 v5.0](../../docs/arch/Aeternum-architecture.md)
