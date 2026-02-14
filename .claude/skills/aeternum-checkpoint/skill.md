# ⚠️ Aeternum 任务检查点技能（强制前置步骤）

**版本**: 1.0.0
**作者**: Aeternum Team
**用途**: 在执行任务前验证相关文档已被阅读，确保不违反架构规范

---

## ⚠️ 强制要求

**这是所有代码修改任务的强制前置步骤！**

如果用户请求涉及以下任何操作，**必须先调用此技能**：
- ✅ 代码修改、重构
- ✅ 功能添加、新特性
- ✅ 架构调整、设计变更
- ✅ 协议实现、状态机

❌ **禁止直接开始编码！** 必须先完成 checkpoint 流程。

---

## 技能目标

本技能解决"文档被忽略"的问题，通过以下机制确保开发符合规范：

1. **任务分类** → 自动识别任务类型
2. **文档映射** → 确定需要阅读的文档
3. **阅读验证** → 确认关键章节已理解
4. **约束提醒** → 强调不可违反的约束

---

## 任务类型与文档映射

### 密码学任务 (crypto)
**触发关键词**: `KEM`, `Kyber`, `X25519`, `加密`, `哈希`, `KDF`, `密钥派生`, `zeroize`

**必读文档**:
| 文档 | 关键章节 | 检查点 |
|------|---------|--------|
| [架构白皮书 v5.0](../../docs/arch/Aeternum-architecture.md) | §3 密码学原语 | 确认使用的算法列表 |
| [数学不变量](../../docs/math/Formal-Invariants.md) | 全部 | 理解四大不变量 |
| [项目结构规范](../../docs/Aeternum-Project-Structure-Spec.md) | §3 core/src/crypto/ | 确认输出目录 |

**不可违反的约束**:
- ❌ 严禁在 Kotlin 层实现密码学逻辑
- ✅ 必须在 `core/src/crypto/` 实现
- ✅ 敏感数据结构必须实现 `Zeroize`

---

### 协议与状态机任务 (protocol)
**触发关键词**: `PQRR`, `纪元升级`, `epoch`, `影子包装`, `Device_0`, `否决`, `设备撤销`, `rekey`

**必读文档**:
| 文档 | 关键章节 | 检查点 |
|------|---------|--------|
| [架构白皮书 v5.0](../../docs/arch/Aeternum-architecture.md) | §4 设备管理 | 理解 PQRR 流程 |
| [同步协议规范](../../docs/protocols/Sync-Wire-Protocol.md) | 全部 | 理解 Aeternum Wire 帧 |
| [持久化与崩溃一致性](../../docs/protocols/Persistence-Crash-Consistency.md) | §2 影子写入 | 理解原子纪元升级协议 |
| [冷锚恢复协议](../../docs/protocols/Cold-Anchor-Recovery.md) | §3 否决机制 | 理解 48h 否决窗口 |
| [数学不变量](../../docs/math/Formal-Invariants.md) | INVARIANT_1, INVARIANT_4 | 理解纪元单调性和否决权优先 |
| [密钥生命周期状态机](../../docs/Android-Key-Lifecycle-State-Machine.md) | §3 状态转换 | 理解 Active/Rekeying/Revoked |

**不可违反的约束**:
- ❌ 禁止回滚 epoch
- ❌ 禁止 RECOVERY 角色执行 σ_rotate
- ✅ 必须通过影子写入实现原子更新
- ✅ 48h 内任何活跃设备的 Veto 必须终止恢复

---

### Android 安全层任务 (android)
**触发关键词**: `UI`, `Compose`, `生物识别`, `BiometricPrompt`, `StrongBox`, `KeyStore`, `Play Integrity`, `Degraded Mode`

**必读文档**:
| 文档 | 关键章节 | 检查点 |
|------|---------|--------|
| [架构白皮书 v5.0](../../docs/arch/Aeternum-architecture.md) | §5 Android 集成 | 理解安全边界 |
| [密钥生命周期状态机](../../docs/Android-Key-Lifecycle-State-Machine.md) | 全部 | 理解状态转换规则 |
| [UniFFI 桥接契约](../../docs/bridge/UniFFI-Bridge-Contract.md) | §3 安全边界 | 理解 Kotlin 层不可接触明文 |

**不可违反的约束**:
- ❌ 禁止 Kotlin 层持有明文密钥的 `ByteArray`
- ❌ 禁止在日志中记录密钥
- ✅ 必须使用 BiometricPrompt (Class 3) 认证
- ✅ 必须使用 Play Integrity API 验证设备完整性

---

### UniFFI 桥接任务 (bridge)
**触发关键词**: `UDL`, `UniFFI`, `FFI`, `桥接`, `Kotlin 接口`, `生成接口`

**必读文档**:
| 文档 | 关键章节 | 检查点 |
|------|---------|--------|
| [UniFFI 桥接契约](../../docs/bridge/UniFFI-Bridge-Contract.md) | 全部 | 理解接口定义规范 |
| [架构白皮书 v5.0](../../docs/arch/Aeternum-architecture.md) | §6 UniFFI 集成 | 理解桥接机制 |

**不可违反的约束**:
- ❌ 禁止手动修改生成的 Kotlin 代码
- ❌ 禁止通过 UDL 暴露明文密钥
- ✅ 修改 UDL 后必须重新生成桥接代码
- ✅ 敏感对象必须实现 `Zeroize`

---

### 架构与项目结构任务 (architecture)
**触发关键词**: `项目结构`, `目录`, `架构`, `模块划分`, `分层`

**必读文档**:
| 文档 | 关键章节 | 检查点 |
|------|---------|--------|
| [架构白皮书 v5.0](../../docs/arch/Aeternum-architecture.md) | 全部 | 理解整体架构 |
| [项目结构规范](../../docs/Aeternum-Project-Structure-Spec.md) | 全部 | 理解目录组织 |

**不可违反的约束**:
- ✅ 核心逻辑必须在 Rust 层实现
- ✅ UI 层（Kotlin）为非信任域
- ✅ Android Security Layer 仅持硬件密钥句柄

---

## 使用流程

### Step 1: 任务分类
根据用户请求中的关键词，识别任务类型。

### Step 2: 确定必读文档
根据上表确定需要阅读的文档列表。

### Step 3: 逐个验证
使用 Read 工具读取每个文档的关键章节，确认理解。

### Step 4: 约束确认
在开始编码前，明确列出不可违反的约束。

### Step 5: 执行任务
在遵守所有约束的前提下执行任务。

---

## 示例对话

### 用户请求
> "实现 PQRR 纪元升级逻辑"

### 检查点响应
1. **任务分类**: protocol 任务
2. **必读文档**:
   - [架构白皮书 v5.0](../../docs/arch/Aeternum-architecture.md) §4
   - [持久化与崩溃一致性](../../docs/protocols/Persistence-Crash-Consistency.md) §2
   - [数学不变量](../../docs/math/Formal-Invariants.md) INVARIANT_1
3. **读取并确认**... (使用 Read 工具)
4. **约束提醒**:
   - ✅ 必须通过影子写入实现原子更新
   - ❌ 禁止回滚 epoch
5. **开始实现**: 在确认所有约束后开始编码

---

## 集成到其他技能

本技能应作为其他 `aeternum:*` 技能的前置检查点：

- `aeternum:crypto` → 先执行 `aeternum:checkpoint` (crypto 分支)
- `aeternum:protocol` → 先执行 `aeternum:checkpoint` (protocol 分支)
- `aeternum:android` → 先执行 `aeternum:checkpoint` (android 分支)
- `aeternum:bridge` → 先执行 `aeternum:checkpoint` (bridge 分支)

---

## 检查点清单

在执行任何任务前，必须确认以下问题：

- [ ] 我已识别任务类型并确定必读文档
- [ ] 我已阅读所有必读文档的关键章节
- [ ] 我理解所有不可违反的约束
- [ ] 我知道输出文件应该放在哪个目录
- [ ] 我知道是否需要调用相关技能
- [ ] 我知道是否需要更新测试

---

## 相关技能

- `aeternum:crypto` - 密码学原语开发
- `aeternum:protocol` - 协议与状态机开发
- `aeternum:android` - Android 安全层开发
- `aeternum:bridge` - UniFFI 桥接管理
- `aeternum:invariant` - 不变量验证
