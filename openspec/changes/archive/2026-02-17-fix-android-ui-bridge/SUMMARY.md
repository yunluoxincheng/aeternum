# fix-android-ui-bridge 提案摘要

**状态**: 🟡 待审批 (Pending Approval)
**优先级**: P0 (阻塞)
**创建日期**: 2026-02-16
**提案类型**: Bug Fix

---

## 📋 提案概述

此提案修复 `add-android-ui-layer` 实现中发现的 **80+ 编译错误**，使 Android UI 层能够成功构建和运行。

### 问题来源

`add-android-ui-layer` 提案已实现 **95% 的功能**（53 个 UI 文件），但在编译时发现：

1. **UniFFI 桥接不完整** - UI 层调用的方法未在 Rust Core 中实现
2. **Compose API 版本兼容性** - 部分已废弃的 API 需要替换
3. **Material3 组件参数** - 新增的组件参数未提供
4. **无障碍 API 更新** - 部分已废弃的无障碍 API 需要替换

---

## 📁 提案文件结构

```
openspec/changes/fix-android-ui-bridge/
├── proposal.md          # 提案主文档
├── design.md            # 设计文档
├── tasks.md             # 详细任务清单 (104 项任务)
├── README.md            # 提案 README
├── SUMMARY.md           # 本文件
└── specs/
    └── fix-bridge/
        └── spec.md      # 技术规范
```

---

## 🎯 修复目标

### 主要目标

| 类别 | 错误数 | 优先级 |
|------|--------|--------|
| UniFFI 桥接接口 | ~10 | P0 |
| Compose API 兼容性 | ~50 | P0 |
| Material3 组件参数 | ~15 | P0 |
| 无障碍 API | ~10 | P0 |

### 验收标准

- ✅ **零编译错误**: `./gradlew build` 成功
- ✅ **所有测试通过**: 单元测试 + UI 测试
- ✅ **安全验证通过**: 静态分析 + 运行时检查
- ✅ **功能正常**: Vault 解锁、设备管理、动画播放

---

## 🔧 技术方案

### 1. UniFFI 接口扩展

在 `core/uniffi/aeternum.udl` 中添加：

```idl
interface VaultSession {
    sequence<string> list_record_ids();
    [Throws=PqrrError] string decrypt_field(string, string);
    [Throws=PqrrError] void store_entry(string, string, string);
    [Throws=PqrrError] string retrieve_entry(string, string);
    void lock();
    boolean is_valid();
}

interface AeternumEngine {
    [Throws=PqrrError] constructor(string vault_path);
    [Throws=PqrrError] void initializeVault(sequence<u8>);
    [Throws=PqrrError] VaultSession unlock(sequence<u8>);
    // ... 其他方法
}
```

### 2. Rust 后端实现

创建 `core/src/bridge/` 模块：

```rust
mod engine;     // AeternumEngine 实现
mod session;    // VaultSession 实现
mod device;     // DeviceInfo 工具
```

### 3. Compose API 替换

| 废弃 API | 替代 API |
|----------|----------|
| `animateValue` | `animateFloatAsState` |
| `VectorConverter` | 手动类型转换 |
| `StrokeCap` | `DrawScope.Stroke` |
| `ACCESSIBILITY_ANNOUNCEMENT` | `SemanticsProperties.announce` |

---

## 📊 任务统计

| 阶段 | 任务数 | 预计时间 |
|------|--------|----------|
| 1. UniFFI 桥接修复 | 22 | 1-2 天 |
| 2. 数据层适配 | 9 | 0.5 天 |
| 3. Compose API 修复 | 32 | 1-2 天 |
| 4. 无障碍 API 更新 | 10 | 0.5 天 |
| 5. 安全边界验证 | 10 | 0.5 天 |
| 6. 测试与验证 | 17 | 1 天 |
| 7. 文档与归档 | 4 | 0.5 天 |
| **总计** | **104** | **3-5 天** |

---

## 🔒 安全约束

### 必须遵守

- ❌ 禁止通过 UDL 暴露明文密钥
- ❌ 禁止手动修改生成的 Kotlin 代码
- ❌ 禁止在 Kotlin 层实现密码学逻辑
- ✅ Kotlin 层仅持有 Rust 实例句柄
- ✅ 修改 UDL 后必须重新生成桥接代码

---

## 📈 预期结果

### 修复前

```
> Task :app:compileReleaseKotlin FAILED
错误数量: 80+
测试状态: 无法运行
```

### 修复后

```
> Task :app:compileReleaseKotlin SUCCESS
> Task :app:test SUCCESS
> Task :app:connectedAndroidTest SUCCESS
测试覆盖率: ≥ 目标值
```

---

## 🚀 开始修复

### 前置条件

1. ✅ 提案已审批
2. ✅ 已阅读 `UniFFI-Bridge-Contract.md`
3. ✅ 已阅读 `Aeternum-architecture.md` §5, §6
4. ✅ 已调用 `aeternum-checkpoint`

### 第一步

```bash
# 1. 扩展 UDL 接口
vim core/uniffi/aeternum.udl

# 2. 创建 Rust 桥接模块
mkdir -p core/src/bridge
vim core/src/bridge/mod.rs

# 3. 生成桥接代码
./scripts/generate-bridge.sh

# 4. 验证编译
cd android && ./gradlew build
```

---

## 📚 相关文档

- **主提案**: `add-android-ui-layer/proposal.md`
- **审查报告**: `reports/openspec-completion-add-android-ui-layer.md`
- **桥接契约**: `docs/bridge/UniFFI-Bridge-Contract.md`
- **架构白皮书**: `docs/arch/Aeternum-architecture.md`

---

## ❓ 常见问题

### Q1: 为什么不降级 Compose 版本？

**A**: 降级会失去安全更新和新特性。使用替代 API 可以保持最新的依赖版本。

### Q2: UniFFI 生成的代码可以手动修改吗？

**A**: ❌ 不可以。每次运行 `generate-bridge.sh` 会覆盖手动修改。应该修改 UDL 或 Rust 后端。

### Q3: 修复需要多长时间？

**A**: 预计 3-5 天，取决于：
- UniFFI 接口的复杂度
- Compose API 替换的工作量
- 测试和调试时间

### Q4: 会破坏现有功能吗？

**A**: ❌ 不会。这是纯修复提案，不改变任何功能逻辑。

---

**审批状态**: ⏳ 等待审批
**预计开始日期**: 审批后立即开始
**预计完成日期**: 开始后 3-5 天

---

*此提案是 `add-android-ui-layer` 的依赖修复，必须完成才能验证原提案的完成情况。*
