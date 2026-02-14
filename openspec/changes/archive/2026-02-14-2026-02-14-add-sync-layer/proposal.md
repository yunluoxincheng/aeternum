# Change: 实现同步协议层 (Aeternum Wire)

## Why

根据架构白皮书 v5.0 和 [同步协议规范](../../../docs/protocols/Sync-Wire-Protocol.md)，Aeternum 需要实现设备间通信的同步协议层。当前 `core/src/sync/` 目录不存在，无法支持：

- **设备配对协议** - 新设备通过二维码 + BLE 加入信任域
- **全局纪元同步** - PQRR 触发后的多设备协调
- **否决信号广播** - 48h 窗口内的恢复拦截机制
- **流量指纹防护** - 通过 Padding 和 Chaff Sync 实现统计学隐匿

这些功能是 Aeternum "多设备主权" 的核心能力。

## What Changes

- **新增**: `core/src/sync/` 模块结构
  - `frame.rs` - Aeternum-Frame 封装格式（8192 字节固定帧）
  - `wire.rs` - Wire 协议实现
  - `codec.rs` - 消息编解码
  - `chaff.rs` - 诱饵流量与指纹混淆
  - `handshake.rs` - 混合加密握手协议
- **新增**: `WireFrame` 结构体（Nonce + Epoch + Payload + Padding + AuthTag）
- **新增**: `HybridHandshake` 协议（X25519 + Kyber-1024）
- **新增**: `ChaffGenerator` 流量混淆器
- **新增**: 消息类型枚举（Handshake, Sync, Veto, Recovery）
- **新增**: 协议版本管理

## Impact

- **影响范围**: `core/src/sync/`, `core/src/lib.rs`
- **依赖项**:
  - `rand = "0.8"` - 随机数生成（Padding）
  - `serde = { version = "1.0", features = ["derive"] }` - 消息序列化
  - 复用 `crypto/` 模块的 X25519, Kyber, XChaCha20
- **向后兼容**: 不影响现有代码（新增功能）
- **跨模块集成**: 与 `models/`（Epoch, DeviceHeader）和 `storage/`（AUP）协同工作

## 风险评估

### 高优先级风险

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| **流量分析攻击** | 高价值目标指纹暴露 | 固定 8192 字节帧 + Chaff Sync + 时序抖动 |
| **密钥重用** | 前向安全失效 | 每次握手生成新密钥对，Nonce 强制唯一 |
| **否决信号劫持** | 恢复流程被滥用 | StrongBox 硬件签名 + 48h 窗口验证 (Invariant #4) |

### 缓解措施

- ✅ 严格遵守 [Wire 协议规范](../../../docs/protocols/Sync-Wire-Protocol.md)
- ✅ 所有消息必须通过 AEAD 加密 + 认证
- ✅ 固定长度 8192 字节防止流量分析
- ✅ 测试覆盖率要求 ≥ 95%
- ✅ 形式化验证四大数学不变量
- ✅ 使用 `proptest` 进行属性测试确保帧大小恒定
- ✅ 敏感数据结构必须实现 `Zeroize`

## 约束确认

- ✅ 必须实现固定 8192 字节 Wire Frame（防止流量指纹）
- ✅ 所有同步指令必须包含 Nonce（防止重放攻击）
- ✅ 必须支持 Invariant #4（否决权优先级最高）
- ✅ 必须通过影子写入实现原子更新

---

## 性能指标

| 指标 | 目标值 | 说明 |
|------|-------|------|
| **握手延迟** | < 500ms | 完整混合握手流程 (X25519 + Kyber-1024) |
| **帧序列化** | < 1ms | 8192 字节帧序列化 |
| **纪元同步** | < 2s | 50%+ 设备确认完成 |
| **否决响应** | < 100ms | 否决信号传播（最高优先级） |
| **内存占用** | < 10MB | sync 模块常驻内存 |
| **测试覆盖率** | ≥ 95% | 单元测试 + 集成测试 |

---

## 验收标准

提案完成需满足：

1. **功能完整性**
   - [x] 所有 8 个阶段的任务完成
   - [x] 76 个具体任务全部标记为 `[x]`
   - [x] 所有测试通过 (`cargo test --all-targets`)

2. **代码质量**
   - [x] `cargo clippy --all-targets` 无警告
   - [x] `./scripts/format-check.sh` 通过
   - [x] 测试覆盖率 ≥ 95%

3. **安全验证**
   - [x] Wire Frame 始终是 8192 字节（属性测试验证）
   - [x] 所有消息通过 AEAD 加密
   - [x] 否决信号具有最高优先级
   - [x] 敏感数据结构实现 `Zeroize`

4. **文档完整性**
   - [x] 公共 API 有文档注释
   - [x] 使用示例添加到关键类型
   - [x] design.md 包含状态机图和时序图

---

## 设计文档

详细的架构设计、状态机定义和威胁模型分析请参见 [design.md](./design.md)。
