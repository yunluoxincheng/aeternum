--- START OF FILE Aeternum-Cold-Anchor-Recovery.md ---

Aeternum 规范文档
物理冷锚 (Device_0) 生成与离线恢复流程

文档编号：AET-RECOVERY-SPEC-005
版本：v1.0 (Implementation Baseline)
安全等级：Root-Authority Critical (最高等级)
核心目标：定义 24 位物理助记词如何转化为数学等价的“虚拟设备”，并在极端灾难下重建信任链。

1. 物理冷锚 (Device_0) 的逻辑定义

在 Aeternum 系统中，Device_0 是一个特殊的“幽灵设备”。

物理形式：24 位助记词（Master Root Seed, MRS）。

逻辑存在：在服务端的 DeviceHeaders 集合中，Device_0 占据一个固定槽位，其公钥由助记词派生。

不可区分性：在服务器看来，Device_0 的加密 Header 与普通硬件设备（Android StrongBox）生成的 Header 在格式、大小、算法上完全一致，无法通过扫描数据库识别出哪个是恢复锚点。

2. 派生算法 (Derivation Path)

系统使用 BLAKE3 结合上下文域隔离（Domain Separation）从 MRS 派生出恢复所需的密钥对。

2.1 派生步骤

助记词转化：将 24 位助记词通过 PBKDF2 (HMAC-SHA512, 2048 iterations) 转化为 512-bit 种子 S。

身份私钥 (IK_cold)：
IK_cold = BLAKE3_Derive(S, context="Aeternum_Identity_v1")

恢复私钥 (RK_cold)：
RK_cold = BLAKE3_Derive(S, context="Aeternum_Recovery_v1")

公钥生成：
PK_cold = ML-KEM-1024_Generate(RK_cold)

注：PK_cold 即为上传至云端的 Device_0 标识。

3. 影子包装 (Shadow Wrapping) 流程

每当系统执行 PQRR（纪元升级）时，活跃设备必须强制执行以下操作：

获取 PK_cold：从本地安全存储中读取（或在初次设置时由助记词生成）。

重封装：使用当前的 DEK_vNext 为 PK_cold 生成一个新的 DeviceHeader。

静默提交：将该 Header 随其他设备的 Header 一起提交至服务器。

结果：即使所有物理设备丢失，只要 MRS 存在，用户就能推导出 RK_cold，从而解开对应的 Header 获得 DEK。

4. 灾难恢复状态机 (Recovery State Machine)

恢复流程严禁“瞬间完成”，必须遵循 Invariant #4 (否决权优先)。

阶段 1：本地重建 (Local Reconstitution)

用户进入“恢复模式”，输入 24 位助记词。

Rust Core 执行派生算法，计算出 RK_cold。

App 尝试解密本地缓存的（或从云端拉取的）最新纪元 Header。

阶段 2：恢复请求 (Recovery Request)

App 向服务器发送一个经由 IK_cold 签名的 RECOVERY_INTENT。

服务器动作：

验证签名。

开启 48 小时冻结倒计时。

向所有 D_active 设备推送高优先级告警：“检测到助记词恢复请求，若非本人操作，请立即否决。”

阶段 3：否决窗口期 (Veto Window)

若在此期间任何一台活跃设备发送 VETO 信号，恢复流程立即作废，Device_0 进入 72 小时锁定限制。

阶段 4：根控制权转移 (Root Rotation)

48 小时结束且无否决。

服务器允许客户端提交新的 DK_hardware（新手机的硬件公钥）。

系统自动触发一次强制 PQRR：

废弃所有旧设备的 Header。

将新手机标记为 Primary Device。

完成“脱胎换骨”式的控制权更迭。

5. 离线演练不变量 (Drill Invariant)

为了防止用户在真正灾难发生时发现助记词失效，系统强制执行“模拟灾难演练”：

演练要求：每 180 天，App 提示用户进入只读演练模式。

校验逻辑：用户必须输入助记词，App 在本地验证派生出的 PK_cold 是否与当前 Vault 中 Device_0 的公钥匹配。

强制性：若演练未通过，系统禁止执行下一次 PQRR，并标记状态为 At Risk。

6. 威胁模型应对
威胁场景	防御机制	结果
助记词被盗	48h Veto 窗口 + 实时推送	用户有 48 小时时间使用在手设备更改根密钥或转移资产。
服务器端删库	本地 Vault 镜像 + 助记词	只要有加密的 Vault 文件和助记词，用户可以完全离线还原数据。
量子算力破解	ML-KEM-1024 (Kyber)	攻击者即使拥有量子计算机也无法从 Header 推导出 RK_cold。
强制劫持	延迟锁定 (Time-lock)	物理上的胁迫无法立即获得全库控制权。
7. 结论

《物理冷锚协议》是 Aeternum 最后的尊严。它确保了：

用户是最终的上帝：不依赖厂商，只依赖数学。

影子隐匿：恢复入口在平时是不可见的“噪声”。

时间换安全：通过 48 小时的时空屏障，消解了助记词这种“静态秘密”固有的脆弱性。

--- END OF FILE Aeternum-Cold-Anchor-Recovery.md ---