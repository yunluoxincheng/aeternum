# UniFFI Proc-Macro 迁移指南

**文档编号：AET-CORE-BRIDGE-SPEC-004**
**版本：v1.0**
**日期：2025-02-17**
**状态：已实施**

---

## 概述

本文档记录 Aeternum 项目从 UniFFI UDL 模式迁移到 proc-macro 模式的过程，解决了 Windows 平台上的 UDL 解析问题。

## 背景

### 问题

在 Windows 平台上，UniFFI 的 UDL 解析器存在严重问题：

| UniFFI 版本 | 简单 UDL | enum | interface | [Error] enum |
|------------|---------|------|-----------|-------------|
| 0.28.3     | ✅      | ❌   | ❌        | ❌          |
| 0.30.0     | ❌      | ❌   | ❌        | ❌          |
| 0.31.0     | ❌      | ❌   | ❌        | ❌          |

**相关 Issues**：
- https://github.com/mozilla/uniffi-rs/issues/2081 - UDL 解析器错误消息不清晰
- https://github.com/mozilla/uniffi-rs/issues/1236 - UDL 解析失败

### 解决方案

采用 **proc-macro 模式**完全替代 UDL 文件，提供：
- ✅ 跨平台兼容性（Windows/Linux/macOS）
- ✅ 类型安全（编译时检查）
- ✅ 更符合 Rust 生态系统
- ✅ 无需维护额外的 UDL 文件

---

## 迁移内容

### 1. 已实现的 UniFFI 导出

项目中的所有接口已经使用 proc-macro 导出：

#### Error 类型

```rust
// core/src/protocol/error.rs
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Error)]
pub enum PqrrError {
    EpochRegression { current: u32, attempted: u32 },
    HeaderIncomplete { device_id: String, reason: String },
    InsufficientPrivileges { role: String, operation: String },
    // ...
}
```

#### Enum 类型

```rust
// core/src/protocol/pqrr.rs
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum ProtocolState {
    Idle,
    Rekeying,
    RecoveryInitiated,
    Degraded,
    Revoked,
}
```

#### Record 类型

```rust
// core/src/bridge/types.rs
#[derive(uniffi::Record, Debug, Clone, PartialEq, Eq)]
pub struct DeviceInfo {
    pub device_id: Vec<u8>,
    pub device_name: String,
    pub epoch: u32,
    pub state: ProtocolState,
    pub last_seen_timestamp: i64,
    pub is_this_device: bool,
}
```

#### Object 类型

```rust
// core/src/bridge/engine.rs
#[derive(uniffi::Object)]
pub struct AeternumEngine {
    vault_path: String,
    state_machine: Arc<RwLock<PqrrStateMachine>>,
    // ...
}

#[uniffi::export]
impl AeternumEngine {
    #[uniffi::constructor]
    pub fn new_with_path(vault_path: String) -> Result<Self> {
        // ...
    }

    pub fn unlock(&self, hardware_key_blob: Vec<u8>) -> Result<VaultSession> {
        // ...
    }
}
```

### 2. Scaffolding 配置

```rust
// core/src/lib.rs
uniffi::setup_scaffolding!("aeternum");
```

### 3. 生成脚本更新

`scripts/generate-bridge.sh` 已更新支持两种模式：

```bash
# Proc-macro 模式（默认，推荐）
./scripts/generate-bridge.sh

# UDL 模式（传统方式，不推荐用于 Windows）
./scripts/generate-bridge.sh --mode udl
```

---

## 使用指南

### 生成 Kotlin 桥接代码

#### Windows 平台

```bash
# Proc-macro 模式（无需 UDL，完全兼容）
cd core
cargo build --release
cd ..
./scripts/generate-bridge.sh
```

#### Linux/macOS 平台

```bash
# 同样使用 proc-macro 模式
./scripts/generate-bridge.sh
```

### 从编译好的库生成

```bash
# 使用 host 平台库
./scripts/generate-bridge.sh --platform host

# 使用 Android 库
./scripts/generate-bridge.sh --platform android
```

---

## 类型映射（Rust → Kotlin）

| Rust | Kotlin |
|------|--------|
| `String` | `String` |
| `Vec<u8>` | `List<Byte>` (pass as `toList()`) |
| `i32/u32` | `Int` |
| `i64/u64` | `Long` |
| `bool` | `Boolean` |
| `Option<T>` | `T?` |
| `Result<T, E>` | `T` (throws exception) |

---

## 添加新的 UniFFI 导出

### 1. 导出新的 Error 类型

```rust
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Error)]
pub enum MyError {
    VariantName { field: String },
}
```

### 2. 导出新的 Enum 类型

```rust
#[derive(Debug, Clone, PartialEq, Eq, uniffi::Enum)]
pub enum MyEnum {
    Variant1,
    Variant2,
}
```

### 3. 导出新的 Record 类型

```rust
#[derive(uniffi::Record, Debug, Clone)]
pub struct MyRecord {
    pub field1: String,
    pub field2: u32,
}
```

### 4. 导出新的 Object 类型

```rust
#[derive(uniffi::Object)]
pub struct MyObject {
    field: String,
}

#[uniffi::export]
impl MyObject {
    #[uniffi::constructor]
    pub fn new(field: String) -> Self {
        Self { field }
    }

    pub fn get_field(&self) -> String {
        self.field.clone()
    }
}
```

---

## 依赖配置

### Cargo.toml

```toml
[dependencies]
uniffi = "0.31"

[build-dependencies]
uniffi = { version = "0.31", features = ["build"] }
```

### build.gradle.kts

```kotlin
dependencies {
    implementation("org.mozilla.uniffi:uniffi-bindgen:0.31.0")
    implementation("org.mozilla.uniffi:uniffi-kotlin:0.31.0")
}
```

---

## UDL 文件状态

### 当前状态

UDL 文件 `core/uniffi/aeternum.udl` 仍然存在于项目中，但**不再用于生成桥接代码**。

### 未来计划

1. **短期**：保留 UDL 文件作为文档参考
2. **中期**：将 UDL 内容迁移到 Rust 文档注释
3. **长期**：移除 UDL 文件

---

## 故障排除

### 问题：生成失败

**症状**：`cargo run --bin uniffi-bindgen` 失败

**解决方案**：
1. 确保已编译核心库：`cd core && cargo build --release`
2. 检查 UniFFI 版本是否为 0.31
3. 确保所有 `#[uniffi::export]` 和 `#[derive(uniffi::*)]` 宏正确使用

### 问题：Kotlin 代码编译错误

**症状**：生成的 Kotlin 代码有编译错误

**解决方案**：
1. 确保 Android 项目已同步 Gradle 依赖
2. 检查 JNA AAR 依赖是否正确添加
3. 运行 `./gradlew clean` 后重新构建

### 问题：Windows 上权限错误

**症状**：`Permission denied` 错误

**解决方案**：
```bash
# 使用 Git Bash 或 WSL
bash scripts/generate-bridge.sh
```

---

## 相关文档

- [UniFFI 桥接契约](./UniFFI-Bridge-Contract.md) - Rust↔Kotlin 接口定义
- [架构白皮书 v5.0](../arch/Aeternum-architecture.md) - 完整架构设计
- [UniFFI 官方文档](https://mozilla.github.io/uniffi-rs/) - UniFFI 使用指南

---

## 变更历史

| 日期 | 版本 | 变更内容 |
|------|------|---------|
| 2025-02-17 | v1.0 | 初始版本，记录 proc-macro 迁移 |
