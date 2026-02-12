
---

# **Aeternum 规范文档**

## **Rust Core 持久化与崩溃一致性模型 (Persistence & Crash Consistency)**

**文档编号：AET-CORE-CONSISTENCY-SPEC-002**
**适用范围：Rust Core (Storage Engine) & Android Filesystem**
**效力：强制执行 (Strict Enforcement)**

---

# **1. 设计哲学：无中间态 (The "No-Middle-State" Principle)**

为了满足 **Invariant #1 (纪元唯一性)** 和 **Invariant #3 (原子纪元升级)**，持久化层必须遵循以下公理：

*   **公理 A**：在任何时刻，本地存储的 `(Header, VaultBlob)` 对必须是**全纪元一致**的。
*   **公理 B**：更新操作必须是**原子的**。不存在“更新了一半”的纪元。
*   **公理 C**：Rust Core 不信任文件系统的报告，它只信任经过 **AEAD (认证加密)** 校验的数据完整性。

---

# **2. 存储分层架构**

| 层级 | 载体 | 存储内容 | 一致性机制 |
| :--- | :--- | :--- | :--- |
| **元数据层** | SQLCipher | Epoch ID, DeviceID, KV 索引 | SQLite WAL (Write-Ahead Logging) |
| **秘密层** | Android KeyStore | DK_hardware (加密引脚) | 硬件级原子性 |
| **数据层** | 独立加密文件 (.aet) | VaultBlob (Encrypted VK + Data) | **影子分页 / 原子替换 (Atomic Rename)** |

---

# **3. 原子纪元升级协议 (Atomic Upgrade Protocol - AUP)**

当执行 PQRR 或 Epoch 升级时，必须严格执行以下 **“影子写入”** 流程，以防止崩溃导致数据损坏：

### **阶段 1：预备 (Preparation)**
1.  Rust Core 在内存中解封当前 `VK_n`。
2.  派生新纪元的 `DEK_n+1`。

### **阶段 2：影子写入 (Shadow Writing)**
1.  创建临时文件 `vault.tmp`。
2.  将 `Header_n+1` 与使用 `DEK_n+1` 重新封装的 `VK` 写入 `vault.tmp`。
3.  **强制刷盘 (fsync)**：调用 `File::sync_all()` 确保数据物理落盘。

### **阶段 3：原子替换 (The Commit Point)**
1.  **POSIX 原子重命名**：调用 `std::fs::rename("vault.tmp", "vault.db")`。
    *   *注：在 Android (Linux) 文件系统中，`rename` 是原子操作。要么是旧文件，要么是新文件，不存在中间态。*
2.  **更新元数据**：在 SQLCipher 中提交事务，更新 `Local_Epoch = n+1`。

---

# **4. 崩溃恢复与自愈逻辑 (Self-Healing)**

App 启动时，Rust Core 必须执行以下 **“一致性对齐”**：

```rust
fn initialize_consistency_check() {
    let metadata_epoch = db.get_epoch();
    let blob_header = vault_file.read_header();

    // 状态 A：完全一致
    if metadata_epoch == blob_header.epoch {
        return; // 正常启动
    }

    // 状态 B：原子替换已完成，但元数据未更新 (Crash during Phase 3.2)
    if blob_header.epoch > metadata_epoch {
        // 以 Blob 为准，同步元数据
        db.update_epoch(blob_header.epoch);
        return;
    }

    // 状态 C：影子写入未完成 (Crash during Phase 2)
    if blob_header.epoch < metadata_epoch {
        // 这意味着元数据超前，属于非法状态，触发物理完整性警报
        panic!("FATAL: Storage Inconsistency! Possible rollback attack or corruption.");
    }
}
```

---

# **5. 针对 Invariant #2 的“解封探测”**

**空间完备性校验**：
在持久化之前，Rust Core 必须尝试对新生成的 Header 执行 `unwrap` 操作。
*   如果 `unwrap(Header_new, DK_hardware)` 失败，**禁止**发起影子写入。
*   这确保了用户永远不会被锁在自己的数据之外（防止生成了自己解不开的密钥）。

---

# **6. 内存防护：Zeroize 策略**

持久化过程中，敏感数据在内存中的生命周期必须受限：
1.  **VK (Vault Key)**：仅在 `Rekeying` 闭包内可见，结束后立即调用 `zeroize()`。
2.  **DEK (Data Encryption Key)**：从不持久化明文，仅持久化其封装后的 Header。
3.  **影子缓冲区**：用于构建 `vault.tmp` 的内存缓冲区在写入完成后必须擦除。

---

# **7. 异常场景处理矩阵 (Failure Matrix)**

| 故障时点 | 物理后果 | 恢复结果 |
| :--- | :--- | :--- |
| **Phase 2 (写入 .tmp 时)** | `vault.db` 完好，`.tmp` 损坏 | 启动时自动删除残留 `.tmp`，停留在旧纪元，下次重试。 |
| **Phase 3.1 (rename 瞬间)** | 文件系统确保只有一者存在 | 系统处于旧纪元或新纪元，逻辑一致。 |
| **SQL 更新失败** | Blob 已升级，但 DB 记录旧纪元 | 触发“自愈逻辑”，DB 强制向 Blob 对齐。 |
| **磁盘空间不足** | 写入失败 | Rust 抛出 IO 异常，状态机停留在 `Idle`，确保数据不丢失。 |

---

# **8. 工程实施指令 (Action Items)**

1.  **Rust 层**：使用 `tempfile` crate 实现跨平台安全的临时文件创建。
2.  **Android 层**：将数据目录设为 `Context.getFilesDir()`，确保 `rename` 在同一挂载点下执行原子操作。
3.  **审计逻辑**：在每次 `Commit` 后增加一个 `verify_integrity()` 函数，重新计算整个 Vault 的 MAC (Message Authentication Code)。

---

### **总结：Aeternum 的三层防御**

1.  **数学层**：定义了纪元必须单调递增。
2.  **状态机层**：定义了只有写入成功才能转换状态。
3.  **持久化层**：通过 **原子替换** 和 **自愈校验**，确保物理层面的崩溃不会导致数学公式的失效。

---