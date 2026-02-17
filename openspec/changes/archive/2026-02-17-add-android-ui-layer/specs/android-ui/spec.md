# Android UI 层规范

## Purpose

定义 Aeternum 后量子安全密钥管理系统的 Android 用户界面层，提供完整的 Jetpack Compose UI 组件，实现状态机驱动的界面转换、生物识别认证流程、密钥管理、设备管理、恢复流程等核心界面。

## Requirements

### Requirement: 欢迎流程

系统 SHALL 提供清晰、友好的首次启动体验，引导用户完成设备初始化。

#### Scenario: 首次启动显示欢迎屏幕

- **WHEN** 用户首次启动 Aeternum 应用
- **THEN** 显示欢迎屏幕，包含应用 Logo、名称和产品描述

#### Scenario: 用户点击开始设置

- **WHEN** 用户在欢迎屏幕点击"开始设置"按钮
- **THEN** 导航到助记词备份屏幕

### Requirement: 助记词备份

系统 SHALL 要求用户安全保存 24 位助记词，并确认用户已完成备份。

#### Scenario: 显示助记词

- **WHEN** 用户进入助记词备份屏幕
- **THEN** 以网格形式显示 24 个助记词，每个词清晰可读

#### Scenario: 复制助记词

- **WHEN** 用户点击"复制"按钮
- **THEN** 助记词被复制到剪贴板，并显示"已复制"提示

#### Scenario: 确认备份完成

- **WHEN** 用户点击"我已经安全保存"按钮
- **AND** 用户在屏幕上停留至少 10 秒
- **THEN** 按钮启用，点击后导航到设备注册屏幕
- **AND** 助记词从内存中清除

### Requirement: 生物识别认证

系统 SHALL 使用 Class 3 生物识别认证（指纹或面部识别）来解锁 Vault。

#### Scenario: 生物识别成功

- **WHEN** 用户触发生物识别认证
- **AND** 生物识别验证成功
- **THEN** 显示流畅的淡入动画
- **AND** 导航到主屏幕（状态转换为 Idle → Decrypting）

#### Scenario: 生物识别失败

- **WHEN** 生物识别验证失败
- **THEN** 显示错误提示"生物识别验证失败，请重试"
- **AND** 保持当前屏幕状态

#### Scenario: 用户取消生物识别

- **WHEN** 用户取消生物识别对话框
- **THEN** 返回到上一个屏幕
- **AND** 不显示错误提示

### Requirement: 主屏幕（Idle 状态）

系统 SHALL 显示设备当前状态、纪元信息和快速操作入口。

#### Scenario: 显示安全状态

- **WHEN** 用户处于 Idle 状态
- **THEN** 主屏幕显示：
  - 状态卡片（安全/警告/危险）
  - 当前纪元徽章（如 "Epoch 5"）
  - 已连接设备数量
  - 快速操作按钮（查看密钥、设备管理、密钥轮换）

#### Scenario: 显示警告状态

- **WHEN** 有设备处于 Degraded 状态
- **THEN** 状态卡片显示黄色警告
- **AND** 显示受影响设备列表

#### Scenario: 显示危险状态

- **WHEN** 收到否决信号或设备被撤销
- **THEN** 状态卡片显示红色警告
- **AND** 显示"需要立即处理"提示

### Requirement: 密钥轮换进度

系统 SHALL 在 PQRR 密钥轮换过程中显示进度信息。

#### Scenario: 显示轮换进度

- **WHEN** 密钥轮换进行中
- **THEN** 显示：
  - 旋转动画（量子效果）
  - 进度条（0-100%）
  - 新旧纪元对比（如 "Epoch 5 → 6"）
  - "请勿关闭应用"警告

#### Scenario: 轮换完成

- **WHEN** 密钥轮换完成
- **THEN** 显示成功动画
- **AND** 状态更新到新纪元
- **AND** 返回到主屏幕（Idle 状态）

### Requirement: 设备管理

系统 SHALL 提供设备列表、设备详情和设备撤销功能。

#### Scenario: 显示设备列表

- **WHEN** 用户进入设备管理屏幕
- **THEN** 显示所有已注册设备，包括：
  - 设备名称
  - 状态（活跃/降级/撤销）
  - 当前纪元
  - 最后在线时间

#### Scenario: 撤销设备

- **WHEN** 用户点击设备卡片上的"撤销"按钮
- **THEN** 显示确认对话框
- **AND** 列出撤销后果（该设备将无法访问 Vault）

#### Scenario: 确认撤销设备

- **WHEN** 用户在确认对话框点击"确认撤销"
- **AND** 用户通过生物识别认证
- **THEN** 触发 PQRR 协议
- **AND** 设备状态更新为"已撤销"
- **AND** 从活跃设备列表中移除

### Requirement: 降级模式

系统 SHALL 在 Play Integrity 验证失败时进入降级模式，限制功能访问。

#### Scenario: 进入降级模式

- **WHEN** Play Integrity 验证失败
- **THEN** 导航到降级模式屏幕
- **AND** 显示：
  - 警告图标（红色）
  - 错误描述
  - 重新验证按钮
  - "了解详情"链接

#### Scenario: 降级模式功能限制

- **WHEN** 应用处于降级模式
- **THEN** 以下功能被禁用：
  - 查看完整密钥数据
  - 导出密钥
  - 发起恢复流程
  - 执行密钥轮换

#### Scenario: 重新验证设备完整性

- **WHEN** 用户点击"重新验证"按钮
- **THEN** 触发 Play Integrity 验证
- **AND** 显示加载指示器

#### Scenario: 验证成功退出降级模式

- **WHEN** Play Integrity 验证成功
- **THEN** 显示成功提示
- **AND** 返回到主屏幕（Idle 状态）

### Requirement: 撤销状态

系统 SHALL 在设备被撤销时显示终态提示，并清除所有本地数据。

#### Scenario: 显示撤销状态

- **WHEN** 设备被撤销
- **THEN** 导航到撤销屏幕
- **AND** 显示：
  - 撤销图标
  - "此设备已被撤销"提示
  - "所有密钥和数据已清除"说明
  - "了解原因"链接

#### Scenario: 清除本地数据

- **WHEN** 设备状态转换为 Revoked
- **THEN** 自动执行：
  - 删除 StrongBox 密钥
  - 清除 SQLCipher 数据库
  - 清除缓存
  - Zeroize 内存

#### Scenario: 撤销状态不可逆

- **WHEN** 应用处于撤销状态
- **THEN** 所有功能被禁用
- **AND** 用户无法返回到其他屏幕
- **AND** 唯一操作是"了解原因"和关闭应用

### Requirement: UI 安全边界

系统 SHALL 确保 UI 层不持有或操作任何明文密钥。

#### Scenario: UI 层不持有密钥

- **WHEN** UI 层需要显示密钥相关数据
- **THEN** 仅显示脱敏后的信息
- **AND** 所有解密操作通过 Rust Core 句柄调用

#### Scenario: 会话自动锁定

- **WHEN** 解密会话处于 Decrypting 状态
- **AND** 应用进入后台超过 30 秒
- **THEN** 自动调用 `session.lock()`
- **AND** 状态转换到 Idle
- **AND** 清除内存中的密钥引用

#### Scenario: 防止截屏

- **WHEN** 用户在敏感界面（助记词备份、生物识别）
- **THEN** 设置 `FLAG_SECURE` 防止截屏和录屏

### Requirement: 动画和反馈

系统 SHALL 提供流畅的动画和清晰的视觉反馈。

#### Scenario: 生物识别成功动画

- **WHEN** 生物识别认证成功
- **THEN** 播放淡入 + 缩放动画（300ms）
- **AND** 使用 `EmphasizedDecelerate` 缓动曲线

#### Scenario: 密钥轮换动画

- **WHEN** 密钥轮换进行中
- **THEN** 播放旋转动画（量子效果）
- **AND** 动画循环播放直到轮换完成

#### Scenario: 否决信号脉冲动画

- **WHEN** 收到否决信号
- **THEN** 播放红色脉冲动画（1000ms 循环）
- **AND** 脉冲动画持续到用户处理为止

#### Scenario: 页面切换动画

- **WHEN** 用户导航到新屏幕
- **THEN** 播放共享元素过渡动画（350ms）
- **AND** 使用 `StandardEasing` 缓动曲线

### Requirement: 无障碍支持

系统 SHALL 支持 Android 无障碍功能，确保残障用户可以使用应用。

#### Scenario: 屏幕阅读器支持

- **WHEN** 用户启用屏幕阅读器（如 TalkBack）
- **THEN** 所有交互元素具有语义化描述
- **AND** 状态变化有语音提示

#### Scenario: 字体缩放

- **WHEN** 用户调整系统字体大小
- **THEN** UI 布局适应字体大小
- **AND** 不影响可用性

#### Scenario: 高对比度模式

- **WHEN** 用户启用高对比度模式
- **THEN** 应用使用高对比度颜色
- **AND** 保持视觉层次结构

### Requirement: 深色主题

系统 SHALL 使用 Material Design 3 深色主题，符合"后量子安全"产品气质。

#### Scenario: 深色主题色彩

- **WHEN** 应用启动
- **THEN** 使用以下颜色方案：
  - 主色：量子蓝 (#00BCD4)
  - 背景：深空灰 (#121212)
  - 表面：#1E1E1E
  - 错误：量子红 (#FF5252)
  - 成功：量子绿 (#69F0AE)
  - 警告：量子黄 (#FFD740)

#### Scenario: 暗色主题对比度

- **WHEN** 显示文本或图标
- **THEN** 确保与背景的对比度至少为 4.5:1
- **AND** 符合 WCAG AA 标准

---

## ADDED Requirements - 组件库

### Requirement: 状态指示器组件

系统必须提供可复用的状态指示器组件，显示设备或功能的当前状态。

#### Scenario: 显示安全状态

- **WHEN** 使用 `StatusIndicator` 组件显示"安全"状态
- **THEN** 显示绿色圆形指示器 + "安全"文本

#### Scenario: 显示警告状态

- **WHEN** 使用 `StatusIndicator` 组件显示"警告"状态
- **THEN** 显示黄色三角形指示器 + "警告"文本

#### Scenario: 显示危险状态

- **WHEN** 使用 `StatusIndicator` 组件显示"危险"状态
- **THEN** 显示红色圆形指示器 + "危险"文本

### Requirement: 纪元徽章组件

系统必须提供纪元徽章组件，显示当前密码学纪元。

#### Scenario: 显示纪元徽章

- **WHEN** 使用 `EpochBadge` 组件
- **THEN** 显示 "Epoch X" 格式的徽章
- **AND** 徽章颜色与状态匹配

#### Scenario: 纪元升级动画

- **WHEN** 纪元从 X 升级到 Y
- **THEN** 徽章播放数字滚动动画
- **AND** 更新为新纪元

### Requirement: 安全文本字段组件

系统必须提供安全文本字段组件，用于显示敏感信息。

#### Scenario: 隐藏敏感信息

- **WHEN** 使用 `SecureTextField` 组件显示密码或密钥
- **THEN** 默认显示为 `•••••`
- **AND** 提供切换按钮显示/隐藏明文

#### Scenario: 防止截屏

- **WHEN** `SecureTextField` 获得焦点
- **THEN** 设置 `FLAG_SECURE` 防止截屏

### Requirement: 量子动画组件

系统必须提供量子主题的动画组件，增强视觉体验。

#### Scenario: 旋转量子动画

- **WHEN** 使用 `QuantumAnimation` 组件显示轮换进度
- **THEN** 显示旋转的量子圆环动画
- **AND** 动画速度与进度匹配

#### Scenario: 脉冲量子动画

- **WHEN** 使用 `QuantumAnimation` 组件显示警告
- **THEN** 显示脉冲的量子圆环动画
- **AND** 脉冲颜色与警告级别匹配

---

## 非功能需求

### Requirement: 性能

#### Scenario: UI 响应时间

- **WHEN** 用户点击按钮或执行操作
- **THEN** UI 必须在 100ms 内提供视觉反馈

#### Scenario: 动画流畅度

- **WHEN** 播放动画
- **THEN** 动画必须以 60fps 运行
- **AND** 不出现掉帧

### Requirement: 兼容性

#### Scenario: Android 版本兼容

- **WHEN** 应用运行在 Android 12 (API 31) 或更高版本
- **THEN** 所有功能正常工作

#### Scenario: 屏幕尺寸兼容

- **WHEN** 应用运行在不同屏幕尺寸（手机、折叠屏、平板）
- **THEN** UI 布局自适应
- **AND** 保持可用性

### Requirement: 可维护性

#### Scenario: 代码结构

- **WHEN** 开发者阅读 UI 代码
- **THEN** 代码遵循清晰的目录结构
- **AND** 组件职责单一
- **AND** 依赖关系清晰

---

**规范版本**: 1.0.0
**最后更新**: 2026-02-15
**作者**: Aeternum Team
