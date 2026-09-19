# Task TODO

## Current State

- Status: pending
- Current phase: documentation prepared; implementation not started
- Next action: read the linked spec and implement the exact `TYPE_NULL` raw-output branch without changing normal editor behavior.

## Checklist

- [x] Confirm `.supermax/specs/voice-input-host-compatibility/spec.md` and current source baseline; preserve user-owned changes.
- [ ] Add/reuse exact `InputType.TYPE_NULL` classification at the IME service boundary and pass raw-target capability to `VoiceRecognitionHandler`.
- [ ] Keep raw-target partial results in Xime UI only; do not write host composing text.
- [ ] Commit raw final/timeout-fallback text once without surrounding-text reads, rewrite deletion, space removal, or heuristic punctuation.
- [ ] Preserve normal rich-editor voice behavior and existing duplicate-final/session-abandon guards.
- [ ] Add focused regression tests only for raw partial, raw final/fallback, and normal-path preservation.
- [ ] Run focused tests, `./gradlew :app:testDebugUnitTest`, and `./gradlew :app:assembleDebug`; repair relevant failures and rerun.
- [ ] Manually verify `git status` dictation in Termux and a normal text-field dictation; record exact evidence.
- [ ] Reconcile the draft spec, `.supermax/specs/index.md`, `.supermax/specs/log.md`, task evidence and status before completion.

## Loaded Context

- Plan: `plan.md`
- Rules/specs/knowledge:
  - `.supermax/AGENTS.md`
  - `.supermax/specs/voice-input-host-compatibility/spec.md`
  - `.supermax/specs/physical-keyboard-input/spec.md` (voice shortcut boundary only)
  - `.supermax/specs/ime-session-lifecycle/spec.md` (session cleanup boundary only)
- Supporting task documents: none.

## Changed Files

| Path | Change | Notes |
| --- | --- | --- |
| `.supermax/specs/voice-input-host-compatibility/spec.md` | created draft spec | Linked to TaskAdmin `master/4`; implementation/validation remain not run |
| `.supermax/specs/index.md` | added routing entry | No implementation state |
| `.supermax/specs/log.md` | recorded spec creation | Concise lifecycle evidence only |

## Validation

| Command / check | Result | Evidence |
| --- | --- | --- |
| Source/spec exploration | passed | Root cause and minimal behavior agreed with user on 2026-09-19 |
| Task/spec linkage | passed | Draft spec references `master/4`; task plan references the exact spec path |
| Automated implementation validation | not run | Implementation has not started |
| Termux/manual validation | not run | Required after implementation |

## Current Errors / Blockers

- None recorded.

## Decisions / Findings

- Root cause is the mismatch between `TYPE_NULL` limited input semantics and rich-editor composing/rewrite operations, not ASR capture or provider initialization.
- Termux supports direct `commitText` to its PTY, but does not provide a normal editable-buffer contract for composing correction and surrounding-text replacement.
- User accepted capability-based `TYPE_NULL` handling, UI-only partials, one-shot final commit, preserved spaces, and no automatic punctuation in raw targets.
- Do not hardcode Termux package names, synthesize per-character key events, parse shell commands, or broaden the task into ASR/plugin redesign.

## Next Actions

1. Continue from the first unresolved checklist item.
2. Keep implementation and tests limited to the agreed raw-target branch.

## Do Not Re-Explore

- Do not repeat generic searches for Termux support: AOSP and Termux source contracts are already recorded in `plan.md`.
- Do not revisit the formatting decision unless new evidence shows exact ASR text cannot be committed: raw targets preserve spaces and skip heuristic punctuation.
- Do not redesign normal text-editor voice input.
