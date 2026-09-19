---
title: IME 输入目标重启与会话清理规格
created: 2026-09-19
updated: 2026-09-19
type: source
doc_role: spec
authority: draft
status: experimental
taskadmin_tag: master
taskadmin_id: 3
sources:
  - git:9f3679220e2ad9fc3f4c118f08aa48b4feca40e5
  - app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt
  - app/src/main/java/com/kingzcheung/xime/service/ImeSessionController.kt
derived_to: []
confidence: medium
---

> **TLDR**: Xime SHALL distinguish a new editor target from Android `restartInput`, preserve authoritative composition during same-target restarts, initialize recent-clipboard candidates only for new targets, and fully clear volatile input state when a session ends.

## Purpose

Define the behavior at Android IME session boundaries changed after `init supermax`, especially the difference between a new input target and a same-target `restartInput` refresh.

## Scope

### In Scope

- `onStartInput` handling for new targets and `restarting=true`.
- Preservation/reset of Rime composition, partial segments and candidate state.
- Schema-switch and default-mode behavior at restart boundaries.
- Initial recent-clipboard candidate population.
- `onFinishInput`, `onFinishInputView` and explicit session cleanup.

### Out Of Scope

- Detailed schema-specific Wubi/Pinyin input semantics.
- Clipboard persistence, synchronization and permission behavior.
- Editor-specific rendering bugs outside the lifecycle callbacks.
- Tool-panel, handwriting, T9 and voice feature internals except their cleanup at session boundaries.

## Actors And Triggers

- System actor: Android starts, restarts or finishes an input connection.
- Host actor: an application switches editor focus or calls `restartInput` for the current editor.
- Runtime actor: Rime and Xime hold volatile composition, candidate and panel state.

## Requirements

### Requirement: New Targets And Restarts Are Different Events

The system SHALL use Android's `restarting` flag as the ownership boundary between a new input target and a same-target refresh. A new target SHALL initialize target-specific state; a restart SHALL preserve active input state unless an independent invalid-state guard requires recovery.

#### Scenario: New editor target

- GIVEN Android starts input with `restarting=false`
- WHEN Xime receives the editor information
- THEN Xime SHALL treat the editor as a new target and perform new-session initialization

#### Scenario: Same editor restarts input

- GIVEN Android starts input with `restarting=true`
- WHEN Xime already has an active composition or candidate state
- THEN Xime SHALL refresh the input context without treating the event as a focus change

### Requirement: Same-Target Restart Preserves Authoritative Composition

During `restartInput`, Xime SHALL retain partial committed segments, Rime composition and current candidate/preedit state. It SHALL refresh the visible UI from the retained engine state instead of clearing composition or replacing candidates.

#### Scenario: App restarts while composing

- GIVEN the user has active Rime composition in an editor
- WHEN that editor invokes `restartInput`
- THEN the composition, preedit and candidates SHALL remain available after UI refresh

#### Scenario: Restart follows partial Wubi/Pinyin segmentation

- GIVEN Xime has retained partial segments used to display or commit a mixed composition
- WHEN the same target restarts input
- THEN those segments SHALL not be cleared solely because of `restartInput`

### Requirement: Restart Does Not Re-switch An Active Schema

When `restarting=true` and Rime has active composition, Xime SHALL not call schema switching merely to reapply the selected schema, because switching would discard the composition. A new target or a restart without active composition MAY align the engine to the selected schema.

#### Scenario: Selected schema already has composition

- GIVEN Rime is composing and Android reports a restart
- WHEN Xime compares the selected and active schema
- THEN Xime SHALL preserve composition rather than switching schema during that restart

#### Scenario: New target needs schema alignment

- GIVEN Android starts a new target and the configured schema differs from Rime's active schema
- WHEN session initialization runs
- THEN Xime MAY switch to the configured schema before new input begins

### Requirement: New Target Resets Volatile Input State

For a new input target, Xime SHALL clear state that must not leak across editors, including partial commit tracking, current candidate/composition presentation and target-owned transient UI. It SHALL then apply editor/input-mode configuration and update the UI for the new target.

#### Scenario: Focus moves between fields

- GIVEN one editor has composition, candidates or transient panels
- WHEN focus moves to another editor and Android starts a non-restarting input target
- THEN the old editor's volatile state SHALL not appear in the new editor

#### Scenario: New target has no recoverable input connection

- GIVEN new-target initialization cannot read optional editor state
- WHEN Xime initializes the session
- THEN optional reads SHALL fail safely without fabricating prior composition or blocking the input method

### Requirement: Recent Clipboard Candidates Initialize Only For New Targets

Xime SHALL populate initial recent-clipboard candidates only for a new input target. It SHALL not run that initialization on `restartInput`, because it clears/replaces candidate presentation. When initialized, the list SHALL exclude pinned items, contain at most 30 recent items, and expose previews no longer than 50 characters.

#### Scenario: New target has recent clipboard items

- GIVEN recent unpinned clipboard entries exist
- WHEN Xime starts a new input target
- THEN it SHALL show up to 30 preview candidates and mark the candidate state as recent-clipboard content

#### Scenario: Same target restarts during composition

- GIVEN composition candidates are visible
- WHEN the editor invokes `restartInput`
- THEN Xime SHALL not repopulate recent clipboard candidates or overwrite the current composition candidates

#### Scenario: Recent clipboard list becomes empty

- GIVEN the UI is currently showing recent-clipboard candidates
- WHEN the observed recent list becomes empty
- THEN Xime SHALL clear that clipboard candidate presentation and update the UI

### Requirement: Session Finish Clears Volatile State

When input finishes, the input view finishes for the target, the keyboard is explicitly hidden through the owning path, or the service is destroyed, Xime SHALL cancel pending hardware-Space work and clear session-owned composition, candidates, partial segments, transient status, tool/panel state and related target flags. Cleanup SHALL leave no stale state for the next editor.

#### Scenario: Input session finishes

- GIVEN volatile composition, candidate or overlay state exists
- WHEN Android calls the session finish path
- THEN Xime SHALL clear that state and reset editor/session context

#### Scenario: Delayed hardware action is pending

- GIVEN the Space long-press callback has been scheduled
- WHEN session cleanup occurs before it fires
- THEN Xime SHALL cancel it and reset Space tracking so voice input cannot start in a later editor

### Requirement: Session Initialization Publishes A Fresh Context

Each `onStartInput` call SHALL update the current editor information and create a fresh internal session identifier used to correlate asynchronous work with the current callback epoch. Asynchronous work SHALL verify its owning state before applying UI changes where such guards exist.

#### Scenario: Two callbacks occur in succession

- GIVEN asynchronous state collection began for an earlier input start
- WHEN a later input start establishes a new session context
- THEN stale work SHALL not be treated as authoritative for the new context

## Edge Cases And Failure Behavior

- `restarting=true` alone SHALL not clear active composition, switch schemas or replace candidates with recent clipboard data.
- New-target initialization SHALL tolerate absent optional `InputConnection` reads.
- Cleanup SHALL be safe when engines/managers were not fully initialized.
- Recent-clipboard collector updates SHALL only clear candidate state when that state is still owned by the clipboard presentation.

## Data / Entity Constraints

- Recent clipboard initialization limit: 30 unpinned items.
- Clipboard preview length: at most 50 characters per candidate.
- `restarting=false` denotes a new target for the behavior in this spec; `restarting=true` denotes a same-target refresh supplied by Android.
- Session identifiers are volatile and are not persisted across input starts.

## Behavior Coverage

| Behavior Surface | Normal Behavior | Authorization / Actors | State / Data Effects | Failure / Edge Cases | External Dependencies | Concurrency / Idempotency | Validation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| New target start | covered | Android/host editor | covered | partial | Rime, `InputConnection`, settings | fresh session epoch covered | source review; device test pending |
| Same-target restart | covered | Android/host editor | covered | covered | Rime composition | repeated restarts designed to be idempotent | source review; regression test missing |
| Clipboard initialization | covered | new-target session | covered | covered | clipboard manager flow | collector ownership partially guarded | source review |
| Finish/cleanup | covered | Android/service | covered | covered | initialized engines/managers | repeated cleanup tolerated by guards | source review |

## Coverage Gaps

- No dedicated automated test drives Android `onStartInput(..., restarting=true)` with an active native Rime composition.
- No instrumentation test verifies focus switching and restart preservation across real host editors.
- The broader cleanup routine owns many legacy features; this spec records only the lifecycle boundary, not every feature's independent contract.

## Acceptance Criteria

- Repeated `restartInput` for the same editor does not lose active composition or replace its candidates.
- Moving to a new editor does not leak composition, partial segments, candidates or transient overlays from the prior editor.
- Recent clipboard candidates appear only on new-target initialization and respect count/preview constraints.
- Finishing a session cancels delayed hardware work and leaves a clean state for the next target.

## Validation Plan

- Run `./gradlew :app:testDebugUnitTest` to guard adjacent routing/state behavior.
- Run `./gradlew :app:assembleDebug` to validate callback and state integration.
- Add a future instrumentation regression test that starts composition, invokes `restartInput`, and asserts retained preedit/candidates.
- Manually verify: compose text, trigger an app-side restart in the same field, then switch fields and confirm preservation vs reset.

## Assumptions And Open Questions

- Assumption: Android supplies a truthful `restarting` value for same-target refreshes.
- Open question: OEM/editor-specific callback sequences have not been manually recorded; keep `authority: draft`.

## Source Trace

- `app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt`: `onStartInput`, `setupRecentClipboardCandidates`, `onStartInputView`, finish callbacks and `clearInputState`.
- `app/src/main/java/com/kingzcheung/xime/service/ImeSessionController.kt`: candidate/session state resets used by the service.
- Commit evidence: `9f367922` and follow-up `05030c14`.
- Adjacent tests: `app/src/test/java/com/kingzcheung/xime/service/SoftCompositionRouteTest.kt`, `app/src/test/java/com/kingzcheung/xime/rime/RimeCandidateTest.kt`.
- Validation evidence: `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` passed on 2026-09-19; dedicated restart/focus device validation remains not run.
- TaskAdmin task: `master/3`.
