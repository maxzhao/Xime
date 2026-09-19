---
title: 语音输入宿主兼容规格
created: 2026-09-19
updated: 2026-09-19
type: source
doc_role: spec
authority: draft
status: experimental
taskadmin_tag: master
taskadmin_id: 4
sources:
  - app/src/main/java/com/kingzcheung/xime/service/VoiceRecognitionHandler.kt
  - app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt
  - https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/text/InputType.java
  - https://github.com/termux/termux-app/blob/master/terminal-view/src/main/java/com/termux/view/TerminalView.java
derived_to: []
confidence: medium
---

> **TLDR**: Xime SHALL treat `InputType.TYPE_NULL` as a limited raw-input target, keep partial speech results inside Xime, and commit accepted final text once without rich-editor rewriting or command-breaking normalization.

## Purpose

Define how Xime delivers speech-recognition results to standard rich text editors and limited raw-input hosts such as Termux. The contract prevents terminal corruption while preserving existing voice behavior in ordinary text fields.

## Scope

### In Scope

- Classification of exact `InputType.TYPE_NULL` targets for voice-result delivery.
- Partial, final and timeout-fallback output behavior for limited raw-input targets.
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
- Trigger: Xime receives an ASR partial/final result or uses the latest partial as an existing timeout fallback.

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

For a limited raw target, Xime SHALL retain nonblank partial recognition text for voice UI and timeout fallback, but SHALL NOT write that partial through host composing operations.

#### Scenario: A partial result arrives in Termux

- GIVEN voice input is active in a raw target
- WHEN the ASR backend emits a partial result
- THEN Xime SHALL display the result in its voice UI
- AND Xime SHALL NOT call host `setComposingText`

#### Scenario: Partial text changes repeatedly

- GIVEN multiple partial revisions arrive before a final result
- WHEN Xime updates the voice UI
- THEN no revision SHALL be sent to the terminal for delete-and-replace correction

### Requirement: Raw Final Results Commit Once Without Rewriting

For a limited raw target, each nonblank final result accepted by the active voice-session state machine SHALL be sent exactly once through `InputConnection.commitText`. Raw delivery SHALL NOT depend on `getTextBeforeCursor`, `finishComposingText`, `deleteSurroundingText`, or replacement of a previously written partial.

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

### Requirement: Timeout Fallback Uses The Same Raw Policy

When the existing voice-session state machine falls back to its latest nonblank partial result because no final result arrives in time, a raw target SHALL receive that fallback once using the same no-composing, no-rewrite and no-normalization policy. A later duplicate final SHALL remain suppressed by the session state machine.

#### Scenario: Final result times out

- GIVEN a raw target has a latest nonblank partial result and the existing final-result wait expires
- WHEN Xime invokes fallback delivery
- THEN the latest partial SHALL be committed once without heuristic punctuation

#### Scenario: A final result arrives after fallback

- GIVEN fallback text was already committed for the voice session
- WHEN a duplicate or late final result arrives
- THEN Xime SHALL NOT commit the same utterance again

### Requirement: Abandoned Sessions Never Write Late Results

Switching input targets, hiding the IME or otherwise abandoning a voice session SHALL prevent its pending partial, final and timeout-fallback results from being written to the old or new host.

#### Scenario: The target changes during recognition

- GIVEN voice recognition is active in a raw target
- WHEN the input session is abandoned before result delivery
- THEN no later partial, final or fallback callback from that session SHALL write host text

## Edge Cases And Failure Behavior

- Empty or whitespace-only ASR results SHALL not produce a raw commit.
- ASR error messages SHALL remain error/UI behavior and SHALL not be committed as terminal text.
- A missing or invalid `InputConnection` SHALL fail without synthesized command text.
- Raw-target handling SHALL not automatically send Enter or execute the committed command.
- Callback ordering SHALL continue to use the existing duplicate-final and abandoned-session guards.
- A non-`TYPE_NULL` editor SHALL not enter raw mode solely because optional surrounding-text reads fail.

## Data / Entity Constraints

- Raw-target discriminator: exact `InputType.TYPE_NULL`.
- Raw host write operation: one `commitText(result, 1)` per accepted result/fallback.
- Raw partial host writes: zero.
- Raw compatibility normalization: no global space removal and no Xime heuristic punctuation.
- Package identifiers and terminal-app allowlists are not part of this contract.

## Behavior Coverage

| Behavior Surface | Normal Behavior | Authorization / Actors | State / Data Effects | Failure / Edge Cases | External Dependencies | Concurrency / Idempotency | Validation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Raw-target classification | covered | Android editor contract | covered | covered | `EditorInfo.inputType` | n/a | unit test + source review |
| Raw partial display | covered | active voice user | covered | covered | ASR partial callbacks, Xime UI | repeated revisions covered | unit test + manual Termux check |
| Raw final/fallback commit | covered | active voice session | covered | covered | `InputConnection.commitText` | duplicate/late callbacks covered | unit test + manual Termux check |
| Command-text preservation | covered | ASR result | covered | covered | provider output | deterministic | unit test + manual Termux check |
| Ordinary editor regression boundary | covered | ordinary text-field user | existing behavior retained | covered | rich `InputConnection` | existing guards retained | regression test + manual text-field check |

## Coverage Gaps

- Automated JVM mocks cannot prove behavior against Termux's real PTY-backed `InputConnection`; one device/manual Termux check remains required.
- Compatibility with other `TYPE_NULL` hosts is contract-based but not individually device-tested.
- ASR provider accuracy and provider-specific formatting remain outside this specification.

## Acceptance Criteria

- A partial result in Termux remains visible in Xime but does not enter the terminal before final/fallback delivery.
- Dictating `git status` inserts exactly `git status` once, without automatic punctuation, removed spaces, prompt deletion or automatic execution.
- Final timeout fallback cannot produce a later duplicate commit.
- Hiding or switching the input target prevents late voice text from reaching another editor.
- Ordinary rich text fields retain existing composing preview and final formatting behavior.

## Validation Plan

- Add focused unit coverage for exact `TYPE_NULL` classification and zero composing writes for raw partials.
- Add focused unit coverage for one-shot raw final and timeout-fallback commits, exact text preservation and late-final suppression.
- Retain or add one assertion that ordinary rich-editor partial/final behavior remains unchanged.
- Run `./gradlew :app:testDebugUnitTest`.
- Run `./gradlew :app:assembleDebug`.
- Manually dictate `git status` into Termux without pressing Enter and verify exact insertion.
- Manually dictate one sentence into an ordinary text field and verify existing partial preview and final punctuation behavior.

## Assumptions And Open Questions

- Assumption: Termux continues to accept `InputConnection.commitText` and forward it to the active terminal session, as shown by its maintained `TerminalView` implementation.
- Assumption: Android reports Termux's default terminal target as exact `InputType.TYPE_NULL` unless the user enables a Termux compatibility input mode.
- Open question: device/manual validation across Termux versions and other terminal emulators has not been recorded; keep `authority: draft` until implementation and required validation converge.

## Source Trace

- User decision on 2026-09-19: use capability-based `TYPE_NULL` handling, UI-only partials, one-shot final/fallback commit, preserved spaces and no heuristic punctuation in raw targets.
- `app/src/main/java/com/kingzcheung/xime/service/VoiceRecognitionHandler.kt`: current composing, correction, normalization and duplicate-suppression behavior.
- `app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt`: active editor and voice-session ownership boundary.
- `.supermax/specs/physical-keyboard-input/spec.md`: voice shortcut boundary.
- `.supermax/specs/ime-session-lifecycle/spec.md`: abandoned-session and cleanup boundary.
- AOSP `InputType.TYPE_NULL`: limited/non-rich target contract.
- Termux `TerminalView`: default `TYPE_NULL` and PTY-backed `commitText` behavior.
- TaskAdmin task: `master/4`.
