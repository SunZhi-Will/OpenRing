---
name: duolingo_word_match_guard
description: Deterministic Duolingo word-match; pair with describe_ambient_audio for listen/sound exercises.
---

# duolingo_word_match_guard

**Listen / sound / type-what-you-hear (do this first when audio matters):** Call host **`describe_ambient_audio`** while the clip plays or after **replay**. Enable **phone playback audio** (MediaProjection) + **RECORD_AUDIO** in OpenRing Permissions (internal mix, not just mic). From the transcript, build **`target`**, then **`choices`** from `get_view_tree` / `summarize_view_tree`, then use this skill or `duolingo_match_pick`. Do **not** guess listen answers from the accessibility tree if the spoken word is not shown as text.

**Word-match / pair grids:** Prefer `skill_duolingo_word_match_guard` or `call_skill` with `duolingo_word_match_guard` before clicks. **Input:** `target`, `choices[]`, optional `allowContainsFallback`. If `status != ok`, refresh UI—do not click blindly.

**Keep going:** After each action, refresh the view and continue with tools until the exercise advances or the user stops; avoid ending with a short reply while Duolingo still shows an active lesson.

**Not for:** tasks with no enumerable `choices`, or unrelated chat.
