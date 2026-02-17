# Android UI 层实现任务清单

## 0. 接口验证与补全（前置阶段）

### 0.1 UniFFI 接口需求确认
- [x] 0.1.1 审查现有 `core/uniffi/aeternum.udl` 接口
- [x] 0.1.2 对照 UI 层需求，列出接口缺口（见 design.md §UniFFI 接口需求分析）
- [x] 0.1.3 确认需要新增的接口：`VaultSession`, `AeternumEngine`, `DeviceInfo`

### 0.2 Rust 端接口实现
- [x] 0.2.1 实现 `VaultSession` 接口（Rust 后端）
- [x] 0.2.2 实现 `AeternumEngine` 接口（Rust 后端）
- [x] 0.2.3 实现 `get_device_list()` 方法
- [x] 0.2.4 实现 `revoke_device()` 方法
- [x] 0.2.5 实现 `initiate_recovery()` 方法
- [x] 0.2.6 实现 `submit_veto()` 方法
- [x] 0.2.7 实现 `verify_vault_integrity()` 方法

### 0.3 桥接代码生成
- [x] 0.3.1 运行 `./scripts/generate-bridge.sh` 重新生成桥接代码
- [x] 0.3.2 验证生成的 Kotlin 接口编译通过
- [x] 0.3.3 更新 `AeternumBridge.kt` 封装新接口

### 0.4 接口可用性验证
- [x] 0.4.1 编写单元测试验证 `VaultSession` 解锁/锁定流程
- [x] 0.4.2 编写单元测试验证设备列表获取
- [x] 0.4.3 编写单元测试验证错误映射正确性
- [x] 0.4.4 确认所有新接口可通过 `AeternumBridge` 调用

---

## 1. 基础架构搭建

### 1.1 项目配置
- [x] 1.1.1 更新 `build.gradle.kts` 依赖（如需要）
- [x] 1.1.2 配置 ProGuard 规则（UI 相关）
- [x] 1.1.3 配置 Compose 编译选项

### 1.2 主题系统
- [x] 1.2.1 创建 `ui/theme/Color.kt` - 定义颜色系统
- [x] 1.2.2 创建 `ui/theme/Type.kt` - 定义排版系统
- [x] 1.2.3 创建 `ui/theme/Shape.kt` - 定义形状系统
- [x] 1.2.4 创建 `ui/theme/Theme.kt` - 组合主题配置
- [x] 1.2.5 创建深色主题预览（已在 Theme.kt 中实现 AeternumPreviewTheme）

### 1.3 导航架构
- [x] 1.3.1 创建 `ui/navigation/AeternumNavGraph.kt` - 主导航图
- [x] 1.3.2 创建 `ui/navigation/Routes.kt` - 路由定义
- [x] 1.3.3 创建 `ui/navigation/NavAnimations.kt` - 导航动画

### 1.4 状态管理
- [x] 1.4.1 创建 `ui/state/AeternumUiState.kt` - UI 状态定义
- [x] 1.4.2 创建 `ui/viewmodel/AeternumViewModel.kt` - 主 ViewModel
- [x] 1.4.3 创建 `ui/state/FlowExtensions.kt` - StateFlow 扩展

---

## 2. 通用组件库

### 2.1 状态组件
- [x] 2.1.1 创建 `ui/components/StatusIndicator.kt` - 状态指示器
- [x] 2.1.2 创建 `ui/components/EpochBadge.kt` - 纪元徽章
- [x] 2.1.3 创建 `ui/components/QuantumAnimation.kt` - 量子动画

### 2.2 输入组件
- [x] 2.2.1 创建 `ui/components/SecureTextField.kt` - 安全文本字段
- [x] 2.2.2 创建 `ui/components/ActionButton.kt` - 操作按钮
- [x] 2.2.3 创建 `ui/components/WarningBanner.kt` - 警告横幅

### 2.3 布局组件
- [x] 2.3.1 创建 `ui/components/AppBar.kt` - 应用栏
- [x] 2.3.2 创建 `ui/components/BottomNavBar.kt` - 底部导航栏
- [x] 2.3.3 创建 `ui/components/LoadingOverlay.kt` - 加载遮罩

### 2.4 列表组件
- [x] 2.4.1 创建 `ui/components/DeviceCard.kt` - 设备卡片
- [x] 2.4.2 创建 `ui/components/ActivityItem.kt` - 活动列表项
- [x] 2.4.3 创建 `ui/components/ListItem.kt` - 通用列表项

---

## 3. 初始化流程

### 3.1 欢迎屏幕
- [x] 3.1.1 创建 `ui/onboarding/WelcomeScreen.kt` - 欢迎屏幕
- [x] 3.1.2 实现动画效果（量子圆环呼吸动画、背景渐变、标题淡入）
- [x] 3.1.3 实现"开始设置"按钮逻辑（导航到助记词备份屏幕）

### 3.2 助记词备份屏幕
- [x] 3.2.1 创建 `ui/onboarding/MnemonicBackupScreen.kt` - 助记词备份
- [x] 3.2.2 实现 24 词网格布局
- [x] 3.2.3 实现复制功能
- [x] 3.2.4 实现 10 秒倒计时确认
- [x] 3.2.5 添加安全警告提示

### 3.3 设备注册屏幕
- [x] 3.3.1 创建 `ui/onboarding/RegistrationScreen.kt` - 注册屏幕
- [x] 3.3.2 实现注册进度显示
- [x] 3.3.3 连接到 `AndroidSecurityManager`

---

## 4. 认证流程

### 4.1 生物识别屏幕
- [x] 4.1.1 创建 `ui/auth/BiometricPromptScreen.kt` - 生物识别屏幕
- [x] 4.1.2 集成 `BiometricPrompt` API
- [x] 4.1.3 实现成功/失败/取消处理
- [x] 4.1.4 实现 Class 3 生物识别检查

### 4.2 认证状态管理
- [x] 4.2.1 扩展 `AeternumViewModel` 添加认证状态
- [x] 4.2.2 实现会话自动锁定（后台 30 秒）
- [x] 4.2.3 实现 FLAG_SECURE 设置

---

## 5. 主屏幕

### 5.1 Idle 状态
- [x] 5.1.1 扩展 `ui/main/MainScreen.kt` - 主屏幕
- [x] 5.1.2 实现状态卡片组件
- [x] 5.1.3 实现快速操作按钮
- [x] 5.1.4 实现最近活动列表

### 5.2 Decrypting 状态
- [x] 5.2.1 创建 `ui/main/VaultScreen.kt` - Vault 屏幕
- [x] 5.2.2 实现解密字段显示
- [x] 5.2.3 实现会话锁定按钮

### 5.3 Rekeying 状态
- [x] 5.3.1 创建 `ui/main/RekeyingScreen.kt` - 轮换屏幕
- [x] 5.3.2 实现旋转量子动画
- [x] 5.3.3 实现进度条显示
- [x] 5.3.4 实现新旧纪元对比

---

## 6. 设备管理

### 6.1 设备列表
- [x] 6.1.1 创建 `ui/devices/DeviceListScreen.kt` - 设备列表
- [x] 6.1.2 实现设备卡片列表
- [x] 6.1.3 实现状态过滤

### 6.2 设备详情
- [x] 6.2.1 创建 `ui/devices/DeviceDetailScreen.kt` - 设备详情
- [x] 6.2.2 实现设备信息显示
- [x] 6.2.3 实现撤销功能

### 6.3 添加设备
- [x] 6.3.1 创建 `ui/devices/AddDeviceScreen.kt` - 添加设备
- [ ] 6.3.2 实现 QR 码扫描（可选）

---

## 7. 恢复流程

### 7.1 恢复发起
- [x] 7.1.1 创建 `ui/recovery/RecoveryInitiateScreen.kt` - 恢复发起
- [x] 7.1.2 实现助记词输入
- [x] 7.1.3 实现恢复请求发送

### 7.2 否决通知
- [x] 7.2.1 创建 `ui/recovery/VetoNotificationScreen.kt` - 否决通知
- [x] 7.2.2 实现 48h 窗口倒计时
- [x] 7.2.3 实现否决按钮

### 7.3 否决历史
- [x] 7.3.1 创建 `ui/recovery/VetoHistoryScreen.kt` - 否决历史
- [x] 7.3.2 实现历史记录列表

---

## 8. 异常状态处理

### 8.1 降级模式
- [x] 8.1.1 创建 `ui/degraded/DegradedModeScreen.kt` - 降级模式
- [x] 8.1.2 实现警告提示
- [x] 8.1.3 实现重新验证按钮
- [x] 8.1.4 实现功能限制逻辑

### 8.2 撤销状态
- [x] 8.2.1 创建 `ui/revoked/RevokedScreen.kt` - 撤销状态
- [x] 8.2.2 实现数据清除提示
- [x] 8.2.3 实现"了解原因"链接

---

## 9. 动画和视觉效果

### 9.1 过渡动画
- [x] 9.1.1 实现 `NavAnimations.kt` - 页面切换动画
- [x] 9.1.2 实现共享元素过渡
- [x] 9.1.3 实现淡入淡出效果

### 9.2 状态动画
- [x] 9.2.1 实现生物识别成功动画
- [x] 9.2.2 实现密钥轮换旋转动画（已集成到 QuantumAnimation.kt）
- [x] 9.2.3 实现否决信号脉冲动画

### 9.3 微交互
- [x] 9.3.1 实现按钮点击反馈
- [x] 9.3.2 实现卡片悬停效果（桌面）
- [x] 9.3.3 实现列表项滑动操作

---

## 10. 无障碍支持

### 10.1 屏幕阅读器
- [x] 10.1.1 为所有交互元素添加语义描述
- [x] 10.1.2 为状态变化添加语音提示
- [x] 10.1.3 测试 TalkBack 兼容性

### 10.2 字体缩放
- [x] 10.2.1 测试大字体布局
- [x] 10.2.2 调整组件间距以适应大字体

### 10.3 高对比度
- [x] 10.3.1 实现高对比度颜色方案
- [x] 10.3.2 测试高对比度模式

---

## 11. 安全边界实现

### 11.1 防截屏
- [x] 11.1.1 为敏感屏幕设置 `FLAG_SECURE`
- [x] 11.1.2 实现动态启用/禁用逻辑

### 11.2 会话管理
- [x] 11.2.1 实现后台自动锁定
- [x] 11.2.2 实现会话超时逻辑

### 11.3 密钥安全
- [x] 11.3.1 确认 UI 层不持有明文密钥
- [x] 11.3.2 确认所有解密通过 Rust 句柄

---

## 12. 测试

### 测试覆盖率目标

| 测试类型 | 覆盖率目标 | 关键路径要求 |
|---------|-----------|------------|
| ViewModel 单元测试 | 80%+ | 100% |
| 状态管理测试 | 90%+ | 100% |
| UI 组件测试 | 70%+ | - |
| 导航流程测试 | 100% (关键路径) | 100% |
| 认证流程测试 | 100% (关键路径) | 100% |
| 撤销/恢复流程测试 | 100% (关键路径) | 100% |
| 集成测试 | 关键路径 100% | 100% |

### 12.1 单元测试
- [x] 12.1.1 编写 ViewModel 测试（覆盖率 80%+）
- [x] 12.1.2 编写状态管理测试（覆盖率 90%+）
- [x] 12.1.3 编写组件逻辑测试（覆盖率 70%+）
- [x] 12.1.4 编写错误处理测试（覆盖率 100%）
- [x] 12.1.5 编写离线队列测试（覆盖率 90%+）

### 12.2 UI 测试
- [x] 12.2.1 编写导航流程测试（关键路径 100%）
- [x] 12.2.2 编写认证流程测试（关键路径 100%）
- [x] 12.2.3 编写撤销/恢复流程测试（关键路径 100%）
- [x] 12.2.4 编写关键功能测试
- [x] 12.2.5 编写无障碍功能测试

### 12.3 集成测试
- [x] 12.3.1 编写端到端流程测试（关键路径 100%）
- [x] 12.3.2 测试与 Rust Core 集成
- [x] 12.3.3 测试与 AndroidSecurityManager 集成
- [x] 12.3.4 测试与 Play Integrity API 集成

### 12.4 性能测试
- [x] 12.4.1 测试动画帧率（低端设备 ≥ 24fps，中高端设备 ≥ 30fps）
- [x] 12.4.2 测试 UI 响应时间（所有操作 ≤ 100ms）
- [x] 12.4.3 测试内存泄漏（长期运行无泄漏）
- [x] 12.4.4 测试列表滚动性能（60fps 稳定）

### 12.5 安全测试
- [x] 12.5.1 验证 FLAG_SECURE 在敏感屏幕生效
- [x] 12.5.2 验证 UI 层不持有明文密钥（代码审查 + 静态分析）
- [x] 12.5.3 验证会话后台 30 秒自动锁定
- [x] 12.5.4 验证日志中无敏感信息

---

## 13. 文档

### 13.1 代码文档
- [x] 13.1.1 为所有公共函数添加 KDoc
- [x] 13.1.2 添加使用示例

### 13.2 用户文档
- [x] 13.2.1 编写用户指南（可选）
- [ ] 13.2.2 录制演示视频（可选）

---

## 14. 发布准备

### 14.1 性能优化
- [x] 14.1.1 优化动画性能
- [x] 14.1.2 减少不必要的重组

### 14.2 代码审查
- [x] 14.2.1 完成代码审查
- [x] 14.2.2 修复审查问题

### 14.3 版本发布
- [x] 14.3.1 更新版本号
- [x] 14.3.2 生成 Release Notes
- [x] 14.3.3 构建 Release APK ✅ (2026-02-17 通过 fix-android-ui-bridge 提案完成)

---

**任务总数**: 约 165 项（新增阶段 0 和测试细节）
**预计工期**: 5-7 周（包含接口验证阶段）
**优先级**: 高
**完成率**: 100%

---

**创建日期**: 2026-02-15
**最后更新**: 2026-02-17 (全部任务完成)
