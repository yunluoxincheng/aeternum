# Tasks: PQRR 协议状态机实现

**Change ID**: `add-protocol`
**Total Tasks**: 20
**Estimated Duration**: 3 weeks

---

## Task Categories

- 📋 **Phase 1**: 核心状态机 (Week 1)
- 🔐 **Phase 2**: 恢复协议与否决权 (Week 2)
- 📱 **Phase 3**: 设备管理生命周期 (Week 2)
- 🔄 **Phase 4**: 纪元升级协调 (Week 3)
- 🌉 **Phase 5**: UniFFI 集成 (Week 3)

---

## Phase 1: 核心状态机

### 1.1 创建 protocol 模块结构 ⚠️
- [x] 创建 `core/src/protocol/` 目录
- [x] 创建 `core/src/protocol/mod.rs`
- [x] 添加模块到 `core/src/lib.rs`
- [x] 配置 `Cargo.toml` 依赖 (如需要)

**验证**: `cargo build --package aeternum-core` 通过

---

### 1.2 实现 PqrrStateMachine 核心结构 ⚠️
- [x] 定义 `PqrrStateMachine` 结构体
- [x] 定义 `ProtocolState` 枚举 (Idle, Rekeying, RecoveryInitiated, Degraded, Revoked)
- [x] 实现 `new()` 构造函数
- [x] 实现 `current_epoch()` 和 `state()` 查询方法

**验证**: 单元测试通过

---

### 1.3 实现状态转换逻辑 (Idle ↔ Rekeying/Degraded/Revoked) ⚠️
- [x] 实现 `transition_to_rekeying()` 方法
- [x] 实现 `transition_to_degraded()` 方法
- [x] 实现 `transition_to_revoked()` 方法
- [x] 实现 `return_to_idle()` 方法
- [x] 添加状态转换断言 (防止非法转换)

**验证**: 单元测试覆盖所有转换

---

### 1.4 实现不变量 #1 (纪元单调性) 强制执行 ⚠️
- [x] 实现 `apply_epoch_upgrade()` 方法
- [x] 添加 `assert!(new_epoch > current_epoch)` 断言
- [x] 实现 `validate_epoch_monotonicity()` 辅助函数
- [x] 添加熔断逻辑 (断言失败时)

**验证**: 熔断测试通过, 熔断失败时正确熔断

---

### 1.5 添加单元测试和属性测试 ⚠️
- [x] 编写状态转换单元测试
- [x] 编写纪元单调性测试
- [x] 添加 `proptest` 属性测试 (无死锁, 无非法转换)
- [x] 验证测试覆盖率达到 100%

**验证**: `cargo test --package aeternum-core --all` 通过

---

## Phase 2: 恢复协议与否决权

### 2.1 实现 RecoveryWindow 结构 ✅
- [x] 定义 `RecoveryWindow` 结构体
- [x] 实现 `new()` 构造函数
- [x] 实现 `is_within_window()` 时间检查方法
- [x] 实现 `add_veto()` 方法

**验证**: 单元测试通过

---

### 2.2 实现不变量 #4 (否决权优先) 强制执行 ✅
- [x] 实现 `check_veto_supremacy()` 方法
- [x] 实现 `terminate_recovery()` 终止逻辑（通过返回错误实现）
- [x] 添加 48h 窗口超时检查
- [x] 实现否决信号广播（VetoMessage 结构）

**验证**: 否决信号立即终止恢复流程

---

### 2.3 实现 48h 窗口管理逻辑 ✅
- [x] 实现窗口启动逻辑
- [x] 实现窗口过期检查
- [x] 实现窗口清理逻辑
- [x] 添加时间漂移容错 (±5min)

**验证**: 窗口过期后恢复可完成, 过期前否决有效

---

### 2.4 添加恢复流程集成测试 ✅
- [x] 编写恢复流程集成测试
- [x] 编写否决权测试
- [x] 编写窗口过期测试
- [x] 验证跨设备否决场景

**验证**: 集成测试通过 (33 tests), 场景覆盖完整

---

## Phase 3: 设备管理生命周期

### 3.1 实现设备注册逻辑 ⚠️
- [x] 实现 `register_device()` 方法
- [x] 实现 `validate_device_registration()` 辅助函数
- [x] 添加 DeviceId 生成逻辑
- [x] 实现 DeviceHeader 生成

**验证**: 设备注册成功, Header 正确生成

---

### 3.2 实现不变量 #2 (Header 完备性) 验证 ⚠️
- [x] 实现 `validate_header_completeness()` 方法
- [x] 添加每个设备有且仅有一个 Header 的检查
- [x] 实现重复 Header 检测
- [x] 添加缺失 Header 检测

**验证**: Header 完备性测试通过

---

### 3.3 实现设备撤销清理逻辑 ⚠️
- [x] 实现 `revoke_device()` 方法
- [x] 实现 `cleanup_revoked_headers()` 方法
- [x] 实现设备状态更新
- [x] 添加撤销确认逻辑

**验证**: 撤销后设备无法解密新数据

---

### 3.4 添加设备管理集成测试 ⚠️
- [x] 编写设备注册集成测试
- [x] 编写设备撤销集成测试
- [x] 编写 Header 完备性测试
- [x] 验证多设备管理场景

**验证**: 集成测试通过, 场景覆盖完整

---

## Phase 4: 纪元升级协调

### 4.1 实现原子纪元升级协议 (AUP) ✅
- [x] 实现 `execute_epoch_upgrade()` 方法
- [x] 集成 `storage::aug::*` 影子写入
- [x] 实现升级原子性保证
- [x] 添加升级失败回滚逻辑

**验证**: AUP 升级成功, 断电后可恢复

---

### 4.2 实现不变量 #3 (因果熵障) 强制执行 ✅
- [x] 实现 `execute_rotation()` 方法
- [x] 添加角色检查 (RECOVERY 无法执行 σ_rotate)
- [x] 实现 `PermissionDenied` 错误返回
- [x] 添加权限提升检测

**验证**: RECOVERY 角色无法执行 σ_rotate

---

### 4.3 集成 storage::aug::* 影子写入 ⚠️
- [x] 调用 `aup_prepare()` 准备升级
- [x] 调用 `aup_shadow_write()` 影子写入
- [x] 调用 `aup_atomic_commit()` 原子提交
- [x] 实现 AUP 崩溃恢复逻辑

**验证**: 影子写入成功, 崩溃后自动对齐

---

### 4.4 添加 AUP 崩溃恢复测试 ⚠️
- [x] 编写 AUP 升级测试
- [x] 编写 AUP 崩溃恢复测试
- [x] 编写 AUP 回滚测试
- [x] 验证原子性保证

**验证**: 崩溃恢复测试通过, 原子性保证验证

---

## Phase 5: UniFFI 集成

### 5.1 更新 UniFFI UDL 接口定义 ✅
- [x] 在 `aeternum.udl` 中导出 `PqrrStateMachine`
- [x] 导出相关错误类型
- [x] 定义回调接口 (如需要)
- [x] 使用 proc-macro 模式生成桥接代码

**验证**: UDL 更新成功, 桥接代码生成无错

---

### 5.2 重新生成桥接代码 ✅
- [x] 使用 `uniffi-bindgen generate --library` 生成 Kotlin 代码
- [x] 检查生成的 Kotlin 代码 (aeternum.kt ~78KB)
- [x] 验证 FFI 接口正确性
- [x] 添加必要的 Kotlin 包装函数

**验证**: 桥接代码生成成功, Kotlin 可调用

---

### 5.3 更新 Android 测试 ✅
- [x] 添加协议层 Kotlin 单元测试
- [x] 编写跨设备协议集成测试
- [x] 验证 FFI 开销 < 100ms
- [x] 验证 UI 响应时间 < 100ms

**验证**: Kotlin 测试通过, 性能指标达标

---

### 5.4 验证跨语言不变量 ✅
- [x] 验证 Kotlin 层无法绕过不变量检查
- [x] 验证密钥在 Rust 层处理
- [x] 验证错误正确传播到 Kotlin 层
- [x] 添加跨语言集成测试

**验证**: 跨语言不变量验证通过, 测试覆盖完整

---

## Task Dependencies

```
┌─────────────────────────────────────────────────────────────┐
│ Task Dependencies (Directed Acyclic Graph)                 │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1.1 → 1.2 → 1.3 → 1.4 → 1.5                     │
│                           │                               │
│                           ↓                               │
│  2.1 → 2.2 → 2.3 → 2.4  ──────────────────────────────┤
│                           │                               │
│                           ↓                               │
│  3.1 → 3.2 → 3.3 → 3.4  ──────────────────────────────┤
│                           │                               │
│                           ↓                               │
│  4.1 → 4.2 → 4.3 → 4.4  ──────────────────────────────┤
│                           │                               │
│                           ↓                               │
│  5.1 → 5.2 → 5.3 → 5.4  ──────────────────────────────┘
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**并行化机会**:
- Task 1.5 可与 2.1 并行开始
- Task 2.4 可与 3.1 并行开始
- Task 3.4 可与 4.1 并行开始
- Task 4.4 可与 5.1 并行开始

---

## Milestone Definitions

### Milestone 1: 核心状态机完成 (Week 1 End)
- Tasks 1.1 - 1.5 完成
- 核心状态机可运行
- 不变量 #1 强制执行实现

### Milestone 2: 恢复协议实现 (Week 2 End)
- Tasks 2.1 - 2.4 完成
- 不变量 #4 强制执行实现
- 48h 否决窗口可运行

### Milestone 3: 设备管理实现 (Week 2 End)
- Tasks 3.1 - 3.4 完成
- 不变量 #2 强制执行实现
- 设备撤销可运行

### Milestone 4: 纪元升级实现 (Week 3 End)
- Tasks 4.1 - 4.4 完成
- 不变量 #3 强制执行实现
- AUP 协议可运行

### Milestone 5: UniFFI 集成完成 (Week 3 End)
- Tasks 5.1 - 5.4 完成
- Android 集成完成
- 跨语言不变量验证通过

---

## 总体进度跟踪

| Milestone | Target Date | Status | Progress |
|-----------|-------------|--------|-----------|
| M1: 核心状态机 | Week 1 | ✅ 完成 | 100% |
| M2: 恢复协议 | Week 2 | ✅ 完成 | 100% |
| M3: 设备管理 | Week 2 | ✅ 完成 | 100% |
| M4: 纪元升级 | Week 3 | ✅ 完成 | 100% |
| M5: UniFFI 集成 | Week 3 | ✅ 完成 | 100% |

---

**最后更新**: 2025-02-14
**负责人**: Aeternum Protocol Team
