# Change: 修复 Android UI 桥接与编译错误

## Why

`add-android-ui-layer` 提案已实现 95% 的功能（53 个 UI 文件），但在编译时发现 80+ 错误，主要问题：

1. **UniFFI 桥接接口不匹配**: UI 层调用的 `unlockVault`、`storeEntry`、`retrieveEntry` 等方法未在 Rust Core 中实现或未正确生成桥接代码
2. **Compose API 版本兼容性**: 多个 Compose API（如 `animateValue`、`VectorConverter`、`StrokeCap`）在当前 BOM 版本中不可用或签名已更改
3. **Material3 组件参数缺失**: `Switch` 组件的新增参数（如 `checkedBorderColor`）未提供
4. **无障碍 API 更新**: `ACCESSIBILITY_ANNOUNCEMENT`、`isHighTextContrastEnabled` 等 API 已废弃或更改

这些编译错误阻塞了测试执行，必须修复才能验证 `add-android-ui-layer` 提案的完成情况。

## What Changes

- **BREAKING**: 无破坏性变更，仅修复现有代码
- 完善 UniFFI UDL 接口定义
- 实现 Rust Core 后端接口（`AeternumEngine`、`VaultSession`）
- 重新生成 UniFFI 桥接代码
- 修复 Compose API 兼容性问题
- 更新 Material3 组件调用
- 替换废弃的无障碍 API

## Impact

### 影响的规范 (Affected Specs)
- 修复 `android-ui` 规范的实现问题
- 补充 `UniFFI-Bridge-Contract` 的接口定义

### 影响的代码 (Affected Code)
- `core/uniffi/aeternum.udl` - 扩展接口定义
- `core/src/bridge/` - 新增 Rust 后端实现
- `android/app/src/main/kotlin/io/aeternum/bridge/` - 重新生成的桥接代码
- `android/app/src/main/kotlin/io/aeternum/data/VaultRepository.kt` - 使用新接口
- `android/app/src/main/kotlin/io/aeternum/ui/components/*.kt` - 修复 API 兼容性
- `android/app/src/main/kotlin/io/aeternum/ui/accessibility/*.kt` - 替换废弃 API
- `android/app/build.gradle.kts` - 可能需要调整依赖版本

### 新增/修改文件
```
core/
├── uniffi/aeternum.udl          # 扩展接口定义
└── src/bridge/
    ├── mod.rs                   # 桥接模块入口
    ├── engine.rs                # AeternumEngine 实现
    ├── session.rs               # VaultSession 实现
    └── device.rs                # DeviceInfo 相关实现

android/app/src/main/kotlin/io/aeternum/
├── bridge/                      # 重新生成 (勿手动修改)
│   ├── AeternumEngine.kt        # UniFFI 生成
│   ├── VaultSession.kt          # UniFFI 生成
│   └── ...
├── data/
│   └── VaultRepository.kt       # 使用新接口
└── ui/components/               # 修复 API 兼容性
    ├── QuantumAnimation.kt      # 修复动画 API
    ├── BiometricSuccessAnimation.kt
    ├── VetoPulseAnimation.kt
    ├── ListItem.kt              # 修复 Switch 参数
    ├── AppBar.kt                # 修复导入
    └── ...
```

## 设计决策

### Decision 1: UniFFI 接口扩展策略

**选择**: 在现有 `aeternum.udl` 基础上扩展，而非创建新的 UDL 文件

**理由**:
- 保持接口定义的集中管理
- 避免桥接代码分散
- 简化依赖关系

**替代方案**:
- ❌ 创建新的 `aeternum_v2.udl`：增加维护复杂度

### Decision 2: Rust 后端实现位置

**选择**: 在 `core/src/bridge/` 创建新的桥接模块

**理由**:
- 与现有 `core/src/crypto/`、`core/src/protocol/` 等模块保持一致
- 清晰的模块边界
- 便于测试和维护

**实现结构**:
```rust
// core/src/bridge/mod.rs
pub mod engine;
pub mod session;
pub mod device;

pub use engine::AeternumEngine;
pub use session::VaultSession;
```

### Decision 3: Compose API 兼容性修复策略

**选择**: 使用替代 API 而非降级依赖版本

**理由**:
- 保持使用最新的 Compose BOM
- 避免引入安全漏洞
- 长期可维护性

**API 替换映射**:
| 废弃 API | 替代 API |
|----------|----------|
| `animateValue` | `animateFloatAsState` + 自定义绘制 |
| `VectorConverter` | 手动类型转换 |
| `StrokeCap` | `DrawScope.Stroke` 参数 |
| `ACCESSIBILITY_ANNOUNCEMENT` | `SemanticsProperties.announce` |

### Decision 4: Material3 Switch 参数处理

**选择**: 提供所有新增参数的默认值

**理由**:
- 避免 API 不匹配
- 保持设计一致性
- 使用主题色作为默认值

**示例**:
```kotlin
Switch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    // 新增参数
    checkedBorderColor = MaterialTheme.colorScheme.primary,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
    // ... 其他参数
)
```

### Decision 5: UniFFI JNA 依赖处理

**问题**: UniFFI 生成的 Kotlin 代码使用 `com.sun.jna.*` 包，这需要 JNA (Java Native Access) 库支持。

**选择**: 添加 JNA AAR 依赖到 Android 项目

**理由**:
- UniFFI 0.28 的 Kotlin 后端完全依赖 JNA 进行 FFI 调用
- JNA 5.12.0+ 提供 Android AAR 支持，可直接集成
- 官方推荐的 UniFFI Android 集成方式
- 相比手动 JNI 绑定更易维护

**替代方案**:
- ❌ 手动实现 JNI 绑定：UniFFI 0.28 的 JNI 支持仍在开发中（Issue #2672）
- ❌ 等待 UniFFI 原生 Android 支持：时间线不确定，阻塞当前开发

**实现**:
```kotlin
// android/app/build.gradle.kts
dependencies {
    // UniFFI JNA 依赖
    implementation("net.java.dev.jna:jna:5.14.0@aar")
}
```

**ProGuard 规则**:
```proguard
# 保留 JNA 类和方法
-keep class com.sun.jna.** { *; }
-keepclassmembers class * extends com.sun.jna.Callback { *; }
-keepclassmembers class * extends com.sun.jna.Structure { *; }
```

**配置文件**:
```toml
# core/uniffi/aeternum.toml
[bindings.kotlin]
cdylib_name = "aeternum_core"
android = true  # 启用 Android 优化
android_cleaner = "android"
```

## UniFFI 接口扩展

### 新增接口定义

```idl
namespace aeternum {
    // Vault 会话接口 - 用于解密操作
    interface VaultSession {
        // 获取脱敏的记录 ID 列表
        sequence<string> list_record_ids();

        // 解密字段 - 明文仅在 Rust 内存中
        [Throws=PqrrError]
        string decrypt_field(string record_id, string field_key);

        // 存储条目 - 加密后存储
        [Throws=PqrrError]
        void store_entry(string record_id, string field_key, string plaintext_value);

        // 检索条目 - 解密后返回
        [Throws=PqrrError]
        string retrieve_entry(string record_id, string field_key);

        // 显式锁定 - 清除内存中的密钥
        void lock();

        // 检查会话是否有效
        boolean is_valid();
    };

    // 扩展 AeternumEngine 接口
    interface AeternumEngine {
        [Throws=PqrrError]
        constructor(string vault_path);

        // 初始化 Vault（首次使用）
        [Throws=PqrrError]
        void initializeVault(sequence<u8> hardware_key_blob);

        // 解锁 Vault - 返回会话句柄
        [Throws=PqrrError]
        VaultSession unlock(sequence<u8> hardware_key_blob);

        // 获取脱敏的设备列表
        [Throws=PqrrError]
        sequence<DeviceInfo> get_device_list();

        // 撤销设备
        [Throws=PqrrError]
        void revoke_device(sequence<u8> device_id);

        // 发起恢复
        [Throws=PqrrError]
        string initiate_recovery(sequence<u8> mnemonic_bytes);

        // 提交否决
        [Throws=PqrrError]
        void submit_veto(string recovery_id);

        // 验证 Vault 完整性
        [Throws=PqrrError]
        boolean verify_vault_integrity();

        // 关闭引擎
        void close();
    };

    // 设备信息字典（已存在，无需修改）
    dictionary DeviceInfo {
        sequence<u8> device_id;
        string device_name;
        u32 epoch;
        ProtocolState state;
        i64 last_seen_timestamp;
        boolean is_this_device;
    };
}
```

### Rust 后端实现示例

```rust
// core/src/bridge/session.rs
use uniffi::*;

#[uniffi_export]
impl VaultSession for AeternumVaultSession {
    fn list_record_ids(&self) -> Vec<String> {
        self.inner.list_ids()
    }

    fn decrypt_field(&self, record_id: String, field_key: String) -> Result<String, PqrrError> {
        self.inner.decrypt_field(&record_id, &field_key)
            .map_err(|e| e.into())
    }

    fn store_entry(&self, record_id: String, field_key: String, plaintext_value: String) -> Result<(), PqrrError> {
        self.inner.store_entry(&record_id, &field_key, plaintext_value.as_bytes())
            .map_err(|e| e.into())
    }

    fn retrieve_entry(&self, record_id: String, field_key: String) -> Result<String, PqrrError> {
        self.inner.retrieve_entry(&record_id, &field_key)
            .map_err(|e| e.into())
            .map(|bytes| String::from_utf8_lossy(&bytes).to_string())
    }

    fn lock(&mut self) {
        self.inner.lock();
        // zeroize 密钥材料
    }

    fn is_valid(&self) -> bool {
        self.inner.is_valid()
    }
}

// core/src/bridge/engine.rs
#[uniffi_export]
impl AeternumEngine for AeternumCoreEngine {
    constructor new(vault_path: String) -> Result<Self, PqrrError> {
        // 初始化逻辑
    }

    fn initialize_vault(&self, hardware_key_blob: Vec<u8>) -> Result<(), PqrrError> {
        // 初始化 Vault
    }

    fn unlock(&self, hardware_key_blob: Vec<u8>) -> Result<VaultSession, PqrrError> {
        // 解锁并返回会话句柄
    }

    // ... 其他方法
}
```

## Compose API 修复详情

### 1. 动画 API 替换

**问题代码**:
```kotlin
// QuantumAnimation.kt:121
animateValue(
    initialValue = startRotation,
    targetValue = endRotation,
    typeConverter = VectorConverter,
    animationSpec = infiniteRepeatable(tween(1000))
)
```

**修复方案**:
```kotlin
// 使用 animateFloatAsState
val rotation by animateFloatAsState(
    targetValue = endRotation,
    animationSpec = infiniteRepeatable(
        animation = tween(1000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
    ),
    label = "rotation"
)

// 自定义绘制时直接使用 Float 值
drawBehind {
    rotate(rotation) {
        drawCircle(...)
    }
}
```

### 2. Material3 Switch 参数

**问题代码**:
```kotlin
// ListItem.kt:185
Switch(
    checked = checked,
    onCheckedChange = onCheckedChange
)
```

**修复方案**:
```kotlin
Switch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    // 新增必需参数
    checkedBorderColor = MaterialTheme.colorScheme.primary,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
    checkedIconColor = MaterialTheme.colorScheme.onPrimary,
    uncheckedIconColor = MaterialTheme.colorScheme.outline,
    disabledCheckedThumbColor = MaterialTheme.colorScheme.surface,
    disabledUncheckedThumbColor = MaterialTheme.colorScheme.surface,
    // ... 其他参数
)
```

### 3. 无障碍 API 替换

**问题代码**:
```kotlin
// AccessibilityAnnouncer.kt:88
SemanticsProperties.AccessibilityAnnouncement
```

**修复方案**:
```kotlin
SemanticsProperties.announce
// 或使用
SemanticsProperties.LiveRegion
```

## 任务清单

### 阶段 1: UniFFI 桥接修复 (P0)

- [ ] 1.1 扩展 `core/uniffi/aeternum.udl` 添加 VaultSession 接口
- [ ] 1.2 扩展 `core/uniffi/aeternum.udl` 添加 AeternumEngine 方法
- [ ] 1.3 创建 `core/src/bridge/mod.rs`
- [ ] 1.4 实现 `core/src/bridge/session.rs`
- [ ] 1.5 实现 `core/src/bridge/engine.rs`
- [ ] 1.6 实现 `core/src/bridge/device.rs`
- [ ] 1.7 运行 `./scripts/generate-bridge.sh` 重新生成桥接代码
- [ ] 1.8 验证生成的 Kotlin 接口编译通过

### 阶段 2: 数据层适配 (P0)

- [ ] 2.1 更新 `VaultRepository.kt` 使用新的 AeternumEngine 接口
- [ ] 2.2 移除占位符实现，连接到实际 Rust 接口
- [ ] 2.3 测试 Vault 解锁流程
- [ ] 2.4 测试数据存储/检索流程

### 阶段 3: Compose API 修复 (P0)

- [ ] 3.1 修复 `QuantumAnimation.kt` 动画 API
- [ ] 3.2 修复 `BiometricSuccessAnimation.kt` Path 类型问题
- [ ] 3.3 修复 `VetoPulseAnimation.kt` 动画 API
- [ ] 3.4 修复 `ListItem.kt` Switch 参数
- [ ] 3.5 修复 `AppBar.kt` Column 导入
- [ ] 3.6 修复 `LoadingOverlay.kt` Modifier.size 参数
- [ ] 3.7 修复 `MicroInteractions.kt` 协程上下文
- [ ] 3.8 修复 `SessionAwareContent.kt` 用户活动检测
- [ ] 3.9 修复 `StatusIndicator.kt` @Composable 上下文

### 阶段 4: 无障碍 API 更新 (P0)

- [ ] 4.1 替换 `AccessibilityAnnouncer.kt` 废弃 API
- [ ] 4.2 替换 `AccessibilityState.kt` isHighTextContrastEnabled
- [ ] 4.3 替换 `AccessibilityExtensions.kt` TextBox API
- [ ] 4.4 测试无障碍功能

### 阶段 5: 安全边界验证 (P1)

- [ ] 5.1 运行 `SecurityStaticAnalysisTest.kt` 验证无密钥泄漏
- [ ] 5.2 验证 FLAG_SECURE 防截屏生效
- [ ] 5.3 验证后台自动锁定功能
- [ ] 5.4 验证生物识别 Class 3 要求

### 阶段 6: 测试与验证 (P1)

- [ ] 6.1 运行单元测试: `./gradlew test`
- [ ] 6.2 运行 UI 测试: `./gradlew connectedAndroidTest`
- [ ] 6.3 验证测试覆盖率达标
- [ ] 6.4 运行安全静态分析测试
- [ ] 6.5 构建 Debug APK: `./gradlew assembleDebug`
- [ ] 6.6 构建 Release APK: `./gradlew assembleRelease`

### 阶段 7: 文档与归档 (P2)

- [ ] 7.1 更新 `tasks.md` 标记已完成的任务
- [ ] 7.2 更新 `add-android-ui-layer` 提案状态
- [ ] 7.3 归档本修复提案
- [ ] 7.4 更新 RELEASE_NOTES.md（如有必要）

## 安全边界

### 禁止事项
- ❌ 通过 UDL 暴露明文密钥或敏感数据
- ❌ 手动修改 UniFFI 生成的 Kotlin 代码
- ❌ 在 Kotlin 层实现密码学逻辑
- ❌ 在日志中记录密钥或助记词

### 必须事项
- ✅ 所有解密操作在 Rust 端完成
- ✅ Kotlin 层仅持有 Rust 实例句柄
- ✅ 敏感对象实现 Zeroize
- ✅ 修改 UDL 后必须重新生成桥接代码

## 非目标 (Non-Goals)

- 不修改 UI 层的设计和交互逻辑
- 不改变安全架构
- 不添加新的功能特性（仅修复）

## 验收标准

### 编译成功
```bash
cd android
./gradlew clean
./gradlew assembleDebug
./gradlew assembleRelease
```
✅ 期望：零编译错误，零警告

### 测试通过
```bash
./gradlew test
./gradlew connectedAndroidTest
```
✅ 期望：所有测试通过，覆盖率 ≥ 目标值

### 安全验证
```bash
./gradlew test --tests SecurityStaticAnalysisTest
```
✅ 期望：所有安全测试通过

### 功能验证
✅ 期望：
- Vault 解锁流程正常
- 设备列表正确显示
- 密钥轮换动画正常播放
- 无障碍功能正常工作
- FLAG_SECURE 防截屏生效

## 风险与缓解

### Risk 1: UniFFI 接口不兼容

**风险**: 新增接口与现有 `PqrrStateMachine` 冲突

**缓解**:
- 保持接口命名空间隔离
- 使用不同的接口名称
- 充分测试桥接代码生成

### Risk 2: Compose API 替换影响动画效果

**风险**: 替换动画 API 后效果不一致

**缓解**:
- 逐个对比动画效果
- 使用性能测试验证帧率
- 必要时微调动画参数

### Risk 3: 修复引入新的安全问题

**风险**: 修改桥接代码可能破坏安全边界

**缓解**:
- 运行完整的安全静态分析
- 人工审查所有新增的 UDL 接口
- 验证 Zeroize 实现正确

## 相关提案

- 依赖提案: `add-android-ui-layer`
- 相关规范: `UniFFI-Bridge-Contract`
- 相关文档: `Aeternum-architecture.md`

---

**提案创建日期**: 2026-02-16
**提案类型**: Bug Fix
**预期工作量**: 3-5 天
**依赖提案**: add-android-ui-layer
**优先级**: P0 (阻塞)
