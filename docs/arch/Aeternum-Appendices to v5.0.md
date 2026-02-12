📜 Aeternum 协议不变量与工程宪法附录 (Appendices to v5.0)

生效日期： 架构冻结日
效力： 高于任何功能实现，任何违反以下附录的代码提交 (PR) 将被自动否决。

⚖️ 附录 I：纪元单调性法则 (The Law of Epoch Monotonicity)

核心：防止并发冲突与回滚攻击。

严格单调递增： 每一个生成的 DEK_vNext 必须伴随一个严格大于当前版本的 Epoch_n。

原子性提交： 服务器必须保证 Epoch 升级的原子性。若 Headers 写入成功但版本号更新失败，该次同步视为无效，客户端必须重试。

拒绝回滚： 任何客户端（包括 Cold Anchor）在同步时，若发现服务器 Epoch 低于本地已知的最高 Epoch，必须立即触发“本地完整性锁定”，防止降级攻击。

⚖️ 附录 II：不可区分性流量防护 (Indistinguishable Traffic Shield)

核心：消除 Shadow Wrapping 的统计学指纹。

Header Padding： 所有 DeviceHeader 的大小必须通过随机填充 (Padding) 保持一致，确保观察者无法通过数据包大小区分“真实硬件公钥”与“物理助记词影子”。

时序混淆： 针对 Device_0 的更新指令必须与其它活跃设备同步下发。若某次更新仅针对 Device_0，系统必须插入随机延迟或诱饵数据包，确保流量模式符合“多设备同步”的统计分布。

⚖️ 附录 III：否决权绝对优先级 (Veto Absolute Supremacy)

核心：安全优先于活跃性（Safety over Liveness）。

信号优先： 即使恢复倒计时（48h）已经结束，只要 Root Rotation（根控制权变更）操作尚未完成最终提交，任何抵达服务器的合法 Veto 信号必须拥有最高优先级，立即撤销该恢复流程。

离线宽限： 在恢复流程的最后 1 小时内，若系统检测到主活跃设备（Primary Device）处于离线状态，服务器可根据用户预设策略自动延长 6 小时窗口，确保“否决机会”的公平性。

⚖️ 附录 IV：全局原子纪元升级 (Global Atomic Epoch Upgrade)

核心：防止“混血 Vault”导致的永久损坏。

全链路一致： 一次 Crypto_Epoch 的升级必须包含 Headers（封装算法）与 Vault Key（包装算法）的同步提升。

禁止部分迁移： 禁止出现“用新算法封装 Headers，但保留旧算法加密 VK”的中间态。如果升级中断，系统必须回滚至上一个稳定 Epoch，确保数据在任何时刻都能被合法密钥全链路还原。

⚖️ 附录 V：访问权与控制权彻底解耦 (Separation of Access and Authority)

核心：限制 Cold Anchor 泄露后的灾难半径。

功能隔离： 助记词恢复流程仅能生成一个受限的 Access_Token（允许解密现有数据）。

二次验证： 任何涉及 Root_Rotation（修改助记词公钥、吊销所有设备）的指令，必须在 Access_Token 的基础上，额外满足以下条件之一：

(A) 原有受信任设备的生物识别签名同步。

(B) 社交阈值恢复节点的共识签名。

禁止提权： 绝不允许单纯通过助记词在 48 小时内瞬间完成“数据解密”与“管理员更替”的双重操作。

