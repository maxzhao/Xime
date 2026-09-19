---
title: 五笔拼音混输与组合态实体键盘行为增量规格
created: 2026-09-14
updated: 2026-09-19
type: source
doc_role: spec
authority: proposed
status: accepted
accepted_at: 2026-09-19
merged_to:
  - ".supermax/specs/wubi-pinyin-input/spec.md"
validation:
  automated: passed
  human: not-run
archive_state: retained
taskadmin_tag: master
taskadmin_id: 3
change_id: complete-wubi-pinyin-input
capability: wubi-pinyin-input
sources:
  - ".supermax/specs/wubi-pinyin-input/spec.md"
  - ".supermax/specs/changes/complete-wubi-pinyin-input/proposal.md"
  - ".supermax/specs/changes/complete-wubi-pinyin-input/design.md"
confidence: high
---

# Delta Spec: wubi-pinyin-input

## ADDED Requirements

### Requirement: Configurable First-Use Mode

The system SHALL obtain `wubi86_pinyin`'s first-use Chinese/English mode from merged Xime configuration, default to English, allow `xime.custom.yaml` to override it, and restore a later persisted value when present.

#### Scenario: Configuration applies only before a saved choice exists

- GIVEN no saved `ascii_mode`
- WHEN `wubi86_pinyin` starts
- THEN the merged configuration value is applied
- GIVEN a saved `ascii_mode` later exists
- WHEN the schema starts again
- THEN the saved value is applied instead

### Requirement: Accepted Physical Shortcuts

Physical right Shift SHALL commit raw composition and switch mode; left Shift SHALL not switch. Physical `Ctrl+.`, `;`, `'`, `[`, and `]` SHALL toggle punctuation, select candidates 2/3, and page backward/forward respectively.

#### Scenario: Shortcuts are consumed by input handling

- GIVEN the applicable mode/menu state
- WHEN an accepted physical shortcut is pressed
- THEN the configured action occurs once
- AND no unrelated host key effect occurs

### Requirement: Source-Aware Wubi Four-/Five-Key Behavior

The system SHALL auto-commit after four letters only for exactly one exact Wubi candidate. Before a fifth letter, it SHALL commit the current first candidate only when that candidate is exact Wubi, then retain the fifth letter as new input. Otherwise it SHALL continue Pinyin input without a global four-letter limit.

#### Scenario: Wubi commits, Pinyin continues

- GIVEN an exact Wubi four-letter state
- WHEN uniqueness or a fifth key satisfies the corresponding rule
- THEN the Wubi candidate commits as specified
- GIVEN a Pinyin-origin first candidate
- WHEN a fifth or later letter is typed
- THEN the same composition continues

### Requirement: Shared Fuzzy Pinyin

Both standalone `pinyin_simp` and the Pinyin path of `wubi86_pinyin` SHALL support the accepted bidirectional fuzzy pairs, including `n↔l` and `eng↔ong`, with longer finals applied before shorter suffix rules.

#### Scenario: Same relation works in both schemes

- GIVEN either target scheme
- WHEN input matches one side of a configured fuzzy relation
- THEN candidates matching the paired spelling are available

### Requirement: Pinyin-Origin Mixed Candidate Wubi Annotation

Only Pinyin-origin candidates in `wubi86_pinyin` SHALL display a parenthesized full Wubi code. The code SHALL be the longest lowercase alphabetic code, with dictionary-order tie-break. The annotation SHALL be visible on both candidate surfaces regardless of general comment visibility and SHALL not change committed text.

#### Scenario: `发` is displayed and committed correctly

- GIVEN Pinyin-origin `发` has codes `v`, `ntc`, and `ntcy`
- WHEN it is displayed
- THEN the label is `发(ntcy)`
- WHEN selected
- THEN committed text is `发`

### Requirement: Engine-Authoritative Composition Keys

Composition-sensitive physical keys and the corresponding soft-key paths SHALL use ordered Rime state/results rather than asynchronous UI snapshots. Host fallback SHALL occur only after an authoritative Rime unhandled result; unavailable/busy state SHALL not mutate host text.

#### Scenario: Immediate Backspace removes composition

- GIVEN a letter has entered Rime but its UI update is pending
- WHEN Backspace is pressed
- THEN the uncommitted letter is removed
- AND host text is unchanged

#### Scenario: Accepted editing matrix

- GIVEN composition exists
- WHEN Backspace, Forward Delete, Left/Right, Home/End, Up/Down, Page keys, brackets, Space, Enter, or Escape is pressed
- THEN the key follows the composition behavior defined in `.supermax/specs/wubi-pinyin-input/spec.md`
- AND does not also perform a host action

## MODIFIED Requirements

None. No previously accepted stable spec existed for this capability.

## REMOVED Requirements

None.

## Convergence

- Requirements are merged into `.supermax/specs/wubi-pinyin-input/spec.md` and implemented by commit `d81c4cc33f18dd81768a26eba4ca528c8177e627`.
- Automated JVM tests and debug assembly passed on 2026-09-19; device/manual validation is `not-run`, so the stable owner remains draft.

## Source Trace

- `.supermax/specs/wubi-pinyin-input/spec.md`
- `.supermax/specs/changes/complete-wubi-pinyin-input/proposal.md`
- `.supermax/specs/changes/complete-wubi-pinyin-input/design.md` (retained historical evidence)
- User requirements agreed in chat on 2026-09-14.
- TaskAdmin task: `master/3`.
