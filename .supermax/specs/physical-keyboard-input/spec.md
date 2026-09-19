---
title: 实体键盘输入路由、候选窗与模式提示规格
created: 2026-09-19
updated: 2026-09-19
type: source
doc_role: spec
authority: draft
status: experimental
taskadmin_tag: master
taskadmin_id: 3
sources:
  - git:df0b83d8d0f42db6126d5ef43d8a3d68b2fd083c..d3dbe89c7cbe469ee0cac2ab853d67a3df34b155
  - app/src/main/java/com/kingzcheung/xime/service/KeyCodeMapper.kt
  - app/src/main/java/com/kingzcheung/xime/service/ImeKeyRouter.kt
  - app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt
  - app/src/main/java/com/kingzcheung/xime/ui/keyboard/HardwareKeyboardCandidateBar.kt
  - app/src/main/java/com/kingzcheung/xime/ui/keyboard/HardwareKeyboardStatusOverlay.kt
derived_to: []
confidence: high
---

> **TLDR**: Xime SHALL translate Android physical-key events into ordered Rime key/modifier events, preserve host-editor fallback for unhandled input, provide exact hardware shortcuts, and render cursor-anchored candidates plus transient mode feedback in compact hardware-keyboard mode.

## Purpose

Define the observable contract for physical-keyboard input added after `init supermax`: key translation, modifier ordering, Rime/host ownership, candidate shortcuts, voice shortcuts, candidate-window placement and mode-status feedback.

## Scope

### In Scope

- Android physical-key layout translation and Rime keysym/modifier mapping.
- Key-down/key-up routing, Rime consumption and host-editor fallback.
- Physical candidate selection and paging.
- Right Shift, `Ctrl+.`, `Shift+Space`, `Ctrl+0` and unmodified-Space voice behavior.
- Compact-mode candidate/preedit/voice card placement and transient status overlay.

### Out Of Scope

- Schema-specific Wubi/Pinyin completion and annotation rules; owned by `.supermax/specs/wubi-pinyin-input/spec.md`.
- Speech recognition provider behavior after a voice session starts.
- Soft-keyboard layout and touch-key rendering.
- Plugin candidate transformation internals except the displayed-candidate selection boundary.

## Actors And Triggers

- Actor: a user typing on a physical keyboard while Xime owns the active input connection.
- System actor: Android delivers `KeyEvent` down/up events and cursor-anchor coordinates.
- Runtime actor: Rime returns whether a key was handled and the resulting commit/composition/candidate state.

## Requirements

### Requirement: Keyboard-Layout-Aware Text Translation

For printable physical keys, the system SHALL prefer the Unicode character resolved by Android's active keyboard layout. If no printable Unicode character is available, it SHALL use the maintained Android-key fallback map. Shifted US punctuation and digits SHALL map to their printable symbols rather than raw Android key codes.

#### Scenario: Non-US layout produces a printable character

- GIVEN Android resolves a physical key event to a printable Unicode code point
- WHEN Xime converts the event to text
- THEN Xime SHALL route that resolved character without assuming a US layout

#### Scenario: Android does not expose a printable character

- GIVEN the key is in Xime's fallback map
- WHEN Android returns no usable printable character
- THEN Xime SHALL route the mapped fallback character

#### Scenario: Key cannot be translated

- GIVEN the event is neither printable nor supported by the fallback/special-key map
- WHEN Xime receives the event
- THEN Xime SHALL return it to the normal Android input-method fallback path

### Requirement: Explicit Modifier And Special-Key Mapping

The system SHALL translate Android Shift, Ctrl, Alt and Meta states into Rime modifier bits explicitly; it SHALL NOT pass Android bit values through as if they were Rime values. It SHALL map supported modifier, navigation, editing, function, keypad, lock, Enter, Tab and Escape keys to Rime/X11 keysyms.

#### Scenario: Chord reaches Rime

- GIVEN a supported physical key is pressed with one or more modifiers
- WHEN Xime dispatches it to Rime
- THEN the Rime key code and translated modifier mask SHALL represent the Android event

#### Scenario: Modifier is tapped

- GIVEN Shift, Ctrl, Alt or Meta is pressed and released
- WHEN Xime dispatches the modifier
- THEN key-down and key-up SHALL be sent to Rime in order so Rime can evaluate tap-only modifier bindings

### Requirement: Rime-First Routing With Safe Host Fallback

Supported physical keys SHALL be offered to Rime first. A handled result SHALL update the authoritative commit/composition/candidate state and consume the Android event. An unhandled result SHALL fall back exactly once: printable input through Xime's text route when safe, or the original key event through the active `InputConnection` for host-owned editing/command behavior.

#### Scenario: Rime handles the event

- GIVEN Rime returns a handled result
- WHEN Xime applies that result
- THEN Xime SHALL consume the Android event and SHALL NOT duplicate it into the host editor

#### Scenario: Rime declines a printable key

- GIVEN the event has no command modifier and can be translated to printable text
- WHEN Rime returns unhandled with no authoritative commit
- THEN Xime MAY route the translated text through the ordinary key path exactly once

#### Scenario: Rime declines an editor or command key

- GIVEN the key is an editing/special key or carries Ctrl/Alt/Meta
- WHEN Rime returns unhandled
- THEN Xime SHALL forward the original event to the current `InputConnection` instead of fabricating text

#### Scenario: Forwarded key re-enters the input method

- GIVEN Xime is forwarding an unhandled event to the editor
- WHEN Android sends that event through the input method again
- THEN the forwarding guard SHALL bypass Xime routing and prevent recursion or duplicate commits

### Requirement: Composition Editing Keys Preserve Engine Authority

While Rime has an active composition, Delete, Forward Delete, arrows, Home, End and related supported editing keys SHALL be offered to Rime before host fallback. Xime SHALL apply the Rime result before deciding whether the editor should receive the key.

#### Scenario: Editing key changes composition

- GIVEN Rime has an active composition
- WHEN the user presses a supported editing key and Rime handles it
- THEN the updated composition/candidate state SHALL be shown and the host editor SHALL not receive a duplicate event

#### Scenario: Editing key is not handled

- GIVEN Rime does not consume the editing key
- WHEN no Rime commit or composition update owns the event
- THEN the original key SHALL be forwarded to the host editor once

### Requirement: Candidate Shortcuts Target Displayed Candidates

Number-row and numeric-keypad candidate shortcuts SHALL resolve against the displayed candidate/action list, not blindly against the raw Rime page. Digits `1` through `9` SHALL address indexes `0` through `8`, and `0` SHALL address index `9`. If plugin transformation removed the requested displayed item, the shortcut SHALL be consumed without selecting a different engine candidate. Page navigation SHALL update the visible Rime candidate page when available.

#### Scenario: Displayed item maps to an engine candidate

- GIVEN a numbered displayed candidate references an engine-page index
- WHEN the user presses its number shortcut
- THEN Xime SHALL select that referenced engine candidate

#### Scenario: Displayed item is a plugin action

- GIVEN the numbered item is a plugin-provided direct action
- WHEN the user presses its shortcut
- THEN Xime SHALL execute that displayed action rather than the raw engine index

#### Scenario: Requested displayed index no longer exists

- GIVEN transformation reduced the displayed list below the requested index
- WHEN the user presses that shortcut
- THEN Xime SHALL consume it and SHALL NOT fall through to an unrelated Rime candidate

### Requirement: Exact Hardware Option Shortcuts

The system SHALL recognize `Ctrl+.` as the punctuation-mode toggle and `Shift+Space` as the full-width/half-width toggle only when no additional command modifier is present. The key SHALL be sent through Rime; when the corresponding Rime option changes, Xime SHALL persist the option state and show the resulting localized status.

#### Scenario: Punctuation toggle

- GIVEN the active schema exposes `ascii_punct`
- WHEN the user presses exactly `Ctrl+.`
- THEN Rime SHALL toggle the option, Xime SHALL persist it, and Xime SHALL show Chinese- or English-punctuation status matching the resulting state

#### Scenario: Character-width toggle

- GIVEN the active schema exposes `full_shape`
- WHEN the user presses exactly `Shift+Space`
- THEN Rime SHALL toggle the option, Xime SHALL persist it, and Xime SHALL show full-width or half-width status matching the resulting state

#### Scenario: Shortcut has extra modifiers

- GIVEN Alt or Meta is added to either shortcut, or Ctrl is added to `Shift+Space`
- WHEN the key is pressed
- THEN Xime SHALL NOT classify it as an option shortcut

### Requirement: Right Shift Reports Actual Mode Changes

Xime SHALL compare `ascii_mode` before Right Shift key-down and after Right Shift key-up. It SHALL show Chinese/English mode status only on release and only when the mode actually changed. Left Shift, missing option results and unchanged state SHALL be silent.

#### Scenario: Right Shift changes language mode

- GIVEN Rime changes `ascii_mode` in response to a Right Shift tap
- WHEN Right Shift is released
- THEN Xime SHALL show the resulting Chinese or English mode status

#### Scenario: Right Shift does not change mode

- GIVEN `ascii_mode` is the same before key-down and after key-up
- WHEN Right Shift is released
- THEN Xime SHALL show no mode-change status

### Requirement: Voice Shortcuts Are Gated By STT Availability

When speech-to-text is enabled, exact `Ctrl+0` or `Ctrl+Numpad0` SHALL toggle sticky voice input on the first key-down and consume the matching release. An unmodified physical Space SHALL be held until release: a short press SHALL route one normal Space, while holding through Android's long-press timeout SHALL start non-sticky voice input and release SHALL end that voice session. When speech-to-text is disabled, these keys SHALL not be captured as voice shortcuts.

#### Scenario: Sticky voice toggle

- GIVEN speech-to-text is enabled
- WHEN the user presses exact `Ctrl+0` without Shift, Alt or Meta
- THEN Xime SHALL toggle sticky voice input once despite repeat key-down events

#### Scenario: Short Space press

- GIVEN speech-to-text is enabled and no Shift/Ctrl/Alt/Meta is held
- WHEN Space is released before the long-press timeout
- THEN Xime SHALL cancel the pending voice action and route exactly one Space

#### Scenario: Long Space press

- GIVEN speech-to-text is enabled and unmodified Space remains held through the long-press timeout
- WHEN the timeout fires and the user later releases Space
- THEN Xime SHALL start non-sticky voice input at the timeout and end that voice session on release without inserting Space

### Requirement: Compact Candidate Card Follows The Cursor And Screen Bounds

In compact hardware-keyboard mode, Xime SHALL render the candidate/preedit/voice card when voice mode, input text, candidates or recent-clipboard candidates are present. A valid visible cursor anchor SHALL be converted from screen coordinates to candidate-layer coordinates. The card SHALL prefer the cursor's left edge and a position below the cursor, flip above it when needed, and clamp both axes inside the candidate layer. Invalid or unavailable cursor coordinates SHALL use a horizontally centered, top-inset fallback that remains inside the layer.

#### Scenario: Cursor is near a normal screen position

- GIVEN valid cursor and layer screen coordinates
- WHEN the compact candidate card is measured
- THEN its local position SHALL be derived from the coordinate difference and vertical gap

#### Scenario: Cursor is near an edge

- GIVEN the preferred card position would cross a layer boundary
- WHEN position is calculated
- THEN the card SHALL be flipped or clamped so the complete card stays inside the layer

#### Scenario: Cursor anchor is invalid

- GIVEN cursor visibility is false or cursor coordinates are non-finite/invalid
- WHEN position is calculated
- THEN Xime SHALL use the constrained centered fallback rather than placing the card off-screen

### Requirement: Candidate Card Exposes Composition And Paging State

The compact card SHALL display composition/preedit content, up to ten numbered candidate items, current highlight, and previous/next page affordances when those pages exist. Candidate labels SHALL use `1` through `9` and `0` in the same order as physical shortcuts. Voice mode SHALL replace ordinary candidate content with voice-state feedback owned by the same compact card.

#### Scenario: Candidate page is visible

- GIVEN the engine exposes candidates and optional page boundaries
- WHEN the card is rendered
- THEN the displayed labels, highlight and page affordances SHALL match the current candidate state

### Requirement: Hardware Status Is Independent And Self-Clearing

Mode/option status SHALL render in a dedicated top-centered overlay in compact mode, independently of candidate visibility. New status SHALL replace an older status and reset its one-second timeout. Clearing SHALL occur only if the delayed callback still refers to the currently displayed message. Session cleanup SHALL remove the message.

#### Scenario: Status is shown while candidates exist

- GIVEN the candidate card is visible
- WHEN a hardware shortcut changes a mode
- THEN the status overlay SHALL remain visible above the candidate layer without replacing candidate content

#### Scenario: Status changes before timeout

- GIVEN one message is pending automatic dismissal
- WHEN another status message is emitted
- THEN the old dismissal SHALL be cancelled and the new message SHALL receive a fresh timeout

## Edge Cases And Failure Behavior

- A null `KeyEvent`, unsupported key, unavailable Rime result or unavailable input connection SHALL fall back without fabricating a commit.
- Auto-repeat SHALL not repeatedly toggle sticky voice input.
- Shortcut status SHALL not claim a change when the relevant Rime option is unavailable or unchanged.
- Candidate position calculations SHALL constrain cards larger than or near the edge of the available layer.
- Session teardown SHALL cancel pending Space long-press work and clear transient status.

## Data / Entity Constraints

- Android modifier masks and Rime modifier masks are distinct domains and SHALL be converted explicitly.
- Candidate shortcut labels and indexes are fixed as `1..9,0` → `0..9`.
- Compact candidate rendering exposes at most ten candidates.
- The voice Space path applies only to Space with none of Shift/Ctrl/Alt/Meta.
- Transient hardware status duration is one second in the current implementation.

## Behavior Coverage

| Behavior Surface | Normal Behavior | Authorization / Actors | State / Data Effects | Failure / Edge Cases | External Dependencies | Concurrency / Idempotency | Validation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Key translation/routing | covered | physical-key user | covered | covered | Android key layout, Rime, `InputConnection` | event order and recursion covered | unit tests + source review |
| Candidate shortcuts | covered | physical-key user | covered | covered | Rime/plugin display mapping | stale index guarded | unit tests |
| Option/language shortcuts | covered | physical-key user | persisted Rime option state | covered | Rime options | repeat/change tracking covered | unit tests; device UI pending |
| Voice shortcuts | covered | STT-enabled user | voice session start/end | covered | Android timeout, STT setting/provider | repeat and delayed callback covered | mapper tests + source review |
| Candidate/status UI | covered | hardware-keyboard user | transient Compose state | covered | cursor-anchor info | delayed clear guarded | position tests + source review; device UI pending |

## Coverage Gaps

- No automated UI test verifies the rendered candidate card, status overlay, localization or one-second visual duration on a device.
- No automated integration test exercises the complete host-editor fallback/recursion path with a real `InputConnection`.
- Voice provider success/failure is outside this capability and is not validated here.

## Acceptance Criteria

- Printable, modifier, navigation/editing, function and keypad inputs obey the mapping and fallback requirements without duplicate commits.
- Candidate number keys select the displayed action or are safely consumed when that action is absent.
- Right Shift, `Ctrl+.`, `Shift+Space`, `Ctrl+0` and Space long press satisfy exact-modifier and status/session semantics.
- Candidate positioning remains within all tested layer edges and uses a deterministic fallback for invalid anchors.
- General hardware behavior remains schema-independent; schema-specific semantics stay in their owning spec.

## Validation Plan

- Run `./gradlew :app:testDebugUnitTest` and verify at least:
  - `KeyCodeMapperTest`
  - `CandidateShortcutResolverTest`
  - `HardwareOptionShortcutTest`
  - `RightShiftModeChangeTrackerTest`
  - `HardwareCandidatePositionTest`
  - `RimeCandidateTest`
- Run `./gradlew :app:assembleDebug` to validate Android/Compose integration.
- Manually verify on a physical keyboard: non-US printable input, candidate selection/paging, host shortcuts, all mode shortcuts, Space voice handling and candidate/status placement near screen edges.

## Assumptions And Open Questions

- Assumption: Rime remains the authority for whether a mapped key was handled and for resulting option/composition state.
- Open question: device/manual validation across OEM keyboards, keyboard layouts and editors has not been recorded; keep `authority: draft` until that review is complete.

## Source Trace

- `app/src/main/java/com/kingzcheung/xime/service/KeyCodeMapper.kt`: printable/layout mapping, modifier masks, voice predicates and Rime special/editing key maps.
- `app/src/main/java/com/kingzcheung/xime/service/ImeKeyRouter.kt`: hardware dispatch, displayed-candidate actions, option persistence, status messages and right-Shift tracking.
- `app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt`: Android event ownership, Space/voice timing, fallback forwarding and compact UI composition.
- `app/src/main/java/com/kingzcheung/xime/ui/keyboard/HardwareKeyboardCandidateBar.kt`: card visibility/content and coordinate conversion/constraining.
- `app/src/main/java/com/kingzcheung/xime/ui/keyboard/HardwareKeyboardStatusOverlay.kt`: independent status rendering.
- Tests: `app/src/test/java/com/kingzcheung/xime/service/KeyCodeMapperTest.kt`, `HardwareOptionShortcutTest.kt`, `RightShiftModeChangeTrackerTest.kt`, `app/src/test/java/com/kingzcheung/xime/rime/CandidateShortcutResolverTest.kt`, `RimeCandidateTest.kt`, and `app/src/test/java/com/kingzcheung/xime/ui/keyboard/HardwareCandidatePositionTest.kt`.
- Commit evidence: `fdcb8032`, `d81c4cc3`, `9f367922`, `05030c14`.
- Validation evidence: `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` passed on 2026-09-19; physical-device/manual validation remains not run.
- TaskAdmin task: `master/3`.
