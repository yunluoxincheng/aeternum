# Project Context

## Purpose

**Aeternum** 是一个后量子安全的移动密钥管理系统（架构 v5.0 已冻结）。

### 核心目标
- 提供后量子安全的密钥管理，抵御量子计算机攻击
- 实现设备间安全同步与恢复机制
- 通过分层架构保护用户密钥安全
- 支持硬件级密钥存储（Android StrongBox/KeyStore）

### 关键特性
- **后量子安全**: ML-KEM (Kyber-1024) + X25519 混合加密
- **影子恢复 (Shadow Wrapping)**: Device_0 物理锚点不可区分
- **PQRR 协议**: 设备撤销与密钥轮换，前向安全保证
- **48h 否决机制**: 防止助记词被盗后的即时恢复
- **密码学纪元**: 支持算法平滑迁移

## Tech Stack

### 核心密码学层 (Rust)
- **语言**: Rust 2021 Edition
- **密码学库**:
  - `kyber1024`: ML-KEM (Kyber-1024) 后量子 KEM
  - `x25519-dalek`: X25519 密钥交换
  - `chacha20poly1305`: XChaCha20-Poly1305 AEAD
  - `argon2`: Argon2id 密钥派生
  - `blake3`: BLAKE3 哈希
- **跨平台桥接**: UniFFI (Rust ↔ Kotlin)
- **测试**: `proptest` (属性测试), `criterion` (性能基准测试)

### Android 应用层 (Kotlin)
- **UI 框架**: Jetpack Compose
- **安全层**: Android Biometric API (Class 3), Play Integrity API
- **数据库**: SQLCipher
- **最小 SDK**: Android 8.0 (API 26)
- **目标 SDK**: Android 15 (API 35)

### 构建工具
- **Rust 交叉编译**: Android NDK targets (ARM64/ARMv7/x86_64)
- **Gradle**: 8.x
- **UniFFI**: 0.28.x

## Project Conventions

### Code Style

#### Rust 代码风格
- 遵循 `rustfmt` 默认格式
- 使用 `cargo clippy` 进行代码检查
- 所有敏感数据结构必须实现 `zeroize::Zeroize` trait
- 使用 `Zeroizing<Vec<u8>>` 确保密钥内存物理擦除

#### Kotlin 代码风格
- 遵循 Android Kotlin 编码规范
- 禁止在 Kotlin 层实现任何密码学逻辑
- Kotlin 严禁持有明文密钥的 `ByteArray`
- 仅持有 Rust 实例句柄，明文在内存中"即用即走"

### Architecture Patterns

#### 分层架构（信任边界）
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

#### UniFFI 桥接契约
- **接口定义**: `core/uniffi/aeternum.udl`（修改后必须重新生成桥接代码）
- **Kotlin 生成位置**: `android/app/src/main/kotlin/aeternum/`（勿手动修改）

#### Android 状态机模型
```
Uninitialized → Initializing → Active (Idle/Decrypting/Rekeying)
                                    ↓         ↓
                               Degraded ← Revoked
```

### Testing Strategy

#### 测试覆盖要求
- **Rust 核心**: 要求 100% 代码覆盖率
- **属性测试**: 使用 `proptest` 验证密码学不变量
- **性能基准**: 使用 `criterion` 定期运行性能基准测试
- **灾难演练**: 每 180 天强制"模拟灾难演练"验证助记词

#### 测试命令
```bash
# Rust 核心测试
cd core && cargo test --package aeternum-core

# Android 单元测试
cd android && ./gradlew test

# Android 设备测试
cd android && ./gradlew connectedAndroidTest
```

### Git Workflow

#### 分支策略
- `main`: 生产就绪代码
- `develop`: 开发集成分支
- `feature/*`: 功能开发分支
- `fix/*`: Bug 修复分支
- `openspec/*`: OpenSpec 提案分支

#### 提交约定
- 使用约定式提交（Conventional Commits）
- 格式: `type(scope): description`
- 类型: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`

#### 代码审查
- 所有更改必须通过 Pull Request
- 安全相关代码需要额外审查
- 密码学代码必须由具备密码学背景的开发者审查

## Domain Context

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

### 四大数学不变量（必须强制执行）

**Invariant #1 — 纪元单调性**: 所有设备的 epoch 必须严格单调递增，禁止回滚

**Invariant #2 — Header 完备性**: 每个活跃设备必须且仅能通过一个正确的 Header 获取 DEK

**Invariant #3 — 因果熵障**: 解密权限 ≠ 管理权限（RECOVERY 角色不能执行 σ_rotate）

**Invariant #4 — 否决权优先**: 48h 窗口内任何活跃设备的 Veto 信号必须立即终止恢复流程

违反不变量时必须触发熔断：内核锁定 → 状态隔离 → 用户警示

### 原子纪元升级协议（AUP）

1. **预备**: Rust Core 在内存中解封当前 VK，派生新 DEK
2. **影子写入**: 创建临时文件 `vault.tmp`，强制 fsync
3. **原子替换**: `rename("vault.tmp", "vault.db")` - POSIX 原子操作
4. **更新元数据**: SQLCipher 提交事务，更新 Local_Epoch

崩溃恢复：启动时比较 metadata_epoch 与 blob_header.epoch，自动对齐

## Important Constraints

### 安全约束
- **禁止**在 Kotlin 层实现任何密码学逻辑
- **禁止**将密钥以任何形式记录到日志
- **禁止**使用 Flutter、React Native、WebView 作为主界面层
- **禁止**在 Git 中提交 API 密钥或测试证书

### 必须事项
- **必须**为所有敏感数据结构实现 `zeroize::Zeroize`
- **必须**使用 Android Biometric API (Class 3) 进行用户认证
- **必须**使用 Play Integrity API 验证设备完整性
- **必须**在 Rust 层强制执行四大数学不变量检查

### 性能约束
- 密钥解密操作必须在 500ms 内完成
- UI 响应时间不得超过 100ms
- 密码学原语必须通过 `criterion` 基准测试验证

### 兼容性约束
- 最低支持 Android 8.0 (API 26)
- 必须支持 ARM64、ARMv7、x86_64 三种架构
- SQLCipher 数据库格式必须向后兼容

## External Dependencies

### 密码学库（Rust）
- `kyber1024` - 后量子 KEM 实现
- `x25519-dalek` - X25519 密钥交换
- `chacha20poly1305` - AEAD 加密
- `argon2` - 密钥派生函数
- `blake3` - 哈希函数

### Android 系统服务
- **Android Keystore/StrongBox**: 硬件级密钥存储
- **BiometricPrompt**: 生物识别认证
- **Play Integrity API**: 设备完整性验证

### 开发工具
- **UniFFI**: Rust ↔ Kotlin 跨平台 FFI 代码生成
- **Android NDK**: Rust 交叉编译工具链
- **Gradle**: Android 构建系统

### 相关文档规范
- [架构白皮书 v5.0](docs/arch/Aeternum-architecture.md)
- [UniFFI 桥接契约](docs/bridge/UniFFI-Bridge-Contract.md)
- [同步协议规范](docs/protocols/Sync-Wire-Protocol.md)
- [Android 密钥生命周期](docs/Android-Key-Lifecycle-State-Machine.md)
- [冷锚恢复协议](docs/protocols/Cold-Anchor-Recovery.md)
- [持久化与崩溃一致性](docs/protocols/Persistence-Crash-Consistency.md)
- [形式化数学不变量](docs/math/Formal-Invariants.md)
- [项目结构规范](docs/Aeternum-Project-Structure-Spec.md)

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

### 代码质量检查

```bash
./scripts/format-check.sh            # 代码格式检查
./scripts/security-audit.sh          # 安全审计脚本
```

## Docker Hub 镜像

- **Docker Hub 用户名**: yunluoxincheng
- 项目相关容器镜像用于 CI/CD 和开发环境
