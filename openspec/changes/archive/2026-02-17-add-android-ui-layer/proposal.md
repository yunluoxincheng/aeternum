# Change: 添加 Android UI 层

## Why

当前 Aeternum 项目中，Rust Core 密码内核已完成 90%+ 的实现，但 Android UI 层仅完成约 10%，只有基础的 MainScreen 框架。需要实现完整的用户界面，将后量子安全密钥管理系统的功能交付给最终用户。

## What Changes

- **BREAKING**: 无破坏性变更，新增 UI 层功能
- 添加完整的 Jetpack Compose UI 组件
- 实现状态机驱动的界面转换
- 集成生物识别认证流程
- 实现密钥管理、设备管理、恢复流程等核心界面
- 采用 Material Design 3 深色主题，符合"后量子安全"产品气质

## Impact

### 影响的规范 (Affected Specs)
- 新增 `android-ui` 规范

### 影响的代码 (Affected Code)
- `android/app/src/main/kotlin/io/aeternum/ui/` - 新增 UI 组件
- `android/app/src/main/kotlin/io/aeternum/security/` - 扩展生物识别集成
- `android/app/src/main/kotlin/io/aeternum/bridge/` - 可能需要新增 UniFFI 接口
- `android/app/build.gradle.kts` - 添加新依赖（如需要）

### 新增目录结构
```
android/app/src/main/kotlin/io/aeternum/ui/
├── navigation/         # Navigation 图路由
│   └── AeternumNavGraph.kt
├── onboarding/         # 初始化流程
│   ├── WelcomeScreen.kt
│   ├── MnemonicBackupScreen.kt
│   └── RegistrationScreen.kt
├── auth/               # 生物识别
│   └── BiometricPromptScreen.kt
├── main/               # 主屏幕
│   ├── MainScreen.kt    # (已有, 需扩展)
│   ├── StatusCard.kt
│   └── QuickActions.kt
├── vault/              # 密钥管理
│   ├── VaultStatusScreen.kt
│   └── KeyRotationHistoryScreen.kt
├── devices/            # 设备管理
│   ├── DeviceListScreen.kt
│   └── DeviceDetailScreen.kt
├── recovery/           # 恢复流程
│   ├── RecoveryInitiateScreen.kt
│   └── VetoNotificationScreen.kt
├── degraded/           # 降级状态
│   └── DegradedModeScreen.kt
├── revoked/            # 撤销状态
│   └── RevokedScreen.kt
├── components/         # 通用组件
│   ├── StatusIndicator.kt
│   ├── EpochBadge.kt
│   ├── SecureTextField.kt
│   └── QuantumAnimation.kt
└── theme/              # 主题配置
    ├── Color.kt
    ├── Type.kt
    └── Theme.kt
```

## 安全边界 (Security Boundaries)

本提案严格遵守以下安全约束：

### 禁止事项
- ❌ 在 Kotlin 层实现任何密码学逻辑
- ❌ 在日志中记录密钥或敏感信息
- ❌ Kotlin 层持有明文密钥的 `ByteArray`
- ❌ 将密钥以任何形式传递到 UI 层

### 必须事项
- ✅ UI 层仅显示脱敏后的数据
- ✅ 所有解密操作通过 Rust Core 句柄调用
- ✅ 使用 BiometricPrompt (Class 3) 进行用户认证
- ✅ 使用 Play Integrity API 验证设备完整性
- ✅ 遵循 [UniFFI 桥接契约](../../bridge/UniFFI-Bridge-Contract.md)

## 设计原则

### UI 设计理念
- **极简主义**：减少视觉噪音，突出核心功能
- **安全感**：通过动画和色彩传达"安全"的感觉
- **透明度**：清晰展示安全状态（纪元、设备列表、否决权）
- **后量子科技感**：使用深色主题和科技感元素

### Material Design 3 深色主题
- 主色：量子蓝 (#00BCD4) - 传达科技与安全
- 次要色：深空灰 (#121212) - 背景色
- 错误色：量子红 (#FF5252) - 警告与危险
- 成功色：量子绿 (#69F0AE) - 安全状态
- 警告色：量子黄 (#FFD740) - 需要关注

### 动画设计
- **生物识别成功**：流畅的淡入动画 (300ms)
- **密钥轮换**：旋转的量子效果 (500ms)
- **设备撤销**：收缩淡出动画 (400ms)
- **否决信号**：脉冲红色警告 (1000ms 循环)

## 参考文档

设计参考了以下最佳实践：

- [Material Design 3 Dark Theme](https://m2.material.io/design/color/dark-theme.html)
- [How to Design Dark Mode for Your Mobile App - A 2026 Guide](https://appinventiv.com/blog/guiding-on-designing-dark-mode-for-mobile-app/)
- [Jetpack Biometric Library - Android Developers](https://developer.android.com/jetpack/androidx/releases/biometric)
- [Implementing Biometric Authentication in Android with Jetpack Compose](https://medium.com/@ashiiqbal666/implementing-biometric-authentication-in-android-with-jetpack-compose-02d441647391)

## 非目标 (Non-Goals)

- 不实现 Web 界面
- 不支持自定义主题（仅深色主题）
- 不实现多语言（仅中文）
- 不实现社交恢复功能（L2/L3 模式暂不实现 UI）

---

**提案创建日期**: 2026-02-15
**提案类型**: 新功能 (Feature)
**预期工作量**: 中大型
**依赖提案**: 无 (依赖 Rust Core 已完成的工作)
**状态**: ✅ 已完成 (2026-02-17)
**完成备注**: 编译错误已通过 `fix-android-ui-bridge` 提案修复，Debug/Release APK 构建成功
