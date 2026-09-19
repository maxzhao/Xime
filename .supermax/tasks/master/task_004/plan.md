# Implementation Plan

## Goal

- Observable outcome: Xime voice input can enter recognized text into Termux and other `InputType.TYPE_NULL` hosts without relying on rich-editor composing or surrounding-text operations; ordinary text-editor voice behavior remains unchanged.

## Scope

- In scope:
  - Classify `InputType.TYPE_NULL` as a raw/limited voice-output target at the IME service boundary.
  - Keep partial ASR results visible in Xime's voice UI without writing composing text to a raw target.
  - Commit each accepted final result, or the existing timeout fallback partial result, once through `InputConnection.commitText`.
  - Preserve spaces returned by ASR and suppress Xime's heuristic punctuation in raw targets.
  - Add focused regression coverage and validate on Termux.
  - Converge the linked draft spec after implementation and validation.
- Out of scope:
  - Termux package-name checks or app-specific allowlists.
  - Per-character `KeyEvent` synthesis, shell-command parsing, command execution, or newline submission.
  - Changes to ASR providers, plugin APIs, recording/model lifecycle, normal text-editor formatting, or unrelated terminal input behavior.
  - Broad editor-capability abstraction beyond the demonstrated `TYPE_NULL` boundary.

## Spec References

- Proposal/change: none; this is a new draft capability owner.
- Delta specs: none.
- Stable owner: `.supermax/specs/voice-input-host-compatibility/spec.md`

## Authoritative Inputs

- Read first:
  - `.supermax/AGENTS.md`
  - `.supermax/specs/voice-input-host-compatibility/spec.md`
  - `app/src/main/java/com/kingzcheung/xime/service/VoiceRecognitionHandler.kt`
  - `app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt`
  - Relevant voice-handler tests under `app/src/test/java/com/kingzcheung/xime/service/`
- External references:
  - AOSP `InputType.TYPE_NULL` contract: `https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/text/InputType.java`
  - Termux terminal `InputConnection`: `https://github.com/termux/termux-app/blob/master/terminal-view/src/main/java/com/termux/view/TerminalView.java`
- Constraints:
  - Preserve user-owned worktree changes.
  - Prefer the existing editor classifier if it exists on the execution baseline; otherwise keep `TYPE_NULL` detection local to the IME service boundary.
  - Do not change standard text-editor voice behavior.
  - Keep tests proportional to this regression.

## Current Facts

- AOSP defines `TYPE_NULL` as a non-rich input connection that cannot be assumed to support candidate text or current-text retrieval; IMEs are expected to use a limited mode.
- Termux defaults its terminal view to `TYPE_NULL`. Its `commitText` writes content to the PTY, while deletion is translated into Backspace events and surrounding text is not a normal editable-buffer contract.
- `VoiceRecognitionHandler` currently reads text before the cursor, writes partial results with `setComposingText`, finalizes with `finishComposingText`, may call `deleteSurroundingText`, removes all spaces, and may append Chinese punctuation.
- That flow assumes rich-editor composition/rewrite semantics and can corrupt or fail terminal command entry.
- The current `v2.8.0` checkout has no terminal-specific voice path. The repository's `main` ref recognizes `TYPE_NULL` for other restricted-editor behavior but still uses the same voice-output flow.
- Existing specs cover voice shortcuts and session cleanup, not host-output compatibility.

## Approach

1. Determine raw-target mode from the current `EditorInfo` using exact `InputType.TYPE_NULL`, and expose that decision to `VoiceRecognitionHandler` through the narrow existing service/handler boundary.
2. In raw-target mode, retain partial text for voice UI and timeout fallback but do not call `setComposingText` or mark host composing state.
3. In raw-target mode, commit a nonblank final/fallback result exactly once with `commitText(text, 1)`; do not call `finishComposingText`, `deleteSurroundingText`, remove internal spaces, or append heuristic punctuation. Preserve duplicate-final and abandoned-session guards.
4. Leave the existing rich-editor path unchanged.
5. Add focused tests for raw partial behavior, exact one-shot raw final/fallback commit, and preservation of the rich-editor path.
6. Run the closest unit tests and debug assembly, then manually verify one spoken command in Termux and one ordinary text-field dictation. Record evidence and reconcile the draft spec before task completion.

## Decisions

- Decision: capability-based targeting.
  - Choice: detect exact `TYPE_NULL`.
  - Rationale: matches Android's declared limited-input contract and covers Termux without hardcoded package identity.
  - Rejected alternative: checking `com.termux` or maintaining terminal-app allowlists.
- Decision: no live host composing for raw targets.
  - Choice: show partial results only in Xime UI and write once on final/fallback.
  - Rationale: avoids terminal PTY rewrites and Backspace-based correction.
  - Rejected alternative: continue composing then attempt delete-and-replace.
- Decision: terminal-safe text preservation.
  - Choice: preserve ASR spaces and do not add Xime heuristic punctuation in raw mode.
  - Rationale: spaces and punctuation are command syntax.
  - Rejected alternative: retain normal prose normalization in terminals.

## Affected Paths

- `app/src/main/java/com/kingzcheung/xime/service/VoiceRecognitionHandler.kt` — raw-target partial/final output behavior.
- `app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt` — current-editor capability decision passed to the handler.
- `app/src/main/java/com/kingzcheung/xime/service/EditorInfoClassifier.kt` — reuse or minimally extend only if present on the execution baseline.
- `app/src/test/java/com/kingzcheung/xime/service/` — focused regression tests.
- `.supermax/specs/voice-input-host-compatibility/spec.md` — behavior contract and convergence state.
- `.supermax/specs/index.md`, `.supermax/specs/log.md` — spec routing and concise lifecycle evidence.

## Risks And Failure Handling

- Risk: raw-mode changes accidentally alter ordinary EditText dictation.
  - Failure response: gate only exact `TYPE_NULL` and retain a rich-editor regression assertion/manual check.
- Risk: final and timeout callbacks duplicate terminal text.
  - Failure response: preserve and test existing duplicate-final/session-abandon guards.
- Risk: an ASR backend emits blank results.
  - Failure response: keep blank-result rejection; never fabricate command text.
- Risk: the execution baseline differs between `v2.8.0` and `main`.
  - Failure response: reuse baseline helpers when present, but preserve the specified service/handler ownership and behavior rather than copying branch-specific code blindly.

## Acceptance Criteria

- [ ] In a `TYPE_NULL` target, partial ASR text appears in Xime's voice UI and no host composing operation is issued.
- [ ] A nonblank final or timeout-fallback result is committed exactly once to the raw target.
- [ ] Raw-target output preserves spaces and does not receive Xime heuristic punctuation.
- [ ] Raw-target voice output performs no surrounding-text read, delete-and-replace, or composing finalization as part of result delivery.
- [ ] Ordinary rich text fields retain existing partial composing, finalization, normalization, punctuation, duplicate suppression, and session-abandon behavior.
- [ ] Termux manual verification enters a spoken command without duplicated text, deleted prompt content, removed spaces, or appended punctuation.
- [ ] The linked spec, index/log, task TODO and validation evidence converge before the task is marked done.

## Validation Plan

- Command/check: run the closest focused voice-handler unit test task available on the execution baseline.
- Command/check: `./gradlew :app:testDebugUnitTest`.
- Command/check: `./gradlew :app:assembleDebug`.
- Expected result: all commands pass; no standard-editor regression.
- Remaining manual review:
  - Termux: dictate a command containing a space, for example `git status`; verify exact one-time insertion without automatic punctuation and without executing it.
  - Ordinary text field: verify partial preview and final punctuation remain unchanged.
