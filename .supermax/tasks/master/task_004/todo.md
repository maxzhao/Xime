# Task TODO

## Current State

- Status: done
- Current phase: implementation, automated validation, device review and spec convergence complete
- Next action: none.

## Checklist

- [x] Confirm `.supermax/specs/voice-input-host-compatibility/spec.md` and current source baseline; preserve user-owned changes.
- [x] Add/reuse exact `InputType.TYPE_NULL` classification at the IME service boundary and pass raw-target capability to `VoiceRecognitionHandler`.
- [x] Keep raw-target partial results in Xime UI only; do not write host composing text.
- [x] Commit only uncommitted raw text after a one-second partial pause, accepted final, or explicit stop without surrounding-text reads, rewrite deletion, space removal, or heuristic punctuation.
- [x] Preserve normal rich-editor voice behavior and existing duplicate-final/session-abandon guards.
- [x] Add focused regression tests for raw partial preview/pause scheduling, one-shot pause/final/explicit-stop commit, cumulative-result deduplication, and absence of the pause timer in normal editors.
- [x] Run focused tests, `./gradlew :app:testDebugUnitTest`, and the project-approved `just build-release-arm64`; repair the initial test-environment failure and rerun.
- [x] Manually verify Termux floating preview, pause commit, explicit `Ctrl+0` commit, deduplication and unchanged ordinary text-field behavior; record exact evidence.
- [x] Converge `.supermax/specs/voice-input-host-compatibility/spec.md` to normative, update `.supermax/specs/index.md` / `log.md`, and reconcile task evidence.

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
| `.supermax/specs/voice-input-host-compatibility/spec.md` | created and converged normative spec | Automated and human validation passed |
| `.supermax/specs/index.md` | added routing entry | No implementation state |
| `.supermax/specs/log.md` | recorded spec creation | Concise lifecycle evidence only |
| `app/src/main/java/com/kingzcheung/xime/service/VoiceRecognitionHandler.kt` | implemented raw-target output branch | Floating partials; raw-only one-second pause commit; uncommitted-suffix deduplication; explicit-stop commit; rich-editor path retained |
| `app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt` | passed exact `TYPE_NULL` capability and rendered raw partials in the compact floating card | No package-specific detection; ordinary editors retain existing rendering |
| `app/src/test/java/com/kingzcheung/xime/service/VoiceRecognitionHandlerTest.kt` | added focused regression tests | Raw pause/final/explicit-stop paths plus rich-editor no-timer preservation |

## Validation

| Command / check | Result | Evidence |
| --- | --- | --- |
| Source/spec exploration | passed | Root cause and minimal behavior agreed with user on 2026-09-19 |
| Task/spec linkage | passed | Normative spec references `master/4`; task plan references the exact spec path |
| `git diff --check` on changed code/task/spec files | passed | No whitespace errors |
| Initial direct Gradle / Windows validation attempts | recovered | Direct Gradle lacked SDK env; Windows lacked SDK. The project recipe revealed and exported `$HOME/Android/Sdk`; temporary Windows validation copy was deleted. |
| Focused `VoiceRecognitionHandlerTest` | passed | Initial run exposed unmocked `android.util.Log`; current 11-test class now covers raw one-second pause scheduling/commit, explicit stop, deduplication and rich-editor no-timer behavior |
| `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew :app:testDebugUnitTest --console=plain` | passed | Latest pause-commit implementation returned `BUILD SUCCESSFUL`; 48 actionable tasks |
| `just build-release-arm64` | passed | Latest build is v2-signature-verified `app/build/outputs/apk/release/Xime-2.8.0-arm64-v8a.apk`; SHA-256 `1f63d1ee1cb2a4c8bed50c9aaa5e10a81ea82cec74003cae7fa8d8e56d71e266` |
| Initial Termux/manual validation | failed, repaired | Recognized text stayed buffered until another key ended sticky voice; first follow-up added compact floating preview, second follow-up added raw-only one-second pause commit |
| Follow-up Termux/manual validation | passed | User confirmed floating partial preview, raw-only pause commit, explicit `Ctrl+0` commit and ordinary-editor behavior on the latest APK |

## Current Errors / Blockers

- None recorded.

## Decisions / Findings

- Root cause is the mismatch between `TYPE_NULL` limited input semantics and rich-editor composing/rewrite operations, not ASR capture or provider initialization.
- Termux supports direct `commitText` to its PTY, but does not provide a normal editable-buffer contract for composing correction and surrounding-text replacement.
- User accepted capability-based `TYPE_NULL` handling, floating-card partial preview, one-second pause commit plus explicit `Ctrl+0` commit, preserved spaces, and no automatic punctuation in raw targets.
- Device feedback confirmed that buffering until explicit stop was insufficient. Raw partial text now uses the existing physical-keyboard candidate floating card, restarts a one-second timer on each update, commits only the uncommitted suffix after pause, clears the preview, and keeps sticky voice available.
- The pause timer is gated by exact `TYPE_NULL`; ordinary editors retain existing composing/final behavior and never schedule it.
- Do not hardcode Termux package names, synthesize per-character key events, parse shell commands, or broaden the task into ASR/plugin redesign.

## Next Actions

1. No required actions remain.

## Do Not Re-Explore

- Do not repeat generic searches for Termux support: AOSP and Termux source contracts are already recorded in `plan.md`.
- Do not revisit the formatting decision unless new evidence shows exact ASR text cannot be committed: raw targets preserve spaces and skip heuristic punctuation.
- Do not redesign normal text-editor voice input.
