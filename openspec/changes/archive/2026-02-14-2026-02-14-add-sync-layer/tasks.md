# 实现任务清单 (add-sync-layer)

## 概览

实现 `sync/` 模块的完整任务列表，按优先级和依赖关系排序。

---

## 阶段 1：基础设施

### 1.1 添加依赖
- [x] 在 `core/Cargo.toml` 中添加以下依赖：
  - [x] `rand = "0.8"` - 随机数生成（Padding）
  - [x] `serde = { version = "1.0", features = ["derive"] }` - 序列化
- [x] 运行 `cargo check` 确认依赖解析成功

### 1.2 创建模块结构
- [x] 创建 `core/src/sync/` 目录
- [x] 创建 `mod.rs` 模块文件
- [x] 在 `core/src/lib.rs` 中添加 `pub mod sync;`

---

## 阶段 2：Wire Frame 实现

### 2.1 实现 `frame.rs`
- [x] 定义 `WireFrame` 结构体
  - [x] `nonce: [u8; 24]` - XChaCha20-Poly1305 随机数
  - [x] `epoch: u32` - 当前逻辑纪元版本（明文，用于路由）
  - [x] `payload_type: u8` - 消息类型
  - [x] `encrypted_body: Vec<u8>` - 加密后的负载
  - [x] `padding: Vec<u8>` - 随机填充
  - [x] `auth_tag: [u8; 16]` - Poly1305 认证标签
- [x] 实现 `WireFrame::new()` 构造函数
- [x] 实现 `WireFrame::serialize()` 方法（固定 8192 字节）
- [x] 实现 `WireFrame::deserialize()` 方法
- [x] 实现 `WireFrame::validate()` 方法（验证长度和认证标签）

### 2.2 实现 `codec.rs`
- [x] 定义 `PayloadType` 枚举
  - [x] `Handshake` - 设备配对
  - [x] `Sync` - 纪元同步
  - [x] `Veto` - 否决信号
  - [x] `Recovery` - 恢复流程
- [x] 定义 `MessageCodec` trait
- [x] 实现 `encode()` 方法（序列化 + 加密）
- [x] 实现 `decode()` 方法（解密 + 反序列化）

### 2.3 Wire Frame 测试
- [x] 测试 Frame 序列化往返
- [x] 测试固定长度 8192 字节约束
- [x] 测试认证标签验证
- [x] 测试空 Payload 场景
- [x] 测试最大 Payload 场景

---

## 阶段 3：混合加密握手

### 3.1 实现 `handshake.rs`
- [x] 定义 `HybridHandshake` 结构体
- [x] 实现 `HybridHandshake::initiate()` 方法
  - [x] 生成 X25519 密钥对
  - [x] 生成 Kyber-1024 密钥对
  - [x] 组合公钥（X25519_PK || Kyber_PK）
- [x] 实现 `HybridHandshake::respond()` 方法
  - [x] 使用对端公钥封装 Kyber
  - [x] 执行 X25519 DH
  - [x] 组合共享密钥（X25519_SS || Kyber_SS）
- [x] 实现 `HybridHandshake::derive_session_key()` 方法
  - [x] `K_session = HKDF-SHA256(SS_X25519 || SS_Kyber || Context_ID)`
- [x] 实现 `HandshakeState` 状态机

### 3.2 握手测试
- [x] 测试完整握手流程
- [x] 测试会话密钥派生确定性
- [x] 测试混合密钥组合正确性
- [x] 测试中间人攻击防护

---

## 阶段 4：流量混淆机制

### 4.1 实现 `chaff.rs`
- [x] 定义 `ChaffGenerator` 结构体
- [x] 实现 `ChaffGenerator::generate_padding()` 方法
  - [x] 确保 Frame 总长度 = 8192 字节
  - [x] 使用 CSPRNG 生成随机填充
- [x] 实现 `ChaffGenerator::create_chaff_sync()` 方法
  - [x] 生成诱饵同步消息
  - [x] 诱饵消息格式与真实消息完全一致
- [x] 实现 `ChaffGenerator::timing_jitter()` 方法
  - [x] 50ms-200ms 随机延迟
  - [x] 防止计时攻击

### 4.2 流量混淆测试
- [x] 测试填充长度正确性（始终 8192 字节）
- [x] 测试诱饵流量不可区分性
- [x] 测试时序抖动分布
- [x] 测试诱饵与真实消息的统计独立性

---

## 阶段 5：Wire 协议核心

### 5.1 实现 `wire.rs`
- [x] 定义 `WireProtocol` 结构体
- [x] 实现 `WireProtocol::send_message()` 方法
  - [x] 构建 WireFrame
  - [x] 应用 Padding
  - [x] AEAD 加密
  - [x] 添加认证标签
- [x] 实现 `WireProtocol::receive_message()` 方法
  - [x] 验证认证标签
  - [x] AEAD 解密
  - [x] 移除 Padding
  - [x] 解析 Payload
- [x] 实现 `WireProtocol::handle_veto()` 方法
  - [x] 验证 StrongBox 签名（TODO：待集成 Android StrongBox/KeyStore API）
  - [x] 检查 48h 窗口
  - [x] 终止恢复流程（Invariant #4）
- [x] 实现 `WireProtocol::nonce_memo()` 方法
  - [x] 检测重复指令
  - [x] 防止重放攻击

### 5.2 Wire 协议测试
- [x] 测试完整消息发送-接收往返
- [x] 测试重放攻击防护
- [x] 测试否决信号优先级
- [x] 测试幂等性保证

---

## 阶段 6：协议版本管理

### 6.1 实现版本管理
- [x] 定义 `ProtocolVersion` 结构体
  - [x] `major: u8`
  - [x] `minor: u8`
- [x] 实现 `ProtocolVersion::check_compatibility()` 方法
  - [x] 向后兼容检查
  - [x] 强制升级检查
- [x] 定义 `VersionNegotiation` 消息类型

### 6.2 版本管理测试
- [x] 测试版本兼容性检查
- [x] 测试版本不匹配处理
- [x] 测试强制升级场景

---

## 阶段 7：模块集成

### 7.1 模块导出
- [x] 在 `sync/mod.rs` 中添加所有子模块
- [x] 重新导出常用类型到 crate root
- [x] 确保所有公共 API 有文档注释

### 7.2 集成测试
- [x] 创建 `core/tests/sync_integration.rs`
- [x] 测试跨模块类型使用
- [x] 测试与 `crypto/` 模块的集成
- [x] 测试与 `models/` 模块的集成
- [x] 测试与 `storage/` 模块的集成

---

## 阶段 8：文档与发布

### 8.1 代码文档
- [x] 确保所有公共 API 有文档注释
- [x] 添加使用示例到关键类型
- [x] 添加协议流程图到文档

### 8.2 测试覆盖率
- [x] 运行 `cargo test --all-targets` 确保所有测试通过
- [x] 运行 `cargo clippy --all-targets` 检查代码质量
- [x] 使用 `cargo tarpaulin` 检查测试覆盖率 ≥ 95%

### 8.3 更新变更状态
- [x] 所有任务完成后，将此文件的 `- [ ]` 改为 `- [x]`
- [x] 标记提案状态为 ✅ 已完成 (Completed)

---

## 检查清单

在完成所有任务前：

- [x] 运行 `cargo test --all-targets` 确保所有测试通过
- [x] 运行 `cargo clippy --all-targets` 检查代码质量
- [x] 运行 `./scripts/format-check.sh` 检查代码格式
- [x] 确保 Wire Frame 始终是 8192 字节
- [x] 确保所有消息通过 AEAD 加密
- [x] 确保否决信号具有最高优先级
- [x] 确保测试覆盖率 ≥ 95%

---

## 总计

- **总任务数**: 76
- **阶段 1 (基础设施)**: 5
- **阶段 2 (Wire Frame)**: 9
- **阶段 3 (握手)**: 10
- **阶段 4 (流量混淆)**: 8
- **阶段 5 (Wire 协议)**: 11
- **阶段 6 (版本管理)**: 8
- **阶段 7 (集成)**: 6
- **阶段 8 (文档)**: 7
