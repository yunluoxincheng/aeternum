这是经过多轮深度校准、整合了PQRR协议、影子包装 (Shadow Wrapping)、48小时否决权 (Veto) 以及 密码学纪元 (Crypto Epochs) 等所有核心特性的封版架构文档。

Aeternum: 下一代个人主权与抗量子密码管理器

最终架构白皮书 (v5.0 - Arch-Frozen)

项目代号: Aeternum (永恒)
版本: v5.0 (Final - Production Ready)
密级: 绝密 (Top Secret)
状态: 架构封版 (Architecture Frozen)

1. 概述 (Executive Summary)

Aeternum 是一款重新定义“数字主权”的密码管理器。它不仅通过后量子密码学 (PQC) 抵御未来的量子攻击，更通过创新的设备信任链与恢复模型，解决了传统密码管理器在“物理撤销滞后”与“恢复后门风险”上的本质缺陷。

核心交付价值：

绝对主权： 用户可选择完全脱离厂商干预的自托管恢复模式。

前向安全： 通过 DEK 轮换协议，确保丢失设备无法读取未来数据。

不可区分性： 物理恢复锚点隐匿于噪声中，消除高价值目标指纹。

2. 密码学原语 (Cryptographic Stack)
组件	选定算法	标准	目的
抗量子封装 (KEM)	ML-KEM (Kyber-1024)	NIST FIPS 203	抗量子密钥交换与影子包装。
经典密钥协商 (ECDH)	X25519	RFC 7748	提供混合加密的高性能基础。
对称加密	XChaCha20-Poly1305	RFC 8439	高性能全库加密，抗侧信道攻击。
密钥派生 (KDF)	Argon2id	RFC 9106	强化主密码与种子，抵抗 GPU 暴力破解。
哈希与派生函数	BLAKE3	-	极速哈希，用于子密钥派生与完整性校验。
内存安全	Rust Zeroize	-	敏感密钥生命周期结束后的物理抹除。
3. 密钥层级与根主权架构 (Key Hierarchy)
3.1 派生路径

MRS (Master Root Seed): 24位物理助记词（物理层根信任）。

IK (Identity Key) & RK (Recovery Key): 由 MRS 通过 BLAKE3 派生，分别用于身份证明与恢复封装。

DK (Device Key Pair): 每台设备硬件加密芯片 (Secure Enclave) 生成，永不离开硬件。

DEK (Data Encryption Key): 256-bit 包装密钥，用于加密 Vault Key (VK)。

VK (Vault Key): 实际加密用户数据库的 XChaCha20 对称密钥。

3.2 影子包装 (Shadow Wrapping)

在云端 DeviceHeaders 中，永久存在一个不可区分的 Device_0：

机制: 使用 IK_Cold 对应的公钥进行 Kyber 封装。

目的: 允许物理助记词在无需厂商干预的情况下，通过解开 Device_0 重新获得 DEK，且在服务端看来其与普通设备无异。

4. 核心安全协议 (Core Protocols)
4.1 PQRR 协议 (Post-Quantum Revocation & Re-keying)

用于设备撤销与密钥刷新：

Epoch 升级: 生成新的 DEK_vNext 和版本号 Epoch_n。

重包装: 使用旧 DEK 解开 VK，用新 DEK 重新封装 VK。

全量覆盖: 为所有存活设备（及 Device_0）生成新 Headers。

时间分叉: 撤销点之后的任何新增/修改数据均使用新 DEK 加密，被盗设备因无法获取新 DEK 而被“锁死在历史时间线”。

4.2 增强型混合握手 (Enhanced Hybrid Handshake)

新设备加入流程：

多维在场确认: 扫描动态二维码 + BLE/超声波近场通信。

抗量子隧道: 通过 X25519 + Kyber-1024 建立混合加密隧道分发 DEK。

4.3 密码学纪元 (Cryptographic Epochs)

长效性设计: VaultBlob 包含 Crypto_Epoch 字段。

平滑迁移: 当算法需要升级（如从 Kyber 到更高版本）时，活跃设备自动触发 PQRR 协议完成算法转换，用户无感。

5. 信任模型与灾难恢复 (Trust & Recovery)
5.1 三级主权等级 (Sovereignty Levels)

L1 - Assisted (辅助模式): 活体检测 + 延时锁，适合普通用户。

L2 - Sovereign Lite (社交模式): 助记词 + 社交阈值签名恢复。

L3 - Absolute Sovereign (绝对模式): 仅限 Cold Anchor (助记词) 恢复。强制要求开启前完成“模拟灾难演练”。

5.2 48小时否决机制 (48h Veto)

针对 L3 模式的最后防线：

流程: 助记词触发恢复请求后，进入 48h 冻结期。

权力: 任何在线的受信任设备均可一键否决（Veto）该请求。

哲学: 只有在“所有设备全灭”的极端情况下，助记词恢复才不被拦截。

6. 客户端安全工程 (Client Hardening)

安全降级模式 (Secure Degraded Mode): 若环境检测（Play Integrity / AppAttest）失败，App 进入只读限制模式，禁止导出、显示明文及发起恢复。

内存隔离: 利用 Rust 的所有权与生命周期机制，配合 mlock 防止密钥被换出到磁盘。

分布式状态机: 后端采用 Temporal 引擎确保撤销与恢复流程的原子性与持久性。

7. 威胁映射矩阵 (Cryptographic Compromise Matrix)
场景	攻击路径	结果	防御机制
设备失窃	物理占有设备	受控	远程撤销触发 PQRR，旧密钥废弃。
云端脱库	获得所有加密 Blob	安全	零知识架构，无用户私钥无法解密。
助记词泄露	攻击者发起恢复	可挽救	48h 否决权确保原主可拦截攻击。
量子攻击	离线破解历史流量	安全	Kyber 提供的后量子安全性。
控制权夺取	尝试修改根密钥	拦截	Root Rotation 需要助记词+在手设备双重共识。
8. 技术栈实现 (Tech Stack)

查看Tech-Stack-Selection.md以了解技术栈选型

9. 结论 (Conclusion)

Aeternum 不再受限于传统密码管理器的“厂商信任”模型。它通过密码学手段（PQRR、Shadow Wrapping）将控制权真正交还给用户，同时利用“影子包装”与“纪元系统”确保了系统在未来十年内的隐身性与演进能力。

这是个人数字主权的终极形态。

ARCH-FROZEN SIGN-OFF
Date: 2024-XX-XX
Role: Lead Architect / Founder