# Design Document: PQRR 协议状态机

**Change ID**: `add-protocol`
**Status**: Design
**Version**: 1.0
**Date**: 2025-02-14

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [State Machine Design](#state-machine-design)
3. [Invariant Enforcement Strategy](#invariant-enforcement-strategy)
4. [Error Handling & Meltdown](#error-handling--meltdown)
5. [Concurrency & Thread Safety](#concurrency--thread-safety)
6. [Memory Management](#memory-management)
7. [Testing Strategy](#testing-strategy)
8. [Performance Considerations](#performance-considerations)
9. [Security Analysis](#security-analysis)

---

## Architecture Overview

### 模块依赖图

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

### 关键设计决策

#### 决策 1: 状态机作为单一源否理

**问题**: 状态机信息应该在哪里维护？

**选项**:
- A: 在 Kotlin 层维护 (Android SharedPreferences)
- B: 在 Rust 层维护 (内存状态)
- C: 混合维护 (跨语言同步)

**选择**: B - 在 Rust 层维护

**理由**:
1. **安全边界**: 密钥相关状态必须在可信域 (Rust) 维护
2. **不变量强制执行**: Rust 类型系统可防止非法状态转换
3. **原子性**: 状态转换与密钥操作在单一内存空间完成
4. **性能**: 避免 FFI 开销

**权衡**:
- ✅ 安全性提升
- ✅ 不变量强制执行更可靠
- ✅ 测试覆盖更易验证
- ❌ 需要额外的状态重建逻辑 (从 `device_headers` 重建)

---

#### 决策 2: 同步 vs 异步状态转换

**问题**: 状态转换应该是同步还是异步？

**选项**:
- A: 同步 (调用方阻塞直到转换完成)
- B: 异步 (转换在后台线程执行)

**选择**: 混合 - 核心转换同步, 耗时操作异步

**细节**:
- **Rekeying**: 核心逻辑同步, 影子写入异步
- **RecoveryInitiated**: 同步 (检查 48h 窗口)
- **Degraded/Revoked**: 同步 (立即状态转换)

**理由**:
1. **不变量保证**: 同步转换可确保状态机始终一致
2. **UI 响应**: 同步核心路径确保 UI 反馈及时
3. **性能**: 影子写入 (I/O 密集) 可异步执行

---

#### 决策 3: 状态机持久化策略

**问题**: 状态机是否需要持久化到磁盘？

**选项**:
- A: 持久化到 SQLite 数据库
- B: 从 `device_headers` 和 `current_epoch` 重建

**选择**: B - 重建策略

**理由**:
1. **简单性**: 避免额外的持久化逻辑
2. **崩溃恢复**: `current_epoch` 和 `device_headers` 已有崩溃恢复
3. **一致性**: 单一数据源避免不一致

**重建逻辑**:
```rust
pub fn rebuild_state() -> Self {
    let current_epoch = read_current_epoch_from_metadata();
    let device_headers = read_all_headers_from_vault();
    PqrrStateMachine::new(current_epoch, device_headers)
}
```

---

## State Machine Design

### 状态定义

```rust
pub enum ProtocolState {
    /// 空闲状态 - 无操作进行
    /// - 设备可正常解密数据
    /// - 可接受新 Header 或恢复请求
    Idle,

    /// 纪元升级中 (不变量 #1 + #3 执行点)
    /// - 旧 DEK 仍有效 (解密历史数据)
    /// - 新 DEK 生成中 (加密新数据)
    /// - 所有设备 Header 更新完成前保持此状态
    Rekeying(RekeyingContext),

    /// 恢复已发起 (等待 48h 否决窗口)
    /// - 任何活跃设备可否决
    /// - 48h 内无否决则恢复完成
    RecoveryInitiated(RecoveryContext),

    /// 完整性降级 (Invariant #2 检查失败)
    /// - 只读模式
    /// - 无法执行解密或导出
    /// - 等待完整性恢复或设备撤销
    Degraded {
        reason: DegradedReason,
        last_valid_epoch: u32,
    },

    /// 设备已撤销 (Invariant #2 清理完成)
    /// - 终态 (不可逆)
    /// - 无法执行任何操作
    Revoked,
}
```

### 状态转换矩阵

| From | To | 条件 | 不变量检查 |
|------|----|----|------------|
| Idle | Rekeying | 收到新 Header 且 epoch > current_epoch | #1: `new_epoch > current_epoch` |
| Idle | RecoveryInitiated | 收到恢复请求且角色 = AUTHORIZED | #3: `role != RECOVERY` |
| Idle | Degraded | 完整性验证失败 | #2: Header 完备性 |
| Idle | Revoked | 撤销指令 | #2: 清理 Header |
| Rekeying | Idle | 所有设备 Header 更新完成 | - |
| RecoveryInitiated | Idle | 48h 窗口过期且无 Veto | #4: 否决权优先 |
| RecoveryInitiated | Idle | 收到 Veto | #4: 否决权优先 |
| Degraded | Revoked | 持续完整性失败 | - |
| Degraded | Idle | 完整性恢复 | - |

### 状态转换伪代码

```rust
impl PqrrStateMachine {
    pub fn transition_to(&mut self, new_state: ProtocolState) -> Result<()> {
        // 不变量强制执行点
        match (&self.state, &new_state) {
            (ProtocolState::Idle, ProtocolState::Rekeying(_)) => {
                // 检查纪元单调性 (不变量 #1)
                assert!(new_epoch > self.current_epoch);
            }
            (ProtocolState::Idle, ProtocolState::RecoveryInitiated(_)) => {
                // 检查角色权限 (不变量 #3)
                assert!(initiator_role != Role::Recovery);
            }
            (ProtocolState::RecoveryInitiated(ctx), ProtocolState::Idle) => {
                // 检查否决窗口 (不变量 #4)
                assert!(ctx.has_veto() || ctx.is_window_expired());
            }
            _ => {}
        }

        // 执行状态转换
        self.state = new_state;
        Ok(())
    }
}
```

---

## Invariant Enforcement Strategy

### 不变量 #1: 纪元单调性

**数学定义**:
```
∀ d₁, d₂ ∈ D_active ⟹ epoch(d₁) = epoch(d₂) = S_epoch
```

**强制执行点**:
```rust
pub fn apply_epoch_upgrade(&mut self, header: &DeviceHeader) -> Result<()> {
    // 熔断: 禁止回滚
    assert!(
        header.epoch > self.current_epoch,
        "Epoch regression: current={}, attempted={}",
        self.current_epoch, header.epoch
    );

    // 更新纪元
    self.current_epoch = header.epoch;
    Ok(())
}
```

**熔断机制**:
```rust
// 在 invariant.rs 中实现
pub fn enforce_epoch_monotonicity(current: u32, attempted: u32) -> Result<()> {
    if attempted <= current {
        Err(InvariantViolation::EpochRegression { current, attempted })
    } else {
        Ok(())
    }
}
```

---

### 不变量 #2: Header 完备性

**数学定义**:
```
∀ d ∈ D_active \ {Device_0} ⟹ ∃! h ∈ Ve+1.H: unwrap(h, d) = DEKe
∀ d ∈ D_active \ {Device_0} ⟹ |{h ∈ Ve+1.H: unwrap(h, d) = DEK_e}| = 1
```

**强制执行点**:
```rust
pub fn validate_header_completeness(&self, device_id: &DeviceId) -> Result<()> {
    let headers = self.device_headers.get(device_id);

    // 检查 1: 设备必须有 Header
    let header = headers.ok_or_else(|| {
        InvariantViolation::MissingHeader(device_id.clone())
    })?;

    // 检查 2: 设备只能有一个 Header
    if headers.len() > 1 {
        return Err(InvariantViolation::MultipleHeaders(
            device_id.clone(),
            headers.len()
        ));
    }

    // 检查 3: Header 必须可解封
    let dek = unwrap_header(header)?;
    if !is_valid_dek(&dek) {
        return Err(InvariantViolation::InvalidHeader(device_id.clone()));
    }

    Ok(())
}
```

---

### 不变量 #3: 因果熵障

**数学定义**:
```
Role(σ) = RECOVERY ⟹ σ_rotate ∉ P(S)
```

**强制执行点**:
```rust
pub fn execute_rotation(&self, role: Role) -> Result<()> {
    // 熔断: RECOVERY 角色无法执行 σ_rotate
    if role == Role::Recovery {
        return Err(InvariantViolation::CausalEntopyBarrier);
    }

    // 执行根控制权转移
    self.perform_root_rotation()?;
    Ok(())
}
```

---

### 不变量 #4: 否决权优先

**数学定义**:
```
Status(req) = COMMITTED
∧ (tnow ≥ Tstart + ΔTwindow ∨ Vetoes(req) ≠ ∅)
⟹ Status(req) = REJECTED
```

**强制执行点**:
```rust
pub fn check_veto_supremacy(&self, req: &RecoveryRequest) -> Result<()> {
    let window = self.recovery_windows.get(&req.id)?;

    // 检查 1: 窗口内收到 Veto → 立即终止
    if window.has_veto() {
        return Err(InvariantViolation::VetoSupremacy);
    }

    // 检查 2: 窗口过期 → 允许恢复
    if window.is_expired() {
        return Ok(());
    }

    // 检查 3: 窗口未过期且无 Veto → 等待
    Err(InvariantViolation::VetoWindowActive)
}
```

---

## Error Handling & Meltdown

### 错误类型定义

```rust
pub enum ProtocolError {
    // 不变量违规
    InvariantViolation(InvariantViolation),

    // 状态转换错误
    InvalidTransition {
        from: ProtocolState,
        to: ProtocolState,
    },

    // I/O 错误
    StorageError(StorageError),

    // 密码学错误
    CryptoError(CryptoError),
}

pub enum InvariantViolation {
    EpochRegression { current: u32, attempted: u32 },
    MissingHeader(DeviceId),
    MultipleHeaders(DeviceId, usize),
    CausalEntopyBarrier,
    VetoSupremacy,
    VetoWindowActive,
}
```

### 熔断后熔断机制

```rust
pub fn handle_invariant_violation(violation: InvariantViolation) -> ! {
    // 1. 内核锁定: 停止所有 DEK 解密操作
    lock_crypto_kernel();

    // 2. 状态隔离: 标记"异常分叉"
    isolate_forked_state();

    // 3. 用户警示: 强制高优先级风险警告
    show_critical_alert("系统检测到安全异常, 请重新验证根信任");

    // 4. 终止: 进入 Degraded 或终止
    std::process::exit(1);
}
```

---

## Concurrency & Thread Safety

### 线程安全保证

```rust
// 使用 Mutex 保护可变状态
pub struct PqrrStateMachine {
    state: Mutex<ProtocolState>,
    current_epoch: AtomicU32,
    device_headers: RwLock<HashMap<DeviceId, DeviceHeader>>,
    veto_signals: Mutex<HashMap<RecoveryRequestId, Vec<VetoMessage>>>,
}
```

### 并发场景处理

| 场景 | 并发控制 | 不变量保证 |
|------|----------|------------|
| 多设备同时触发 Rekeying | Mutex 状态锁 | #1: 纪元单调性 |
| 并发否决信号 | Mutex veto_signals | #4: 否决权优先 |
| Header 并发更新 | RwLock Headers | #2: Header 完备性 |

---

## Memory Management

### Zeroize 策略

所有敏感数据结构必须实现 `Zeroize`:

```rust
pub struct RekeyingContext {
    old_dek: Zeroizing<Vec<u8>>,  // 自动擦除
    new_dek: Zeroizing<Vec<u8>>,  // 自动擦除
    // ...
}
```

### 内存锁定策略

```rust
// 使用 mlock 保护密钥内存
pub fn lock_key_memory(key: &mut [u8]) -> Result<()> {
    unsafe {
        if libc::mlock(key.as_ptr() as *const libc::c_void, key.len()) != 0 {
            return Err(std::io::Error::last_os_error().into());
        }
    }
    Ok(())
}
```

---

## Testing Strategy

### 单元测试

```rust
#[cfg(test)]
mod tests {
    #[test]
    fn test_epoch_monotonicity() {
        let mut sm = PqrrStateMachine::new(1, HashMap::new());
        let header = create_test_header(2);

        // 应该成功
        assert!(sm.apply_epoch_upgrade(&header).is_ok());

        // 应该失败 (回滚)
        let old_header = create_test_header(1);
        assert!(sm.apply_epoch_upgrade(&old_header).is_err());
    }
}
```

### 属性测试 (Proptest)

```rust
proptest! {
    #[test]
    fn prop_epoch_always_increments(
        start_epoch in 1u32..1000,
        increments in 1u32..10
    ) {
        let mut current = start_epoch;
        for _ in 0..increments {
            let next = current + 1;
            assert!(next > current, "Epoch must strictly increase");
            current = next;
        }
    }
}
```

### 集成测试

```rust
#[test]
fn test_full_pqrr_flow() {
    // 1. 初始化状态机
    let mut sm = PqrrStateMachine::new(1, HashMap::new());

    // 2. 模拟设备注册
    let device_id = register_test_device(&mut sm);

    // 3. 模拟纪元升级
    let new_header = create_test_header(2);
    sm.transition_to_rekeying(&new_header).unwrap();

    // 4. 验证状态转换
    assert!(matches!(sm.state, ProtocolState::Rekeying(_)));

    // 5. 完成 Rekeying
    sm.return_to_idle().unwrap();

    // 6. 验证最终状态
    assert!(matches!(sm.state, ProtocolState::Idle));
    assert_eq!(sm.current_epoch(), 2);
}
```

---

## Performance Considerations

### 目标指标

| 操作 | 目标时间 | 测量方法 |
|------|----------|----------|
| 状态转换 | < 10ms | Criterion 基准测试 |
| 不变量检查 | < 1ms | 单元测试时间 |
| Header 更新 | < 50ms | 集成测试时间 |
| Veto 检查 | < 1ms | 单元测试时间 |

### 优化策略

1. **缓存 Header**: 避免重复从存储读取
2. **批量更新**: Rekeying 时批量更新所有设备 Header
3. **异步 I/O**: 影子写入异步执行
4. **零拷贝**: 借用 Rust 所有权系统避免克隆

---

## Security Analysis

### 威胁模型

| 威胁 | 攻击向量 | 防御机制 |
|------|---------|----------|
| 纪元回滚 | 修改 current_epoch | 不变量 #1 熔断 |
| 设备伪造 | 注入假 Header | 不变量 #2 检查 |
| 权限提升 | RECOVERY 执行 σ_rotate | 不变量 #3 检查 |
| 恢复劫持 | 跳过 48h 窗口 | 不变量 #4 检查 |

### 缓解措施

1. **类型安全**: Rust 类型系统防止内存安全漏洞
2. **不变量强制执行**: 运行时断言防止非法状态
3. **熔断机制**: 检测到违规立即终止
4. **Zeroize**: 密钥内存物理擦除

---

## References

- [架构白皮书 v5.0](../../docs/arch/Aeternum-architecture.md) - §4 设备管理
- [形式化数学不变量](../../docs/math/Formal-Invariants.md) - 四大不变量定义
- [持久化与崩溃一致性](../../docs/protocols/Persistence-Crash-Consistency.md) - §2 影子写入
- [冷锚恢复协议](../../docs/protocols/Cold-Anchor-Recovery.md) - §3 否决机制

---

**设计版本**: 1.0
**最后更新**: 2025-02-14
**作者**: Aeternum Protocol Team
