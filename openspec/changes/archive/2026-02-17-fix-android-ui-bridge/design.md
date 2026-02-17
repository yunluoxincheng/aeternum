# Android UI 桥接修复设计文档

## Context

`add-android-ui-layer` 提案已完成 95% 的功能实现，但在编译时发现 80+ 错误。这些错误主要分为三类：

1. **UniFFI 桥接不完整**: UI 层需要的接口未在 Rust Core 中实现
2. **Compose API 版本兼容性**: 使用的 API 在当前 BOM 版本中已废弃或签名更改
3. **无障碍 API 更新**: 部分无障碍 API 已被替换

本文档描述修复方案的设计决策和实现细节。

---

## Goals / Non-Goals

### Goals (目标)

1. ✅ 修复所有编译错误，确保代码可以成功构建
2. ✅ 保持与现有架构和安全约束的兼容性
3. ✅ 不引入新的功能或破坏性变更
4. ✅ 确保所有测试通过
5. ✅ 验证安全边界未被破坏

### Non-Goals (非目标)

1. ❌ 不修改 UI 设计和交互逻辑
2. ❌ 不改变安全架构
3. ❌ 不添加新功能
4. ❌ 不降级依赖版本（使用替代方案）

---

## 设计决策

### Decision 1: UniFFI 接口扩展策略

**选择**: 在现有 `aeternum.udl` 中扩展接口，而非创建新文件

**理由**:
- 保持接口定义集中管理
- 避免桥接代码分散
- 简化依赖关系
- 与现有 `PqrrStateMachine` 共享类型定义

**接口层次结构**:
```
aeternum.udl
├── ProtocolState (已有)
├── PqrrError (已有)
├── PqrrStateMachine (已有)
├── DeviceHeaderInfo (已有)
├── VaultSession (新增)
├── DeviceInfo (已有，扩展)
└── AeternumEngine (新增)
```

### Decision 2: Rust 后端实现架构

**选择**: 在 `core/src/bridge/` 创建新的桥接模块

**模块结构**:
```rust
core/src/bridge/
├── mod.rs              // 模块入口，导出公共接口
├── engine.rs           // AeternumEngine 实现
├── session.rs          // VaultSession 实现
└── device.rs           // DeviceInfo 相关工具函数
```

**职责分离**:
- `engine.rs`: 负责引擎级别操作（初始化、解锁、设备管理）
- `session.rs`: 负责会话级别操作（加解密、存储）
- `device.rs`: 负责设备信息转换和格式化

**与现有模块的关系**:
```
core/src/
├── crypto/     (密码学原语)
├── storage/    (存储引擎)
├── protocol/   (PQRR 协议)
├── sync/       (同步协议)
└── bridge/     (新增) - UniFFI 桥接
```

### Decision 3: Compose API 替换策略

**选择**: 使用功能等效的替代 API，而非降级版本

**动画 API 替换**:

| 场景 | 废弃 API | 替代方案 |
|------|----------|----------|
| 值动画 | `animateValue` | `animateFloatAsState` |
| 向量动画 | `VectorConverter` | 手动 Float → Vector 转换 |
| 无限循环 | `infiniteRepeatable` | 保留（可用） |

**示例代码**:
```kotlin
// 修复前
val rotation by animateValue(
    initialValue = 0f,
    targetValue = 360f,
    typeConverter = Float.VectorConverter,
    animationSpec = infiniteRepeatable(tween(1000))
)

// 修复后
val rotation by animateFloatAsState(
    targetValue = if (atEnd) 360f else 0f,
    animationSpec = infiniteRepeatable(
        animation = tween(1000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart
    ),
    label = "rotation"
)
```

### Decision 4: Material3 组件参数处理

**选择**: 提供所有新增参数的合理默认值

**Switch 组件参数**:

```kotlin
Switch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    // 新增参数 - 使用主题色
    colors = SwitchDefaults.colors(
        checkedBorderColor = MaterialTheme.colorScheme.primary,
        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        checkedIconColor = MaterialTheme.colorScheme.onPrimary,
        uncheckedIconColor = MaterialTheme.colorScheme.outline,
    ),
    // 其他新增参数...
)
```

### Decision 5: 无障碍 API 迁移

**选择**: 使用最新的 Compose 无障碍 API

**API 迁移映射**:

| 功能 | 旧 API | 新 API |
|------|--------|--------|
| 语音公告 | `SemanticsProperties.AccessibilityAnnouncement` | `SemanticsProperties.announce` |
| 高对比度检测 | `isHighTextContrastEnabled` (系统) | `LocalAccessibilityManager` |
| 文本框范围 | `SemanticsProperties.TextBox` | `SemanticsProperties.Text` + `TextRange` |

---

## 实现细节

### 1. UniFFI 接口定义

#### VaultSession 接口

```idl
interface VaultSession {
    sequence<string> list_record_ids();

    [Throws=PqrrError]
    string decrypt_field(string record_id, string field_key);

    [Throws=PqrrError]
    void store_entry(string record_id, string field_key, string plaintext_value);

    [Throws=PqrrError]
    string retrieve_entry(string record_id, string field_key);

    void lock();

    boolean is_valid();
};
```

**设计考虑**:
- `decrypt_field` 和 `retrieve_entry` 的区别:
  - `decrypt_field`: 解密已存储的加密字段
  - `retrieve_entry`: 便捷方法，组合检索和解密
- `lock()`: 显式释放资源，触发 Zeroize
- `is_valid()`: 检查会话是否仍然有效（未过期、未被撤销）

#### AeternumEngine 接口

```idl
interface AeternumEngine {
    [Throws=PqrrError]
    constructor(string vault_path);

    [Throws=PqrrError]
    void initializeVault(sequence<u8> hardware_key_blob);

    [Throws=PqrrError]
    VaultSession unlock(sequence<u8> hardware_key_blob);

    [Throws=PqrrError]
    sequence<DeviceInfo> get_device_list();

    [Throws=PqrrError]
    void revoke_device(sequence<u8> device_id);

    [Throws=PqrrError]
    string initiate_recovery(sequence<u8> mnemonic_bytes);

    [Throws=PqrrError]
    void submit_veto(string recovery_id);

    [Throws=PqrrError]
    boolean verify_vault_integrity();

    void close();
};
```

**设计考虑**:
- `initializeVault`: 仅首次使用调用，创建新的 Vault
- `unlock`: 每次应用启动时调用，返回会话句柄
- `close`: 显式释放引擎资源
- 所有方法返回 `Result` 类型，错误通过 `PqrrError` 传播

### 2. Rust 后端实现

#### VaultSession 实现

```rust
use uniffi::*;
use zeroize::Zeroize;

pub struct AeternumVaultSession {
    inner: Arc<Mutex<VaultSessionInner>>,
}

struct VaultSessionInner {
    engine: Arc<AeternumCoreEngine>,
    session_id: Uuid,
    is_valid: bool,
    // 密钥材料仅存在于这里
    // 不会序列化到 Kotlin
}

impl VaultSessionInner {
    fn decrypt_field(&self, record_id: &str, field_key: &str) -> Result<String, PqrrError> {
        // 1. 从存储中获取加密数据
        let encrypted = self.engine.storage.get(record_id, field_key)?;

        // 2. 使用会话密钥解密
        let plaintext = self.engine.crypto.decrypt(&encrypted, &self.session_key)?;

        // 3. 转换为字符串
        Ok(String::from_utf8_lossy(&plaintext).to_string())
    }
}

impl Drop for VaultSessionInner {
    fn drop(&mut self) {
        // Zeroize 密钥材料
        self.session_key.zeroize();
        self.is_valid = false;
    }
}
```

**安全特性**:
- 使用 `Arc<Mutex<>>` 实现线程安全
- `Drop` trait 确保密钥被 Zeroize
- 密钥永不离开 Rust 内存

#### AeternumEngine 实现

```rust
pub struct AeternumCoreEngine {
    vault_path: PathBuf,
    crypto: CryptoModule,
    storage: StorageEngine,
    state_machine: Arc<RwLock<PqrrStateMachine>>,
}

impl AeternumCoreEngine {
    pub fn initialize_vault(&self, hardware_key: &[u8]) -> Result<(), PqrrError> {
        // 1. 验证硬件密钥
        let dk_hardware = self.crypto.validate_hardware_key(hardware_key)?;

        // 2. 生成新的 DEK
        let dek = self.crypto.generate_dek()?;

        // 3. 创建初始 Header
        let header = DeviceHeader::new(dk_hardware, dek);

        // 4. 存储到 Vault
        self.storage.create_vault(&header)?;

        Ok(())
    }

    pub fn unlock(&self, hardware_key: &[u8]) -> Result<VaultSession, PqrrError> {
        // 1. 验证硬件密钥
        let dk_hardware = self.crypto.validate_hardware_key(hardware_key)?;

        // 2. 加载当前 Header
        let header = self.storage.load_header()?;

        // 3. 解密 DEK
        let dek = self.crypto.unwrap_dek(&header, dk_hardware)?;

        // 4. 创建会话
        let session = AeternumVaultSession::new(
            self.clone(),
            dek,
        );

        Ok(session)
    }
}
```

### 3. Compose API 修复示例

#### QuantumAnimation 修复

```kotlin
// 修复前（使用废弃 API）
@Composable
fun RotatingQuantumRing(
    animating: Boolean,
) {
    val rotation by animateValue(
        initialValue = 0f,
        targetValue = 360f,
        typeConverter = Float.VectorConverter,
        animationSpec = infiniteRepeatable(tween(1000)),
    )
    // ...
}

// 修复后（使用兼容 API）
@Composable
fun RotatingQuantumRing(
    animating: Boolean,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    // ...
}
```

#### BiometricSuccessAnimation 修复

```kotlin
// 修复前
import android.graphics.Path
import android.graphics.CornerPathEffect

// 修复后
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

// Path 类型修复
val path = Path().apply {
    addOval(Rect(/* ... */))
}

// Stroke Cap 修复
drawScope.drawIntoCanvas {
    drawPath(
        path = path,
        style = Stroke(
            width = 4.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
    )
}
```

---

## 错误处理

### Rust 到 Kotlin 错误映射

| Rust Error (PqrrError) | Kotlin UiError | 用户提示 | 可恢复 |
|------------------------|----------------|---------|--------|
| `EpochRegression` | `UiError.EpochError` | "纪元版本冲突" | ❌ |
| `HeaderIncomplete` | `UiError.DataError` | "数据不完整" | ❌ |
| `InsufficientPrivileges` | `UiError.AuthError` | "权限不足" | ✅ |
| `StorageError` | `UiError.StorageError` | "存储失败" | ✅ |

### 错误处理示例

```kotlin
// VaultRepository.kt
fun unlockVault(hardwareKey: ByteArray): Result<VaultSessionHandle> {
    return try {
        val session = bridge.unlock(hardwareKey)
        Result.Success(VaultSessionHandle.Valid(session))
    } catch (e: PqrrException) {
        val error = when (e.errorCode) {
            PqrrErrorCode.EPOCH_REGRESSION ->
                UiError.EpochError(e.currentEpoch, e.attemptedEpoch)
            PqrrErrorCode.HEADER_INCOMPLETE ->
                UiError.DataError("设备头不完整")
            PqrrErrorCode.STORAGE_ERROR ->
                UiError.StorageError(e.message)
            else ->
                UiError.UnknownError(e.message)
        }
        Result.Error(error)
    }
}
```

---

## 测试策略

### 单元测试

```kotlin
class VaultRepositoryTest {
    @Test
    fun `unlockVault with valid key returns session`() {
        // Given
        val hardwareKey = generateTestHardwareKey()
        val mockBridge = MockAeternumBridge()

        // When
        val result = repository.unlockVault(hardwareKey)

        // Then
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
    }

    @Test
    fun `unlockVault with invalid key returns error`() {
        // Given
        val invalidKey = byteArrayOf(0x00, 0x01, /* ... */)

        // When
        val result = repository.unlockVault(invalidKey)

        // Then
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UiError.AuthError)
    }
}
```

### 集成测试

```kotlin
@RunWith(AndroidJUnit4::class)
class VaultIntegrationTest {
    @Test
    fun `full unlock, decrypt, lock flow works`() {
        // Given
        val engine = AeternumEngine(vaultPath)
        engine.initializeVault(generateHardwareKey())

        // When
        val session = engine.unlock(generateHardwareKey())
        val plaintext = session.retrieveEntry("test", "field")
        session.lock()

        // Then
        assertEquals("expected_value", plaintext)
        assertFalse(session.is_valid())
    }
}
```

---

## 性能考虑

### 动画性能

| 动画类型 | 目标帧率 | 优化策略 |
|---------|---------|---------|
| 量子旋转 | ≥ 30fps | 使用 `rememberInfiniteTransition` |
| 生物识别成功 | ≥ 30fps | 简化动画，减少重绘 |
| 否决脉冲 | ≥ 24fps | 限制脉冲半径范围 |

### 内存管理

```kotlin
// 会话自动清理
@Composable
fun VaultScreen(
    viewModel: AeternumViewModel,
) {
    val sessionHandle by viewModel.sessionHandle.collectAsState()

    DisposableEffect(sessionHandle) {
        onDispose {
            // 确保会话被锁定
            viewModel.lockSession()
        }
    }
    // ...
}
```

---

## 安全验证

### 静态分析检查

```kotlin
// SecurityStaticAnalysisTest.kt
@Test
fun testNoByteArrayInViewModel() {
    val violations = mutableListOf<String>()

    scanKotlinFiles(viewModelDir) { file, content ->
        val pattern = Regex("""val\s+\w+:\s*ByteArray""")
        pattern.findAll(content).forEach { match ->
            violations.add("${file.name}: ${match.value}")
        }
    }

    assertTrue("发现 ByteArray 属性: $violations", violations.isEmpty())
}
```

### 运行时安全检查

```kotlin
// SessionSecurityTest.kt
@Test
fun testSessionClearedAfterLock() {
    val session = engine.unlock(hardwareKey)
    assertTrue(session.is_valid())

    session.lock()
    assertFalse(session.is_valid())

    // 验证无法在锁定后操作
    assertThrows<PqrrError> {
        session.decrypt_field("test", "field")
    }
}
```

---

## 迁移路径

### 从占位符到真实实现

```kotlin
// 修复前（占位符）
class VaultRepository {
    fun unlockVault(): VaultSessionHandle {
        return VaultSessionHandle.Placeholder
    }
}

// 修复后（真实实现）
class VaultRepository(
    private val bridge: AeternumBridge,
) {
    fun unlockVault(hardwareKey: ByteArray): Result<VaultSessionHandle> {
        return try {
            val session = bridge.unlock(hardwareKey)
            Result.Success(VaultSessionHandle.Valid(session))
        } catch (e: Exception) {
            Result.Error(/* ... */)
        }
    }
}
```

---

## 验收标准

### 编译成功

```bash
cd android
./gradlew clean
./gradlew assembleDebug   # ✅ 零错误
./gradlew assembleRelease # ✅ 零错误
```

### 测试通过

```bash
./gradlew test                     # ✅ 100% 通过
./gradlew connectedAndroidTest     # ✅ 100% 通过
./gradlew test --tests SecurityStaticAnalysisTest # ✅ 通过
```

### 功能验证

- [ ] Vault 可以成功初始化
- [ ] 生物识别解锁流程正常
- [ ] 数据可以存储和检索
- [ ] 设备列表正确显示
- [ ] 密钥轮换动画正常播放
- [ ] 无障碍功能正常工作
- [ ] FLAG_SECURE 防截屏生效

---

**文档版本**: 1.0.0
**最后更新**: 2026-02-16
**作者**: Aeternum Team
