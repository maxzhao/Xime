---
title: 完成五笔拼音混输与组合态实体键盘行为
created: 2026-09-14
updated: 2026-09-14
doc_role: change-proposal
authority: proposed
status: draft
accepted_at:
merged_to: []
validation:
  automated: passed
  human: not-run
archive_state:
change_id: complete-wubi-pinyin-input
capability: wubi-pinyin-input
sources:
  - ".supermax/specs/wubi-pinyin-input/spec.md"
  - "User requirements agreed in chat on 2026-09-14"
confidence: high
---

# Proposal: complete-wubi-pinyin-input

## Intent

Make Xime's `wubi86_pinyin` behavior match the accepted Android physical-keyboard workflow and remove composition-editing races caused by using asynchronously propagated UI state as input authority.

## Scope

### In Scope

- Configurable first-use English mode with later mode memory.
- Physical right Shift, `Ctrl+.`, candidate selection, and bracket paging.
- Source-aware Wubi four-/five-key auto-commit without limiting Pinyin length.
- Agreed fuzzy-Pinyin rules in mixed and standalone schemes.
- Pinyin-origin mixed candidates displayed with longest full Wubi code.
- Composition-first Backspace, delete, navigation, Space, Enter, and Escape behavior with tri-state Rime dispatch.
- Normal and physical-keyboard floating candidate surfaces.

### Non-Goals

- New soft-keyboard candidate shortcut keys.
- Changes to unrelated T9, handwriting, voice, calculator, plugin, clipboard, or association product behavior.
- A compatibility mode for previous conflicting behavior.
- General settings/platform abstraction beyond the required configuration and routing contracts.

## Affected Capabilities

- Stable draft spec: `.supermax/specs/wubi-pinyin-input/spec.md`
- Android IME key routing and Rime result transport.
- Bundled `wubi86_pinyin`, `pinyin_simp`, and global Rime defaults.
- Candidate UI rendering.

## Proposed Behavior Change

- Replace UI-snapshot-based composition decisions with ordered, authoritative Rime key results.
- Distinguish handled, authoritatively unhandled, and unavailable/busy dispatch outcomes; permit host fallback only for authoritatively unhandled keys.
- Add source identity and Wubi-code display metadata to candidate transport without changing commit text or selection index.
- Implement source-aware exact-Wubi four-/five-key behavior in the Xime JNI/Rime integration boundary; do not set a mixed-schema global `max_code_length`.
- Configure the first-use mode through merged Xime configuration, while persisted `user.yaml` option presence controls later restoration.
- Consolidate fuzzy-Pinyin algebra into the shared Pinyin schema source used by standalone Pinyin and mixed input.
- Align fixed global Rime bindings with the accepted physical shortcuts.

## Risks And Compatibility

- Source-aware candidate inspection touches the native Rime integration hot path; keep metadata bounded to the current candidate page and avoid repeated per-candidate dictionary construction.
- Current worktree contains unrelated user-owned changes in key-routing/UI files; implementation must patch around them rather than overwrite them.
- `app/src/main/assets/rime` and `app/src/main/jni/librime*` are submodules. Changes must be committed at their owning source repositories/submodules and then referenced by the parent repository.
- Existing Xime plugin candidate transformations depend on engine indexes; added display metadata must not reorder or rewrite candidate identity.
- Existing installations require the final fixed configuration source to be synchronized/redeployed by the normal Xime deployment flow; no legacy behavioral fallback will be retained.

## Acceptance Criteria

- Every acceptance criterion in `.supermax/specs/wubi-pinyin-input/spec.md` passes.
- Automated unit/build validation is recorded.
- Physical-keyboard manual validation is recorded before the stable spec is promoted from draft.
- No unrelated subsystem behavior is intentionally changed.

## Source Trace

- `.supermax/specs/wubi-pinyin-input/spec.md`
- `app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt`
- `app/src/main/java/com/kingzcheung/xime/service/ImeKeyRouter.kt`
- `app/src/main/java/com/kingzcheung/xime/service/ImeSessionController.kt`
- `app/src/main/java/com/kingzcheung/xime/rime/RimeEngine.kt`
- `app/src/main/jni/librime_jni/rime_jni.cc`
- `app/src/main/assets/rime/wubi86_pinyin.schema.yaml`
- `app/src/main/assets/rime/pinyin_simp.schema.yaml`
- `app/src/main/assets/default.custom.yaml`
