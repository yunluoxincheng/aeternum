--- START OF FILE Aeternum-Project-Structure-Spec.md ---

# **Aeternum 规范文档**

## **项目工程目录结构规范 (Project Directory Structure)**

**文档编号：AET-SPEC-DIR-006**
**版本：v1.0 (Implementation Baseline)**
**目标：定义 Aeternum 单体仓库 (Monorepo) 的物理组织方式，确保逻辑解耦、可审计性及跨平台构建的一致性。**

---

### **1. 目录设计原则**

Aeternum 的工程结构遵循以下三项原则：
1.  **内核主权化 (Core Sovereignty)**：密码学内核（Rust）作为“单一真理来源”，独立于平台 API 存在，可独立通过所有安全审计。
2.  **UI/内核强隔离 (UI-Core Decoupling)**：所有界面层逻辑不触碰密钥，仅通过生成的 UniFFI 桥接层与内核对话。
3.  **规格说明驱动 (Spec-Driven)**：文档、形式化验证脚本与代码并列存储，确保实现与设计永不偏离。

---

### **2. 物理目录树 (Tree Map)**

```text
Aeternum/
├── docs/                       # 📖 形式化规范文档库 (Mandatory)
│   ├── arch/                   # 架构设计与 PQRR 协议
│   ├── bridge/                 # UniFFI 调用契约与接口定义
│   ├── math/                   # 数学不变量与形式化准则
│   └── protocols/              # 同步、恢复与 Wire 协议
│
├── core/                       # 🦀 Rust 密码内核 (The Root of Trust)
│   ├── Cargo.toml
│   ├── uniffi/                 # UniFFI 接口描述文件 (.udl)
│   └── src/
│       ├── lib.rs              # FFI 入口与会话句柄管理
│       ├── crypto/             # Kyber-1024, XChaCha20-Poly1305 实现
│       ├── storage/            # 影子写入与崩溃一致性引擎
│       ├── sync/               # Aeternum Wire 帧封装与协议逻辑
│       └── models/             # 纪元(Epoch)与 Header 数据模型
│
├── android/                    # 🤖 原生 Android 客户端
│   ├── build.gradle.kts
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── jniLibs/        # 编译产出的各架构 .so 文件
│   │   │   └── kotlin/io/aeternum/
│   │   │       ├── ui/         # Jetpack Compose 界面与组件
│   │   │       ├── security/   # StrongBox / Keystore 硬件调用
│   │   │       ├── bridge/     # UniFFI 生成的 Kotlin 胶水代码
│   │   │       └── data/       # SQLCipher 管理与本地镜像 IO
│   └── scripts/                # Android 特定构建脚本
│
├── server/                     # ☁️ 后端状态机 (Non-Trust Domain)
│   ├── api/                    # gRPC 接口与中继逻辑
│   └── workflow/               # Temporal 恢复工作流 (48h 延时引擎)
│
├── shared/                     # 📋 跨端共享资源
│   └── proto/                  # Protobuf 协议定义
│
├── scripts/                    # 🛠️ 自动化构建与审计工具
│   ├── build-core.sh           # 自动化交叉编译 Rust (Android/Desktop)
│   ├── generate-bridge.sh      # 运行 uniffi-bindgen 生成代码
│   └── security-audit.sh       # 敏感代码扫描与依赖检查
│
└── .github/workflows/          # 🚀 CI/CD 自动化流水线
```

---

### **3. 核心目录职能说明**

#### **3.1 `core/` (Rust 密码内核)**
*   **地位**：系统的“心脏”，严禁包含任何 UI 代码或平台特有库（如 Android Context）。
*   **职责**：处理所有 DEK/VK 的生命周期、执行 PQRR 算法、封装同步帧。
*   **安全性要求**：必须通过 `cargo test` 实现 100% 的逻辑覆盖，所有密钥结构必须绑定 `Zeroize`。

#### **3.2 `android/` (原生外壳)**
*   **地位**：系统的“感官”，负责与硬件及用户交互。
*   **职责**：管理生物识别验证、调用物理 StrongBox 芯片生成 DK_hardware、渲染加密数据的 UI 展现。
*   **限制**：禁止在 Kotlin 层解密任何 Vault 数据，所有密文必须送入 `core/` 处理。

#### **3.3 `docs/` (文档库)**
*   **地位**：系统的“宪法”。
*   **要求**：任何对 `core/` 逻辑的重大修改（PR），必须同步更新 `docs/` 下对应的规范文件。

---

### **4. 典型开发工作流**

1.  **协议变更**：修改 `docs/bridge/` 下的 UDL 定义及协议文档。
2.  **内核实现**：在 `core/src/` 中使用 Rust 实现逻辑。
3.  **生成桥接**：运行 `scripts/generate-bridge.sh` 更新 Kotlin 接口。
4.  **平台集成**：运行 `scripts/build-core.sh` 将 Rust 编译为 `aarch64-linux-android` 等平台的 `.so` 库。
5.  **UI 开发**：在 Android Studio 中基于更新后的接口编写 Compose 界面。

---

### **5. 结论**

本目录结构将 Aeternum 从一个复杂的数学猜想转化为一个条理清晰、职责分明的工程项目。它确保了开发人员在修改 UI 时不会破坏底层的安全不变量，同时也为第三方安全审计提供了极其清晰的逻辑边界。

--- END OF FILE Aeternum-Project-Structure-Spec.md ---