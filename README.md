# Aeternum

**下一代个人主权与抗量子密码管理器**

> 架构版本: v5.0 (Arch-Frozen) | 密级: 生产就绪

---

## 概述

Aeternum 是一款重新定义"数字主权"的密码管理器。它不仅通过后量子密码学 (PQC) 抵御未来的量子攻击，更通过创新的设备信任链与恢复模型，解决了传统密码管理器在"物理撤销滞后"与"恢复后门风险"上的本质缺陷。

### 核心价值

| 价值 | 说明 |
|------|------|
| **绝对主权** | 用户可选择完全脱离厂商干预的自托管恢复模式 |
| **前向安全** | 通过 DEK 轮换协议，确保丢失设备无法读取未来数据 |
| **不可区分性** | 物理恢复锚点隐匿于噪声中，消除高价值目标指纹 |
| **后量子安全** | ML-KEM (Kyber-1024) + X25519 混合加密 |

### 密码学原语

| 组件 | 选定算法 | 标准 | 目的 |
|------|---------|------|------|
| 抗量子封装 (KEM) | ML-KEM (Kyber-1024) | NIST FIPS 203 | 抗量子密钥交换与影子包装 |
| 经典密钥协商 (ECDH) | X25519 | RFC 7748 | 混合加密的高性能基础 |
| 对称加密 | XChaCha20-Poly1305 | RFC 8439 | 全库加密，抗侧信道攻击 |
| 密钥派生 (KDF) | Argon2id | RFC 9106 | 抵抗 GPU 暴力破解 |
| 哈希与派生 | BLAKE3 | - | 子密钥派生与完整性校验 |
| 内存安全 | Rust Zeroize | - | 敏感密钥生命周期结束后物理抹除 |

---

## 架构设计

Aeternum 采用分层架构设计，确保明文密钥仅在根信任域内处理：

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

### 核心协议

- **PQRR 协议 (Post-Quantum Revocation & Re-keying)**: 设备撤销与密钥轮换，前向安全保证
- **影子包装 (Shadow Wrapping)**: Device_0 物理锚点不可区分
- **48h 否决机制**: 防止助记词被盗后的即时恢复
- **密码学纪元**: 支持算法平滑迁移

---

## 四大数学不变量

系统强制执行以下数学不变量，违反时触发熔断：

| 不变量 | 含义 |
|--------|------|
| **Invariant #1 — 纪元单调性** | 所有设备的 epoch 必须严格单调递增，禁止回滚 |
| **Invariant #2 — Header 完备性** | 每个活跃设备必须且仅能通过一个正确的 Header 获取 DEK |
| **Invariant #3 — 因果熵障** | 解密权限 ≠ 管理权限（RECOVERY 角色不能执行 σ_rotate） |
| **Invariant #4 — 否决权优先** | 48h 窗口内任何活跃设备的 Veto 信号必须立即终止恢复流程 |

---

## 密钥层级

```
MRS (Master Root Seed) - 24位助记词
    ├── BLAKE3 派生
    ├── IK (Identity Key) - 身份证明
    ├── RK (Recovery Key) - 恢复封装
    └── DK (Device Key) - StrongBox 硬件生成，永不离开硬件
        └── DEK (Data Encryption Key) - 256-bit 包装密钥
            └── VK (Vault Key) - 实际加密用户数据的 XChaCha20 对称密钥
```

---

## 状态机模型

```
Uninitialized → Initializing → Active (Idle/Decrypting/Rekeying)
                                    ↓         ↓
                               Degraded ← Revoked
```

| 状态 | 说明 |
|------|------|
| **Idle** | 无明文密钥在内存 |
| **Decrypting** | DEK/VK 仅存在 Rust 堆内（mlock + zeroize） |
| **Rekeying** | 执行 PQRR，必须严格按顺序完成才能转换状态 |
| **Degraded** | Play Integrity verdict ≠ STRONG，只读模式 |
| **Revoked** | 终态，删除所有密钥和数据 |

---

## 项目结构

```
Aeternum/
├── docs/                       # 📖 形式化规范文档库
│   ├── arch/                   # 架构设计与 PQRR 协议
│   ├── bridge/                 # UniFFI 调用契约与接口定义
│   ├── math/                   # 数学不变量与形式化准则
│   └── protocols/              # 同步、恢复与 Wire 协议
│
├── core/                       # 🦀 Rust 密码内核 (根信任域)
│   ├── uniffi/                 # UniFFI 接口描述文件 (.udl)
│   └── src/
│       ├── crypto/             # Kyber-1024, XChaCha20-Poly1305
│       ├── storage/            # 影子写入与崩溃一致性引擎
│       ├── sync/               # Aeternum Wire 帧封装
│       └── models/             # 纪元与 Header 数据模型
│
├── android/                    # 🤖 原生 Android 客户端
│   └── app/src/main/kotlin/io/aeternum/
│       ├── ui/                 # Jetpack Compose 界面 (非信任域)
│       ├── security/           # StrongBox/KeyStore 调用
│       ├── bridge/             # UniFFI 生成的桥接代码
│       └── data/               # SQLCipher 管理与本地镜像 IO
│
├── server/                     # ☁️ 后端状态机 (非信任域)
│   ├── api/                    # gRPC 接口与中继逻辑
│   └── workflow/               # Temporal 恢复工作流 (48h 延时引擎)
│
├── shared/                     # 📋 跨端共享资源
│   └── proto/                  # Protobuf 协议定义
│
└── scripts/                    # 🛠️ 自动化构建与审计工具
    ├── build-core.sh           # 交叉编译 Rust (Android/Desktop)
    ├── generate-bridge.sh      # 运行 uniffi-bindgen 生成代码
    └── security-audit.sh       # 敏感代码扫描与依赖检查
```

---

## 快速开始

### 前置要求

| 组件 | 版本 |
|------|------|
| Rust | 1.80+ (Stable) |
| Kotlin | 2.0.20+ |
| JDK | 17 (LTS) |
| Android SDK (Min) | 31 (Android 12) |
| Android NDK | 25.2+ |
| Gradle | 8.7+ |

### 构建步骤

```bash
# 1. 交叉编译 Rust 核心到各 Android 平台（ARM64/ARMv7/x86_64）
./scripts/build-core.sh

# 2. 生成 UniFFI 桥接代码（Kotlin 接口 + Rust scaffolding）
./scripts/generate-bridge.sh

# 3. 构建 Android APK
cd android && ./gradlew assembleDebug
```

### 开发

```bash
# Rust 核心开发
cd core
cargo test                          # 运行所有测试
cargo clippy --all-targets           # 代码检查
cargo audit                          # 安全审计

# Android 开发
cd android
./gradlew test                       # 运行单元测试
./gradlew connectedAndroidTest       # 运行设备测试
```

---

## 安全边界

| 层 | 是否可信 | 是否可接触明文密钥 |
|---|-----|------------|
| Rust Core | ✅ | ✅ |
| Android Security Layer | ✅（部分） | ❌（只持硬件句柄） |
| UI Layer | ❌ | ❌ |
| Backend | ❌ | ❌ |

### 威胁映射矩阵

| 场景 | 攻击路径 | 结果 | 防御机制 |
|------|---------|------|----------|
| 设备失窃 | 物理占有设备 | 受控 | 远程撤销触发 PQRR，旧密钥废弃 |
| 云端脱库 | 获得所有加密 Blob | 安全 | 零知识架构，无用户私钥无法解密 |
| 助记词泄露 | 攻击者发起恢复 | 可挽救 | 48h 否决权确保原主可拦截攻击 |
| 量子攻击 | 离线破解历史流量 | 安全 | Kyber 提供的后量子安全性 |
| 控制权夺取 | 尝试修改根密钥 | 拦截 | Root Rotation 需要助记词+在手设备双重共识 |

---

## 文档

| 文档 | 描述 |
|------|------|
| [架构白皮书 v5.0](docs/arch/Aeternum-architecture.md) | 完整架构设计 |
| [形式化数学不变量](docs/math/Formal-Invariants.md) | 四大不变量数学定义 |
| [UniFFI 桥接契约](docs/bridge/UniFFI-Bridge-Contract.md) | Rust↔Kotlin 接口定义 |
| [同步协议规范](docs/protocols/Sync-Wire-Protocol.md) | Aeternum Wire 通信协议 |
| [冷锚恢复协议](docs/protocols/Cold-Anchor-Recovery.md) | 助记词恢复流程 |
| [持久化与崩溃一致性](docs/protocols/Persistence-Crash-Consistency.md) | 存储引擎规范 |
| [Android 密钥生命周期](docs/Android-Key-Lifecycle-State-Machine.md) | 状态机详细定义 |
| [项目结构规范](docs/Aeternum-Project-Structure-Spec.md) | 工程目录组织 |
| [技术栈版本选型](docs/arch/Technical-Stack-Versions.md) | 版本基准规范 |

---

## 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

---

## 贡献

欢迎提交 Pull Request 和 Issue！请确保遵循项目的安全规范和编码标准。
