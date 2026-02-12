

# **Aeternum 规范文档**

## **Android 端密钥生命周期与状态机实现规范**

**文档编号：AET-ANDROID-KL-SPEC-001**
**适用版本：Android 12+**
**安全级别：Root-of-Trust Critical**

---

# **1. 规范目标**

本规范定义 Android 客户端中：

* 密钥生命周期状态
* 状态转换规则
* 不变量强制点
* 崩溃恢复逻辑
* Rust ↔ Kotlin 安全职责边界

其目的是在物理设备上实现以下数学不变量：

| 不变量                                    | 物理含义           |
| -------------------------------------- | -------------- |
| **Invariant #1 — Epoch Monotonicity**  | 客户端永不接受旧纪元     |
| **Invariant #2 — Header Completeness** | Header 必须能成功解封 |
| **Invariant #3 — Atomic Rekey**        | 不允许混血 Vault    |
| **Invariant #4 — Veto Supremacy**      | 否决信号优先于恢复      |

---

# **2. 状态机总体模型**

状态机由 **Rust Core 驱动**，Kotlin 仅作为执行器。

```
Uninitialized → Initializing → Active → (Degraded | Revoked)
```

---

# **3. 状态定义**

## **3.1 Uninitialized**

设备尚未生成 DK_hardware，未注册。

| 条件        | 值   |
| --------- | --- |
| DK        | 不存在 |
| Header    | 不存在 |
| Epoch     | 0   |
| Vault Key | 不存在 |

**允许操作：**

* 生成 StrongBox 硬件密钥
* 初始化设备

---

## **3.2 Initializing**

设备完成硬件密钥生成，正在注册。

| 条件          | 值            |
| ----------- | ------------ |
| DK          | 已在 StrongBox |
| Attestation | 待上传          |
| Header      | 待生成          |

**进入条件：**

```
StrongBox KeyGen 成功
```

**退出条件（必须同时满足）：**

1. 服务端验证 Key Attestation
2. Rust 生成 DeviceHeader
3. Header 成功写入本地存储

否则 → 回滚到 Uninitialized。

---

## **3.3 Active（核心运行态）**

### 子状态

| 子状态        | 含义                |
| ---------- | ----------------- |
| Idle       | 无密钥在内存            |
| Decrypting | DEK/VK 在 Rust 内存中 |
| Rekeying   | 正在执行 PQRR         |

---

### 3.3.1 Idle

| 内存 | 无明文密钥 |
| -- | ----- |

触发事件：

| 事件           | 迁移           |
| ------------ | ------------ |
| 生物识别成功       | → Decrypting |
| 收到新 Header   | → Rekeying   |
| Integrity 失败 | → Degraded   |
| 撤销信号         | → Revoked    |

---

### 3.3.2 Decrypting

| 内存 | DEK/VK 仅存在 Rust 堆内 |
| -- | ------------------ |

**必须执行：**

* mlock 内存页
* 所有密钥类型实现 zeroize

退出：

| 事件      | 行为             |
| ------- | -------------- |
| UI 操作结束 | zeroize → Idle |
| App 后台  | 强制 zeroize     |

---

### 3.3.3 Rekeying（不变量 #1 + #3 落地）

这是最关键状态。

执行流程（严格顺序）：

1. Rust 校验 `new_epoch > current_epoch`
2. Rust 使用旧 DEK 解封 VK
3. Rust 派生新 DEKₙ₊₁
4. Rust 重封 VK
5. Kotlin 将新 Header 写入本地数据库
6. **写入成功回调 Rust**
7. Rust 更新 `current_epoch`

⚠️ 若第 5 步失败：

* 状态机保持在 Rekeying
* 禁止回到 Idle
* 等待重试
* 旧 DEK 仍有效

这保证：

> 永远不会出现 “Header 是新算法，但 VK 仍旧” 的混血状态

---

## **3.4 Degraded（安全降级态）**

进入条件：

* Play Integrity verdict ≠ STRONG

行为：

| 功能     | 状态 |
| ------ | -- |
| 解密完整数据 | ❌  |
| 导出     | ❌  |
| 恢复流程   | ❌  |
| 查看脱敏字段 | ✅  |

退出条件：

* 重新获得 STRONG verdict
  否则可进入 Revoked。

---

## **3.5 Revoked（终态）**

进入条件：

* PQRR 撤销
* Root Rotation 吊销
* 本地完整性锁定

执行：

```
delete StrongBox key
wipe SQLCipher
wipe cache
zeroize memory
```

不可逆。

---

# **4. 状态转换合法性表**

| From          | To           | 条件                |
| ------------- | ------------ | ----------------- |
| Uninitialized | Initializing | KeyGen 成功         |
| Initializing  | Active       | Header 写入成功       |
| Active        | Rekeying     | 收到新 Header        |
| Rekeying      | Active       | Header 写入成功       |
| Active        | Degraded     | Integrity fail    |
| Active        | Revoked      | 撤销指令              |
| Degraded      | Revoked      | 持续 Integrity fail |

---

# **5. Rust 强制不变量检查**

```rust
pub fn apply_epoch_upgrade(&mut self, header: Header) {
    assert!(header.epoch > self.current_epoch, "Epoch regression");

    let dek = unwrap_header(header)
        .expect("Header incomplete");

    self.rewrap_vault(dek);
    self.current_epoch = header.epoch;
}
```

---

# **6. 崩溃恢复策略**

如果 App 在 Rekeying 崩溃：

1. 启动时 Rust 读取 Header
2. 比较本地 Epoch 与 Header Epoch
3. 若 Header 更新但状态未提交 → 自动完成 rewrap

---

# **7. Kotlin 与 Rust 职责边界**

| 操作              | Kotlin | Rust |
| --------------- | ------ | ---- |
| StrongBox DK 生成 | ✅      | ❌    |
| Key Attestation | ✅      | ❌    |
| Header 存储       | ✅      | ❌    |
| DEK 解封          | ❌      | ✅    |
| VK 解密           | ❌      | ✅    |
| Epoch 判断        | ❌      | ✅    |

---

# **8. 安全保证结论**

该状态机保证：

* 设备无法回滚到旧密钥纪元
* PQRR 在任何中断下保持一致
* 攻击者无法通过 Hook 获得明文密钥
* 否决权逻辑在后台可执行

---

这份规范的级别相当于：

> **Android 客户端的“微内核安全调度器设计文档”**

---

