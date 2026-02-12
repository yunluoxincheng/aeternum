--- START OF FILE Aeternum-Technical-Stack-Versions.md ---

# **Aeternum 规范文档**

## **技术栈版本选型与基准规范 (Tech-Stack Versioning & Baselines)**

**文档编号：AET-SPEC-TECH-007**
**版本：v1.0 (2024-2025 Baseline)**
**效力：强制执行 (Strict Enforcement)**
**目标：定义 Aeternum 系统各层的技术版本基准，确保硬件信任根（StrongBox）兼容性、抗量子算法标准对齐及长效稳定性。**

---

### **1. 选型原则**

1.  **安全优先 (Security First)**：核心密码学库必须使用经过审计的稳定版本，严禁在生产环境使用 Beta 或 RC 版。
2.  **硬件绑定 (Hardware Bound)**：最低支持版本必须确保 `StrongBox`（Keymaster 4.0+）和 `Key Attestation` 的物理可用性。
3.  **长期支持 (LTS)**：基础框架（如 JDK, Postgres）选用 LTS 版本，减少非对称升级成本。

---

### **2. Android 客户端技术栈 (The Shell)**

| 组件 | 推荐版本 | 说明 |
| :--- | :--- | :--- |
| **Kotlin** | **2.0.20+** | **K2 编译器**：显著提升大型项目编译速度，Compose 编译器集成。 |
| **JDK** | **17 (LTS)** | Android 现代开发标准版本。 |
| **Android SDK (Min)** | **31 (Android 12)** | **基准线**：确保 StrongBox、VcnManager 及现代权限模型的成熟支持。 |
| **Android SDK (Target)** | **35 / 36** | Android 15 / 16。 |
| **Jetpack Compose BOM** | **2025.02.00+** | 确保 UI 组件库版本兼容性。 |
| **Gradle** | **8.13+** | 支持最新的编译并行优化。 |
| **SQLCipher** | **4.5.x** | 数据库加密层，配合 Rust Core 索引。 |

---

### **3. Rust 密码学核心 (The Truth)**

| 组件 | 推荐版本 | 说明 |
| :--- | :--- | :--- |
| **Rust Toolchain** | **1.80.0+ (Stable)** | 确保能够使用最新的安全补丁与标准库性能增强。 |
| **Rust Edition** | **2021** | 目前最稳健的工程版本，待 2024 版完全稳定后迁移。 |
| **UniFFI** | **0.28.0+** | 桥接层核心。**必须**确保 Rust 侧与 Kotlin 运行时版本完全一致。 |
| **Cargo NDK** | **3.x** | 处理 Android 交叉编译的最佳工具。 |

#### **3.1 核心密码库 (Crates)**
*   **pqcrypto-kyber (0.6.x)**：实现 **ML-KEM (Kyber-1024)**。必须对齐 NIST FIPS 203 标准。
*   **chacha20poly1305 (0.10.x)**：对称加密，提供高性能 AEAD。
*   **zeroize (1.8.x)**：物理内存擦除，需开启 `derive` 特性。
*   **blake3 (1.5.x)**：用于高性能哈希派生。

---

### **4. 后端与基础设施 (The Infrastructure)**

| 组件 | 推荐版本 | 说明 |
| :--- | :--- | :--- |
| **Go** | **1.22+** | 改进的随机数安全 API 及循环变量作用域修复。 |
| **PostgreSQL** | **16 (LTS)** | 支持逻辑复制增强，适合 Epoch 元数据同步。 |
| **Temporal.io** | **1.24+** | 48 小时否决权工作流的持久化执行引擎。 |
| **gRPC / Protobuf** | **1.60+** | 二进制通信协议，确保跨端类型一致性。 |

---

### **5. 依赖管理策略**

#### **5.1 Android Version Catalog (`libs.versions.toml`)**
所有 Android 依赖必须通过 `gradle/libs.versions.toml` 统一管理，禁止在各个 module 的 `build.gradle` 中声明零散版本。

#### **5.2 Rust 依赖锁死 (Crate Pinning)**
为了防止供应链攻击，`core/Cargo.toml` 中的密码学相关库必须使用 **精确版本号**（例如 `=0.6.0`），禁止使用波浪号 `~` 或插入号 `^`。

#### **5.3 跨语言对齐检测**
CI/CD 流水线必须包含版本对齐检查脚本：
*   **UniFFI Check**：验证 `core/Cargo.toml` 中的 `uniffi` 版本与 `android/app/build.gradle.kts` 中的 `uniffi-kotlin` 运行时版本是否绝对匹配。

---

### **6. 兼容性矩阵 (Compatibility Matrix)**

| 维度 | 要求 |
| :--- | :--- |
| **硬件** | 支持 ARM64-v8a (强制), x86_64 (模拟器调试可用)。 |
| **安全芯片** | 强烈建议设备具备 **StrongBox**（如 Pixel 3+, Samsung Knox 设备），否则降级至 TEE 处理。 |
| **网络** | 传输层 TLS 1.3 强制开启，应用层 Aeternum Wire 混合加密。 |

---

### **7. 总结**

本版本选型不仅是为了追求技术的新颖性，更是为了在 **Android 12+** 提供的现代安全特性之上，构建起 Aeternum 的抗量子信任链。建议每季度进行一次“依赖安全性审查”，并在发现严重漏洞 24 小时内完成紧急版本更迭。

--- END OF FILE Aeternum-Technical-Stack-Versions.md ---