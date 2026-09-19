---
title: 五笔拼音混输与组合态实体键盘行为规格
created: 2026-09-14
updated: 2026-09-19
type: source
doc_role: spec
authority: draft
status: experimental
taskadmin_tag: master
taskadmin_id: 3
sources:
  - "User requirements agreed in chat on 2026-09-14"
  - "git:d81c4cc33f18dd81768a26eba4ca528c8177e627"
  - "app/src/main/assets/rime/wubi86_pinyin.schema.yaml"
  - "app/src/main/assets/rime/pinyin_simp.schema.yaml"
  - "app/src/main/assets/default.custom.yaml"
  - "app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt"
  - "app/src/main/java/com/kingzcheung/xime/service/ImeKeyRouter.kt"
  - "app/src/main/java/com/kingzcheung/xime/service/KeyCodeMapper.kt"
  - "app/src/main/java/com/kingzcheung/xime/service/ImeSessionController.kt"
  - "app/src/main/java/com/kingzcheung/xime/service/ImeSchemaController.kt"
  - "app/src/main/java/com/kingzcheung/xime/service/ImeKeyboardCallbacks.kt"
  - "app/src/main/java/com/kingzcheung/xime/rime/RimeEngine.kt"
  - "app/src/main/jni/librime_jni/rime_jni.cc"
  - "app/src/main/jni/librime/src/rime/gear/speller.cc"
  - "app/src/main/jni/librime/src/rime/gear/reverse_lookup_translator.cc"
  - "app/src/main/jni/librime/src/rime/gear/reverse_lookup_filter.cc"
  - "app/src/main/jni/librime/src/rime/engine.cc"
  - "app/src/androidTest/java/com/kingzcheung/xime/rime/WubiPinyinNativeIntegrationTest.kt"
  - "app/src/test/java/com/kingzcheung/xime/rime/WubiPinyinSchemaTest.kt"
  - "app/src/test/java/com/kingzcheung/xime/rime/RimeCandidateTest.kt"
derived_to: []
confidence: high
---

> **TLDR**: `wubi86_pinyin` SHALL open in a configurable default mode, remember later mode changes, preserve unrestricted fuzzy Pinyin, apply four-/five-key auto-commit only to exact Wubi candidates, annotate only Pinyin-origin mixed candidates with their longest full Wubi code, and route composition-editing keys through authoritative Rime state before host-editor fallback.

## Purpose

Govern the observable behavior of Xime's `wubi86_pinyin` mixed-input schema and its physical-keyboard editing semantics. The contract covers initial mode, mode and punctuation switching, candidate keys, Wubi auto-commit, Pinyin fuzzy matching, mixed-candidate Wubi-code display, and uncommitted-composition editing.

## Scope

### In Scope

- `wubi86_pinyin` on Xime for Android.
- Configurable first-use default Chinese/English mode with subsequent state memory.
- Physical-keyboard right Shift, `Ctrl+.`, `[`/`]`, `;`/`'`, and composition editing/navigation keys.
- Wubi exact-code behavior inside unprefixed Wubi/Pinyin mixed input.
- Pinyin fuzzy rules shared by mixed input and standalone `pinyin_simp`.
- Candidate display in the normal candidate bar and physical-keyboard floating candidate bar.
- Correct ordering when physical and soft-keyboard events are processed faster than UI state propagation.

### Out Of Scope

- Adding `;`, `'`, `[` or `]` as dedicated soft-keyboard keys.
- Applying Wubi-code annotations to standalone `pinyin_simp`, pure-Wubi candidates, punctuation, Lua utility candidates, associations, clipboard items, handwriting candidates, T9, or plugin-injected candidates.
- Changing T9, handwriting, voice-input, calculator, plugin candidate-transform, or English direct-commit product behavior except where a shared input path must preserve its existing semantics.
- Transitional compatibility modes or preservation of old conflicting input behavior.
- A general-purpose settings framework beyond the configuration fields required here.
- Schema-independent physical-key mapping, host fallback, voice shortcuts, cursor-anchored candidate placement and transient status UI; owned by `.supermax/specs/physical-keyboard-input/spec.md`.

## Terms And Ownership

- **First use**: the target schema has no persisted `ascii_mode` value in `user.yaml`.
- **Remembered mode**: the last explicit Chinese/English state selected after first use.
- **Composition**: authoritative Rime context with non-empty raw input or an active unconfirmed segment; `candidateState` is only a display snapshot.
- **Exact Wubi candidate**: a candidate produced by the Wubi table path whose current segment consumes exactly four raw Wubi letters and is not a completion, reverse-lookup/Pinyin, punctuation, Lua, association, clipboard, T9, or plugin candidate.
- **Pinyin-origin mixed candidate**: a `wubi86_pinyin` candidate whose genuine Rime source type is `reverse_lookup`.
- **Full Wubi code**: the longest lowercase alphabetic code associated with the candidate text in dictionary `wubi86`; equal-length ties use dictionary order. Example: `发` has `v`, `ntc`, and `ntcy`, so its full code is `ntcy`.
- **Host fallback**: forwarding the key to Android's target `InputConnection` only after Rime authoritatively reports that the key was not consumed and no Xime-owned pending state requires handling.

## Requirements

### Requirement: Configurable First-Use Default And Remembered Mode

The system SHALL expose a Xime configuration value for the first-use mode of `wubi86_pinyin`. The built-in value SHALL be English. The value SHALL be overridable from user configuration rather than hard-coded in Kotlin or JNI.

When a persisted `ascii_mode` value exists, the system SHALL restore it. When it does not exist, the system SHALL use the configured first-use default and SHALL NOT coerce missing state to `false`.

The first-use value SHALL NOT force English at every input session.

#### Scenario: First use defaults to English

- GIVEN `wubi86_pinyin` has no persisted `ascii_mode`
- AND the effective Xime configuration selects English as the default
- WHEN the schema session starts
- THEN `ascii_mode` is enabled
- AND the keyboard UI reflects English mode

#### Scenario: User override defaults to Chinese

- GIVEN `wubi86_pinyin` has no persisted `ascii_mode`
- AND user configuration overrides the default to Chinese
- WHEN the schema session starts
- THEN `ascii_mode` is disabled

#### Scenario: Later sessions remember the user choice

- GIVEN the user has explicitly changed `ascii_mode`
- WHEN a later input session or schema switch restores the schema
- THEN the persisted state wins over the configured first-use default

#### Scenario: Missing persisted value is not false

- GIVEN `user.yaml` contains no `var/option/ascii_mode`
- WHEN Xime restores schema options
- THEN Xime leaves the configured first-use mode effective
- AND does not write or apply `false` merely because the key is absent

### Requirement: Physical Right Shift Switches Chinese/English

For a physical keyboard, a standalone right Shift SHALL switch Chinese/English mode. Left Shift SHALL remain a modifier and SHALL NOT switch mode.

When switching while composition exists, right Shift SHALL commit the raw uncommitted input exactly once before changing mode. When no composition exists, it SHALL only change mode. Xime SHALL preserve distinct left/right modifier press and release events so Rime can recognize standalone right Shift.

#### Scenario: Right Shift commits raw input and switches

- GIVEN Chinese mode and raw composition `abcd`
- WHEN the user taps physical right Shift
- THEN `abcd` is committed exactly once
- AND `ascii_mode` becomes enabled
- AND no Shift event is forwarded to the host editor

#### Scenario: Left Shift remains a modifier

- GIVEN either input mode
- WHEN the user uses physical left Shift alone or in a chord
- THEN it does not switch Chinese/English mode
- AND its ordinary modifier semantics remain available

### Requirement: Physical `Ctrl+.` Toggles Punctuation Mode

Physical `Ctrl+.` SHALL toggle `ascii_punct`. The selected punctuation state SHALL persist across later sessions. The shortcut SHALL be consumed by Rime/Xime and SHALL NOT be forwarded to the host when handled.

#### Scenario: Toggle punctuation mode

- GIVEN an active Rime schema exposing `ascii_punct`
- WHEN the user presses physical `Ctrl+.`
- THEN `ascii_punct` toggles once
- AND the new value is persisted

### Requirement: Physical Candidate Selection And Paging

While a candidate menu exists:

- `;` SHALL select candidate 2;
- `'` SHALL select candidate 3;
- `[` SHALL request the previous candidate page;
- `]` SHALL request the next candidate page.

These keys SHALL be interpreted before punctuation or host fallback. If the requested candidate or page does not exist, the key SHALL NOT commit an unrelated candidate or delete/modify host text.

#### Scenario: Select candidates two and three

- GIVEN at least three candidates
- WHEN the user presses `;` and later `'`
- THEN the second and third displayed candidates are selected respectively

#### Scenario: Page with brackets

- GIVEN a candidate menu with another page
- WHEN the user presses `[` or `]`
- THEN the previous or next candidate page is shown respectively

### Requirement: Wubi Completion Is Visible Per Keystroke

In `wubi86_pinyin`, every accepted input letter SHALL update the raw/preedit display and available candidates without requiring an explicit commit key. This requirement does not require pure-Wubi candidates to gain an added Wubi-code annotation.

#### Scenario: Incremental feedback

- GIVEN Chinese mode in `wubi86_pinyin`
- WHEN the user types one, two, three, and four valid letters
- THEN each accepted letter produces an updated composition/candidate state

### Requirement: Exact Four-Key Wubi Auto-Commit

After processing the fourth raw letter, the system SHALL auto-commit only when exactly one exact Wubi candidate exists for that four-letter segment. Pinyin-origin candidates and Wubi completion candidates SHALL NOT count toward the exact-Wubi uniqueness test.

If zero or multiple exact Wubi candidates exist, composition SHALL remain open.

#### Scenario: Unique exact four-key Wubi candidate commits

- GIVEN the first four raw letters resolve to exactly one exact Wubi candidate
- WHEN the fourth letter is processed
- THEN that Wubi candidate is committed exactly once
- AND composition becomes empty

#### Scenario: Pinyin remains unrestricted

- GIVEN the current raw input is a valid Pinyin path such as `jiang` or `guang`
- AND there is no qualifying unique exact Wubi candidate at four letters
- WHEN the fifth and later letters are typed
- THEN the letters continue the same Pinyin composition
- AND no global four-letter limit truncates the input

### Requirement: Fifth Key Commits The Current Exact Wubi First Candidate

Before adding a fifth letter to a four-letter segment, the system SHALL inspect the current first candidate. If its genuine source is an exact Wubi candidate consuming the four-letter segment, the system SHALL commit that candidate exactly once and process the fifth letter as the first letter of a new composition. If the first candidate is Pinyin-origin or otherwise not exact Wubi, the fifth letter SHALL continue the existing composition.

#### Scenario: Fifth key starts the next Wubi code

- GIVEN four raw letters remain in composition
- AND the first candidate is an exact Wubi candidate
- WHEN the user types a fifth valid letter
- THEN the first candidate is committed
- AND the fifth letter is retained as the new composition's first raw letter

#### Scenario: Fifth key continues Pinyin

- GIVEN four raw letters remain in composition
- AND the first candidate is Pinyin-origin
- WHEN the user types a fifth valid letter
- THEN no candidate is committed by the Wubi fifth-key rule
- AND all five letters remain in the same composition

### Requirement: Fuzzy Pinyin Rules

Both `wubi86_pinyin`'s Pinyin path and standalone `pinyin_simp` SHALL apply these bidirectional fuzzy relations:

- `z ↔ zh`
- `c ↔ ch`
- `s ↔ sh`
- `n ↔ l`
- `an ↔ ang`
- `en ↔ eng`
- `in ↔ ing`
- `ian ↔ iang`
- `uan ↔ uang`
- `eng ↔ ong`

Longer finals (`ian/iang`, `uan/uang`) SHALL be handled before shorter suffix rules so they are not unintentionally re-expanded as `an/ang`.

#### Scenario: Fuzzy rules work in both schemas

- GIVEN either `wubi86_pinyin` or `pinyin_simp`
- WHEN the user enters a spelling matching one side of a configured relation
- THEN candidates matching the paired spelling are also available

### Requirement: Annotate Only Pinyin-Origin Mixed Candidates

In `wubi86_pinyin`, each Pinyin-origin mixed candidate with a Wubi code SHALL display `候选文字(完整五笔码)`, for example `发(ntcy)`. The candidate's committed text SHALL remain only `候选文字`.

The code SHALL be selected by the full-Wubi-code rule: longest code first, dictionary order for equal lengths. This annotation SHALL appear in both the normal candidate bar and physical-keyboard floating candidate bar and SHALL remain visible even when the general candidate-comment preference is set to hide comments.

Pure-Wubi candidates SHALL remain unchanged. Candidates without a Wubi code SHALL display only their original text.

#### Scenario: Pinyin candidate displays full Wubi code

- GIVEN `发` is produced by the `reverse_lookup` Pinyin path in `wubi86_pinyin`
- AND `wubi86` contains codes `v`, `ntc`, and `ntcy`
- WHEN candidates are displayed
- THEN the visible candidate is `发(ntcy)`
- AND selecting it commits only `发`

#### Scenario: Pure-Wubi candidate is unchanged

- GIVEN a candidate's genuine source is Wubi `table`, `user_table`, or Wubi `completion`
- WHEN candidates are displayed
- THEN Xime does not add a parenthesized Wubi code

#### Scenario: Hidden general comments do not hide this annotation

- GIVEN the general candidate-comment preference is disabled
- AND a Pinyin-origin mixed candidate has a Wubi code
- WHEN either candidate surface renders it
- THEN the parenthesized Wubi code remains visible

### Requirement: Composition State Is Engine-Authoritative

Xime SHALL use the Rime context/result as the authority for composition-sensitive key decisions. `candidateState`, `preeditText`, candidate arrays, and other UI snapshots SHALL NOT decide whether a key is sent to the host.

Physical and soft-keyboard events affecting composition SHALL execute in the existing ordered key-processing path. A key that arrives immediately after a letter SHALL observe that preceding letter even if UI propagation is pending or conflated.

The Rime dispatch contract SHALL distinguish:

1. handled by Rime;
2. authoritatively unhandled by Rime;
3. engine/session unavailable or input lock not acquired.

Only state 2 MAY use host fallback. State 3 SHALL NOT be treated as authoritatively unhandled and SHALL NOT delete, move, or submit host text.

#### Scenario: Immediate Backspace does not use stale UI

- GIVEN raw composition `w` exists in Rime
- AND the corresponding UI update has not yet run
- WHEN Backspace is processed
- THEN Rime removes `w`
- AND no Backspace is sent to the host editor

#### Scenario: Busy engine does not cause host fallback

- GIVEN Rime cannot authoritatively process a composition-sensitive key because maintenance or locking is in progress
- WHEN that key is dispatched
- THEN Xime does not reinterpret the condition as `processed=false`
- AND does not mutate host text as fallback

### Requirement: Composition Editing Key Semantics

While composition exists, physical keyboard keys SHALL behave as follows:

| Key | Required composition behavior |
| --- | --- |
| Backspace | Delete the raw input unit before the Rime composition caret. |
| Forward Delete | Delete the raw input unit after the Rime composition caret. |
| Left / Right | Move the Rime composition caret left / right. |
| Home / End | Move the Rime composition caret to the start / end. |
| Up / Down | Navigate the candidate menu according to the schema binding. |
| Page Up / Page Down | Navigate candidate pages. |
| `[` / `]` | Navigate previous / next candidate pages. |
| Space | Commit the current first candidate. |
| Enter / Numpad Enter | Commit the raw input, not an implicit candidate, and do not also trigger a host editor action. |
| Escape | Clear composition without changing committed host text. |

When no composition exists, ordinary host behavior MAY occur except for configured Xime/Rime shortcuts. Soft-keyboard Backspace, Enter, Space, and cursor operations SHALL follow the same composition-first rule; the physical-only candidate shortcut requirement does not add soft keys.

#### Scenario: Backspace removes one uncommitted unit

- GIVEN Rime raw input is `wg`
- WHEN Backspace is pressed
- THEN raw input becomes `w`
- AND committed host text is unchanged

#### Scenario: Enter commits raw input

- GIVEN Rime raw input is `wg`
- WHEN Enter is pressed
- THEN `wg` is committed exactly once
- AND no editor action or newline is also sent

#### Scenario: Escape cancels composition

- GIVEN composition exists
- WHEN Escape is pressed
- THEN composition becomes empty
- AND committed host text is unchanged

#### Scenario: No composition permits host editing

- GIVEN Rime authoritatively reports no composition
- WHEN Backspace, navigation, Home, End, or Enter is not consumed by a configured shortcut
- THEN the key may be forwarded to the host with its ordinary Android behavior

### Requirement: No Duplicate Commit Or Fallback

For every key event, at most one of these effects SHALL occur: Rime composition mutation, Rime commit, Xime-owned pending-text mutation, or host fallback. A handled event SHALL NOT also be forwarded to the host, and a committed string SHALL NOT be emitted twice.

#### Scenario: Handled key has one effect

- GIVEN a composition-sensitive key is consumed by Rime
- WHEN its result contains committed text
- THEN Xime commits that text once
- AND does not issue the corresponding Android key event

## Edge Cases And Failure Behavior

- Empty candidate menus do not authorize host fallback while raw composition still exists.
- A nonexistent second/third candidate or previous/next page does not select another item.
- Invalid or missing Wubi reverse lookup leaves the Pinyin-origin candidate unannotated; input remains usable.
- Multiple Wubi codes are normalized to lowercase alphabetic codes, then resolved by longest length and dictionary order.
- Punctuation/Lua/plugin candidates are not mistaken for Pinyin-origin candidates even when their text exists in `wubi86`.
- General comment hiding affects ordinary candidate comments only; the required parenthesized code is part of mixed-candidate display text.
- Plugin candidate transforms continue to map selections to original engine candidates; display decoration must not change committed text or engine indexes.
- Number and symbol panels may keep their panel-specific host-edit behavior only after any prior composition has been resolved according to this spec.
- Long-press Backspace coalescing may reduce queued repetition, but it must preserve key order and must not switch from composition deletion to host deletion using a stale snapshot.

## Data / Configuration Constraints

- The configurable first-use mode SHALL be represented in Xime's merged configuration (`xime.yaml` overridden by user `xime.custom.yaml`) with an explicit boolean/enum value; built-in default is English.
- `ascii_mode` and `ascii_punct` persistence use `user.yaml` option state. Presence must be distinguishable from `false`.
- `wubi86_pinyin` must expose `ascii_mode` and `ascii_punct` switches to Rime and Xime UI parsing.
- The mixed schema SHALL NOT set a global `speller/max_code_length: 4`; Pinyin length must remain unrestricted.
- Fuzzy rules live in a source that both standalone Pinyin and the mixed schema dependency compile from; they must not depend on a local runtime-only `user.yaml`.
- Candidate source identity and display annotation must survive JNI/Kotlin transport without changing candidate selection indexes or commit text.

## Behavior Coverage

| Behavior Surface | Normal Behavior | Authorization / Actors | State / Data Effects | Failure / Edge Cases | External Dependencies | Concurrency / Idempotency | Validation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Initial and remembered mode | covered | user/config | covered | covered | Rime `user.yaml` | covered | covered |
| Physical shortcuts | covered | physical keyboard user | covered | covered | Android `KeyEvent`, Rime | covered | covered |
| Wubi/Pinyin discrimination | covered | keyboard user | covered | covered | `wubi86`/`pinyin_simp` dictionaries | covered | covered |
| Candidate annotation | covered | display/selection | covered | covered | Wubi reverse dictionary | n/a | covered |
| Composition editing | covered | physical and selected soft-key paths | covered | covered | Rime session, Android `InputConnection` | covered | covered |
| T9/handwriting/plugins outside boundary | n/a | n/a | n/a | covered by non-regression boundary | existing subsystems | partial: preserve existing paths | covered |

## Coverage Gaps

- Automated JVM/native integration coverage exists, but the physical-keyboard device matrix and rendered candidate-label checks have not been recorded as human validation.
- General hardware-key routing and compact candidate/status UI are now owned separately by `.supermax/specs/physical-keyboard-input/spec.md`.

## Acceptance Criteria

- Built-in first use of `wubi86_pinyin` starts in English; a user-configured Chinese default works; later sessions restore the last explicit mode.
- Physical right Shift switches mode and commits existing raw composition exactly once; left Shift does not switch.
- Physical `Ctrl+.`, `;`, `'`, `[`, and `]` satisfy their specified punctuation/candidate behaviors.
- Four-key unique exact Wubi input auto-commits; ambiguous/absent exact Wubi does not; five-key behavior follows current first-candidate source.
- Long Pinyin inputs such as `jiang` and `guang` are not truncated by Wubi rules.
- All listed fuzzy pairs work in both mixed and standalone Pinyin input.
- Pinyin-origin `发` in mixed input is displayed as `发(ntcy)` on both candidate surfaces and commits only `发`; pure-Wubi `发` is not decorated.
- Immediate `letter → Backspace/Enter/Space/navigation` sequences do not depend on UI update timing.
- Engine unavailable/busy state never falls through to destructive host editing.
- Relevant automated validation passes; physical-keyboard manual validation remains the gate for promoting this draft to normative authority.

## Validation Plan

- JVM suite: `./gradlew :app:testDebugUnitTest`, including `WubiPinyinInputConfigTest`, `WubiPinyinSchemaTest`, `RimeCandidateTest`, `CandidateShortcutResolverTest`, `CandidateTransformCoordinatorTest`, `SoftCompositionRouteTest` and `DefaultCustomYamlTest`.
- Build/native integration: `./gradlew :app:assembleDebug` and `app/src/androidTest/java/com/kingzcheung/xime/rime/WubiPinyinNativeIntegrationTest.kt` on a device/emulator with the native engine available.
- Device validation:
  - run the physical-keyboard acceptance matrix for right Shift, `Ctrl+.`, candidate keys, paging, Backspace, Forward Delete, arrows, Home/End, Page keys, Space, Enter, and Escape;
  - repeat immediate letter-plus-edit-key sequences without waiting for candidate UI refresh;
  - verify normal and compact candidate bars display `发(ntcy)` and commit `发`;
  - verify `jiang`/`guang` and representative inputs for all fuzzy pairs in both schemas.

## Assumptions And Open Questions

- Assumption: The configuration field controls only the first use of `wubi86_pinyin`; this is the user's accepted `1A` behavior.
- Assumption: Full Wubi code means longest lowercase alphabetic code, equal-length ties by dictionary order; accepted by the user.
- Open questions: None.

## Source Trace

- User agreement, 2026-09-14: first-use configurable default English with later memory; extra `n↔l` and `eng↔ong`; mixed/standalone fuzzy Pinyin; source-aware four/five-key Wubi rule; composition key table; Pinyin-origin-only longest-code annotation and `发(ntcy)` example.
- Commit `d81c4cc33f18dd81768a26eba4ca528c8177e627`: implementation and regression tests for this capability.
- `app/src/main/assets/rime/wubi86_pinyin.schema.yaml`, `pinyin_simp.schema.yaml`, and `app/src/main/assets/default.custom.yaml`: switches, translators, shared fuzzy algebra and fixed bindings.
- `app/src/main/java/com/kingzcheung/xime/settings/KeysConfigHelper.kt` and `SettingsPreferences.kt`: merged first-use configuration and persisted option behavior.
- `app/src/main/java/com/kingzcheung/xime/rime/RimeEngine.kt` and `app/src/main/jni/librime_jni/rime_jni.cc`: tri-state ordered dispatch, candidate source metadata, exact-Wubi source decisions and lookup transport.
- `app/src/main/jni/librime_jni/wubi_source_order.h`: longest-code and dictionary-order tie behavior.
- `app/src/main/java/com/kingzcheung/xime/service/ImeKeyRouter.kt`, `ImeSessionController.kt`, `ImeSchemaController.kt` and `XimeInputMethodService.kt`: composition-authoritative routing, default/remembered mode and UI state.
- Tests: `WubiPinyinInputConfigTest`, `WubiPinyinSchemaTest`, `RimeCandidateTest`, `CandidateShortcutResolverTest`, `CandidateTransformCoordinatorTest`, `SoftCompositionRouteTest`, `DefaultCustomYamlTest`, `app/src/test/cpp/wubi_source_order_test.cc`, and `WubiPinyinNativeIntegrationTest`.
- Validation evidence: `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` passed on 2026-09-19; device/manual validation remains not run.
- Related general hardware owner: `.supermax/specs/physical-keyboard-input/spec.md`.
- Historical change workspace: `.supermax/specs/changes/complete-wubi-pinyin-input/`.
- TaskAdmin task: `master/3`.
