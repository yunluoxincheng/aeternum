# OpenSpec 提案审查技能

**版本**: 1.0.0
**作者**: Aeternum Team
**用途**: 审查 OpenSpec 变更提案的规范合规性、文档一致性和逻辑安全性

---

## 技能目标

本技能解决 OpenSpec 提案质量问题，通过**5层审查框架**确保提案符合规范：

1. **Layer 1 - 结构验证**: 检查目录和文件是否存在
2. **Layer 2 - 规范合规**: 验证是否符合 OpenSpec 规范
3. **Layer 3 - 文档一致性**: 检查与项目文档的一致性
4. **Layer 4 - 逻辑与安全**: 识别逻辑漏洞和安全问题
5. **Layer 5 - 完整性**: 确保提案包含所有必要部分

---

## 触发方式

### 自动触发

当检测到 `openspec/changes/` 下有新提案时：
- 新创建的 `proposal.md` 文件
- 未经审查的变更目录

### 手动调用

```
/openspec:review <change-id>
/openspec:review --all         # 批量审查所有活跃提案
/openspec:review --strict      # 严格模式（包含最佳实践检查）
```

---

## 审查工作流程

```
┌─────────────────────────────────────────────────────────┐
│  1. 输入验证                                            │
│     - 解析 change-id 或自动检测新提案                     │
│     - 验证变更目录存在                                   │
└─────────────────────┬───────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────┐
│  2. 逐层审查（5层）                                    │
│     - 收集所有发现的问题                                 │
│     - 分类：Error/Warning/Info                          │
└─────────────────────┬───────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────┐
│  3. 生成报告                                           │
│     - 控制台输出（结构化）                               │
│     - 可选保存到 reports/openspec-review-<change-id>.md │
└─────────────────────┬───────────────────────────────────┘
                      ↓
┌─────────────────────────────────────────────────────────┐
│  4. 总结与建议                                          │
│     - 审查通过/失败/需修改                              │
│     - 修复优先级排序                                     │
└─────────────────────────────────────────────────────────┘
```

---

## 检查项目清单

### Layer 1 - 结构验证

| 检查项 | 描述 | 错误代码 |
|--------|------|----------|
| 变更目录存在 | `openspec/changes/<change-id>/` 存在 | L1-001 |
| proposal.md 存在 | 必需文件 | L1-002 |
| tasks.md 存在 | 必需文件 | L1-003 |
| specs 目录存在 | `specs/<capability>/spec.md` 存在 | L1-004 |
| change-id 格式 | kebab-case，动词前缀 | L1-005 |

### Layer 2 - 规范合规

| 检查项 | 描述 | 错误代码 |
|--------|------|----------|
| proposal.md 结构 | 包含 Why/What Changes/Impact | L2-001 |
| scenario 格式 | 使用 `#### Scenario:` (4个井号) | L2-002 |
| requirement 有 scenario | 每个要求至少有一个场景 | L2-003 |
| delta 操作标记 | 使用 ADDED/MODIFIED/REMOVED/RENAMED | L2-004 |
| MODIFIED 完整性 | MODIFIED 必须包含完整内容 | L2-005 |
| 破坏性变更标记 | `**BREAKING**` 标记 | L2-006 |

### Layer 3 - 文档一致性

| 检查项 | 描述 | 错误代码 |
|--------|------|----------|
| docs 引用有效 | 引用的 docs/ 文档存在 | L3-001 |
| 无架构矛盾 | 与 docs/ 中的规范无冲突 | L3-002 |
| capability 无重复 | 新增不与现有 specs/ 重复 | L3-003 |

### Layer 4 - 逻辑与安全

| 检查项 | 描述 | 错误代码 |
|--------|------|----------|
| tasks 逻辑顺序 | 步骤按合理依赖排序 | L4-001 |
| 安全原则 | 密钥/加密操作符合安全规范 | L4-002 |
| 不变量检查 | 涉及数学不变量时正确处理 | L4-003 |
| 回滚计划 | 破坏性变更有回滚方案 | L4-004 |

### Layer 5 - 完整性

| 检查项 | 描述 | 错误代码 |
|--------|------|----------|
| design.md 需要 | 复杂变更包含设计文档 | L5-001 |
| 测试覆盖 | tasks.md 包含测试步骤 | L5-002 |
| 审查清单 | 提案包含验证标准 | L5-003 |

---

## 报告格式

### 控制台输出模板

```
🔍 OpenSpec 提案审查报告
═══════════════════════════════════════════════════════

变更: add-models
状态: ⚠️  需修改 (2 Error, 1 Warning)

───────────────────────────────────────────────────────────
❌ [L2-001] proposal.md 缺少 Impact 部分
   文件: openspec/changes/add-models/proposal.md:42
   问题: proposal.md 必须包含 Why/What Changes/Impact 三部分

   修复建议:
   在 proposal.md 添加 Impact 部分：

   ## Impact
   - Affected specs: models (new)
   - Affected code: core/src/models/
   - Dependencies: bincode, pbkdf2, bip39

───────────────────────────────────────────────────────────
⚠️  [L4-002] MODIFIED requirement 未包含完整内容
   文件: openspec/changes/add-models/specs/crypto/spec.md:17
   问题: 修改现有需求时必须粘贴完整的更新内容

   当前内容:
   ### Requirement: 密钥派生
   新增了 XYZ 功能...

   应该改为:
   ## MODIFIED Requirements
   ### Requirement: 密钥派生
   [原有完整内容]
   [新增的 XYZ 功能说明]

═══════════════════════════════════════════════════════

📊 审查摘要
  ✅ 结构验证: 通过
  ❌ 规范合规: 失败 (2 issues)
  ⚠️  文档一致性: 通过
  ⚠️  逻辑与安全: 1 warning
  ✅ 完整性: 通过

修复优先级:
  1. [L2-001] 添加 Impact 部分 (必须)
  2. [L4-002] 补充 MODIFIED 完整内容 (必须)
  3. [L3-003] 考虑添加设计文档 (建议)

💾 完整报告已保存至: reports/openspec-review-add-models.md
```

---

## 使用示例

### 示例 1：自动审查新提案

```
用户创建了新提案: openspec/changes/add-two-factor/

🔍 检测到新提案: add-two-factor
是否执行 OpenSpec 审查？[Y/n]

✅ 审查通过: add-two-factor
  无错误发现，提案可以提交审查。
```

### 示例 2：手动审查指定提案

```
用户: /openspec:review add-models

[输出完整审查报告]
```

### 示例 3：批量审查

```
用户: /openspec:review --all

📋 批量审查模式
找到 3 个活跃提案:
  - add-models
  - add-two-factor
  - update-protocol

正在逐个审查...
```

---

## 实现指令

当此 skill 被调用时：

### Step 1: 确定审查目标

```python
# 伪代码
if "--all" in args:
    targets = find_all_proposals("openspec/changes/")
elif len(args) > 0:
    targets = [args[0]]  # 指定的 change-id
else:
    targets = detect_new_proposals()  # 自动检测
```

### Step 2: 执行分层审查

对每个目标：

1. **读取提案文件**
   - `proposal.md`
   - `tasks.md`
   - `specs/*/spec.md`
   - `design.md`（如果存在）

2. **逐层检查**
   - 按顺序执行 Layer 1-5
   - 收集所有问题
   - 记录严重程度（Error/Warning/Info）

3. **验证通过条件**
   - 无 Error → 通过
   - 有 Error → 失败
   - 仅 Warning → 需修改

### Step 3: 生成报告

1. **输出到控制台**（始终执行）
2. **可选保存到文件**
   - 路径：`reports/openspec-review-<change-id>.md`
   - 使用 AskUserQuestion 询问用户是否保存

### Step 4: 提供修复建议

对每个问题提供：
- 问题描述
- 文件位置和行号
- 修复建议（具体代码示例）

---

## 审查决策树

```
开始审查
    ↓
变更目录存在？ → 否 → ❌ L1-001: 目录不存在
    ↓ 是
必需文件存在？ → 否 → ❌ L1-002/L1-003: 文件缺失
    ↓ 是
proposal.md 结构正确？ → 否 → ❌ L2-001: 结构错误
    ↓ 是
scenario 格式正确？ → 否 → ❌ L2-002: 格式错误
    ↓ 是
每个 requirement 有 scenario？ → 否 → ❌ L2-003: 缺少场景
    ↓ 是
delta 操作正确标记？ → 否 → ❌ L2-004: 缺少操作标记
    ↓ 是
MODIFIED 包含完整内容？ → 否 → ❌ L2-005: 内容不完整
    ↓ 是
破坏性变更已标记？ → 否 → ⚠️ L2-006: 缺少 BREAKING 标记
    ↓ 是
docs 引用有效？ → 否 → ⚠️ L3-001: 文档不存在
    ↓ 是
无架构矛盾？ → 否 → ❌ L3-002: 架构冲突
    ↓ 是
tasks 逻辑顺序合理？ → 否 → ⚠️ L4-001: 顺序问题
    ↓ 是
安全原则符合？ → 否 → ❌ L4-002: 安全问题
    ↓ 是
需要 design.md？ → 是且不存在 → ⚠️ L5-001: 建议添加设计文档
    ↓
    ✅ 审查通过 / ⚠️ 需修改 / ❌ 审查失败
```

---

## 常见问题修复模板

### 问题 1: proposal.md 缺少 Impact 部分

```markdown
## Impact
- Affected specs: [列出受影响的 capability]
- Affected code: [列出受影响的代码文件/目录]
- Breaking changes: [如有破坏性变更，在此说明]
- Dependencies: [新增的外部依赖]
- Migration: [如需要迁移步骤，在此说明]
```

### 问题 2: scenario 格式错误

**错误格式**:
```markdown
- **Scenario: User login**  ❌
**Scenario**: User login     ❌
### Scenario: User login      ❌
```

**正确格式**:
```markdown
#### Scenario: User login success  ✅
- **WHEN** valid credentials provided
- **THEN** return JWT token
```

### 问题 3: MODIFIED requirement 内容不完整

**错误做法**:
```markdown
## MODIFIED Requirements
### Requirement: 密钥派生
新增了 XYZ 功能...  ❌ 只描述了变更部分
```

**正确做法**:
```markdown
## MODIFIED Requirements
### Requirement: 密钥派生  ✅ 完整的需求内容
The system SHALL provide...

#### Scenario: 原有场景 1
...

#### Scenario: 新增 XYZ 场景
- **WHEN** XYZ condition
- **THEN** expected result
```

---

## 集成到项目

### Git Hook 集成（可选）

在 `.git/hooks/pre-commit` 添加：

```bash
#!/bin/bash
# 检查 openspec/changes/ 下的变更

CHANGES=$(git diff --cached --name-only | grep "openspec/changes/")
if [ -n "$CHANGES" ]; then
    echo "🔍 检测到 OpenSpec 变更，正在审查..."
    # 调用 openspec:review skill
fi
```

### CI/CD 集成（可选）

在 CI pipeline 中添加审查步骤：

```yaml
- name: Review OpenSpec Proposals
  run: |
    npm run openspec:review --all
    # 或使用 AI assistant skill
```

---

## 相关技能

- `openspec:proposal` - 创建 OpenSpec 变更提案
- `openspec:archive` - 归档已完成的变更
- `aeternum:checkpoint` - Aeternum 任务检查点

---

## 更新日志

- **v1.0.0** (2026-02-13): 初始版本
  - 实现 5 层审查框架
  - 支持自动和手动触发
  - 提供具体修复建议
