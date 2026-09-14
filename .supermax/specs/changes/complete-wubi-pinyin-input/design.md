---
title: 五笔拼音混输与组合态键路由实施设计
created: 2026-09-14
updated: 2026-09-14
doc_role: design
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
  - ".supermax/specs/changes/complete-wubi-pinyin-input/proposal.md"
confidence: high
---

# Design: complete-wubi-pinyin-input

## Summary

Implement one ordered composition-key path backed by a tri-state JNI result, add bounded candidate source/display metadata, apply exact-Wubi four-/five-key rules at the native Xime/Rime boundary, and keep configuration ownership explicit: Xime merged config for first-use mode, Rime schema/default config for Pinyin and physical shortcuts.

## Context

- Spec: `.supermax/specs/wubi-pinyin-input/spec.md`
- Proposal: `.supermax/specs/changes/complete-wubi-pinyin-input/proposal.md`
- TaskAdmin: not applicable
- Relevant source paths:
  - `app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt`
  - `app/src/main/java/com/kingzcheung/xime/service/ImeKeyRouter.kt`
  - `app/src/main/java/com/kingzcheung/xime/service/KeyCodeMapper.kt`
  - `app/src/main/java/com/kingzcheung/xime/service/ImeSessionController.kt`
  - `app/src/main/java/com/kingzcheung/xime/service/ImeSchemaController.kt`
  - `app/src/main/java/com/kingzcheung/xime/service/ImeKeyboardCallbacks.kt`
  - `app/src/main/java/com/kingzcheung/xime/rime/RimeEngine.kt`
  - `app/src/main/jni/librime_jni/rime_jni.cc`
  - `app/src/main/assets/xime.yaml`
  - `app/src/main/assets/default.custom.yaml`
  - `app/src/main/assets/rime/wubi86_pinyin.schema.yaml`
  - `app/src/main/assets/rime/pinyin_simp.schema.yaml`
- Constraints:
  - Preserve unrelated user worktree changes.
  - Do not set mixed-schema `speller/max_code_length: 4`.
  - Do not alter candidate text used for commit or plugin engine-index mapping.
  - Vendored Rime and bundled scheme directories are submodule-owned.

## Approach

### 1. Configuration Ownership

Add a small input-mode section to Xime merged config, for example:

```yaml
input:
  wubi86_pinyin:
    default_ascii_mode: true
```

Extend `XimeConfig` parsing/merging with this field and expose a getter. `xime.custom.yaml` overrides the built-in value. This setting is consulted only when persisted `var/option/ascii_mode` is absent.

Add `ascii_mode` to `wubi86_pinyin.schema.yaml` so Rime and Xime schema-switch UI share the same option definition. Keep Rime's schema reset value aligned with the built-in default as a defensive baseline, but use the merged Xime setting for the configurable first-use decision.

Change the user-config boolean read contract from `Boolean` to presence-aware `Boolean?` across JNI/Kotlin. `restorePersistedSchemaOptions()` applies a value only when present; otherwise it applies the configured first-use value for `wubi86_pinyin` and leaves other switch reset behavior intact. Explicit mode changes continue persisting `var/option/ascii_mode`.

Configure `ascii_punct` persistence and make `Ctrl+period` toggling persist through Xime's option persistence path after a handled event. Do not rely on a missing boolean reading as `false`.

### 2. Physical Shortcut Configuration

Update fixed `app/src/main/assets/default.custom.yaml`:

- `Shift_L: noop`
- `Shift_R: commit_code`
- `Control+period` toggles `ascii_punct`
- retain `; → 2`, `' → 3`, `[ → Page_Up`, `] → Page_Down`

Ensure Xime's fixed default-custom synchronization writes the accepted fixed bindings into an existing user file while preserving the user-managed `schema_list` and current `menu/page_size`. This is a deterministic replacement of Xime-owned baseline bindings, not a compatibility branch.

### 3. Tri-State Rime Dispatch

Introduce an explicit dispatch outcome across `RimeEngine` and JNI:

- `Handled(result)`
- `Unhandled(result)`
- `Unavailable`

The input-event API used by `keyProcessingDispatcher` SHALL take the Rime lock with ordered/waiting semantics. Deployment is already serialized through `keyJobs`; event callers do not use a non-blocking default result that conflates lock contention with Rime rejection.

Keep non-blocking snapshot APIs for UI-only refreshes. Do not use them for host-fallback decisions.

### 4. One Composition-Key Router

Route physical Backspace, Forward Delete, Left/Right, Up/Down, Home/End, Page Up/Page Down, Space, Enter/Numpad Enter, and Escape to one `ImeKeyRouter` operation. Soft-keyboard Backspace, Enter, Space, and cursor callbacks invoke the same operation when composition semantics apply.

Decision order:

1. Preserve explicitly owned Xime states such as quick-send focus, English `pendingEnglishText`, T9 partial state, and number/symbol panel behavior.
2. Submit the key to authoritative Rime in FIFO order.
3. On `Handled`, commit `committedText` once and update UI from the same result.
4. On `Unhandled`, apply the ordinary host behavior.
5. On `Unavailable`, do not mutate host content; schedule/retry only through the existing ordered queue if maintenance serialization has not already guaranteed availability.

Remove ordinary mixed-input decisions based solely on `candidateState.isComposing`, `inputText`, or candidate presence. Keep `candidateState` as display and Xime-owned auxiliary state only.

Add Backspace to the physical composition-key route instead of letting it fall into a host deletion path. Route toolbar Home/End/directions and the soft cursor gesture through the same composition-aware operation before direct `InputConnection` editing.

### 5. Candidate Metadata Transport

Extend native result candidates and `RimeCandidate` with bounded metadata needed by the accepted behavior:

- genuine source type;
- optional full Wubi code;
- optional required display suffix/decoration.

Populate source type from the genuine candidate before converting the current Rime candidate page to JNI. `reverse_lookup` identifies Pinyin-origin mixed candidates. Table-origin types remain undecorated.

For `reverse_lookup` candidates in `wubi86_pinyin`, use a cached `wubi86` reverse dictionary. Parse returned codes in dictionary order, retain lowercase alphabetic codes, choose the longest, and retain the first among equal lengths. Do no lookup for other schemas or source types.

Do not alter `candidate.text`. Kotlin builds a display label `text + "(" + code + ")"` only for the candidate surfaces; candidate actions and engine indexes still refer to the undecorated text/index. Carry the required labels separately from ordinary optional comments so the general comment visibility setting does not hide them. Add the same labels to `HardwareKeyboardCandidateBar`.

Candidate transform handling must preserve the metadata for engine-reference items and omit it for plugin-created items unless the item directly references the same engine candidate.

### 6. Source-Aware Wubi Auto-Commit

Implement the rule in the Xime native Rime integration boundary rather than global mixed-schema `max_code_length`:

- Before an alphabetic fifth key, inspect current Rime raw input and first genuine candidate. If the current input is four letters and the first candidate is exact Wubi table-origin consuming that segment, commit it, then process the new letter as new input.
- After an alphabetic fourth key is accepted and composition is refreshed, inspect the active segment's prepared menu. Count exact Wubi table-origin candidates only. If exactly one exists, select/confirm it through Rime so normal commit history and dictionary learning remain intact.
- Do not count `reverse_lookup`, completion, punctuation, Lua, association, or plugin candidates.
- Reuse the resulting Rime state/commit in the same JNI result; do not issue a second Kotlin-side commit.

Use current `rime::Service` session/context/menu APIs from `rime_jni.cc`; avoid changing generic vendored `speller.cc` semantics for all Rime clients. If a minimal helper is required in the maintained librime fork to expose exact source identity safely, keep it generic and tested, but the policy remains Xime/schema-scoped.

### 7. Fuzzy Pinyin

Move the complete accepted bidirectional fuzzy algebra into `pinyin_simp.schema.yaml`, with `ian/iang` and `uan/uang` before `an/ang`. Because `wubi86_pinyin` depends on `pinyin_simp`, both input paths compile from one source. Remove dependence on a separate bundled `pinyin_simp.custom.yaml` baseline.

### 8. Validation And Convergence

Use focused unit tests for pure routing/config/metadata decisions and existing `KeyCodeMapperTest`. Add only the minimum native/config integration validation needed to prove source identity, longest-code choice, four-/five-key behavior, and Pinyin non-truncation. Finish with Android build and the physical-keyboard manual matrix.

After implementation and validation:

- update `.supermax/specs/wubi-pinyin-input/spec.md` to `authority: normative`, `status: active`;
- mark proposal/design/tasks/delta `status: accepted` with validation and `merged_to`;
- retain the change workspace in place;
- update `.supermax/specs/index.md` and `.supermax/specs/log.md`.

## Decisions

### Decision: Xime Config Controls First Use

- Choice: merged `xime.yaml`/`xime.custom.yaml` controls first-use mode; `user.yaml` controls later memory.
- Rationale: satisfies configurability without forcing the mode each session.
- Alternatives considered: hard-coded Kotlin default and schema reset alone; rejected because neither provides the agreed merged Xime configuration behavior reliably under current manual restoration.

### Decision: Tri-State Ordered Key Dispatch

- Choice: composition-sensitive key routing uses a tri-state, lock-safe Rime result.
- Rationale: prevents `candidateState` latency and lock contention from being interpreted as permission to edit host text.
- Alternatives considered: checking `candidateState` or adding only Backspace to `isRimeSpecialKey`; rejected because Space/Enter/navigation and soft-key paths retain the same race.

### Decision: Native Source Identity, Kotlin Display Decoration

- Choice: identify candidate origin and Wubi code natively; render decoration separately in Kotlin.
- Rationale: candidate type exists only in C++ Rime objects, while keeping commit text/index unchanged avoids selection and plugin regressions.
- Alternatives considered: infer origin from comments/text in Kotlin or alter candidate text in a Rime filter; rejected as ambiguous or capable of changing committed text.

### Decision: Native Xime-Scoped Auto-Commit Policy

- Choice: inspect exact candidates at the JNI/session boundary and leave generic mixed-schema `speller` unlimited.
- Rationale: the built-in `max_code_length` applies to the shared alphabet and would truncate Pinyin.
- Alternatives considered: global `max_code_length: 4`, input prefixing, or a broad librime speller semantic change; rejected by the accepted unprefixed mixed-input requirement and scope.

## Validation Strategy

- Focused JVM tests for:
  - presence-aware persisted booleans and first-use config merge;
  - physical key classification and no-host-fallback decisions;
  - longest-code selection and separate display/commit values;
  - preservation through engine-reference candidate transforms;
  - fuzzy-rule list/order checks.
- Native/config integration tests or instrumented checks for:
  - genuine source type transport;
  - unique exact Wubi four-key commit;
  - fifth-key first-candidate rule;
  - `jiang`/`guang` continuation;
  - `发` reverse code resolves to `ntcy`.
- `./gradlew :app:testDebugUnitTest` and `./gradlew assembleDebug` after implementation.
- Physical Android keyboard manual acceptance matrix from the stable spec.

## Risks And Rollback

- Risk: native candidate inspection may prepare more candidates than the visible page. Mitigation: prepare only enough to prove uniqueness (two qualifying exact candidates) and stop early.
- Risk: reverse-code lookup per candidate can affect latency. Mitigation: cache the loaded dictionary and limit work to visible `reverse_lookup` candidates in `wubi86_pinyin`.
- Risk: user-owned current edits overlap key-routing files. Mitigation: inspect current diff before each edit and apply semantic, narrow changes without reverting unrelated work.
- Rollback: revert the change-specific commits in the parent repository and owning submodules; no runtime compatibility flag or dual behavior is introduced.
