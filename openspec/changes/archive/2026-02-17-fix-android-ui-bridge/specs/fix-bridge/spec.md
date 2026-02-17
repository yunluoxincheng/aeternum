# Android UI 桥接修复规范

## ADDED Requirements

### Requirement: UniFFI 接口完整性

系统 SHALL 提供完整的 UniFFI 桥接接口，确保 Android UI 层可以正确调用 Rust Core 功能。

#### Scenario: Vault 解锁

- **WHEN** 用户通过生物识别认证
- **AND** AndroidSecurityManager 提供硬件密钥
- **THEN** `AeternumEngine.unlock()` 成功返回 `VaultSession` 句柄
- **AND** 会话可用于解密字段

#### Scenario: 数据存储

- **WHEN** UI 层调用 `VaultSession.store_entry()`
- **THEN** 数据在 Rust 端加密后存储
- **AND** Kotlin 层不接触明文

#### Scenario: 数据检索

- **WHEN** UI 层调用 `VaultSession.retrieve_entry()`
- **THEN** Rust 端解密数据
- **AND** 仅返回脱敏后的字符串

### Requirement: Compose API 兼容性

系统 SHALL 使用当前 Compose BOM 版本支持的 API，确保编译成功。

#### Scenario: 动画播放

- **WHEN** 用户触发动画效果
- **THEN** 动画使用兼容的 API 正常播放
- **AND** 帧率 ≥ 30fps

#### Scenario: Material3 组件渲染

- **WHEN** 使用 Material3 组件
- **THEN** 所有必需参数已提供
- **AND** 组件正确渲染

### Requirement: 无障碍功能可用性

系统 SHALL 使用非废弃的无障碍 API，确保残障用户可以使用应用。

#### Scenario: 屏幕阅读器支持

- **WHEN** 用户启用 TalkBack
- **THEN** 所有交互元素具有语义描述
- **AND** 状态变化有语音提示

#### Scenario: 高对比度模式

- **WHEN** 用户启用高对比度
- **THEN** 应用检测到模式变化
- **AND** 使用高对比度颜色

---

## FIXED Requirements

### Requirement: VaultRepository 接口匹配

**修复前**: `VaultRepository` 调用不存在的方法导致编译失败

**修复后**: 所有调用的方法都在 Rust Core 中实现并通过 UniFFI 暴露

#### Scenario: 初始化 Vault

- **WHEN** 首次启动应用
- **AND** 用户完成助记词备份
- **THEN** `AeternumEngine.initializeVault()` 成功创建 Vault

### Requirement: 动画 API 替换

**修复前**: 使用已废弃的 `animateValue` API

**修复后**: 使用 `animateFloatAsState` 等兼容 API

#### Scenario: 量子动画播放

- **WHEN** 用户查看密钥轮换屏幕
- **THEN** 旋转动画使用 `animateFloatAsState` 正常播放
- **AND** 动画流畅无卡顿

### Requirement: 无障碍 API 更新

**修复前**: 使用已废弃的 `ACCESSIBILITY_ANNOUNCEMENT`

**修复后**: 使用 `SemanticsProperties.announce`

#### Scenario: 语音公告

- **WHEN** 重要状态变化发生
- **THEN** 屏幕阅读器朗读状态变化
- **AND** 使用正确的 API

---

## 非功能需求

### Requirement: 编译成功

#### Scenario: Debug 构建

- **WHEN** 运行 `./gradlew assembleDebug`
- **THEN** 编译成功，零错误，零警告

#### Scenario: Release 构建

- **WHEN** 运行 `./gradlew assembleRelease`
- **THEN** 编译成功，ProGuard 混淆正确

### Requirement: 测试通过

#### Scenario: 单元测试

- **WHEN** 运行 `./gradlew test`
- **THEN** 所有测试通过
- **AND** 覆盖率 ≥ 目标值

#### Scenario: 安全测试

- **WHEN** 运行 `SecurityStaticAnalysisTest`
- **THEN** 所有安全检查通过
- **AND** 无密钥泄漏

---

## 安全约束

### INVARIANT: 桥接接口不暴露明文

所有 UniFFI 接口必须遵守：
- ❌ 禁止返回 `ByteArray` 类型的密钥
- ✅ 返回脱敏后的字符串或句柄
- ✅ 所有加密/解密在 Rust 端完成

### INVARIANT: Kotlin 层不持密钥

ViewModel 和 Repository 必须遵守：
- ❌ 禁止持有 `ByteArray` 类型的属性
- ✅ 仅持有 Rust 实例句柄
- ✅ 会话使用后显式调用 `lock()`

---

## API 映射表

### UniFFI 接口映射

| Kotlin 调用 | UniFFI 接口 | Rust 实现 |
|-------------|-------------|-----------|
| `vaultRepository.initializeVault()` | `AeternumEngine.initializeVault()` | `AeternumCoreEngine::initialize_vault()` |
| `vaultRepository.unlockVault()` | `AeternumEngine.unlock()` | `AeternumCoreEngine::unlock()` |
| `session.storeEntry()` | `VaultSession.store_entry()` | `AeternumVaultSession::store_entry()` |
| `session.retrieveEntry()` | `VaultSession.retrieve_entry()` | `AeternumVaultSession::retrieve_entry()` |
| `session.decryptField()` | `VaultSession.decrypt_field()` | `AeternumVaultSession::decrypt_field()` |

### Compose API 替换映射

| 废弃 API | 替代 API | 文件 |
|----------|----------|------|
| `animateValue` | `animateFloatAsState` | `QuantumAnimation.kt` |
| `VectorConverter` | 手动类型转换 | `QuantumAnimation.kt` |
| `StrokeCap` | `DrawScope.Stroke` | `BiometricSuccessAnimation.kt` |
| `ACCESSIBILITY_ANNOUNCEMENT` | `SemanticsProperties.announce` | `AccessibilityAnnouncer.kt` |
| `isHighTextContrastEnabled` | `LocalAccessibilityManager` | `AccessibilityState.kt` |

---

**规范版本**: 1.0.0
**最后更新**: 2026-02-16
**作者**: Aeternum Team
