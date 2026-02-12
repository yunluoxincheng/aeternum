

# **Aeternum 原生 Android 客户端技术栈选型文档**

**版本：v1.0 (Implementation Baseline)**
**适用范围：Android 12+ 首发版本**
**定位：高安全等级、零知识、抗量子密码管理系统客户端**

---

## **1. 设计背景**

Aeternum 并非普通移动应用，而是：

> **以移动设备为信任终端的密码学安全系统**

系统具备以下特性：

* Device-Bound Keys（设备绑定密钥）
* Post-Quantum Cryptography（ML-KEM）
* PQRR 密钥轮换与撤销协议
* Crypto Epoch 全局原子升级机制
* Shadow Wrapping 恢复锚点隐匿设计
* Secure Degraded Mode 安全降级模式
* 48 小时 Veto 恢复否决机制

这些机制要求客户端具备：

* 硬件信任根接入能力
* 原生安全 API 深度控制能力
* 内存级密钥生命周期管理能力

因此技术栈选择遵循原则：

1. **信任根不可跨平台抽象**
2. **密钥处理不可进入解释型运行时**
3. **UI 与安全核心强隔离**

---

# **2. 客户端总体架构**

```
┌──────────────────────────┐
│ Presentation Layer       │
│ Jetpack Compose (UI)     │
└───────────┬──────────────┘
            │ 只接收脱敏数据
┌───────────▼──────────────┐
│ Security Control Layer   │  Kotlin (Native)
│ • Keystore / StrongBox   │
│ • Integrity Verification │
│ • Biometric Gate         │
│ • Secure State Machine   │
└───────────┬──────────────┘
            │ JNI / FFI
┌───────────▼──────────────┐
│ Rust Cryptographic Core  │
│ • PQRR                   │
│ • Shadow Wrapping        │
│ • Epoch Engine           │
│ • DEK/VK lifecycle       │
└──────────────────────────┘
```

---

# **3. Android 客户端技术栈**

## **3.1 UI 层（非信任域）**

| 组件    | 技术                  | 说明              |
| ----- | ------------------- | --------------- |
| UI 框架 | **Jetpack Compose** | 原生、无额外运行时攻击面    |
| 架构模式  | MVVM + StateFlow    | 状态单向流动，UI 不持有密钥 |
| 导航    | Navigation Compose  | 生命周期可控          |
| 图片/资源 | Coil / 原生 API       | 不涉及敏感数据         |

**禁止使用：** Flutter、React Native、WebView 作为主界面层。

---

## **3.2 安全控制层（Android Native 信任域）**

此层为 Android 侧**核心安全壳**。

| 模块       | 技术                                      | 作用                      |
| -------- | --------------------------------------- | ----------------------- |
| 语言       | **Kotlin + 少量 Java**                    | 调用系统安全 API              |
| 硬件密钥     | **Android Keystore + StrongBox**        | DK 生成与存储                |
| 密钥证明     | Key Attestation                         | 验证密钥来源硬件                |
| 生物识别     | BiometricPrompt (Class 3)               | 高等级身份确认                 |
| 完整性验证    | **Play Integrity API (STRONG verdict)** | Root / Hook 检测          |
| 安全态机     | Kotlin FSM                              | Secure Degraded Mode 控制 |
| 反调试      | ptrace 检测 / Frida 特征检测                  | 提升攻击成本                  |
| Native 桥 | JNI + NDK                               | 调用 Rust 核心              |
| 内存锁定     | mlock (NDK)                             | 防止 swap                 |

---

## **3.3 Rust 密码核心（核心信任域）**

Rust 层负责所有真实密钥处理与协议实现。

| 功能   | 技术                      |
| ---- | ----------------------- |
| 语言版本 | **Rust 2024 Edition**   |
| PQC  | pqcrypto-kyber (ML-KEM) |
| 对称加密 | chacha20poly1305        |
| KDF  | argon2 + blake3         |
| 内存安全 | zeroize + secrecy crate |
| 序列化  | bincode + serde         |
| FFI  | cbindgen + cargo-ndk    |
| 内存策略 | mlock + 栈优先分配           |

**安全要求：**

* Rust 层不信任上层传入状态
* 所有密钥结构实现 Drop 时自动 zeroize
* 明文 VK/DEK 永不离开 Rust 层

---

## **3.4 本地数据存储**

| 数据类型    | 技术                   | 安全策略     |
| ------- | -------------------- | -------- |
| 元数据缓存   | SQLCipher            | 无密钥材料    |
| 加密 Blob | 文件系统                 | 已被 VK 加密 |
| 偏好配置    | EncryptedSharedPrefs | 不存密钥     |

---

# **4. 后端技术栈（非信任域）**

服务器仅为协议协调与状态机执行器。

| 模块      | 技术               | 作用             |
| ------- | ---------------- | -------------- |
| 主服务     | **Go**           | 高并发与稳定性        |
| 工作流引擎   | **Temporal.io**  | PQRR / 恢复流程原子性 |
| API 协议  | gRPC + Protobuf  | 二进制通信          |
| 数据库     | PostgreSQL       | 元数据 / Epoch    |
| Blob 存储 | S3 Compatible    | 加密 VaultBlob   |
| HSM     | CloudHSM (L1/L2) | 辅助模式           |

---

# **5. 安全边界定义**

| 层                      | 是否可信  | 是否可接触明文密钥 |
| ---------------------- | ----- | --------- |
| Rust Core              | ✅     | ✅         |
| Android Security Layer | ✅（部分） | ❌（只持硬件句柄） |
| UI Layer               | ❌     | ❌         |
| Backend                | ❌     | ❌         |

---

# **6. 选型结论**

| 层级          | 最终技术决策               |
| ----------- | -------------------- |
| 移动 UI       | **Jetpack Compose**  |
| Android 安全壳 | Kotlin + NDK         |
| 密码核心        | Rust                 |
| 本地安全        | Keystore + StrongBox |
| 完整性检测       | Play Integrity       |
| 后端          | Go + Temporal        |
| 存储          | PostgreSQL + S3      |

---

## **最终原则**

> **Flutter ≠ 安全运行环境**
> **Android 原生 + Rust = 信任根延伸**

该技术栈确保：

* 密钥生命周期在可控 Native 内闭环
* 攻击面最小化
* 满足 Aeternum 架构的“设备即主权”安全哲学

