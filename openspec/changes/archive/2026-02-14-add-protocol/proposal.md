# Proposal: PQRR 协议状态机实现

**Change ID**: `add-protocol`
**Status**: Proposed
**Created**: 2025-02-14
**Author**: Aeternum Protocol Team
**Type**: Feature
**Priority**: P0 (Critical Path)

---

## Executive Summary

实现 Aeternum 核心协议层 - PQRR (Post-Quantum Revocation & Re-keying) 状态机，这是系统安全的最后防线。该模块强制执行四大数学不变量，确保设备撤销、密钥轮换和恢复流程的原子性和前向安全性。

**核心价值**:
- **前向安全保证**: 被盗设备被"锁死在历史时间线"，无法解密未来数据
- **不变量强制执行**: 在 Rust 层通过类型系统和断言确保数学约束不被破坏
- **原子纪元升级**: 通过影子写入实现崩溃一致的密钥轮换
- **否决权优先**: 48h 窗口内任何活跃设备的 Veto 立即终止恢复流程

---

## Background & Motivation

### 当前状态

Aeternum Rust Core 已完成:
- ✅ **crypto/** - 密码学原语 (Kyber-1024, X25519, XChaCha20, Argon2id, BLAKE3)
- ✅ **models/** - 数据模型 (密钥层级, 纪元, 设备, Vault)
- ✅ **storage/** - 存储引擎 (影子写入, 崩溃恢复, 不变量验证)
- ✅ **sync/** - Aeternum Wire 协议层 (帧封装, 握手, 流量混淆)

### 缺失部分

```
core/src/protocol/  (不存在)
├── pqrr.rs          # PQRR 协议状态机 ⚠️
├── recovery.rs      # 否决窗口与恢复协议 ⚠️
├── device_mgmt.rs   # 设备管理生命周期 ⚠️
└── epoch_upgrade.rs # 纪元升级协调 ⚠️
```

**影响**:
1. **无法执行设备撤销** - 被盗设备仍可解密新数据 (违反不变量 #2)
2. **无法强制纪元单调性** - Epoch 可能被回滚 (违反不变量 #1)
3. **无法执行否决权** - 48h Veto 窗口无实现 (违反不变量 #4)
4. **无法阻止权限提升** - RECOVERY 角色可能执行 σ_rotate (违反不变量 #3)

### 为什么现在实现？

1. **密码学基础已就绪** - crypto/ 和 storage/ 已完成
2. **数据模型已冻结** - models/ 提供了所需的类型定义
3. **同步协议已实现** - sync/ 提供了设备间通信的基础
4. **安全依赖链完整** - protocol/ 是安全栈的最后一块拼图

---

## Proposed Solution

### 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│              Rust Core 模块依赖关系                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  │
│  │   crypto/   │  │   models/   │  │  storage/   │  │
│  │ (密码学原语) │  │  (数据模型)  │  │ (存储引擎)   │  │
│  └─────────────┘  └─────────────┘  └─────────────┘  │
│         │                  │                  │         │
│         └──────────────────┼──────────────────┘         │
│                            ↓                            │
│                    ┌─────────────┐                       │
│                    │  protocol/  │ ← 本次实现            │
│                    │  (协议状态机)│                       │
│                    └─────────────┘                       │
│                            ↓                            │
│                    ┌─────────────┐                       │
│                    │    sync/    │                       │
│                    │  (同步协议)  │                       │
│                    └─────────────┘                       │
└─────────────────────────────────────────────────────────────┘
```

### 核心数据结构

```rust
/// PQRR 状态机核心结构
pub struct PqrrStateMachine {
    /// 当前纪元版本号 (不变量 #1: 纪元单调性)
    pub current_epoch: u32,

    /// 当前协议状态
    pub state: ProtocolState,

    /// 所有活跃设备的 Header (不变量 #2: Header 完备性)
    pub device_headers: HashMap<DeviceId, DeviceHeader>,

    /// 否决信号记录 (不变量 #4: 否决权优先)
    pub veto_signals: HashMap<RecoveryRequestId, Vec<VetoMessage>>,

    /// 恢复窗口跟踪 (48h 否决窗口)
    pub recovery_windows: HashMap<RecoveryRequestId, RecoveryWindow>,
}

/// 协议状态枚举
pub enum ProtocolState {
    /// 空闲状态 - 无操作进行
    Idle,

    /// 纪元升级中 (不变量 #1 + #3 执行点)
    Rekeying(RekeyingContext),

    /// 恢复已发起 (等待 48h 否决窗口)
    RecoveryInitiated(RecoveryContext),

    /// 完整性降级 (Invariant #2 检查失败)
    Degraded,

    /// 设备已撤销 (Invariant #2 清理完成)
    Revoked,
}

/// 纪元升级上下文
pub struct RekeyingContext {
    /// 旧纪元版本
    pub old_epoch: u32,

    /// 新纪元版本
    pub new_epoch: u32,

    /// 待处理的设备队列
    pub pending_devices: Vec<DeviceId>,

    /// 已完成的设备集合
    pub completed_devices: HashSet<DeviceId>,

    /// 影子写入临时文件路径
    pub temp_vault_path: PathBuf,
}

/// 恢复窗口上下文
pub struct RecoveryWindow {
    /// 恢复请求 ID
    pub request_id: RecoveryRequestId,

    /// 窗口开始时间
    pub start_time: SystemTime,

    /// 窗口结束时间 (start_time + 48h)
    pub end_time: SystemTime,

    /// 发起设备的角色 (只能是 AUTHORIZED)
    pub initiator_role: Role,

    /// 已收到的否决信号
    pub vetoes: Vec<VetoMessage>,
}
```

### 状态转换图

```
                    ┌─────────────────────────────┐
                    │       Idle (空闲)         │
                    └─────────────────────────────┘
                              │
          ┌─────────────────┼─────────────────┐
          │                   │                 │
          │                   │                 │
    ┌───▼────┐      ┌────▼─────┐    ┌───▼─────┐
    │ Rekeying │      │ Recovery  │    │ Degraded │
    │ (升级)  │      │ (恢复)   │    │ (降级)   │
    └────┬────┘      └────┬─────┘    └───┬─────┘
         │                   │                 │
         │                   │                 │
    ┌───▼────┐      ┌────▼─────┐    ┌───▼─────┐
    │  Idle   │      │  Revoked  │    │ Revoked  │
    └─────────┘      └───────────┘    └─────────┘

    触发条件:
    - Rekeying: 收到新 Header 且 epoch > current_epoch
    - Recovery: 收到恢复请求且角色 = AUTHORIZED
    - Degraded: 完整性验证失败
    - Revoked: 撤销指令或 Root Rotation
```

### 不变量强制执行点

| 不变量 | 执行函数 | 熔断条件 | 熔断后动作 |
|--------|----------|----------|------------|
| #1 纪元单调性 | `apply_epoch_upgrade()` | `new_epoch <= current_epoch` | 熔断崩溃, 状态隔离 |
| #2 Header 完备性 | `validate_header_completeness()` | 设备无 Header 或多个 Header | 熔断崩溃, 状态隔离 |
| #3 因果熵障 | `execute_rotation()` | 角色 = RECOVERY 且尝试 σ_rotate | 返回 PermissionDenied |
| #4 否决权优先 | `check_veto_supremacy()` | 48h 内收到 Veto | 立即终止恢复流程 |

---

## Alternatives Considered

### 方案 A: 在 Kotlin 层实现协议

**优点**:
- 与现有 Android 状态机集成更直接

**缺点**:
- ❌ 违反安全边界 - 密码学逻辑不应在 Kotlin 层
- ❌ 无法强制执行不变量 - Kotlin 层无法访问 Rust 内存
- ❌ 增加 FFI 开销 - 每次状态转换需要跨语言调用
- ❌ 测试覆盖困难 - Kotlin 单元测试无法验证密钥内存

**决策**: ❌ 拒绝 - 安全性不可妥协

### 方案 B: 使用外部状态机库 (如状态机编译器)

**优点**:
- 可能获得更好的性能优化
- 减少手写代码

**缺点**:
- ❌ 增加外部依赖
- ❌ 难以精确匹配四大数学不变量的形式化定义
- ❌ 增加代码审查复杂度

**决策**: ❌ 拒绝 - 简单直接的手写状态机更可验证

### 方案 C: 在 Rust Core 内实现 (选定方案)

**优点**:
- ✅ 完全控制内存安全 - 密钥在 Rust 堆内
- ✅ 精确强制执行不变量 - 通过 Rust 类型系统
- ✅ 与现有模块零拷贝集成 - 借用所有权系统
- ✅ 易于测试 - proptest 可验证状态转换属性

**缺点**:
- 需要仔细设计状态转换逻辑

**决策**: ✅ 采纳 - 安全性和可验证性优先

---

## Impact Analysis

### 对现有模块的影响

#### `crypto/` - 无影响
- 密码学原语不依赖协议层
- protocol/ 将是 crypto/ 的消费者

#### `models/` - 无影响
- 数据模型已冻结
- protocol/ 将使用现有类型定义

#### `storage/` - 集成点
- protocol/ 将调用 `storage::aug::*` 进行原子纪元升级
- protocol/ 将使用 `storage::invariant::InvariantValidator` 验证不变量

#### `sync/` - 集成点
- protocol/ 将通过 `sync::wire::*` 发送否决信号
- protocol/ 将解析 `sync::frame::*` 接收恢复请求

### 对 Android 层的影响

#### 无需修改 Kotlin 代码
- protocol/ 在 Rust 层实现
- UniFFI 自动生成必要的 FFI 接口

#### 需更新 UniFFI UDL
- 导出 `PqrrStateMachine` 及其方法
- 导出相关错误类型

### 对测试策略的影响

#### 新增测试类型
- **状态转换测试**: 验证所有合法转换路径
- **不变量违规测试**: 验证熔断机制
- **属性测试**: 使用 proptest 验证状态机性质

---

## Implementation Plan

### Phase 1: 核心状态机 (Week 1)

**文件**: `core/src/protocol/pqrr.rs`

**任务**:
1. 实现 `PqrrStateMachine` 核心结构
2. 实现状态转换逻辑 (Idle ↔ Rekeying/Degraded/Revoked)
3. 实现不变量 #1 (纪元单调性) 强制执行
4. 添加单元测试和属性测试

**验证标准**:
- 所有状态转换可通过单元测试
- `proptest` 验证状态机性质 (无死锁, 无非法转换)
- 不变量 #1 熔断测试通过

### Phase 2: 恢复协议与否决权 (Week 2)

**文件**: `core/src/protocol/recovery.rs`

**任务**:
1. 实现 `RecoveryWindow` 跟踪
2. 实现不变量 #4 (否决权优先) 强制执行
3. 实现 48h 窗口管理逻辑
4. 添加恢复流程集成测试

**验证标准**:
- 48h 内 Veto 信号立即终止恢复
- 超过 48h 窗口后恢复可完成
- `proptest` 验证时间窗口性质

### Phase 3: 设备管理生命周期 (Week 2)

**文件**: `core/src/protocol/device_mgmt.rs`

**任务**:
1. 实现设备注册逻辑
2. 实现不变量 #2 (Header 完备性) 验证
3. 实现设备撤销清理逻辑
4. 添加设备管理集成测试

**验证标准**:
- 每个设备有且仅有一个有效 Header
- 撤销后设备无法解密新数据
- `proptest` 验证 Header 完备性

### Phase 4: 纪元升级协调 (Week 3)

**文件**: `core/src/protocol/epoch_upgrade.rs`

**任务**:
1. 实现原子纪元升级协议 (AUP)
2. 实现不变量 #3 (因果熵障) 强制执行
3. 集成 `storage::aug::*` 影子写入
4. 添加 AUP 崩溃恢复测试

**验证标准**:
- Epoch 升级过程中断电后可恢复
- RECOVERY 角色无法执行 σ_rotate
- `proptest` 验证 AUP 原子性

### Phase 5: UniFFI 集成 (Week 3)

**文件**: `core/uniffi/aeternum.udl`

**任务**:
1. 导出 `PqrrStateMachine` 及其方法
2. 导出相关错误类型
3. 重新生成桥接代码
4. 更新 Kotlin 测试

**验证标准**:
- Kotlin 可调用 Rust 协议层方法
- FFI 开销 < 100ms (UI 响应时间)
- 跨语言不变量验证通过

---

## Risk Assessment

### 高风险项

#### 风险 1: 状态机死锁

**描述**: 状态转换逻辑错误导致状态机进入无法转换的中间状态

**缓解措施**:
- 使用 `proptest` 进行穷举状态转换测试
- 添加状态转换超时机制
- 实现状态机调试日志

**应急计划**:
- 添加强制状态重置 API (仅用于开发调试)

#### 风险 2: 不变量执行遗漏

**描述**: 某些代码路径绕过不变量检查

**缓解措施**:
- 在模块入口处强制执行不变量
- 使用 Rust 类型系统封装状态转换
- 100% 代码覆盖率要求

**应急计划**:
- 运行时熔断机制触发后进入 Degraded 模式

### 中风险项

#### 风险 3: 性能回退

**描述**: 状态转换开销影响 UI 响应时间

**缓解措施**:
- 将耗时操作移至后台线程
- 使用 `criterion` 进行性能基准测试
- 异步处理非关键路径操作

**应急计划**:
- 降级到简化状态转换逻辑

### 低风险项

#### 风险 4: 测试覆盖不足

**描述**: 某些边缘情况未被测试覆盖

**缓解措施**:
- 使用 `proptest` 进行属性测试
- 代码覆盖率要求 100%
- 添加模糊测试 (fuzzing)

**应急计划**:
- 补充遗漏的测试用例

---

## Success Metrics

### 功能指标

| 指标 | 目标 | 测量方法 |
|------|------|----------|
| 状态转换成功率 | 100% | 单元测试通过率 |
| 不变量违规检测率 | 100% | 熔断测试通过率 |
| 设备撤销完整性 | 100% | 集成测试通过率 |
| 恢复否决响应时间 | < 1s | 性能测试 |

### 非功能指标

| 指标 | 目标 | 测量方法 |
|------|------|----------|
| 代码覆盖率 | 100% | `cargo tarpaulin` |
| 状态转换开销 | < 10ms | `criterion` 基准测试 |
| 内存开销 | < 1MB | 堆分析器 |
| FFI 开销 | < 100ms | Android 性能测试 |

---

## Open Questions

1. **Q**: 是否需要支持并行的多个恢复窗口？
   - **A**: 当前设计不支持。若需要支持，需修改 `RecoveryWindow` 为多实例管理。

2. **Q**: 状态机是否需要持久化到磁盘？
   - **A**: 当前设计不持久化。状态信息可从 `current_epoch` 和 `device_headers` 重建。若需要持久化，需添加 `protocol::state::*` 模块。

3. **Q**: 是否需要支持部分设备离线时的 PQRR？
   - **A**: 是的。当前设计支持异步设备更新，`RekeyingContext` 跟踪 `pending_devices`。

---

## Future Considerations

### FC-01: 状态机持久化

**当前设计**: 状态机不持久化到磁盘，状态信息可从 `current_epoch` 和 `device_headers` 重建。

**未来扩展**: 若需要持久化，考虑添加 `protocol::state::*` 模块：

```rust
// core/src/protocol/state/mod.rs
pub struct StateMachineSnapshot {
    pub current_epoch: u32,
    pub state: ProtocolState,
    pub device_headers: HashMap<DeviceId, DeviceHeader>,
    pub recovery_windows: HashMap<RecoveryRequestId, RecoveryWindow>,
    pub last_snapshot_time: SystemTime,
}

impl StateMachineSnapshot {
    pub fn save(&self, path: &Path) -> Result<(), ProtocolError>;
    pub fn load(path: &Path) -> Result<Self, ProtocolError>;
}
```

**好处**:
- 快速恢复到已知状态
- 支持状态机调试和审计
- 减少启动时的重建开销

**代价**:
- 增加持久化代码复杂度
- 需要处理快照版本兼容性
- 增加磁盘 I/O 开销

---

### FC-02: 性能基准测试增强

**当前设计**: 提案中定义了基本的性能指标（FFI 开销 < 100ms，状态转换 < 10ms）。

**未来扩展**: 添加更详细的性能基准测试：

```rust
// core/src/protocol/benches/pqrr_bench.rs
use criterion::{black_box, criterion_group, criterion_main, Criterion};

fn bench_state_transition(c: &mut Criterion) {
    c.bench_function("idle_to_rekeying", |b| {
        b.iter(|| {
            let sm = PqrrStateMachine::new(5, headers);
            sm.transition_to_rekeying(6, headers)
        });
    });
}

fn bench_veto_check(c: &mut Criterion) {
    c.bench_function("check_veto_supremacy", |b| {
        b.iter(|| {
            check_veto_supremacy(black_box(&request_id))
        });
    });
}

criterion_group!(benches, bench_state_transition, bench_veto_check);
criterion_main!(benches);
```

**建议添加的基准测试**:
- 状态转换延迟 (p50, p95, p99)
- 不变量验证开销
- Header 更新批处理性能
- 大规模设备数量 (100+) 下的性能表现

---

### FC-03: 多恢复窗口支持

**当前设计**: 系统不支持并行的多个恢复窗口。

**未来扩展**: 支持多个独立的恢复窗口：

```rust
// 修改 PqrrStateMachine
pub struct PqrrStateMachine {
    // ... 现有字段

    /// 支持多个并行的恢复窗口
    pub recovery_windows: HashMap<RecoveryRequestId, RecoveryWindow>,

    /// 窗口管理策略
    pub window_policy: RecoveryWindowPolicy,
}

pub enum RecoveryWindowPolicy {
    /// 单一窗口模式 (当前)
    Single,

    /// 多窗口模式，限制最大数量
    Multi { max_concurrent: usize },

    /// 无限制多窗口 (不推荐)
    Unlimited,
}

impl PqrrStateMachine {
    pub fn initiate_recovery_window(
        &mut self,
        request_id: RecoveryRequestId,
        initiator_role: Role,
    ) -> Result<(), ProtocolError> {
        match self.window_policy {
            RecoveryWindowPolicy::Single => {
                if self.recovery_windows.len() > 0 {
                    return Err(ProtocolError::ConcurrentRecoveryNotAllowed);
                }
            }
            RecoveryWindowPolicy::Multi { max_concurrent } => {
                if self.recovery_windows.len() >= max_concurrent {
                    return Err(ProtocolError::TooManyConcurrentRecoveries {
                        max: max_concurrent,
                        current: self.recovery_windows.len(),
                    });
                }
            }
            RecoveryWindowPolicy::Unlimited => {}
        }

        // 创建窗口...
    }
}
```

**好处**:
- 支持多个用户同时发起恢复
- 提高系统可用性
- 支持灾难恢复场景

**代价**:
- 增加状态机复杂度
- 需要处理窗口间的交互
- 增加资源消耗

---

### FC-04: 设备离线容忍增强

**当前设计**: 支持异步设备更新，`RekeyingContext` 跟踪 `pending_devices`。

**未来扩展**: 添加更细粒度的离线容忍策略：

```rust
pub struct DeviceOfflinePolicy {
    /// 单个设备的最大离线时间
    pub max_offline_duration: Duration,

    /// 允许的最大离线设备数量
    pub max_offline_devices: usize,

    /// 离线设备降级策略
    pub degradation_strategy: DegradationStrategy,
}

pub enum DegradationStrategy {
    /// 继续升级，离线设备稍后同步
    Continue,

    /// 等待离线设备上线
    Wait,

    /// 离线设备超过阈值时撤销
    Revoke,
}
```

---

## References

- [架构白皮书 v5.0](../../docs/arch/Aeternum-architecture.md) - §4 设备管理
- [同步协议规范](../../docs/protocols/Sync-Wire-Protocol.md) - Aeternum Wire 帧格式
- [持久化与崩溃一致性](../../docs/protocols/Persistence-Crash-Consistency.md) - §2 影子写入
- [冷锚恢复协议](../../docs/protocols/Cold-Anchor-Recovery.md) - §3 否决机制
- [形式化数学不变量](../../docs/math/Formal-Invariants.md) - 四大不变量定义
- [密钥生命周期状态机](../../docs/Android-Key-Lifecycle-State-Machine.md) - §3 状态转换

---

## Appendix: State Transition Matrix

| From | To | 条件 | 不变量检查 |
|------|----|----|------------|
| Idle | Rekeying | 收到新 Header 且 epoch > current_epoch | #1 |
| Idle | RecoveryInitiated | 收到恢复请求且角色 = AUTHORIZED | #3, #4 |
| Idle | Degraded | 完整性验证失败 | #2 |
| Idle | Revoked | 撤销指令 | #2 |
| Rekeying | Idle | 所有设备 Header 更新完成 | - |
| RecoveryInitiated | Idle | 48h 窗口过期且无 Veto | #4 |
| RecoveryInitiated | Idle | 收到 Veto | #4 |
| Degraded | Revoked | 持续完整性失败 | #2 |
| Degraded | Idle | 完整性恢复 | #2 |
| Revoked | - | 终态 (无转换) | - |

---

**提案版本**: 1.0
**最后更新**: 2025-02-14
**审批状态**: 待审批
