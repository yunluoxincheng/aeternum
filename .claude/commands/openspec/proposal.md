---
name: OpenSpec: Proposal
description: Scaffold a new OpenSpec change and validate strictly.
category: OpenSpec
tags: [openspec, change]
---
<!-- OPENSPEC:START -->
**IMPORTANT: Aeternum 技能集成**

在创建任何提案前，**必须先执行以下步骤**：

1. **调用 `/aeternum-checkpoint`** 识别任务类型并阅读必读文档
2. **根据 checkpoint 结果调用对应技能**：
   - `/aeternum-crypto` - 涉及密码学原语 (KEM, KDF, 哈希)
   - `/aeternum-protocol` - 涉及协议与状态机 (PQRR, 纪元升级, 否决)
   - `/aeternum-android` - 涉及 Android 安全层 (UI, 生物识别, StrongBox)
   - `/aeternum-bridge` - 涉及 UniFFI 桥接 (UDL, Kotlin 接口)
   - `/aeternum-invariant` - 涉及不变量验证

3. **确认所有约束已理解** 后再开始编写提案文档

---

**Guardrails**
- Favor straightforward, minimal implementations first and add complexity only when it is requested or clearly required.
- Keep changes tightly scoped to the requested outcome.
- Refer to `openspec/AGENTS.md` (located inside `openspec/` directory—run `ls openspec` or `openspec update` if you don't see it) if you need additional OpenSpec conventions or clarifications.
- Identify any vague or ambiguous details and ask necessary follow-up questions before editing files.
- **Do not write any code during proposal stage.** Only create design documents (proposal.md, tasks.md, design.md, and spec deltas). Implementation happens in apply stage after approval.

**Steps**
1. **[MANDATORY]** Call `/aeternum-checkpoint` to identify task type and read required documentation
2. **[MANDATORY]** Based on checkpoint results, call the corresponding aeternum skill:
   - `/aeternum-crypto` for cryptography primitives
   - `/aeternum-protocol` for protocol and state machine
   - `/aeternum-android` for Android security layer
   - `/aeternum-bridge` for UniFFI bridging
   - `/aeternum-invariant` for invariant verification
3. Review `openspec/project.md`, run `openspec list` and `openspec list --specs`, and inspect related code or docs (e.g., via `rg`/`ls`) to ground the proposal in current behaviour; note any gaps that require clarification.
4. Choose a unique verb-led `change-id` and scaffold `proposal.md`, `tasks.md`, and `design.md` (when needed) under `openspec/changes/<id>/`.
5. Map change into concrete capabilities or requirements, breaking multi-scope efforts into distinct spec deltas with clear relationships and sequencing.
6. Capture architectural reasoning in `design.md` when solution spans multiple systems, introduces new patterns, or demands trade-off discussion before committing to specs.
7. Draft spec deltas in `changes/<id>/specs/<capability>/spec.md` (one folder per capability) using `## ADDED|MODIFIED|REMOVED Requirements` with at least one `#### Scenario:` per requirement and cross-reference related capabilities when relevant.
8. Draft `tasks.md` as an ordered list of small, verifiable work items that deliver user-visible progress, include validation (tests, tooling), and highlight dependencies or parallelizable work.
9. Validate with `openspec validate <id> --strict` and resolve every issue before sharing the proposal.

**Reference**
- Use `openspec show <id> --json --deltas-only` or `openspec show <spec> --type spec` to inspect details when validation fails.
- Search existing requirements with `rg -n "Requirement:|Scenario:" openspec/specs` before writing new ones.
- Explore codebase with `rg <keyword>`, `ls`, or direct file reads so proposals align with current implementation realities.
<!-- OPENSPEC:END -->
