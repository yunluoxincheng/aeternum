---
name: OpenSpec: Apply
description: Implement an approved OpenSpec change and keep tasks in sync.
category: OpenSpec
tags: [openspec, apply]
---
<!-- OPENSPEC:START -->
**IMPORTANT: Aeternum 技能集成**

在开始实现任何提案前，**必须先执行以下步骤**：

1. **调用 `/aeternum-checkpoint`** 识别任务类型并阅读必读文档
2. **根据 checkpoint 结果调用对应技能**：
   - `/aeternum-crypto` - 涉及密码学原语 (KEM, KDF, 哈希, zeroize)
   - `/aeternum-protocol` - 涉及协议与状态机 (PQRR, 纪元升级, 影子包装, 否决)
   - `/aeternum-android` - 涉及 Android 安全层 (UI, 生物识别, StrongBox, Play Integrity)
   - `/aeternum-bridge` - 涉及 UniFFI 桥接 (UDL 接口, 桥接代码生成)
   - `/aeternum-invariant` - 涉及不变量验证

3. **确认所有约束已理解** 后再开始编码

---

**Guardrails**
- Favor straightforward, minimal implementations first and add complexity only when it is requested or clearly required.
- Keep changes tightly scoped to the requested outcome.
- Refer to `openspec/AGENTS.md` (located inside `openspec/` directory—run `ls openspec` or `openspec update` if you don't see it) if you need additional OpenSpec conventions or clarifications.

**Steps**
1. **[MANDATORY]** Call `/aeternum-checkpoint` to identify task type and read required documentation
2. **[MANDATORY]** Based on checkpoint results, call the corresponding aeternum skill:
   - `/aeternum-crypto` for cryptography primitives (KEM, KDF, hash, zeroize)
   - `/aeternum-protocol` for protocol and state machine (PQRR, epoch upgrade, shadow wrapping, veto)
   - `/aeternum-android` for Android security layer (UI, biometric, StrongBox, Play Integrity)
   - `/aeternum-bridge` for UniFFI bridging (UDL interfaces, bridge code generation)
   - `/aeternum-invariant` for invariant verification
3. Read `changes/<id>/proposal.md`, `design.md` (if present), and `tasks.md` to confirm scope and acceptance criteria.
4. Work through tasks sequentially, keeping edits minimal and focused on the requested change.
5. Confirm completion before updating statuses—make sure every item in `tasks.md` is finished.
6. Update checklist after all work is done so each task is marked `- [x]` and reflects reality.
7. Reference `openspec list` or `openspec show <item>` when additional context is required.

**Reference**
- Use `openspec show <id> --json --deltas-only` if you need additional context from the proposal while implementing.
<!-- OPENSPEC:END -->
