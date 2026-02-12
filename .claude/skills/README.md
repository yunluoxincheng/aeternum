# Aeternum 开发技能系统

Aeternum 项目的专用开发技能集合，用于辅助 AI 辅助开发。

## 技能列表

| 技能 | 用途 | 触发关键词 |
|------|------|-----------|
| `aeternum:crypto` | 密码学原语开发 | Kyber, X25519, KDF, zeroize |
| `aeternum:protocol` | 协议与状态机开发 | PQRR, 纪元升级, 影子包装, 否决 |
| `aeternum:android` | Android 安全层开发 | 生物识别, StrongBox, Play Integrity |
| `aeternum:bridge` | UniFFI 桥接管理 | UDL 接口, 桥接代码, FFI |
| `aeternum:invariant` | 不变量验证 | 验证不变量, 生成报告 |

## 四大数学不变量

1. **Invariant #1 — 纪元单调性**: 所有设备的 epoch 必须严格单调递增
2. **Invariant #2 — Header 完备性**: 每个活跃设备必须且仅能通过一个正确的 Header 获取 DEK
3. **Invariant #3 — 因果熵障**: 解密权限 ≠ 管理权限（RECOVERY 角色不能执行 σ_rotate）
4. **Invariant #4 — 否决权优先**: 48h 窗口内任何活跃设备的 Veto 信号必须立即终止恢复流程

## 使用方式

### 直接调用技能

```
/aeternum:crypto 实现 Kyber-1024 KEM 封装功能
/aeternum:protocol 添加设备撤销功能
/aeternum:android 创建生物识别认证界面
/aeternum:bridge 添加新的 UniFFI 接口方法
/aeternum:invariant 验证 core/src/protocol/ 全部文件
```

### 自然语言触发

```
"实现 PQRR 纪元升级逻辑" → 自动触发 aeternum:protocol
"添加 BLAKE3 密钥派生" → 自动触发 aeternum:crypto
"创建解锁界面" → 自动触发 aeternum:android
"修改 UDL 接口定义" → 自动触发 aeternum:bridge
"检查代码是否符合安全约束" → 自动触发 aeternum:invariant
```

## 技能协作

某些任务需要多个技能协作完成，例如：

**新设备添加流程**:
1. `aeternum:protocol` - 实现设备配对协议
2. `aeternum:crypto` - 实现混合 KEM
3. `aeternum:android` - 创建配对 UI
4. `aeternum:bridge` - 添加 UDL 接口

## 代码位置

```
.claude/skills/
├── aeternum-crypto/
│   └── skill.md          # 密码学原语开发技能
├── aeternum-protocol/
│   └── skill.md          # 协议与状态机开发技能
├── aeternum-android/
│   └── skill.md          # Android 安全层开发技能
├── aeternum-bridge/
│   └── skill.md          # UniFFI 桥接管理技能
├── aeternum-invariant/
│   └── skill.md          # 不变量验证技能
├── metadata.yaml         # 技能元数据配置
└── README.md            # 本文件
```

## 相关文档

- [架构白皮书 v5.0](../../docs/arch/Aeternum-architecture.md)
- [UniFFI 桥接契约](../../docs/bridge/UniFFI-Bridge-Contract.md)
- [形式化数学不变量](../../docs/math/Formal-Invariants.md)
- [项目结构规范](../../docs/Aeternum-Project-Structure-Spec.md)
