这是一份独立的形式化文档，定义了 Aeternum 系统底层的“物理定律”。该文档旨在为形式化验证（如 TLA+、ProVerif）和高保证工程实现提供唯一的数学准则。



Aeternum 系统形式化数学不变量 (Formal Invariants)



文件编号： AET-SPEC-MATH-001

版本： v1.0

状态： 强制性规范 (Normative Specification)

目标： 定义 Aeternum 状态空间中永不违背的逻辑约束。



1\. 定义与算子 (Notation \& Definitions)



在下列公式中，我们定义：



𝐷

𝑎

𝑐

𝑡

𝑖

𝑣

𝑒

D

active

&#x09;​



：当前宇宙中所有处于“活跃/未撤销”状态的设备集合（含影子冷锚 Device\_0）。



𝑆

𝑒

𝑝

𝑜

𝑐

ℎ

S

epoch

&#x09;​



：服务端记录的当前全局纪元版本号。



𝑒

𝑝

𝑜

𝑐

ℎ

(

𝑑

)

epoch(d)

：设备 

𝑑

d

&#x20;本地感知的纪元版本号。



𝑉

𝑒

.

𝐻

V

e

&#x09;​



.H

：纪元 

𝑒

e

&#x20;下 Vault 的加密 Header 集合。



𝐷

𝐸

𝐾

𝑒

DEK

e

&#x09;​



：纪元 

𝑒

e

&#x20;下唯一的合法数据加密密钥。



𝑢

𝑛

𝑤

𝑟

𝑎

𝑝

(

ℎ

,

𝑑

)

unwrap(h,d)

：设备 

𝑑

d

&#x20;使用其私钥解封 Header 

ℎ

h

&#x20;得到的原始密钥值。



𝑃

(

𝑆

)

P(S)

：会话 

𝑆

S

&#x20;的权限空间。



𝜎

𝑟

𝑜

𝑡

𝑎

𝑡

𝑒

σ

rotate

&#x09;​



：修改根控制权（Root Authority）的操作指令。



𝑉

𝑒

𝑡

𝑜

𝑒

𝑠

(

𝑟

𝑒

𝑞

)

Vetoes(req)

：针对特定恢复请求 

𝑟

𝑒

𝑞

req

&#x20;已接收到的有效否决信号集合。



2\. 核心不变量 (Core Invariants)

Invariant #1：纪元全局唯一性 (Temporal Uniqueness)



描述： 定义系统的时间箭头。在任何逻辑时刻，宇宙中只能有一个共同的“现在”。

公式：



∀

𝑑

1

,

𝑑

2

∈

𝐷

𝑎

𝑐

𝑡

𝑖

𝑣

𝑒

⟹

𝑒

𝑝

𝑜

𝑐

ℎ

(

𝑑

1

)

=

𝑒

𝑝

𝑜

𝑐

ℎ

(

𝑑

2

)

=

𝑆

𝑒

𝑝

𝑜

𝑐

ℎ

∀d

1

&#x09;​



,d

2

&#x09;​



∈D

active

&#x09;​



⟹epoch(d

1

&#x09;​



)=epoch(d

2

&#x09;​



)=S

epoch

&#x09;​





引理 1.1（禁止分叉）：

不存在两个并行的 DEK 纪元。若发生 

𝑒

𝑝

𝑜

𝑐

ℎ

(

𝑑

)

>

𝑆

𝑒

𝑝

𝑜

𝑐

ℎ

epoch(d)>S

epoch

&#x09;​



，则该状态被定义为非法的系统性崩溃。



Invariant #2：Header 空间完备性 (Spatial Completeness)



描述： 定义 DEK 的守恒分布。每一台活跃设备必须且仅能通过一个正确的入口获取当前的 DEK。

公式：



∀

𝑑

∈

𝐷

𝑎

𝑐

𝑡

𝑖

𝑣

𝑒

⟺

∃

!

ℎ

∈

𝑉

𝑒

.

𝐻

:

𝑢

𝑛

𝑤

𝑟

𝑎

𝑝

(

ℎ

,

𝑑

)

=

𝐷

𝐸

𝐾

𝑒

∀d∈D

active

&#x09;​



⟺∃!h∈V

e

&#x09;​



.H:unwrap(h,d)=DEK

e

&#x09;​





引理 2.1（撤销彻底性）：

若设备 

𝑑

d

&#x20;被移出 

𝐷

𝑎

𝑐

𝑡

𝑖

𝑣

𝑒

D

active

&#x09;​



，则在 

𝑉

𝑒

\+

1

.

𝐻

V

e+1

&#x09;​



.H

&#x20;中必须不存在任何 

ℎ

h

&#x20;满足 

𝑢

𝑛

𝑤

𝑟

𝑎

𝑝

(

ℎ

,

𝑑

)

=

𝐷

𝐸

𝐾

𝑒

\+

1

unwrap(h,d)=DEK

e+1

&#x09;​



。



Invariant #3：因果熵障 (Causal Entropy Barrier)



描述： 定义权力边界。信息读取能力（解密）不等于系统演化能力（管理）。

公式：



Role

(

𝑆

)

=

RECOVERY

⟹

𝜎

𝑟

𝑜

𝑡

𝑎

𝑡

𝑒

∉

𝑃

(

𝑆

)

Role(S)=RECOVERY⟹σ

rotate

&#x09;​



∈

/

P(S)



引理 3.1（权限提升阻断）：

状态机中不存在任何不经过“活跃设备共识 (Consensus)”而使 

𝑆

S

&#x20;从 RECOVERY 角色转化为 AUTHORIZED 角色的状态转换路径。



Invariant #4：否决权时空优先 (Veto Temporal Supremacy)



描述： 定义冲突坍缩方向。在安全与活跃度发生冲突时，系统强制向安全状态坍缩。

公式：



Status

(

𝑟

𝑒

𝑞

)

=

COMMITTED

⟹

(

𝑡

𝑛

𝑜

𝑤

≥

𝑇

𝑠

𝑡

𝑎

𝑟

𝑡

\+

Δ

𝑇

𝑤

𝑖

𝑛

𝑑

𝑜

𝑤

)

∧

(

𝑉

𝑒

𝑡

𝑜

𝑒

𝑠

(

𝑟

𝑒

𝑞

)

=

∅

)

Status(req)=COMMITTED⟹(t

now

&#x09;​



≥T

start

&#x09;​



+ΔT

window

&#x09;​



)∧(Vetoes(req)=∅)



引理 4.1（回溯效力）：

若在状态转换 

𝑠

𝑡

𝑎

𝑡

𝑢

𝑠

→

𝐶

𝑂

𝑀

𝑀

𝐼

𝑇

𝑇

𝐸

𝐷

status→COMMITTED

&#x20;发生前的一瞬，判定 

𝑉

𝑒

𝑡

𝑜

𝑒

𝑠

(

𝑟

𝑒

𝑞

)

≠

∅

Vetoes(req)



=∅

，则 

𝑠

𝑡

𝑎

𝑡

𝑢

𝑠

status

&#x20;必须强制坍缩为 

𝑅

𝐸

𝐽

𝐸

𝐶

𝑇

𝐸

𝐷

REJECTED

。



3\. 违反不变量的处理 (Violation Handling)



若系统在运行时（Runtime）检测到任何不变量被破坏（Assertion Failure），必须立即执行以下熔断指令：



内核锁定： 立即停止所有 DEK 解密操作，清除内存中的所有明文密钥。



状态隔离： 将当前同步状态标记为“异常分叉 (Fork Detected)”，停止与服务器的数据交换。



用户警示： 强制弹出高优先级风险警告，要求用户必须通过“物理冷锚 (Cold Anchor)”重新建立根信任。



4\. 形式化验证参考



Liveness Properties： 系统最终会达成 

𝑒

𝑝

𝑜

𝑐

ℎ

epoch

&#x20;升级。



Safety Properties： 系统永远不会泄露 

𝐷

𝐸

𝐾

𝑒

DEK

e

&#x09;​



&#x20;给 

𝐷

𝑎

𝑐

𝑡

𝑖

𝑣

𝑒

D

active

&#x09;​



&#x20;之外的任何主体。



Invariant Enforcement： 由 Rust Core 的 AeternumState 模块通过单态化函数强制执行上述检查。



数学模型封版人： Aeternum 系统科学组

日期： 2024-XX-XX

