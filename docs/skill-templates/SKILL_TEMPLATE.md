# OpenRing Skill Instruction Template / 範本

## 繁體中文

```markdown
---
name: your_skill_name
description: 這個 skill 的一句話用途說明。
---

# your_skill_name

請在以下情況使用此 skill：
- 使用者要求 ...
- 任務需要可預期、可重現的 deterministic 處理

呼叫方式：
- 優先使用動態工具：`skill_your_skill_name`
- 或使用 `call_skill`，並傳入 `skill: "your_skill_name"`

輸入：
- fieldA: string
- fieldB: boolean

輸出：
- result: string

請不要在以下情況使用：
- 使用者要求 ...
```

## English

```markdown
---
name: your_skill_name
description: One-line summary for this skill.
---

# your_skill_name

Use this skill when:
- The user asks for ...
- The task needs deterministic processing

Call pattern:
- Prefer dynamic tool: `skill_your_skill_name`
- Or explicit call: `call_skill` with `skill: "your_skill_name"`

Input:
- fieldA: string
- fieldB: boolean

Output:
- result: string

Do not use when:
- The user asks for ...
```

