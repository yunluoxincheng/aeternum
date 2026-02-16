<!-- MANDATORY-CHECKPOINT:START -->
# ⚠️ 强制前置检查点（最高优先级）

**这些指令对所有在此项目中工作的 AI 助手强制执行。**

在开始**任何**代码修改、功能添加或架构调整之前，你必须：

1. **识别任务类型**：
   - `crypto` - 密码学原语（KEM、KDF、哈希等）
   - `protocol` - 协议与状态机（PQRR、纪元升级、设备管理等）
   - `android` - Android 安全层与 UI
   - `bridge` - UniFFI 桥接
   - `invariant` - 不变量验证

2. **执行技能调用链**：
   ```
   Step 1: 调用 aeternum-checkpoint（读取 .claude/skills/aeternum-checkpoint/skill.md）
   Step 2: 完成文档检查和约束确认
   Step 3: 调用对应的 aeternum-[类型] skill
   ```

3. **完成检查清单**：
   - [ ] 我已识别任务类型
   - [ ] 我已调用 aeternum-checkpoint 并阅读所有必读文档
   - [ ] 我理解并记录了所有不可违反的约束
   - [ ] 我已调用对应的 aeternum-[类型] skill
   - [ ] 我知道输出文件应该放在哪个目录

4. **在继续之前**，向用户确认：
   > "✅ Checkpoint 通过。\n> 任务类型：[类型]\n> 必读文档：[列表]\n> 关键约束：[约束列表]\n> 已调用 skill：aeternum-[类型]\n> 是否继续执行？"

**技能调用示例**：
```
任务: "实现 Kyber-1024 KEM"
→ aeternum-checkpoint → aeternum-crypto

任务: "添加设备撤销功能"
→ aeternum-checkpoint → aeternum-protocol

任务: "创建生物识别界面"
→ aeternum-checkpoint → aeternum-android

任务: "修改 UDL 接口"
→ aeternum-checkpoint → aeternum-bridge

任务: "验证不变量"
→ aeternum-checkpoint → aeternum-invariant
```

**例外情况**：
- ❌ 读取文件/查看代码状态 - 不需要 skill
- ❌ 回答解释性问题 - 不需要 skill
- ✅ 任何修改/创建/重构代码 - 必须 checkpoint + 对应 skill

<!-- MANDATORY-CHECKPOINT:END -->

<!-- OPENSPEC:START -->
# OpenSpec Instructions

These instructions are for AI assistants working in this project.

Always open `@/openspec/AGENTS.md` when the request:
- Mentions planning or proposals (words like proposal, spec, change, plan)
- Introduces new capabilities, breaking changes, architecture shifts, or big performance/security work
- Sounds ambiguous and you need the authoritative spec before coding

Use `@/openspec/AGENTS.md` to learn:
- How to create and apply change proposals
- Spec format and conventions
- Project structure and guidelines

Keep this managed block so 'openspec update' can refresh the instructions.

<!-- OPENSPEC:END -->

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

**Aeternum** 是一个后量子安全的移动密钥管理系统（架构 v5.0 已冻结），采用分层架构设计：

```
┌─────────────────────────────────────────────────────┐
│           Android UI (Jetpack Compose)             │
│                 非信任域 - 不触碰密钥                  │
├─────────────────────────────────────────────────────┤
│          Android Security Control Layer            │
│     StrongBox/KeyStore | Biometric | Integrity     │
│          信任域 - 仅持硬件密钥句柄                     │
├─────────────────────────────────────────────────────┤
│              Rust Core (密码内核)                   │
│  - Kyber-1024 KEM  - XChaCha20-Poly1305 AEAD        │
│  - Argon2id KDF     - BLAKE3 Hashing               │
│          根信任域 - 所有密钥在此处理                    │
└─────────────────────────────────────────────────────┘
```

**核心特性：**
- **后量子安全**: ML-KEM (Kyber-1024) + X25519 混合加密
- **影子恢复 (Shadow Wrapping)**: Device_0 物理锚点不可区分
- **PQRR 协议**: 设备撤销与密钥轮换，前向安全保证
- **48h 否决机制**: 防止助记词被盗后的即时恢复
- **密码学纪元**: 支持算法平滑迁移

## 构建命令

### 完整构建流程

```bash
# 1. 交叉编译 Rust 核心到各 Android 平台（ARM64/ARMv7/x86_64）
./scripts/build-core.sh

# 2. 生成 UniFFI 桥接代码（Kotlin 接口 + Rust scaffolding）
./scripts/generate-bridge.sh

# 3. 构建 Android APK
cd android && ./gradlew assembleDebug
```

### Rust 核心开发

```bash
cd core
cargo test                          # 运行所有测试
cargo test --package aeternum-core   # 仅运行核心库测试
cargo clippy --all-targets           # 代码检查
cargo audit                          # 安全审计（需要 cargo-audit）
cargo bench                          # 性能基准测试（Criterion）
```

### Android 开发

```bash
cd android
./gradlew assembleDebug              # 构建 Debug APK
./gradlew assembleRelease            # 构建 Release APK
./gradlew test                       # 运行单元测试
./gradlew connectedAndroidTest       # 运行设备测试
./gradlew clean                      # 清理构建产物
```

### 代码质量

```bash
./scripts/format-check.sh            # 代码格式检查
./scripts/security-audit.sh          # 安全审计脚本
```

## 四大数学不变量（必须强制执行）

**Invariant #1 — 纪元单调性**: 所有设备的 epoch 必须严格单调递增，禁止回滚
**Invariant #2 — Header 完备性**: 每个活跃设备必须且仅能通过一个正确的 Header 获取 DEK
**Invariant #3 — 因果熵障**: 解密权限 ≠ 管理权限（RECOVERY 角色不能执行 σ_rotate）
**Invariant #4 — 否决权优先**: 48h 窗口内任何活跃设备的 Veto 信号必须立即终止恢复流程

违反不变量时必须触发熔断：内核锁定 → 状态隔离 → 用户警示

## 架构关键点

### UniFFI 桥接机制（Rust ↔ Kotlin 边界）

- **接口定义**: 使用 **proc-macro 模式**（`#[uniffi::export]` 和 `#[derive(uniffi::*)]` 宏）
- **Kotlin 生成位置**: `android/app/src/main/kotlin/aeternum/uniffi/aeternum/`（自动生成，勿手动修改）
- **生成脚本**: `./scripts/generate-bridge.sh`（proc-macro 模式，Windows 兼容）
- **核心原则**:
  - Kotlin **严禁**持有明文密钥的 `ByteArray`
  - 所有解密操作必须在 Rust 端完成，仅返回脱敏数据
  - 敏感对象必须实现 `Zeroize` 特性，确保 Drop 时内存被物理擦除
- **相关文档**: [UniFFI Proc-Macro 迁移指南](docs/bridge/UniFFI-Proc-Macro-Migration.md)

### 密码学安全原则

1. **句柄化管理**: Kotlin 仅持有 Rust 实例句柄，明文在内存中"即用即走"
2. **物理擦除**: 使用 `Zeroizing<Vec<u8>>` 确保密钥内存页在释放前被覆写
3. **二阶段提交**: 涉及文件系统更新时，Rust 生成数据流，Kotlin 执行原子重命名
4. **影子写入**: PQRR 升级时先写临时文件，fsync 后原子替换

### 密钥层级与派生路径

```
MRS (Master Root Seed) - 24位助记词
    ├── BLAKE3 派生
    ├── IK (Identity Key) - 身份证明
    ├── RK (Recovery Key) - 恢复封装
    └── DK (Device Key) - StrongBox 硬件生成，永不离开硬件
        └── DEK (Data Encryption Key) - 256-bit 包装密钥
            └── VK (Vault Key) - 实际加密用户数据的 XChaCha20 对称密钥
```

### 目录结构关键约定

```
core/src/
├── crypto/        # 密码学原语实现（KEM、AEAD、KDF）
├── storage/       # 影子写入与崩溃一致性引擎
├── sync/          # Aeternum Wire 帧封装与协议逻辑
└── models/        # 纪元与 Header 数据模型（必须实现 Zeroize）

android/app/src/main/kotlin/io/aeternum/
├── ui/            # Jetpack Compose UI 组件（非信任域）
├── security/      # StrongBox/KeyStore 调用封装
├── bridge/        # UniFFI 生成的桥接代码（勿手动修改）
└── data/          # SQLCipher 管理与本地镜像 IO
```

### Android 状态机模型

```
Uninitialized → Initializing → Active (Idle/Decrypting/Rekeying)
                                    ↓         ↓
                               Degraded ← Revoked
```

**关键状态**:
- **Idle**: 无明文密钥在内存
- **Decrypting**: DEK/VK 仅存在 Rust 堆内（mlock + zeroize）
- **Rekeying**: 执行 PQRR，必须严格按顺序完成才能转换状态
- **Degraded**: Play Integrity verdict ≠ STRONG，只读模式
- **Revoked**: 终态，删除所有密钥和数据

### 原子纪元升级协议（AUP）

1. **预备**: Rust Core 在内存中解封当前 VK，派生新 DEK
2. **影子写入**: 创建临时文件 `vault.tmp`，强制 fsync
3. **原子替换**: `rename("vault.tmp", "vault.db")` - POSIX 原子操作
4. **更新元数据**: SQLCipher 提交事务，更新 Local_Epoch

崩溃恢复：启动时比较 metadata_epoch 与 blob_header.epoch，自动对齐

### 测试覆盖要求

- **Rust 核心**: 要求 100% 代码覆盖率
- 使用 `proptest` 进行属性测试
- 使用 `criterion` 进行性能基准测试
- 每 180 天强制"模拟灾难演练"验证助记词

### 构建依赖

- **Android NDK**: 必须设置 `ANDROID_NDK_HOME` 或 `ANDROID_NDK` 环境变量
- **Rust 目标**: 需要添加 Android 交叉编译目标
  ```bash
  rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
  ```
- **UniFFI**: 首次运行会自动安装 `uniffi-bindgen`

## 安全注意事项

### 禁止事项

- **禁止**在 Kotlin 层实现任何密码学逻辑
- **禁止**将密钥以任何形式记录到日志
- **禁止**使用 Flutter、React Native、WebView 作为主界面层
- **禁止**在 Git 中提交 API 密钥或测试证书

### 必须事项

- **必须**为所有敏感数据结构实现 `zeroize::Zeroize`
- **必须**使用 Android Biometric API (Class 3) 进行用户认证
- **必须**使用 Play Integrity API 验证设备完整性
- **必须**在 Rust 层强制执行四大数学不变量检查

## 安全边界定义

| 层                      | 是否可信 | 是否可接触明文密钥 |
| ---------------------- | ----- | --------- |
| Rust Core              | ✅     | ✅         |
| Android Security Layer | ✅（部分） | ❌（只持硬件句柄） |
| UI Layer               | ❌     | ❌         |
| Backend                | ❌     | ❌         |

## Aeternum 开发技能系统

项目配置了专用开发技能，用于辅助 AI 辅助开发。详见 [.claude/skills/README.md](.claude/skills/README.md)

### 可用技能

| 技能 | 用途 | 触发方式 |
|------|------|---------|
| `openspec-review` | **OpenSpec 提案审查** | 审查提案文档质量 |
| `openspec-completion` | **OpenSpec 完成情况审查** | 审查提案实现完成情况 |
| `aeternum-checkpoint` | **任务检查点（前置必选）** | 所有开发任务 |
| `aeternum-crypto` | 密码学原语开发 | Kyber, X25519, KDF, zeroize |
| `aeternum-protocol` | 协议与状态机开发 | PQRR, 纪元升级, 影子包装, 否决 |
| `aeternum-android` | Android 安全层开发 | 生物识别, StrongBox, Play Integrity |
| `aeternum-bridge` | UniFFI 桥接管理 | UDL 接口, 桥接代码, FFI |
| `aeternum-invariant` | 不变量验证 | 验证不变量, 生成报告 |

> **重要**:
> - `aeternum-checkpoint` 是所有开发任务的前置技能，确保相关文档已被阅读后再开始编码
> - `openspec-completion` 用于提案实现完成后审查代码质量、文档符合性和安全性

### 使用示例

```
/openspec-review add-models                  # 审查提案文档质量
/openspec-completion add-models             # 审查提案实现完成情况
/aeternum-checkpoint 实现密码学功能前的文档检查
/aeternum-crypto 实现 Kyber-1024 KEM 封装功能
/aeternum-protocol 添加设备撤销功能
/aeternum-android 创建生物识别认证界面
/aeternum-bridge 添加新的 UniFFI 接口方法
/aeternum-invariant 验证 core/src/protocol/ 全部文件
```

### Checkpoint 工作流程

当请求任何开发任务时：

1. **任务分类** → 自动识别任务类型（crypto/protocol/android/bridge/invariant）
2. **文档映射** → 确定需要阅读的文档列表
3. **阅读验证** → 逐个读取并确认理解关键章节
4. **约束确认** → 明确不可违反的约束
5. **开始执行** → 在遵守所有约束的前提下开始编码

## 相关规范文档

- [架构白皮书 v5.0](docs/arch/Aeternum-architecture.md) - 完整架构设计
- [UniFFI 桥接契约](docs/bridge/UniFFI-Bridge-Contract.md) - Rust↔Kotlin 接口定义
- [同步协议规范](docs/protocols/Sync-Wire-Protocol.md) - Aeternum Wire 通信协议
- [Android 密钥生命周期](docs/Android-Key-Lifecycle-State-Machine.md) - 状态机详细定义
- [冷锚恢复协议](docs/protocols/Cold-Anchor-Recovery.md) - 助记词恢复流程
- [持久化与崩溃一致性](docs/protocols/Persistence-Crash-Consistency.md) - 存储引擎规范
- [形式化数学不变量](docs/math/Formal-Invariants.md) - 四大不变量数学定义
- [项目结构规范](docs/Aeternum-Project-Structure-Spec.md) - 工程目录组织
