# Storage Engine 设计文档 (add-storage-engine)

## 概述

本文档详细描述 `storage/` 模块的技术设计，包括架构决策、API 设计、数据流和错误处理策略。

---

## 架构概览

```
┌─────────────────────────────────────────────────────────────┐
│           Android SQLCipher (元数据层)                     │
│      Epoch ID, DeviceID, KV 索引                          │
│      (由 Android 层通过 UniFFI 接口访问)                  │
├─────────────────────────────────────────────────────────────┤
│              Rust Storage Engine                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │              Shadow Writer                          │  │
│  │  - begin_shadow_write()  → 创建 .tmp 文件          │  │
│  │  - write_and_sync()       → fsync 强制落盘         │  │
│  │  - commit_shadow_write()   → 原子 rename            │  │
│  └─────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────┐  │
│  │           Crash Recovery Logic                      │  │
│  │  - check_consistency()    → 比较纪元              │  │
│  │  - heal_blob_ahead()      → 自动修复               │  │
│  │  - handle_metadata_ahead() → 触发熔断             │  │
│  └─────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────┐  │
│  │         Invariant Enforcement                       │  │
│  │  - Epoch Monotonicity (Invariant #1)              │  │
│  │  - Header Completeness (Invariant #2)              │  │
│  │  - Causal Barrier (Invariant #3)                   │  │
│  │  - Veto Supremacy (Invariant #4)                   │  │
│  └─────────────────────────────────────────────────────┘  │
│  ┌─────────────────────────────────────────────────────┐  │
│  │           Integrity Audit                           │  │
│  │  - verify_vault_integrity() → AEAD + MAC 验证      │  │
│  │  - compute_vault_mac()       → BLAKE3 哈希         │  │
│  └─────────────────────────────────────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│         独立加密文件 (.aet) - 数据层                       │
│      VaultBlob (Encrypted VK + Data)                       │
└─────────────────────────────────────────────────────────────┘
```

---

## 模块设计

### 1. Shadow Writer (shadow.rs)

**职责**: 实现影子写入机制，确保文件更新操作的原子性。

**核心结构**：

```rust
use std::path::{Path, PathBuf};
use std::fs::File;
use std::io::{self, Write};

/// 影子写入器
pub struct ShadowWriter {
    base_path: PathBuf,
    temp_suffix: String,
}

/// 临时文件句柄（确保 Drop 时自动清理）
pub struct ShadowFile {
    file: File,
    path: PathBuf,
    should_cleanup: bool,
}
```

**关键设计决策**：

1. **临时文件命名**: `{base_path}{temp_suffix}` (默认 `.tmp`)
2. **原子性保证**: 使用 POSIX `rename()` 系统调用（在 Linux/Android 上是原子的）
3. **物理落盘**: 每次写入后调用 `File::sync_all()` 强制 fsync
4. **自动清理**: `ShadowFile` 实现 `Drop` trait，未提交的临时文件自动删除

### 2. Crash Recovery (recovery.rs)

**职责**: 启动时检查存储一致性，自动修复可恢复的状态。

**核心结构**：

```rust
/// 一致性检查结果
pub enum ConsistencyState {
    /// 完全一致 - 正常启动
    Consistent,
    /// Blob 超前于元数据 - 可自动修复
    BlobAhead { blob_epoch: u32, metadata_epoch: u32 },
    /// 元数据超前于 Blob - 非法状态（可能回滚攻击）
    MetadataAhead { blob_epoch: u32, metadata_epoch: u32 },
}

/// 崩溃恢复器
pub struct CrashRecovery {
    metadata_db: MetadataSource,
    vault_storage: VaultStorage,
}
```

**状态机转换**：

```
                启动
                 │
                 ▼
        ┌─────────────────┐
        │ 读取 epoch 信息  │
        └────────┬────────┘
                 │
                 ▼
    ┌──────────────────────────────┐
    │ metadata_epoch == blob_epoch? │
    └──────┬─────────┬────────────┘
           │ Yes     │ No
           │         │
           ▼         ▼
    ┌──────────┐  ┌────────────────────┐
    │ Consistent│  │ 比较 epoch 大小    │
    └────┬─────┘  └────┬──────┬──────┘
         │              │      │
         │              │      │ blob < meta
         │              │      │ (非法！)
         │              │      ▼
         │              │   ┌─────────────────┐
         │              │   │ MetadataAhead   │
         │              │   │ 触发熔断！      │
         │              │   └─────────────────┘
         │              │
         │              │ blob > meta
         │              │
         │              ▼
         │       ┌──────────────┐
         │       │ BlobAhead    │
         │       │ 自动修复     │
         │       │ DB 向 Blob   │
         │       │ 对齐         │
         │       └──────────────┘
         │
         ▼
    ┌──────────┐
    │ 正常启动  │
    └──────────┘
```

### 3. Invariant Validator (invariant.rs)

**职责**: 强制执行四大数学不变量，防止违规操作。

**核心结构**：

```rust
/// 不变量检查器（无状态，纯函数）
pub struct InvariantValidator;

/// 不变量违规类型
#[derive(Debug, Error, Clone, PartialEq)]
pub enum InvariantViolation {
    #[error("Epoch #1: Monotonicity violated (current={current}, new={new})")]
    EpochMonotonicity { current: u32, new: u32 },

    #[error("Epoch #2: Header incomplete (device={device})")]
    HeaderIncomplete { device: DeviceId },

    #[error("Epoch #3: Causal barrier violated (role={role:?}, op={op:?})")]
    CausalBarrier { role: Role, op: Operation },

    #[error("Epoch #4: Veto supremacy violated (vetoes={count})")]
    VetoSupremacy { count: usize },
}
```

**不变量验证矩阵**：

| 不变量 | 检查点 | 违规后果 |
|--------|--------|----------|
| #1 纪元单调性 | 每次 epoch 升级 | 拒绝操作，返回错误 |
| #2 Header 完备性 | 每次设备解密尝试 | 拒绝操作，返回错误 |
| #3 因果熵障 | 每次管理操作执行 | 拒绝操作，返回错误 |
| #4 否决权优先 | 每次恢复请求提交 | 立即终止恢复流程 |

### 4. Integrity Audit (integrity.rs)

**职责**: 验证存储数据的完整性和真实性。

**核心结构**：

```rust
use crate::crypto::hash::blake3::hash;

/// 完整性审计器
pub struct IntegrityAudit {
    vault_blob: Vec<u8>,
}

impl IntegrityAudit {
    /// 验证 Vault 完整性（AEAD 标签 + BLAKE3 MAC）
    pub fn verify_vault_integrity(&self) -> Result<bool, StorageError> {
        // 1. 验证 AEAD 认证标签
        // 2. 验证 BLAKE3 MAC
        // 3. 返回验证结果
    }

    /// 计算整个 Vault 的 BLAKE3 哈希
    pub fn compute_vault_mac(&self) -> [u8; 32] {
        hash(&self.vault_blob)
    }
}
```

---

## 原子纪元升级协议 (AUP) 详细设计

### 完整流程图

```
┌─────────────────────────────────────────────────────────────┐
│ 阶段 1: 预备 (Preparation)                                │
├─────────────────────────────────────────────────────────────┤
│ 1. 在内存中解封当前 VK_n                                   │
│ 2. 派生新纪元的 DEK_n+1                                    │
│ 3. 使用新 DEK 重新加密 VK                                   │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 阶段 2: 影子写入 (Shadow Writing)                          │
├─────────────────────────────────────────────────────────────┤
│ 1. 创建临时文件 vault.tmp                                   │
│ 2. 写入 Header_n+1 与重新封装的 VK                          │
│ 3. 强制刷盘: File::sync_all()                              │
└────────────────────┬────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────────┐
│ 阶段 3: 原子替换 (Atomic Commit)                           │
├─────────────────────────────────────────────────────────────┤
│ 3.1 POSIX 原子重命名: rename("vault.tmp", "vault.db")      │
│      (要么是旧文件，要么是新文件，不存在中间态)                │
│ 3.2 更新 SQLCipher 元数据: Local_Epoch = n+1                │
│ 3.3 提交事务                                               │
└─────────────────────────────────────────────────────────────┘
```

### 错误处理矩阵

| 故障时点 | 物理后果 | 恢复策略 | 用户影响 |
|----------|----------|----------|----------|
| Phase 1 | 内存中密钥派生失败 | 停留在当前纪元 | 无 |
| Phase 2 写入 .tmp 时 | vault.db 完好，.tmp 损坏 | 启动时删除 .tmp | 无 |
| Phase 2 fsync 失败 | 数据未物理落盘 | 返回错误，重试 | 需重试 |
| Phase 3.1 rename 瞬间 | 文件系统保证原子性 | 系统处于旧纪元或新纪元 | 无 |
| Phase 3.2 SQL 更新失败 | Blob 已升级，DB 记录旧纪元 | 触发自愈逻辑（BlobAhead） | 无 |
| 磁盘空间不足 | 写入失败 | 返回 IO 错误 | 需清理空间 |

---

## 错误处理策略

### 错误类型层次

```
Error
├── StorageError (可恢复)
│   ├── ShadowWriteFailed
│   ├── AtomicRenameFailed
│   ├── FsyncFailed
│   ├── ConsistencyCheckFailed
│   └── InvariantViolation
└── FatalError (不可恢复)
    ├── StorageInconsistency
    └── InvariantViolationTriggered
```

### 熔断机制

当检测到 `FatalError` 时：

1. **内核锁定**: 立即停止所有 DEK 解密操作
2. **内存擦除**: 清除内存中的所有明文密钥（调用 `zeroize()`）
3. **状态隔离**: 标记同步状态为 "Fork Detected"
4. **用户警示**: 强制弹出高优先级风险警告
5. **物理锚点**: 要求用户通过助记词重新建立根信任

```rust
impl FatalError {
    pub fn trigger_meltdown(&self) -> ! {
        // 1. 内核锁定
        lock_all_dek_operations();

        // 2. 内存擦除
        zeroize_all_sensitive_data();

        // 3. 状态隔离
        set_sync_status(SyncStatus::ForkDetected);

        // 4. 用户警示
        show_critical_alert(format!("AETERNUM MELTDOWN: {}", self));

        // 5. 终止进程（或进入安全模式等待用户输入）
        panic!("AETERNUM MELTDOWN: {}", self);
    }
}
```

---

## 测试策略

### 单元测试覆盖率要求

| 模块 | 目标覆盖率 | 关键测试场景 |
|------|-----------|-------------|
| `shadow.rs` | 100% | 影子写入、fsync、原子 rename、临时文件清理 |
| `recovery.rs` | 100% | 三种一致性状态、自动修复、熔断触发 |
| `invariant.rs` | 100% | 四大不变量验证、边界条件 |
| `integrity.rs` | 100% | MAC 验证、哈希计算 |
| **总计** | **≥ 95%** | 全覆盖 |

### 属性测试使用 proptest

```rust
use proptest::prelude::*;

proptest! {
    #[test]
    fn test_epoch_always_monotonic(epoch_current in 1u32..1000) {
        let epoch_new = epoch_current + 1;
        assert!(InvariantValidator::check_epoch_monotonicity(
            epoch_current,
            epoch_new
        ).is_ok());
    }

    #[test]
    fn test_shadow_write_preserves_data(data in any::<Vec<u8>>()) {
        // 验证影子写入后数据完整无损
    }
}
```

### 集成测试场景

1. **完整 AUP 流程**: 预备 → 影子写入 → 原子提交
2. **崩溃恢复**: 在 AUP 各阶段模拟崩溃
3. **不变量违规**: 故意触发不变量违规，验证熔断机制

---

## 依赖关系

### 新增外部依赖

```toml
[dependencies]
tempfile = "3.10"           # 安全临时文件创建
parking_lot = "0.12"        # 高效 RwLock（可选）

[dev-dependencies]
tempfile = "3.10"           # 测试临时目录
proptest = "1.4"            # 属性测试
```

### 内部模块依赖

```
storage/
├── → crypto/   (使用 hash::blake3, aead)
├── → models/   (使用 DeviceId, DeviceHeader, VaultBlob)
└── → crypto/error (集成到 StorageError)
```

---

## 性能考虑

### 关键性能指标

| 操作 | 目标延迟 | 说明 |
|------|----------|------|
| 影子写入 | < 100ms (for 1MB) | 包含 fsync 时间 |
| 一致性检查 | < 50ms | 启动时一次性检查 |
| 不变量验证 | < 10ms | 内存操作，无 I/O |
| 完整性审计 | < 200ms (for 1MB) | BLAKE3 哈希计算 |

### 优化策略

1. **延迟 fsync**: 仅在必要时调用 `File::sync_all()`
2. **缓存纪元信息**: 避免重复读取元数据
3. **并行哈希**: 使用 BLAKE3 的多线程特性

---

## 安全考虑

### 攻击向量与缓解

| 攻击向量 | 威胁 | 缓解措施 |
|----------|------|----------|
| 回滚攻击 | 恶意降级纪元 | Invariant #1 强制检查 |
| 伪造 Header | 注入虚假设备 | Invariant #2 完备性检查 |
| 权限提升 | RECOVERY 角色执行管理操作 | Invariant #3 因果熵障 |
| 否决权绕过 | 48h 窗口外强制恢复 | Invariant #4 否决权优先 |
| 时间攻击 | 通过 fsync 时间推断密钥 | 恒定时间 fsync（未来工作） |

### 密钥材料保护

1. **零化策略**: 所有敏感缓冲区使用 `Zeroizing<Vec<u8>>`
2. **生命周期限制**: VK 仅在 `Rekeying` 闭包内可见
3. **硬件密钥**: DK 永不离开 Android KeyStore/StrongBox

---

## 未来扩展

### 可能的后续工作

1. **压缩支持**: 在持久化前压缩 VaultBlob
2. **增量更新**: 仅更新修改的数据块
3. **加密流支持**: 支持流式加密大文件
4. **多版本并存**: 同时保留多个纪元的数据（用于回滚）

---

## 参考文档

- [持久化与崩溃一致性规范](../../docs/protocols/Persistence-Crash-Consistency.md)
- [形式化数学不变量](../../docs/math/Formal-Invariants.md)
- [UniFFI 桥接契约](../../docs/bridge/UniFFI-Bridge-Contract.md)
