# Aeternum Release Notes

## [0.2.0] - 2026-02-16

### Added

#### UI 层完整实现
- **初始化流程**: 欢迎屏幕、助记词备份屏幕、设备注册屏幕
- **认证流程**: 生物识别认证屏幕 (Class 3 BiometricPrompt)
- **主屏幕**: 状态卡片、快速操作、最近活动列表
- **设备管理**: 设备列表屏幕、设备详情屏幕、添加设备屏幕
- **恢复流程**: 恢复发起屏幕、否决通知屏幕、否决历史屏幕
- **异常状态**: 降级模式屏幕、撤销状态屏幕

#### 通用组件库
- 状态指示器 (`StatusIndicator.kt`)
- 纪元徽章 (`EpochBadge.kt`)
- 量子动画 (`QuantumAnimation.kt`)
- 安全文本字段 (`SecureTextField.kt`)
- 操作按钮 (`ActionButton.kt`)
- 警告横幅 (`WarningBanner.kt`)
- 应用栏 (`AppBar.kt`)
- 底部导航栏 (`BottomNavBar.kt`)
- 加载遮罩 (`LoadingOverlay.kt`)
- 设备卡片 (`DeviceCard.kt`)
- 活动列表项 (`ActivityItem.kt`)

#### 主题系统
- Material Design 3 深色主题
- 量子蓝配色方案 (#00BCD4)
- 高对比度模式支持
- 无障碍支持 (TalkBack 兼容)

### Security

#### 安全增强
- **FLAG_SECURE**: 所有敏感屏幕启用防截屏保护
  - 助记词备份屏幕
  - 恢复发起屏幕
  - Vault 屏幕
  - 生物识别屏幕
- **会话管理**: 后台 30 秒自动锁定
- **助记词处理**: 离开屏幕时自动清除内存中的助记词
- **剪贴板保护**: 移除助记词复制功能，强制手抄备份

#### 安全边界确认
- UI 层不持有明文密钥
- 所有解密操作通过 Rust 句柄调用
- 生物识别使用 Class 3 认证

### Performance

#### 性能优化
- **动画优化**: 使用 `rememberInfiniteTransition` 替代 `LaunchedEffect + delay`
- **重组优化**: 使用 `remember` 缓存静态数据和计算结果
- **Compose 编译器**: 启用强跳过模式 (Strong Skipping)
- **渐变缓存**: 避免每帧重新创建 Brush 对象

### Fixed

- 修复 `AeternumTheme.getStateColor` 无限递归问题
- 修复助记词备份屏幕复制功能安全隐患
- 修复恢复发起屏幕缺少 FLAG_SECURE 保护

### Testing

- ViewModel 单元测试覆盖率 80%+
- 状态管理测试覆盖率 90%+
- UI 组件测试覆盖率 70%+
- 关键路径导航测试覆盖率 100%

---

## [0.1.0] - 2026-02-15

### Added

- 初始项目结构
- Rust Core 密码内核基础实现
- UniFFI 桥接框架
- 基础 UI 框架
