---
name: threads
description: Deterministic payload builder for Threads drafts
---

Use this skill when the user wants to draft or prepare content for Threads.

## When to use
- The user asks to prepare a Threads post payload from text.
- The task needs deterministic output fields for downstream handling.

## Input expectations
- `text` (string, required): post body content.
- `includeLink` (boolean, optional): whether link intent should be included.

## Output contract
- `postText` (string): normalized text to post.
- `includeLink` (boolean): link flag for preview or later publishing flow.

## Notes
- This skill does not publish to network services.
- It only prepares deterministic payload data.
