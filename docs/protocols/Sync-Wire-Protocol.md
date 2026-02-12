--- START OF FILE Aeternum-Sync-Wire-Protocol.md ---

# **Aeternum 规范文档**

## **Aeternum 同步与通信协议规范 (The Aeternum Wire)**

**文档编号：AET-WIRE-SPEC-004**
**版本：v1.0 (Implementation Baseline)**
**安全等级：PQ-Secure, Zero-Knowledge Transmission**
**效力：强制执行 (Strict Enforcement)**

---

### **1. 设计哲学：服务器盲中继 (Server-as-Blind-Relay)**

Aeternum 协议遵循“服务器无知”原则：
1.  **零知识状态**：服务器仅作为逻辑状态机和加密 Blob 的存储桶，严禁感知设备拓扑、用户身份或纪元含义。
2.  **传输层解耦**：协议层不依赖 HTTPS 的安全性（尽管使用 HTTPS），而是通过应用层端到端（E2EE）混合加密确保安全性。
3.  **统计学隐匿**：通过固定长度填充（Padding）和诱饵流量，消除“影子包装 (Shadow Wrapping)”的流量指纹。

---

### **2. 通信原语与加密隧道**

#### **2.1 混合加密握手 (Hybrid Handshake)**
新设备配对或敏感信道建立时，必须使用混合抗量子方案：
*   **经典层**：X25519 (ECDH) 提供高性能经典安全。
*   **抗量子层**：ML-KEM-1024 (Kyber) 提供抗量子安全性。
*   **会话密钥派生**：
    `K_session = HKDF-SHA256(SharedSecret_X25519 || SharedSecret_Kyber || Context_ID)`

#### **2.2 消息封装格式 (The Wire Frame)**
所有通过物理网络传输的消息必须封装为固定大小的 **Aeternum-Frame**：

| 字段 | 长度 (Bytes) | 说明 |
| :--- | :--- | :--- |
| **Nonce** | 24 | XChaCha20-Poly1305 随机数 |
| **Epoch_ID** | 4 | 当前逻辑纪元版本（明文，用于路由） |
| **Payload_Type** | 1 | 消息类型（加密） |
| **Encrypted_Body**| Variable | 实际负载（加密） |
| **Padding** | Variable | 随机填充，确保总长度恒定为 **8192 Bytes** |
| **Auth_Tag** | 16 | Poly1305 认证标签 |

---

### **3. 核心协议流程**

#### **3.1 设备配对协议 (Device Pairing - "Handshake")**
用于将新设备加入 `D_active` 集合：
1.  **带外校验 (OOB)**：主设备生成包含 `Kyber_PK_A` 和 `Fingerprint` 的动态二维码。
2.  **隧道建立**：新设备扫描二维码，通过服务器中继发送自己的 `Kyber_Ciphertext`。
3.  **密钥分发**：主设备验证指纹后，在隧道内加密传输当前的 `DEK_e` 和 `Vault_Metadata`。
4.  **影子更新**：主设备发起一次小规模 PQRR，为新设备生成 `DeviceHeader` 并同步至服务器。

#### **3.2 全局纪元升级协议 (Global Epoch Sync - "Sync")**
当发生设备撤销或算法升级时：
1.  **触发阶段**：发起设备向服务器提交 `Rekey_Intent`。
2.  **广播阶段**：服务器向所有活跃设备推送 `Push_Notification(New_Epoch_Pending)`。
3.  **下载阶段**：设备拉取最新的 `Header_Set`。
4.  **校验阶段**：设备解封属于自己的 Header，若无法解封或指纹不符，拒绝升级并进入 `Degraded Mode`。
5.  **提交阶段**：50% 以上设备完成确认后，服务器将新 VaultBlob 标记为 `COMMITTED`。

#### **3.3 否决信号广播 (Veto Signaling)**
针对恢复流程的最高优先级信号：
*   **信号特征**：Veto 信号不经过普通同步队列，具有物理优先权。
*   **不可伪造性**：Veto 必须包含当前 `D_active` 集合中任意一台设备的物理硬件签名（StrongBox Signature）。
*   **冲突处理**：一旦服务器收到合法的 Veto 签名，必须立即逻辑删除当前的 `Recovery_Context`，并在全网广播 `RECOVERY_ABORTED`。

---

### **4. 流量指纹防护 (Traffic Obfuscation)**

为了满足 **附录 II (不可区分性流量防护)**，协议层实施以下策略：

1.  **Header Padding**：
    在 `Header_Set` 中，无论是真实设备还是 `Device_0`（物理助记词影子），其 Header 长度必须完全一致。
2.  **诱饵同步 (Chaff Sync)**：
    系统定期发起“空纪元升级”。服务器随机通知部分设备执行同步操作，但实际数据并未变更，以此掩盖真实 PQRR 的发生频率。
3.  **时序混淆**：
    服务器响应时间引入 50ms-200ms 的随机延迟，防止通过计时攻击（Timing Attack）推测解密难度或设备类型。

---

### **5. 异常处理与断线重连**

*   **幂等性**：所有同步指令必须包含 `Nonce`，服务器需检测重复指令以防止重放攻击。
*   **一致性对齐**：若客户端本地纪元 `e_local < e_server - 1`（落后超过一个版本），客户端必须强制触发“全量一致性校验”，通过解封最新的 Header 直接跳跃至最新纪元，禁止中间态重放。

---

### **6. 协议演进 (Versioning)**

协议头部包含版本号字段。若检测到协议版本不匹配：
*   **向后兼容**：旧版本客户端可以读取数据，但禁止发起 PQRR。
*   **强制升级**：若涉及安全漏洞修复，服务器可下发 `PROTOCOL_UPGRADE_REQUIRED` 指令，强制客户端更新后方可解密。

---

### **7. 结论**

Aeternum Wire 协议不仅是数据传输的管道，更是**分布式数学不变量的物理延伸**。它通过严格的填充策略和抗量子握手，确保了即使在服务器被完全攻破的情况下，攻击者也无法通过流量分析定位用户的恢复锚点或截获任何敏感状态。

--- END OF FILE Aeternum-Sync-Wire-Protocol.md ---