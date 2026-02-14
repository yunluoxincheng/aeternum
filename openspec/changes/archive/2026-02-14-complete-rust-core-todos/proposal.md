# Proposal: Complete Rust Core TODOs

**Change ID**: `complete-rust-core-todos`
**Status**: Proposed
**Created**: 2026-02-14
**Author**: Aeternum Protocol Team
**Type**: Feature
**Priority**: P1 (High)

---

## Executive Summary

完成 Rust Core 中遗留的 TODO 项，确保核心功能完整性：
- **Header 序列化** - 让 protocol 模块可以传递完整的设备信息
- **AUP 密钥派生** - 完善原子纪元升级协议中的密钥派生逻辑
- **VetoExpired 变体** - 为否决超时提供更明确的错误信息

**核心价值**:
- **完整性补齐** - 确保 crypto、storage、sync 模块间的集成点完整
- **错误处理优化** - 提供更精确的错误类型，便于调试
- **为 Android 层准备** - 确保通过 UniFFI 桥接可以完整调用核心功能

---

## Why

Rust Core 各模块已完成基础实现，但存在集成点 TODO：

```
core/src/
├── protocol/
│   └── pqrr.rs:627          → TODO: Serialize header
├── storage/
│   └── aug.rs:128,342         → TODO: VK 派生, SQLCipher 集成
└── sync/
    └── wire.rs:280,283        → TODO: VetoExpired, StrongBox 签名
```

### 影响

1. **无法传递完整 Header 信息** - UniFFI 接口无法返回序列化的设备数据
2. **AUP 协议不完整** - 纪元升级时密钥派生是占位实现
3. **错误信息不明确** - 否决超时返回通用错误

### 为什么现在完成？

1. **依赖链完整** - crypto、storage、sync 模块已就绪
2. **为 Android 层准备** - UniFFI 桥接需要完整的数据结构
3. **测试覆盖要求** - 100% 覆盖率要求必须实现这些功能

---

## What Changes

修改涉及的模块和文件：

```
┌─────────────────────────────────────────────────────┐
│  protocol/   │  storage/    │  sync/      │
│  pqrr.rs     │  aug.rs      │  wire.rs     │
│  - 序列化    │  - 密钥派生  │  - 错误变体  │
│    Header    │    + 加密    │              │
└─────────────────────────────────────────────────────┘
         ↓                  │                    ↓
         └──────────────────┼────────────────────┘
                            ↓
                   ┌─────────────┐
                   │  models/    │ ← 使用现有类型
                   │  vault.rs   │
                   │  device.rs  │
                   │  crypto/    │
                   └─────────────┘
```

---

## Proposed Solution

### 架构概览

```
┌─────────────────────────────────────────────────────┐
│         修改涉及的模块和文件                      │
├─────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
│  │ protocol/   │  │ storage/    │  │  sync/      │  │
│  │ pqrr.rs     │  │ aug.rs      │  │  wire.rs     │  │
│  │             │  │             │  │             │  │
│  │ - 序列化    │  │ - 密钥派生  │  │ - 错误变体  │  │
│  │   Header    │  │   + 加密    │  │             │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  │
│         ↓                  │                    │         │
│         └──────────────────┼────────────────────┘         │
│                            ↓                              │
│                   ┌─────────────┐                       │
│                   │  models/    │ ← 使用现有类型         │
│                   │  vault.rs   │                       │
│                   │  device.rs  │                       │
│                   │  crypto/    │                       │
│                   └─────────────┘                       │
└─────────────────────────────────────────────────────┘
```

---

## Impact Analysis

### 对现有模块的影响

| 模块 | 影响 | 说明 |
|------|------|------|
| `models/` | 使用 | 复用现有类型定义 |
| `crypto/` | 使用 | 调用 AEAD、KDF 进行密钥操作 |
| `storage/` | 修改 | 完善 AUP 密钥派生逻辑 |
| `protocol/` | 修改 | 添加 Header 序列化 |
| `sync/` | 修改 | 添加 VetoExpired 错误变体 |

### 对测试策略的影响

| 测试类型 | 新增/修改 |
|---------|-----------|
| 单元测试 | 添加序列化/密钥派生测试 |
| 集成测试 | 添加跨模块集成测试 |
| 属性测试 | 使用 proptest 验证序化性质 |

---

## Implementation Plan

### Phase 1: Header 序列化

**文件**: `core/src/protocol/pqrr.rs`

**任务**:
- [ ] 为 `DeviceHeader` 实现 `serialize()` 和 `deserialize()` 方法
- [ ] 更新 `get_device_headers()` 返回序列化的 `header_blob`
- [ ] 添加单元测试

### Phase 2: AUP 密钥派生

**文件**: `core/src/storage/aug.rs`

**任务**:
- [ ] 实现 VK 解封逻辑
- [ ] 实现新 DEK 派生
- [ ] 实现 VK 重新加密
- [ ] 实现 VaultBlob 序列化
- [ ] 添加集成测试

### Phase 3: VetoExpired 错误变体

**文件**: `core/src/sync/wire.rs`

**任务**:
- [ ] 添加 `WireError::VetoExpired` 变体
- [ ] 更新 `handle_veto()` 返回新错误
- [ ] 添加错误测试

---

## Success Metrics

| 指标 | 目标 | 验证方法 |
|------|------|----------|
| Header 序列化成功率 | 100% | 单元测试通过 |
| AUP 密钥派生正确性 | 100% | 集成测试通过 |
| 错误类型覆盖率 | 100% | 错误测试通过 |

---

## References

- [UniFFI 桥接契约](../../../docs/bridge/UniFFI-Bridge-Contract.md)
- [持久化与崩溃一致性](../../../docs/protocols/Persistence-Crash-Consistency.md)
- [形式化数学不变量](../../../docs/math/Formal-Invariants.md)
