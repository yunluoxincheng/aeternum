# Android UI 层设计文档

## Context

Aeternum 是一个后量子安全的移动密钥管理系统，Rust Core 密码内核已完成 90%+ 实现，包括完整的密码学原语、存储引擎、PQRR 协议和同步协议。Android 层的安全层和数据层也基本完成，但 UI 层仅有一个基础的 MainScreen 框架。

本项目采用分层架构：

```
┌─────────────────────────────────────────────────────┐
│           Android UI (Jetpack Compose)             │
│                 非信任域 - 不触碰密钥                  │
├─────────────────────────────────────────────────────┤
│          Android Security Control Layer            │
│     StrongBox/KeyStore | Biometric | Integrity     │
│          信任域 - 仅持硬件密钥句柄                     │
├─────────────────────────────────────────────────────┤
│              Rust Core (密码内核)                   │
│  - Kyber-1024 KEM  - XChaCha20-Poly1305 AEAD        │
│  - Argon2id KDF     - BLAKE3 Hashing               │
│          根信任域 - 所有密钥在此处理                    │
└─────────────────────────────────────────────────────┘
```

### 约束与限制

1. **安全边界**: UI 层严禁持有或操作任何明文密钥
2. **状态机**: UI 必须准确反映底层状态机的转换
3. **性能**: UI 响应时间不得超过 100ms
4. **兼容性**: 最低支持 Android 12 (API 31)
5. **依赖**: 必须通过现有的 `AndroidSecurityManager` 和 `AeternumBridge` 与 Rust Core 交互

### 利益相关者

- **最终用户**: 需要简单、安全、直观的密钥管理体验
- **安全审计**: 需要验证所有安全约束被正确执行
- **开发者**: 需要清晰、可维护的代码结构

---

## Goals / Non-Goals

### Goals (目标)

1. ✅ 实现完整的用户界面，覆盖所有核心功能
2. ✅ 遵循 Material Design 3 设计规范
3. ✅ 采用深色主题，符合"后量子安全"产品气质
4. ✅ 实现流畅的动画和微交互
5. ✅ 提供清晰的视觉反馈，让用户了解系统状态
6. ✅ 支持生物识别认证（指纹、面部识别）
7. ✅ 在 Degraded 和 Revoked 状态下提供合适的 UI

### Non-Goals (非目标)

1. ❌ 不实现自定义主题（仅深色主题）
2. ❌ 不支持多语言（仅中文）
3. ❌ 不实现 Web 界面
4. ❌ 不实现社交恢复功能（L2/L3 模式）
5. ❌ 不实现高级统计分析功能

---

## 设计决策

### Decision 1: 导航架构

**选择**: Jetpack Navigation Compose + 单 Activity 架构

**理由**:
- Google 推荐的现代 Android 应用架构
- 类型安全的导航
- 易于测试和维护
- 支持深度链接和状态恢复

**替代方案**:
- ❌ 多 Activity 架构：过时，难以维护状态
- ❌ 自定义导航系统：重复造轮子，增加复杂度

### Decision 2: 状态管理

**选择**: Kotlin StateFlow + Compose State

**理由**:
- 原生支持 Compose
- 响应式编程模型
- 易于测试和调试
- 与 Android Lifecycle 完美集成

**替代方案**:
- ❌ Redux-style 单向数据流：对于此项目过于复杂
- ❌ 手动状态传递：容易出错，难以维护

### Decision 3: UI 组件库

**选择**: Material Design 3 (Material3)

**理由**:
- Google 官方设计系统
- 内置深色主题支持
- 丰富的组件库
- 与 Compose 深度集成

**替代方案**:
- ❌ 自定义组件库：开发成本高，难以保证一致性
- ❌ Material Design 2: 过时的设计系统

### Decision 4: 动画框架

**选择**: Compose Animation API

**理由**:
- 原生支持，性能优异
- 声明式 API
- 易于实现复杂动画
- 支持 Shared Element Transition

**替代方案**:
- ❌ View-based 动画系统：不适用于 Compose
- ❌ Lottie: 增加依赖和包大小

### Decision 5: 错误处理

**选择**: Sealed Class + UI 状态封装

**理由**:
- 类型安全的错误处理
- 编译时穷举检查
- 易于扩展新的错误类型
- 符合 Kotlin 最佳实践

**实现示例**:
```kotlin
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val recoverable: Boolean) : UiState<Nothing>()
}
```

---

## UI 设计规范

### 色彩系统

#### 主色调 (Primary Colors)
```kotlin
// Quantum Blue - 传达科技与安全
val Primary = Color(0xFF00BCD4)      // #00BCD4
val OnPrimary = Color(0xFF000000)     // #000000
val PrimaryContainer = Color(0xFF008B9D)  // #008B9D
val OnPrimaryContainer = Color(0xFFFFFFFF) // #FFFFFF
```

#### 次要色调 (Secondary Colors)
```kotlin
// Deep Space - 背景色系
val Secondary = Color(0xFF00BCD4)
val OnSecondary = Color(0xFF000000)
val SecondaryContainer = Color(0xFF008B9D)
val OnSecondaryContainer = Color(0xFFFFFFFF)

// Backgrounds
val Background = Color(0xFF121212)    // #121212 (Material Dark)
val OnBackground = Color(0xFFE0E0E0)  // #E0E0E0
val Surface = Color(0xFF1E1E1E)        // #1E1E1E
val OnSurface = Color(0xFFE0E0E0)
```

#### 功能色调 (Functional Colors)
```kotlin
// Quantum Red - 错误与危险
val Error = Color(0xFFFF5252)         // #FF5252
val OnError = Color(0xFFFFFFFF)

// Quantum Green - 安全状态
val Success = Color(0xFF69F0AE)        // #69F0AE
val OnSuccess = Color(0xFF000000)

// Quantum Yellow - 警告与关注
val Warning = Color(0xFFFFD740)       // #FFD740
val OnWarning = Color(0xFF000000)

// Info - 信息提示
val Info = Color(0xFF40C4FF)          // #40C4FF
val OnInfo = Color(0xFF000000)
```

#### 状态机色彩映射
```kotlin
// 状态机颜色
sealed class MachineStateColor(val color: Color) {
    data object Idle : MachineStateColor(Color(0xFF69F0AE))      // Green
    data object Decrypting : MachineStateColor(Color(0xFF00BCD4)) // Blue
    data object Rekeying : MachineStateColor(Color(0xFFFFD740))   // Yellow
    data object Degraded : MachineStateColor(Color(0xFFFF5252))  // Red
    data object Revoked : MachineStateColor(Color(0xFFB00020))    // Dark Red
}
```

### 排版系统 (Typography)

```kotlin
val Typography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 57.sp,
        lineHeight = 64.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)
```

### 形状系统 (Shape)

```kotlin
val Shapes = Shapes(
    extraSmall = CornerSize(4.dp),
    small = CornerSize(8.dp),
    medium = CornerSize(12.dp),
    large = CornerSize(16.dp),
    extraLarge = CornerSize(28.dp),
)
```

### 动画规范

#### 标准动画时长
```kotlin
object AnimationDuration {
    const val Instant = 50     // 即时反馈
    const val Fast = 150       // 快速过渡
    const val Normal = 300      // 标准动画
    const val Slow = 500        // 复杂动画
    const val Glacial = 1000    // 特殊效果
}
```

#### 缓动曲线
```kotlin
import androidx.compose.animation.core.*

// 标准缓动
val StandardEasing = FastOutSlowInEasing

// 强强调缓动（用于进入）
val EmphasizedEasing = FastOutLinearInEasing

// 弱强调缓动（用于退出）
val EmphasizedDecelerateEasing = LinearOutSlowInEasing

// 线性（用于进度条）
val LinearEasing = LinearEasing
```

#### 关键动画场景

| 场景 | 动画类型 | 时长 | 缓动曲线 |
|------|----------|------|----------|
| 生物识别成功 | Fade In + Scale | 300ms | EmphasizedDecelerate |
| 密钥轮换 | Rotation + Fade | 500ms | StandardEasing |
| 设备撤销 | Shrink + Fade Out | 400ms | FastOutSlowIn |
| 否决信号 | Pulse (循环) | 1000ms | Linear |
| 页面切换 | Shared Element | 350ms | StandardEasing |
| 状态指示器 | Color Change | 200ms | FastOutSlowIn |

---

## 组件架构

### 组件层次结构

```
MainActivity
    └── AeternumApp
            └── AeternumNavHost
                    ├── OnboardingGraph
                    │   ├── WelcomeScreen
                    │   ├── MnemonicBackupScreen
                    │   └── RegistrationScreen
                    ├── AuthGraph
                    │   └── BiometricPromptScreen
                    ├── MainGraph
                    │   ├── MainScreen (Idle)
                    │   ├── VaultScreen (Decrypting)
                    │   └── RekeyingScreen (Rekeying)
                    ├── DevicesGraph
                    │   ├── DeviceListScreen
                    │   └── DeviceDetailScreen
                    ├── RecoveryGraph
                    │   ├── RecoveryInitiateScreen
                    │   └── VetoNotificationScreen
                    ├── DegradedGraph
                    │   └── DegradedModeScreen
                    └── RevokedGraph
                        └── RevokedScreen
```

### 状态管理架构

```kotlin
// 全局状态管理
@Composable
fun rememberAeternumAppState(): AeternumAppState {
    val viewModel: AeternumViewModel = viewModel()
    remember(viewModel) { viewModel.state }
}

// UI 状态
sealed class AeternumUiState {
    data object Uninitialized : AeternumUiState()
    data object Onboarding : AeternumUiState()
    data class Active(val subState: ActiveSubState) : AeternumUiState()
    data object Degraded : AeternumUiState()
    data object Revoked : AeternumUiState()
}

sealed class ActiveSubState {
    data object Idle : ActiveSubState()
    data class Decrypting(val session: VaultSessionHandle) : ActiveSubState()
    data class Rekeying(val progress: Float) : ActiveSubState()
}
```

### ViewModel 架构

```kotlin
class AeternumViewModel(
    private val securityManager: AndroidSecurityManager,
    private val vaultRepository: VaultRepository,
    private val bridge: AeternumBridge,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AeternumUiState>(AeternumUiState.Uninitialized)
    val uiState: StateFlow<AeternumUiState> = _uiState.asStateFlow()

    init {
        observeVaultState()
        observeSecurityState()
    }

    private fun observeVaultState() {
        viewModelScope.launch {
            vaultRepository.vaultState.collect { state ->
                _uiState.value = when (state) {
                    is VaultState.NotInitialized -> AeternumUiState.Onboarding
                    is VaultState.Locked -> AeternumUiState.Active(ActiveSubState.Idle)
                    is VaultState.Unlocked -> {
                        val session = // 获取 session
                        AeternumUiState.Active(ActiveSubState.Decrypting(session))
                    }
                }
            }
        }
    }

    fun requestBiometricUnlock() {
        viewModelScope.launch {
            when (val result = securityManager.authenticate()) {
                is BiometricResult.Success -> {
                    _uiState.value = AeternumUiState.Active(
                        ActiveSubState.Decrypting(result.session)
                    )
                }
                is BiometricResult.Failed -> {
                    // 显示错误
                }
                is BiometricResult.Cancelled -> {
                    // 取消操作
                }
            }
        }
    }
}
```

---

## 关键屏幕设计

### 1. 欢迎屏幕 (WelcomeScreen)

**目的**: 首次启动时的欢迎界面

**布局**:
```
┌─────────────────────────┐
│                         │
│                         │
│         [Logo]          │
│       Aeternum          │
│    后量子安全密钥管理     │
│                         │
│    [开始设置] 按钮       │
│                         │
│                         │
└─────────────────────────┘
```

**关键元素**:
- 居中的 Logo 和应用名称
- 简洁的产品描述
- "开始设置" 主按钮

### 2. 助记词备份屏幕 (MnemonicBackupScreen)

**目的**: 安全地展示和确认助记词

**布局**:
```
┌─────────────────────────┐
│  ← 创建备份            │
│                         │
│   请安全保存您的助记词   │
│                         │
│  ┌───────────────────┐ │
│  │ word1 word2 word3 │ │
│  │ word4 word5 word6 │ │
│  │ word7 word8 ...  │ │
│  └───────────────────┘ │
│                         │
│   [复制] [显示]        │
│                         │
│   ⚠️ 警告提示         │
│                         │
│   [我已经安全保存]      │
│                         │
└─────────────────────────┘
```

**关键元素**:
- 24 个助记词以网格形式展示
- 复制按钮（可选）
- 安全警告（红色高亮）
- 确认按钮（需等待 10 秒后启用）

### 3. 生物识别认证屏幕 (BiometricPromptScreen)

**目的**: 通过生物识别解锁 Vault

**布局**:
```
┌─────────────────────────┐
│                         │
│                         │
│      [指纹图标]         │
│                         │
│   请验证身份以访问       │
│                         │
│    [取消]              │
│                         │
│                         │
└─────────────────────────┘
```

**关键元素**:
- 系统生物识别对话框
- 加载指示器
- 取消按钮

### 4. 主屏幕 (MainScreen - Idle)

**目的**: 显示设备状态和快速操作

**布局**:
```
┌─────────────────────────┐
│  ☰  Aeternum    ⚙️   │
├─────────────────────────┤
│                         │
│  ┌───────────────────┐ │
│  │ 🔒 安全  Epoch 5  │ │
│  │ 2 设备已连接        │ │
│  └───────────────────┘ │
│                         │
│  [查看密钥]            │
│  [设备管理]            │
│  [密钥轮换]            │
│                         │
│  最近活动               │
│  • 设备 "Pixel" 已连接 │
│  • Epoch 5 已完成     │
│                         │
└─────────────────────────┘
```

**关键元素**:
- 状态卡片（安全/警告/危险）
- 纪元徽章
- 快速操作按钮
- 最近活动列表

### 5. 密钥轮换屏幕 (RekeyingScreen)

**目的**: 显示 PQRR 密钥轮换进度

**布局**:
```
┌─────────────────────────┐
│  ← 密钥轮换            │
│                         │
│      [旋转动画]         │
│                         │
│   正在轮换密钥...       │
│   Epoch 5 → 6          │
│                         │
│   ████████░░ 80%       │
│                         │
│   ⚠️ 请勿关闭应用      │
│                         │
└─────────────────────────┘
```

**关键元素**:
- 旋转动画（量子效果）
- 进度条
- 新旧纪元对比
- 警告提示

### 6. 设备列表屏幕 (DeviceListScreen)

**目的**: 管理已注册的设备

**布局**:
```
┌─────────────────────────┐
│  ← 设备管理      +     │
├─────────────────────────┤
│                         │
│  我的设备              │
│                         │
│  ┌───────────────────┐ │
│  │ Pixel 9 Pro       │ │
│  │ ✅ 活跃 • Epoch 5 │ │
│  │ 本机              │ │
│  └───────────────────┘ │
│                         │
│  ┌───────────────────┐ │
│  │ iPad Pro          │ │
│  │ ✅ 活跃 • Epoch 5 │ │
│  │ 最后在线: 2h 前   │ │
│  └───────────────────┘ │
│                         │
│  ┌───────────────────┐ │
│  │ Pixel 8           │ │
│  │ ⚠️ 降级 • Epoch 4 │ │
│  │ 最后在线: 7d 前   │ │
│  └───────────────────┘ │
│                         │
└─────────────────────────┘
```

**关键元素**:
- 设备卡片列表
- 状态指示器
- 纪元信息
- 最后在线时间

### 7. 降级模式屏幕 (DegradedModeScreen)

**目的**: 设备完整性验证失败时显示

**布局**:
```
┌─────────────────────────┐
│                         │
│                         │
│      [警告图标]         │
│                         │
│   安全模式已激活        │
│                         │
│   设备完整性验证失败    │
│   请检查设备是否已root  │
│   或安装了未经授权的应用 │
│                         │
│   [了解详情]            │
│   [重新验证]           │
│                         │
└─────────────────────────┘
```

**关键元素**:
- 警告图标（红色）
- 错误描述
- 重新验证按钮
- 了解详情链接

### 8. 撤销屏幕 (RevokedScreen)

**目的**: 设备已被撤销的终态提示

**布局**:
```
┌─────────────────────────┐
│                         │
│                         │
│   [撤销图标]            │
│                         │
│   此设备已被撤销        │
│                         │
│   所有密钥和数据已清除  │
│                         │
│   如需重新使用，请       │
│   在其他设备上重新注册   │
│                         │
│   [了解原因]            │
│                         │
└─────────────────────────┘
```

**关键元素**:
- 撤销图标
- 不可逆的状态提示
- 了解原因链接

---

## 安全检查清单

每个 UI 组件必须满足以下安全要求：

### 检查点

- [ ] **不持有明文密钥**: UI 层不存储任何密钥材料
- [ ] **不记录敏感信息**: 日志中不包含密钥、助记词或敏感数据
- [ ] **状态一致性**: UI 状态准确反映底层状态机
- [ ] **生物识别认证**: 所有敏感操作需要 Class 3 生物识别
- [ ] **会话超时**: 解密会话在后台时自动锁定
- [ ] **截屏保护**: 敏感界面禁止截屏
- [ ] **防窥视**: 使用 FLAG_SECURE 防止截屏录屏
- [ ] **Play Integrity**: 定期验证设备完整性

### 安全边界示例

```kotlin
// ❌ 错误：UI 层持有密钥
class WrongViewModel {
    private var decryptedKey: ByteArray? = null  // 禁止！
}

// ✅ 正确：通过 Rust 句柄访问
class CorrectViewModel {
    private var sessionHandle: VaultSessionHandle? = null  // 正确

    fun decryptField(id: String): String {
        return sessionHandle?.decryptField(id, "field") ?: ""
        // 明文仅在 Rust 内存中存在
    }
}
```

---

## UniFFI 接口需求分析

### 现有接口评估

根据 `core/uniffi/aeternum.udl`，现有接口包括：

| 接口 | 状态 | 说明 |
|------|------|------|
| `PqrrStateMachine` | ✅ 已实现 | 状态机核心接口 |
| `get_current_epoch()` | ✅ 已实现 | 获取当前纪元 |
| `get_state()` | ✅ 已实现 | 获取协议状态 |
| `get_device_headers()` | ✅ 已实现 | 获取设备头信息列表 |
| `transition_to_rekeying()` | ✅ 已实现 | 转换到轮换状态 |
| `check_veto_supremacy()` | ✅ 已实现 | 检查否决权 |

### 需要新增的接口

根据 UI 层需求，以下接口需要新增到 UDL：

```idl
namespace aeternum {
    // Vault 会话接口 - 用于解密操作
    interface VaultSession {
        // 获取脱敏的记录 ID 列表
        sequence<string> list_record_ids();

        // 解密字段 - 明文仅在 Rust 内存中
        [Throws=PqrrError]
        string decrypt_field(string record_id, string field_key);

        // 显式锁定 - 清除内存中的密钥
        void lock();

        // 检查会话是否有效
        boolean is_valid();
    };

    // 扩展 PqrrStateMachine 或创建 AeternumEngine
    interface AeternumEngine {
        constructor(string vault_path);

        // 解锁 Vault - 返回会话句柄
        [Throws=PqrrError]
        VaultSession unlock(sequence<u8> hardware_key_blob);

        // 准备纪元升级 - 返回新加密数据
        [Throws=PqrrError]
        dictionary RekeyResult {
            sequence<u8> new_vault_blob;
            u32 new_epoch;
        };

        // 获取脱敏的设备列表
        [Throws=PqrrError]
        sequence<DeviceInfo> get_device_list();

        // 撤销设备
        [Throws=PqrrError]
        void revoke_device(sequence<u8> device_id);

        // 发起恢复
        [Throws=PqrrError]
        string initiate_recovery();

        // 提交否决
        [Throws=PqrrError]
        void submit_veto(string recovery_id);

        // 验证 Vault 完整性
        [Throws=PqrrError]
        boolean verify_vault_integrity(sequence<u8> vault_blob);
    };

    // 设备信息字典
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

### 接口缺口总结

| 功能 | 现有接口 | 需要新增 | 优先级 |
|------|---------|---------|--------|
| 状态机查询 | ✅ | - | - |
| 纪元获取 | ✅ | - | - |
| 设备头信息 | ✅ | - | - |
| Vault 解锁 | ❌ | ✅ | P0 |
| 字段解密 | ❌ | ✅ | P0 |
| 设备列表详情 | ❌ | ✅ | P1 |
| 撤销设备 | ❌ | ✅ | P1 |
| 恢复发起 | ❌ | ✅ | P1 |
| 否决提交 | ❌ | ✅ | P1 |

### 实现计划

1. **阶段 0.1**：扩展现有 `PqrrStateMachine` 或创建新 `AeternumEngine` 接口
2. **阶段 0.2**：实现 `VaultSession` 接口及其 Rust 后端
3. **阶段 0.3**：运行 `./scripts/generate-bridge.sh` 重新生成桥接代码
4. **阶段 0.4**：验证生成的 Kotlin 接口可用性

---

## 错误处理映射

### Rust 错误到 Kotlin UI 错误映射表

| Rust Error (PqrrError) | Kotlin UI Error | 用户提示 | 可恢复 |
|------------------------|----------------|---------|--------|
| `EpochRegression` | `UiError.EpochError` | "纪元版本冲突，请刷新应用" | ❌ |
| `HeaderIncomplete` | `UiError.DataError` | "数据不完整，请重新同步" | ❌ |
| `InsufficientPrivileges` | `UiError.AuthError` | "权限不足，请重新认证" | ✅ |
| `PermissionDenied` | `UiError.AuthError` | "访问被拒绝" | ❌ |
| `Vetoed` | `UiError.VetoError` | "操作已被其他设备否决" | ❌ |
| `InvalidStateTransition` | `UiError.StateError` | "状态转换无效，请重试" | ✅ |
| `StorageError` | `UiError.StorageError` | "存储操作失败，请检查存储空间" | ✅ |

### UI 错误状态定义

```kotlin
/**
 * UI 错误封装 - Sealed Class 实现
 *
 * INVARIANT: 类型安全的错误处理，编译时穷举检查
 */
sealed class UiError {
    abstract val message: String
    abstract val recoverable: Boolean

    data class EpochError(
        override val message: String,
        val currentEpoch: UInt,
        val expectedEpoch: UInt,
    ) : UiError() {
        override val recoverable = false
    }

    data class DataError(
        override val message: String,
        val missingFields: List<String>,
    ) : UiError() {
        override val recoverable = false
    }

    data class AuthError(
        override val message: String,
        val requiresBiometric: Boolean = true,
    ) : UiError() {
        override val recoverable = true
    }

    data class VetoError(
        override val message: String,
        val vetoingDevice: String,
        val remainingWindow: Duration,
    ) : UiError() {
        override val recoverable = false
    }

    data class StateError(
        override val message: String,
        val currentState: String,
        val attemptedTransition: String,
    ) : UiError() {
        override val recoverable = true
    }

    data class StorageError(
        override val message: String,
        val availableSpace: Long?,
    ) : UiError() {
        override val recoverable = true
    }

    data class NetworkError(
        override val message: String,
        val isOffline: Boolean,
    ) : UiError() {
        override val recoverable = true
    }

    data class UnknownError(
        override val message: String,
        val originalError: String? = null,
    ) : UiError() {
        override val recoverable = true
    }
}

/**
 * UI 状态封装 - 包含错误状态
 */
sealed class UiState<out T> {
    data object Idle : UiState<Nothing>()
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val error: UiError) : UiState<Nothing>()
}
```

### 错误处理最佳实践

```kotlin
// ViewModel 中的错误处理示例
class AeternumViewModel(
    private val bridge: AeternumBridge,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<DeviceInfo>>>(UiState.Idle)
    val uiState: StateFlow<UiState<List<DeviceInfo>>> = _uiState.asStateFlow()

    fun loadDevices() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val devices = bridge.getDeviceList()
                _uiState.value = UiState.Success(devices)
            } catch (e: PqrrException) {
                val uiError = when (e.errorCode) {
                    PqrrErrorCode.EpochRegression -> UiError.EpochError(
                        message = "纪元版本冲突",
                        currentEpoch = e.currentEpoch,
                        expectedEpoch = e.expectedEpoch,
                    )
                    PqrrErrorCode.StorageError -> UiError.StorageError(
                        message = "存储操作失败",
                        availableSpace = e.availableSpace,
                    )
                    // ... 其他映射
                }
                _uiState.value = UiState.Error(uiError)
            }
        }
    }
}
```

---

## 动画性能基准

### 性能要求

| 动画类型 | 最低帧率 | 推荐帧率 | 最大延迟 | 适用设备 |
|---------|---------|---------|---------|---------|
| 页面切换 | 30 fps | 60 fps | 100ms | 所有设备 |
| 生物识别成功 | 30 fps | 60 fps | 150ms | 所有设备 |
| 密钥轮换旋转 | 24 fps | 60 fps | 200ms | 中高端设备 |
| 否决脉冲 | 24 fps | 60 fps | 200ms | 中高端设备 |
| 列表滚动 | 30 fps | 60 fps | 16ms/帧 | 所有设备 |

### 设备分级与降级策略

```kotlin
/**
 * 设备性能等级 - 用于自适应动画
 */
enum class DevicePerformanceTier {
    LOW,      // 旧设备，关闭复杂动画
    MEDIUM,   // 主流设备，简化动画
    HIGH,     // 高端设备，完整动画
    ULTRA,    // 旗舰设备，所有特效
}

/**
 * 性能检测器
 */
object PerformanceDetector {
    fun detectTier(): DevicePerformanceTier {
        val isLowRam = ActivityManager.isLowRamDevice()
        val cores = Runtime.getRuntime().availableProcessors()
        val totalMemory = ActivityManager.MemoryInfo().totalMem

        return when {
            isLowRam || cores < 4 -> DevicePerformanceTier.LOW
            cores >= 8 && totalMemory > 8_000_000_000L -> DevicePerformanceTier.HIGH
            cores >= 4 && totalMemory > 4_000_000_000L -> DevicePerformanceTier.MEDIUM
            else -> DevicePerformanceTier.LOW
        }
    }
}

/**
 * 动画配置 - 根据设备性能自适应
 */
data class AnimationConfig(
    val enableComplexAnimations: Boolean,
    val maxFrameRate: Int,
    val easing: Easing,
    val durationScale: Float = 1.0f,
) {
    companion object {
        fun forTier(tier: DevicePerformanceTier): AnimationConfig = when (tier) {
            DevicePerformanceTier.LOW -> AnimationConfig(
                enableComplexAnimations = false,
                maxFrameRate = 30,
                easing = LinearEasing,
                durationScale = 0.5f, // 加速完成
            )
            DevicePerformanceTier.MEDIUM -> AnimationConfig(
                enableComplexAnimations = true,
                maxFrameRate = 30,
                easing = FastOutSlowInEasing,
                durationScale = 0.75f,
            )
            DevicePerformanceTier.HIGH -> AnimationConfig(
                enableComplexAnimations = true,
                maxFrameRate = 60,
                easing = EmphasizedDecelerateEasing,
                durationScale = 1.0f,
            )
            DevicePerformanceTier.ULTRA -> AnimationConfig(
                enableComplexAnimations = true,
                maxFrameRate = 60,
                easing = EmphasizedDecelerateEasing,
                durationScale = 1.0f,
            )
        }
    }
}
```

### 性能监控

```kotlin
/**
 * 动画性能监控器
 */
class AnimationPerformanceMonitor {
    private val frameTimes = ArrayDeque<Long>(maxCapacity = 60)

    fun recordFrame() {
        frameTimes.addLast(System.nanoTime())
    }

    fun getAverageFps(): Double {
        if (frameTimes.size < 2) return 60.0
        val duration = (frameTimes.last() - frameTimes.first()) / 1_000_000_000.0
        return frameTimes.size / duration
    }

    fun shouldDowngradeAnimations(): Boolean {
        return getAverageFps() < 24.0 // 连续低于 24fps
    }
}
```

---

## 离线模式处理

### 离线状态检测

```kotlin
/**
 * 网络状态监听器
 */
class NetworkMonitor(private val context: Context) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isOnline: StateFlow<Boolean> = flow {
        emit(checkConnectivity())
    }.stateIn(
        scope = CoroutineScope(Dispatchers.Default),
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = true,
    )

    private fun checkConnectivity(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
```

### 离线模式 UI 行为

| 场景 | 在线行为 | 离线行为 |
|------|---------|---------|
| 主屏幕 | 显示完整状态 | 显示 "离线模式" 横幅 |
| 设备列表 | 显示实时状态 | 显示缓存状态 + "上次同步: 时间" |
| 撤销设备 | 立即生效 | 加入队列，"操作将在恢复网络后执行" |
| 密钥轮换 | 实时进度 | 禁用操作，提示 "需要网络连接" |
| 恢复流程 | 发起请求 | 禁用操作，提示 "需要网络连接" |
| 否决操作 | 立即提交 | 禁用操作，提示 "需要网络连接" |

### 离线队列机制

```kotlin
/**
 * 离线操作队列
 */
class OfflineOperationQueue(
    private val bridge: AeternumBridge,
    private val networkMonitor: NetworkMonitor,
) {
    private val queue = Channel<OfflineOperation>(capacity = 64)

    sealed class OfflineOperation {
        data class RevokeDevice(val deviceId: ByteArray) : OfflineOperation()
        data class SubmitVeto(val recoveryId: String) : OfflineOperation()
    }

    init {
        // 当网络恢复时，自动执行队列中的操作
        networkMonitor.isOnline
            .drop(1) // 跳过初始值
            .filter { it } // 仅在网络恢复时
            .onEach { processQueue() }
            .launchIn(CoroutineScope(Dispatchers.IO))
    }

    fun enqueue(operation: OfflineOperation) {
        queue.trySend(operation)
    }

    private suspend fun processQueue() {
        for (operation in queue) {
            try {
                when (operation) {
                    is OfflineOperation.RevokeDevice -> {
                        bridge.revokeDevice(operation.deviceId)
                    }
                    is OfflineOperation.SubmitVeto -> {
                        bridge.submitVeto(operation.recoveryId)
                    }
                }
            } catch (e: Exception) {
                // 记录错误，继续处理下一个
                Log.e("OfflineQueue", "Failed to process $operation", e)
            }
        }
    }
}
```

---

## 国际化架构预留

### 字符串资源组织

虽然当前版本不支持多语言，但字符串资源应组织为易于后续国际化的形式：

```xml
<!-- res/values/strings.xml -->
<resources>
    <!-- 应用名称 -->
    <string name="app_name">Aeternum</string>

    <!-- 通用 -->
    <string name="common_confirm">确认</string>
    <string name="common_cancel">取消</string>
    <string name="common_retry">重试</string>
    <string name="common_loading">加载中...</string>

    <!-- 状态 -->
    <string name="status_secure">安全</string>
    <string name="status_warning">警告</string>
    <string name="status_danger">危险</string>

    <!-- 纪元 -->
    <string name="epoch_label">Epoch %d</string>
    <string name="epoch_upgrading">Epoch %d → %d</string>

    <!-- 错误 -->
    <string name="error_epoch_conflict">纪元版本冲突</string>
    <string name="error_incomplete_data">数据不完整</string>
    <string name="error_auth_failed">认证失败</string>
    <string name="error_vetoed">操作已被否决</string>

    <!-- 离线模式 -->
    <string name="offline_banner">离线模式</string>
    <string name="offline_last_sync">上次同步: %s</string>
    <string name="offline_operation_queued">操作已加入队列，将在恢复网络后执行</string>

    <!-- 生物识别 -->
    <string name="biometric_title">验证身份</string>
    <string name="biometric_subtitle">使用指纹或面部识别解锁 Aeternum</string>
    <string name="biometric_failed">生物识别验证失败，请重试</string>

    <!-- 设备管理 -->
    <string name="device_list_title">我的设备</string>
    <string name="device_active">活跃</string>
    <string name="device_degraded">降级</string>
    <string name="device_revoked">已撤销</string>
    <string name="device_this_device">本机</string>
    <string name="device_last_seen">最后在线: %s</string>
    <string name="device_revoke_confirm">确认要撤销此设备吗？</string>
    <string name="device_revoke_warning">撤销后，该设备将无法访问 Vault</string>

    <!-- 密钥轮换 -->
    <string name="rekeying_title">密钥轮换中</string>
    <string name="rekeying_progress">%d%%</string>
    <string name="rekeying_warning">请勿关闭应用</string>

    <!-- 降级模式 -->
    <string name="degraded_title">安全模式已激活</string>
    <string name="degraded_message">设备完整性验证失败</string>
    <string name="degraded_instruction">请检查设备是否已 root 或安装了未经授权的应用</string>
    <string name="degraded_reverify">重新验证</string>

    <!-- 撤销状态 -->
    <string name="revoked_title">此设备已被撤销</string>
    <string name="revoked_message">所有密钥和数据已清除</string>
    <string name="revoked_instruction">如需重新使用，请在其他设备上重新注册</string>
</resources>
```

### 代码中使用字符串资源

```kotlin
// ✅ 正确 - 使用字符串资源
Text(text = stringResource(R.string.epoch_label, currentEpoch))

// ❌ 错误 - 硬编码字符串
Text(text = "Epoch $currentEpoch")
```

---

## Risks / Trade-offs

### Risk 1: 动画性能

**风险**: 复杂动画可能在低端设备上卡顿

**缓解措施**:
- 使用 Compose Animation API（硬件加速）
- 提供动画开关（无障碍模式）
- 在低端设备上降低动画复杂度

### Risk 2: 状态同步

**风险**: UI 状态与 Rust Core 状态不同步

**缓解措施**:
- 使用 StateFlow 进行响应式更新
- 实现状态一致性检查
- 提供状态重置机制

### Risk 3: 生物识别兼容性

**风险**: 部分设备不支持 Class 3 生物识别

**缓解措施**:
- 提供降级方案（设备凭据）
- 清晰显示认证要求
- 引导用户升级设备

### Risk 4: 用户体验复杂度

**风险**: 后量子安全概念可能让用户困惑

**缓解措施**:
- 使用简洁的语言解释安全功能
- 提供渐进式引导
- 默认配置适合大多数用户

---

## Migration Plan

### 阶段 1: 基础架构
1. 设置 Navigation Compose
2. 创建主题系统
3. 实现基础组件库

### 阶段 2: 核心流程
1. 实现初始化流程
2. 实现生物识别认证
3. 实现主屏幕

### 阶段 3: 管理功能
1. 实现设备管理界面
2. 实现密钥轮换界面
3. 实现恢复流程界面

### 阶段 4: 异常处理
1. 实现降级模式界面
2. 实现撤销状态界面
3. 完善错误处理

### 阶段 5: 测试与优化
1. 编写单元测试
2. 编写 UI 测试
3. 性能优化

---

## Open Questions

1. **Q**: 是否需要支持多语言？
   **A**: 暂不支持，仅中文。如需支持，后续添加国际化。

2. **Q**: 是否需要支持浅色主题？
   **A**: 暂不支持，仅深色主题。浅色主题与"后量子安全"产品气质不符。

3. **Q**: 是否需要支持平板布局？
   **A**: 基础支持，但优先手机体验。后续可优化平板布局。

4. **Q**: 是否需要支持辅助功能？
   **A**: 是，必须支持。遵循 Android 辅助功能指南。

---

**文档版本**: 1.0.0
**最后更新**: 2026-02-15
**作者**: Aeternum Team
