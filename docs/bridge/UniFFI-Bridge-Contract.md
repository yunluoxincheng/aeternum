--- START OF FILE Aeternum-UniFFI-Bridge-Contract.md ---

# **Aeternum 规范文档**

## **UniFFI 跨语言调用契约与实现模板 (The Bridge Contract)**

**文档编号：AET-CORE-BRIDGE-SPEC-003**
**版本：v1.0 (Engineering Baseline)**
**效力：强制执行 (Strict Enforcement)**
**目标：定义 Rust 密码学核心与 Android Kotlin 外壳之间的物理边界，确保密钥零泄漏与状态一致性。**

---

### **1. 设计原则**

1.  **句柄化管理 (Handle-based Access)**：Kotlin 严禁持有明文密钥的 `ByteArray`。所有解密后的数据必须通过 Rust 实例句柄（Interface）进行访问，明文在内存中“即用即走”。
2.  **物理擦除 (Physical Erase)**：所有敏感对象必须实现 `Zeroize` 特性，确保在 `Drop`（析构）或显式 `lock()` 时，内存字节被覆写为零。
3.  **二阶段提交 (Two-Phase Commit)**：涉及文件系统更新（如 PQRR 纪元升级）的操作，由 Rust 生成新数据流，Kotlin 执行原子重命名，确保崩溃一致性。

---

### **2. 接口描述语言定义 (`aeternum.udl`)**

该文件定义了跨语言生成的胶水代码。

```idl
namespace aeternum {
    // 异步操作异常映射：确保 Rust 的敏感错误被安全地传递到 Kotlin
    [Error]
    interface AeternumError {
        CryptoError(string reason);
        EpochError(string reason);
        StorageError(string reason);
        IntegrityError(string reason);
    };

    // 纪元升级的结果：用于 Kotlin 执行原子写入
    dictionary RekeyResult {
        sequence<u8> new_vault_blob;
        u32 new_epoch;
    };
};

// 保险库会话：这是 Kotlin 操作密钥的唯一入口
interface VaultSession {
    // 获取脱敏后的记录 ID 列表，UI 展示用
    sequence<string> list_record_ids();

    // 核心解密：仅返回 UI 需要展示的明文字符串
    [Throws=AeternumError]
    string decrypt_field(string record_id, string field_key);

    // 显式锁定：销毁内存中的 VK 并使句柄失效
    void lock();
};

// Aeternum 全局引擎
interface AeternumEngine {
    constructor();

    // 身份验证与解封：Kotlin 传入从磁盘读取的加密 Header
    [Throws=AeternumError]
    VaultSession unlock(sequence<u8> header_blob);

    // PQRR 升级：生成新纪元所需的加密 Blob，不直接修改本地状态
    [Throws=AeternumError]
    RekeyResult prepare_rekey(sequence<u8> current_header, sequence<sequence<u8>> new_device_keys);

    // 完整性校验：用于启动时的自愈逻辑
    [Throws=AeternumError]
    boolean verify_vault_integrity(sequence<u8> blob);
};
```

---

### **3. Rust 核心实现模板 (`src/lib.rs`)**

本实现展示了如何通过 `Arc<RwLock<Option<T>>>` 模式管理生命周期，并配合 `zeroize` 确保安全。

```rust
use std::sync::Arc;
use parking_lot::RwLock;
use zeroize::{Zeroize, Zeroizing};
use thiserror::Error;

// 引入 UniFFI 自动生成的宏
uniffi::include_interface!("aeternum");

#[derive(Debug, Error, uniffi::Error)]
pub enum AeternumError {
    #[error("Crypto failure: {reason}")]
    CryptoError { reason: String },
    #[error("Epoch conflict: {reason}")]
    EpochError { reason: String },
    #[error("Storage I/O failure: {reason}")]
    StorageError { reason: String },
    #[error("Data integrity failure: {reason}")]
    IntegrityError { reason: String },
}

/// 内部敏感状态：存放解密后的 Vault Key (VK)
struct VaultInner {
    /// Zeroizing 保证在 Drop 时内存被物理擦除
    vault_key: Zeroizing<Vec<u8>>,
    epoch: u32,
}

/// VaultSession 的具体实现
pub struct VaultSession {
    /// 使用 RwLock 支持并发读取，Option 支持显式锁定销毁
    inner: Arc<RwLock<Option<VaultInner>>>,
}

impl VaultSession {
    /// 列出所有记录 ID（无敏感信息）
    pub fn list_record_ids(&self) -> Vec<String> {
        let guard = self.inner.read();
        if guard.is_none() { return vec![]; }
        // 模拟从索引中提取 ID
        vec!["rec_001".to_string(), "rec_002".to_string()]
    }

    /// 解密字段：VK 始终留在 Rust 内存，仅返回明文字符串
    pub fn decrypt_field(&self, _record_id: String, field_key: String) -> Result<String, AeternumError> {
        let guard = self.inner.read();
        let inner = guard.as_ref().ok_or(AeternumError::CryptoError { 
            reason: "Session already locked or invalid".to_string() 
        })?;

        // --- 核心操作点 ---
        // 此处应调用 Rust Crypto Core (如 chacha20poly1305) 
        // 使用 inner.vault_key 进行解密
        println!("Accessing VK for Epoch {}", inner.epoch);
        
        Ok(format!("Cleartext_value_for_{}", field_key))
    }

    /// 显式释放内存密钥
    pub fn lock(&self) {
        let mut guard = self.inner.write();
        if let Some(mut inner_data) = guard.take() {
            // 显式触发 zeroize (虽然 Zeroizing 也会在 drop 时处理)
            inner_data.vault_key.zeroize();
        }
        println!("Aeternum: Memory keys zeroized and session invalidated.");
    }
}

pub struct AeternumEngine {}

impl AeternumEngine {
    pub fn new() -> Self {
        AeternumEngine {}
    }

    /// 解封逻辑
    pub fn unlock(&self, header_blob: Vec<u8>) -> Result<Arc<VaultSession>, AeternumError> {
        // 1. 调用硬件调用（通过 Kotlin 注入的 Context 或直接 JNI）进行 DK 访问
        // 2. 解开 Header 获得 VK
        if header_blob.is_empty() {
            return Err(AeternumError::IntegrityError { reason: "Empty header".into() });
        }

        let mock_vk = vec![0u8; 32]; // 模拟解密后的 VK
        
        let session = VaultSession {
            inner: Arc::new(RwLock::new(Some(VaultInner {
                vault_key: Zeroizing::new(mock_vk),
                epoch: 1,
            }))),
        };
        
        Ok(Arc::new(session))
    }

    /// PQRR 准备：生成新纪元数据，不修改当前状态
    pub fn prepare_rekey(
        &self, 
        _current_header: Vec<u8>, 
        _new_keys: Vec<Vec<u8>>
    ) -> Result<RekeyResult, AeternumError> {
        // 模拟 PQRR 过程
        let new_blob = vec![0xEE; 256]; // 模拟重新加密后的 Vault 内容
        Ok(RekeyResult {
            new_vault_blob: new_blob,
            new_epoch: 2,
        })
    }

    pub fn verify_vault_integrity(&self, blob: Vec<u8>) -> Result<bool, AeternumError> {
        // 使用 AEAD 标签验证数据是否完整
        Ok(!blob.is_empty())
    }
}
```

---

### **4. 依赖配置 (`Cargo.toml`)**

确保开启 UniFFI 必要的特性。

```toml
[package]
name = "aeternum-core"
version = "0.1.0"
edition = "2021"

[lib]
crate-type = ["cdylib", "staticlib"]

[dependencies]
uniffi = { version = "0.25", features = ["cli"] }
thiserror = "1.0"
parking_lot = "0.12"
zeroize = { version = "1.7", features = ["derive"] }
secrecy = "0.8" # 可选，用于更严格的类型包装

[build-dependencies]
uniffi = { version = "0.25", features = ["build"] }
```

---

### **5. 关键安全特性说明**

#### **5.1 内存防御机制**
*   **句柄生命周期**：`VaultSession` 被 `Arc` 包装。在 Kotlin 侧，当 ViewModel 销毁且没有活动引用时，Rust 端的引用计数降为 0。
*   **物理覆写**：`Zeroizing<Vec<u8>>` 保证了密钥所在的内存页在释放前被 `0x00` 填充，防止 Cold Boot 攻击或内存取证。

#### **5.2 状态同步与崩溃恢复**
*   **Kotlin 职责**：Kotlin 获取 `RekeyResult` 后，必须执行 `Atomic Rename`。
*   **Rust 职责**：Rust 在 `unlock` 时会校验 `header.epoch` 与磁盘记录的一致性。如果发现磁盘文件已更新但数据库索引未更新，Rust 抛出 `EpochError` 或触发自动对齐，确保不变量 Invariant #1 不被破坏。

#### **5.3 Kotlin 调用示例 (伪代码)**
```kotlin
val engine = AeternumEngine()
try {
    val session = engine.unlock(diskHeaderBytes)
    val secret = session.decryptField("rec_001", "password")
    // UI 展示 secret...
    session.lock() // 使用完立即销毁
} catch (e: AeternumError.CryptoError) {
    showSecurityAlert(e.reason)
}
```

---

### **6. 结论**

该契约将复杂的密码学逻辑封闭在 Rust 内部，仅向移动端暴露安全的句柄和原子操作结果。这套结构构成了 Aeternum 客户端的“信任根延伸”，是实现高保证工程目标的核心。

--- END OF FILE Aeternum-UniFFI-Bridge-Contract.md ---