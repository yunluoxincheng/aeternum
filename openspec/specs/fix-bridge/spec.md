# fix-bridge Specification

## Purpose
TBD - created by archiving change fix-android-ui-bridge. Update Purpose after archive.
## Requirements
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

