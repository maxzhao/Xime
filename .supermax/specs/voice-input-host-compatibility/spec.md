---
title: 语音输入宿主兼容规格
created: 2026-09-19
updated: 2026-09-19
type: source
doc_role: spec
authority: normative
status: active
taskadmin_tag: master
taskadmin_id: 4
sources:
  - app/src/main/java/com/kingzcheung/xime/service/VoiceRecognitionHandler.kt
  - app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt
  - https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/text/InputType.java
  - https://github.com/termux/termux-app/blob/master/terminal-view/src/main/java/com/termux/view/TerminalView.java
derived_to: []
confidence: high
---

> **TLDR**: Xime SHALL treat `InputType.TYPE_NULL` as a limited raw-input target, preview partial speech only in Xime's floating card, and commit pending text once after a short speech pause or explicit stop without rich-editor rewriting or command-breaking normalization.

## Purpose

Define how Xime delivers speech-recognition results to standard rich text editors and limited raw-input hosts such as Termux. The contract prevents terminal corruption while preserving existing voice behavior in ordinary text fields.

## Scope

### In Scope

- Classification of exact `InputType.TYPE_NULL` targets for voice-result delivery.
- Partial preview, pause-triggered commit, final result and explicit-stop behavior for limited raw-input targets.
- Preservation of recognized command text, duplicate suppression and abandoned-session safety.
- Regression boundary between raw targets and ordinary rich text editors.

### Out Of Scope

- Speech recognition provider selection, audio capture, model loading or plugin protocol behavior.
- Voice button gestures and physical-keyboard voice shortcuts; owned by `.supermax/specs/physical-keyboard-input/spec.md`.
- General IME session teardown except late-result safety; owned by `.supermax/specs/ime-session-lifecycle/spec.md`.
- Package-name allowlists, shell-command parsing, command execution, automatic Enter, or per-character key-event synthesis.
- Redesigning composition, punctuation or normalization behavior in ordinary rich text fields.

## Actors And Triggers

- Actor: a user starts and ends Xime voice input while a host owns the active `InputConnection`.
- Host actor: an ordinary text editor or a limited raw-input target such as a Termux terminal.
- Runtime actor: the configured ASR backend emits partial, final, error and state callbacks.
- Trigger: Xime receives an ASR partial/final result, the raw pause interval expires, or the user explicitly stops voice input.

## Requirements

### Requirement: Raw Voice Targets Follow The Android Input-Type Contract

Xime SHALL classify an editor whose input type is exactly `InputType.TYPE_NULL` as a limited raw voice target. This decision SHALL be capability-based and SHALL NOT depend on the host package name.

#### Scenario: Termux exposes a raw target

- GIVEN the active editor reports `InputType.TYPE_NULL`
- WHEN a voice session starts
- THEN Xime SHALL use limited raw-target result delivery

#### Scenario: An ordinary text field is active

- GIVEN the active editor reports a supported non-`TYPE_NULL` text input type
- WHEN a voice session starts
- THEN Xime SHALL retain its existing rich-editor voice-result behavior

### Requirement: Raw Partial Results Stay Inside Xime

For a limited raw target, Xime SHALL display the current uncommitted partial recognition text in its compact floating card and SHALL NOT write it through host composing operations while partial updates continue. After no newer partial arrives for the raw pause interval, Xime SHALL commit the uncommitted text once and clear the floating preview. This pause behavior SHALL NOT apply to ordinary editors.

#### Scenario: A partial result arrives in Termux

- GIVEN voice input is active in a raw target
- WHEN the ASR backend emits a partial result
- THEN Xime SHALL display the result in its voice UI
- AND Xime SHALL NOT call host `setComposingText`

#### Scenario: Partial text changes repeatedly

- GIVEN multiple partial revisions arrive before the raw pause interval
- WHEN Xime updates the floating preview
- THEN the pending pause commit SHALL restart
- AND no revision SHALL be sent to the terminal for delete-and-replace correction

#### Scenario: Speech pauses in Termux

- GIVEN a raw target has nonblank uncommitted partial text
- WHEN no newer partial arrives for the raw pause interval
- THEN Xime SHALL commit only that pending text once
- AND the floating preview SHALL clear while the sticky voice session remains available

#### Scenario: Ordinary editor receives partial text

- GIVEN the active editor is not `TYPE_NULL`
- WHEN partial recognition pauses
- THEN Xime SHALL retain its existing composing behavior
- AND SHALL NOT schedule the raw pause commit

### Requirement: Raw Final Results Commit Once Without Rewriting

For a limited raw target, each nonblank, not-yet-committed final segment accepted by the active voice-session state machine SHALL be sent exactly once through `InputConnection.commitText`. Raw delivery SHALL NOT depend on `getTextBeforeCursor`, `finishComposingText`, `deleteSurroundingText`, or replacement of a previously written partial. A final callback that repeats text already committed by the pause path SHALL not duplicate it.

#### Scenario: A final result arrives

- GIVEN a raw target, an active non-abandoned voice session and a nonblank final result
- WHEN Xime accepts that final result
- THEN Xime SHALL call `commitText` once with that result
- AND Xime SHALL NOT perform composing finalization or surrounding-text deletion for that result

#### Scenario: No valid input connection is available

- GIVEN a final result arrives after the input connection is unavailable
- WHEN Xime attempts result delivery
- THEN Xime SHALL not fabricate text or key events
- AND session cleanup and duplicate guards SHALL remain effective

### Requirement: Raw Output Preserves Command Syntax

Xime SHALL preserve the ASR result used for raw delivery, including meaningful spaces. Xime SHALL NOT globally remove spaces or append Xime's heuristic punctuation in raw mode.

#### Scenario: Recognition returns a command with spaces

- GIVEN the accepted result is `git status`
- WHEN Xime commits it to a raw target
- THEN the committed text SHALL remain `git status`
- AND Xime SHALL NOT append `，`, `。` or another heuristic punctuation mark

#### Scenario: The backend already returns punctuation

- GIVEN the ASR backend includes punctuation in its accepted result
- WHEN raw delivery occurs
- THEN Xime SHALL not add, remove or replace that punctuation as part of host compatibility handling

### Requirement: Pause And Explicit Stop Use The Same Raw Policy

A raw pause, a final callback and explicit voice stop (`Ctrl+0`, release, or normal-key transition) SHALL commit only the currently uncommitted raw text using the same no-composing, no-rewrite and no-normalization policy. Already committed raw prefixes SHALL not be repeated by later cumulative partial/final callbacks.

#### Scenario: Ctrl+0 stops before the pause interval

- GIVEN a raw target has a visible uncommitted partial result
- WHEN the user presses `Ctrl+0` to stop sticky voice before the pause interval
- THEN Xime SHALL cancel the pending pause callback
- AND commit the visible text once without heuristic punctuation

#### Scenario: Ctrl+0 stops after pause commit

- GIVEN the pause path already committed and cleared the current partial
- WHEN the user presses `Ctrl+0`
- THEN Xime SHALL end the voice session without repeating the committed text

#### Scenario: A final result repeats pause-committed text

- GIVEN raw text was already committed after a pause
- WHEN a cumulative final result repeats that text
- THEN Xime SHALL NOT commit the same text again

### Requirement: Abandoned Sessions Never Write Late Results

Switching input targets, hiding the IME or otherwise abandoning a voice session SHALL cancel its pending raw pause callback and prevent its pending partial or final results from being written to the old or new host.

#### Scenario: The target changes during recognition

- GIVEN voice recognition is active in a raw target
- WHEN the input session is abandoned before result delivery
- THEN no pending pause callback or later partial/final callback from that session SHALL write host text

## Edge Cases And Failure Behavior

- Empty or whitespace-only ASR results SHALL not produce a raw commit.
- ASR error messages SHALL remain error/UI behavior and SHALL not be committed as terminal text.
- A missing or invalid `InputConnection` SHALL fail without synthesized command text.
- Raw-target handling SHALL not automatically send Enter or execute the committed command.
- Callback ordering SHALL continue to use the existing duplicate-final and abandoned-session guards.
- A non-`TYPE_NULL` editor SHALL not enter raw mode solely because optional surrounding-text reads fail.

## Data / Entity Constraints

- Raw-target discriminator: exact `InputType.TYPE_NULL`.
- Raw pause interval: one second after the latest partial update.
- Raw host write operation: one `commitText(pendingText, 1)` per accepted pause/final/explicit-stop segment.
- Raw partial host writes before the pause interval: zero.
- Raw compatibility normalization: no global space removal and no Xime heuristic punctuation.
- Package identifiers and terminal-app allowlists are not part of this contract.

## Behavior Coverage

| Behavior Surface | Normal Behavior | Authorization / Actors | State / Data Effects | Failure / Edge Cases | External Dependencies | Concurrency / Idempotency | Validation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Raw-target classification | covered | Android editor contract | covered | covered | `EditorInfo.inputType` | n/a | unit test + source review |
| Raw partial display / pause commit | covered | active voice user | covered | covered | ASR partial callbacks, Xime UI, one-second timer | timer reset and cumulative-prefix dedupe covered | unit test + manual Termux check |
| Raw final / explicit-stop commit | covered | active voice session | covered | covered | `InputConnection.commitText` | duplicate/late callbacks covered | unit test + manual Termux check |
| Command-text preservation | covered | ASR result | covered | covered | provider output | deterministic | unit test + manual Termux check |
| Ordinary editor regression boundary | covered | ordinary text-field user | existing behavior retained | covered | rich `InputConnection` | existing guards retained | regression test + manual text-field check |

## Coverage Gaps

- Automated JVM mocks cannot prove behavior against Termux's real PTY-backed `InputConnection`; one device/manual Termux check remains required.
- Compatibility with other `TYPE_NULL` hosts is contract-based but not individually device-tested.
- ASR provider accuracy and provider-specific formatting remain outside this specification.

## Acceptance Criteria

- A partial result in Termux remains visible only in Xime's floating card while speech continues.
- Pausing for the raw interval commits and clears the visible text without ending sticky voice.
- Dictating `git status` inserts exactly `git status` once after a pause or explicit `Ctrl+0`, without automatic punctuation, removed spaces, prompt deletion or automatic execution.
- A later cumulative partial/final callback cannot duplicate text already committed by the pause path.
- Hiding or switching the input target prevents late voice text from reaching another editor.
- Ordinary rich text fields retain existing composing preview and final formatting behavior.

## Validation Plan

- Add focused unit coverage for zero composing writes, floating-state updates and one-second pause scheduling in raw mode.
- Add focused unit coverage for one-shot raw pause/final/explicit-stop commits, exact text preservation and cumulative-result deduplication.
- Retain an assertion that ordinary rich-editor partial/final behavior remains unchanged and does not schedule a raw pause commit.
- Run `./gradlew :app:testDebugUnitTest`.
- Run `./gradlew :app:assembleDebug`.
- Manually dictate `git status` into Termux without pressing Enter and verify exact insertion.
- Manually dictate one sentence into an ordinary text field and verify existing partial preview and final punctuation behavior.

## Assumptions And Open Questions

- Assumption: Termux continues to accept `InputConnection.commitText` and forward it to the active terminal session, as shown by its maintained `TerminalView` implementation.
- Assumption: Android reports Termux's default terminal target as exact `InputType.TYPE_NULL` unless the user enables a Termux compatibility input mode.
- Open question: compatibility with additional Termux versions and other terminal emulators has not been device-tested; failures SHALL be evaluated against the same `TYPE_NULL` capability contract rather than package allowlists.

## Source Trace

- User decisions on 2026-09-19: use capability-based `TYPE_NULL` handling, floating-card partial preview, one-second pause commit plus explicit `Ctrl+0` commit, preserved spaces and no heuristic punctuation; ordinary editors keep existing behavior without pause auto-commit.
- `app/src/main/java/com/kingzcheung/xime/service/VoiceRecognitionHandler.kt`: current composing, correction, normalization and duplicate-suppression behavior.
- `app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt`: active editor and voice-session ownership boundary.
- `.supermax/specs/physical-keyboard-input/spec.md`: voice shortcut boundary.
- `.supermax/specs/ime-session-lifecycle/spec.md`: abandoned-session and cleanup boundary.
- AOSP `InputType.TYPE_NULL`: limited/non-rich target contract.
- Termux `TerminalView`: default `TYPE_NULL` and PTY-backed `commitText` behavior.
- Automated validation on 2026-09-19: focused `VoiceRecognitionHandlerTest`, full `:app:testDebugUnitTest`, and `just build-release-arm64` passed.
- Human validation on 2026-09-19: Termux floating partial preview, raw-only pause commit, explicit `Ctrl+0` commit, deduplication and unchanged ordinary-editor behavior passed on the rebuilt ARM64 APK.
- TaskAdmin task: `master/4`.
