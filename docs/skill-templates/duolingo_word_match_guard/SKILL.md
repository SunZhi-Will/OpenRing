---
name: duolingo_word_match_guard
description: Resolve a Duolingo word-match target deterministically and refuse ambiguous picks.
---

# duolingo_word_match_guard

Use this skill when:
- The user asks to solve Duolingo "match the pairs" / word-match exercises.
- The UI shows multiple similar labels and blind clicking is risky.
- You need deterministic matching before calling click tools.

Call pattern:
- Prefer dynamic tool: `skill_duolingo_word_match_guard`
- Or explicit call: `call_skill` with `skill: "duolingo_word_match_guard"`

Input:
- `target`: string, the token you intend to click.
- `choices`: string[], clickable labels currently visible on screen.
- `allowContainsFallback` (optional): boolean, default false.

Output:
- `status`: `"ok" | "ambiguous" | "not_found" | "invalid_argument"`
- `selected`: chosen label when status is `ok`.
- `confidence`: number in [0,1].
- `reason`: deterministic explanation.
- `matchedIndices`: index list from `choices`.

Execution policy:
- If `status != "ok"`, do not click. Refresh UI (`get_view_tree` / `summarize_view_tree`) and retry with better target text.
- Prefer exact normalized matches; only enable contains fallback when explicitly requested.

Do not use when:
- The task is free-form conversation generation.
- The UI is not a discrete option list where `choices` can be enumerated.
