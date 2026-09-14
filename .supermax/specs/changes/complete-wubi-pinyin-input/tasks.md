---
title: 五笔拼音混输与组合态键路由实施清单
created: 2026-09-14
updated: 2026-09-14
doc_role: implementation-tasks
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
  - ".supermax/specs/changes/complete-wubi-pinyin-input/design.md"
confidence: high
---

# Tasks: complete-wubi-pinyin-input

> This checklist is SpecAdmin implementation planning, not TaskAdmin state or progress.

## Context

- Spec: `.supermax/specs/wubi-pinyin-input/spec.md`
- Proposal: `.supermax/specs/changes/complete-wubi-pinyin-input/proposal.md`
- Design: `.supermax/specs/changes/complete-wubi-pinyin-input/design.md`
- Delta spec: `.supermax/specs/changes/complete-wubi-pinyin-input/specs/wubi-pinyin-input/spec.md`
- TaskAdmin task: not applicable

## Phase 1: Configuration And Schema

- [x] T001 Inspect the current user-owned diffs in all affected parent/submodule files and record overlap before editing; do not revert unrelated changes.
- [x] T002 Add merged Xime configuration for `input.wubi86_pinyin.default_ascii_mode` in `app/src/main/assets/xime.yaml` and the corresponding `KeysConfigHelper.kt` model, merge, getter, and focused config tests.
- [x] T003 Add `ascii_mode` to `app/src/main/assets/rime/wubi86_pinyin.schema.yaml`; keep Pinyin length unlimited and remove/avoid conflicting global mixed-schema auto-select/max-length settings.
- [x] T004 Consolidate the accepted fuzzy algebra in `app/src/main/assets/rime/pinyin_simp.schema.yaml`, preserving long-final rule order, and add a focused rule-presence/order test.
- [x] T005 Update `app/src/main/assets/default.custom.yaml` to make left Shift `noop`, right Shift `commit_code`, add persistent `Ctrl+period → ascii_punct`, and retain `;`, `'`, `[`, `]` candidate bindings.
- [x] T006 Extend `RimeConfigHelper.patchDefaultCustomContent()` and `DefaultCustomYamlTest.kt` so existing user `default.custom.yaml` deterministically receives the Xime-owned accepted bindings while preserving `schema_list` and configured `page_size`.

## Phase 2: Presence-Aware State And Key Dispatch

- [x] T007 Change user-config boolean access in `rime_jni.cc` and `RimeEngine.kt` to represent missing versus explicit `false`; add focused tests for the Kotlin decision logic.
- [x] T008 Update `ImeSessionController.restorePersistedSchemaOptions()` to restore existing option values and otherwise apply the effective first-use `wubi86_pinyin` default without forcing it on later sessions.
- [x] T009 Add explicit handled/unhandled/unavailable Rime key-dispatch types and an ordered lock-safe input API in `RimeEngine.kt`/`rime_jni.cc`; keep non-blocking APIs only for UI snapshots.
- [x] T010 Centralize composition-sensitive physical keys in `ImeKeyRouter.kt` and route Backspace, Forward Delete, Left/Right, Up/Down, Home/End, Page Up/Page Down, Space, Enter/Numpad Enter, and Escape through Rime before host fallback.
- [x] T011 Route soft-keyboard Backspace, Enter, Space, cursor gestures, and toolbar Home/End/directions through the same composition-aware behavior while preserving explicit quick-send, English pending-text, T9, number/symbol, handwriting, voice, and calculator ownership.
- [x] T012 Remove composition/host-fallback decisions that depend only on `candidateState` timing; ensure `Unavailable` never triggers host deletion, movement, editor action, or newline.
- [ ] T013 Add focused routing tests for immediate `letter → Backspace/Enter/Space/navigation`, no duplicate commit, no fallback after handled events, and no destructive fallback for unavailable/busy dispatch.

## Phase 3: Candidate Source, Annotation, And Wubi Rules

- [x] T014 Extend native/Kotlin candidate result models with genuine source type and optional full-Wubi-code/display metadata without changing candidate text or engine index.
- [x] T015 Add a cached `wubi86` reverse lookup in `rime_jni.cc`; normalize candidate codes and select longest code with dictionary-order tie-break. Add a focused pure/helper test including `发 → ntcy`.
- [x] T016 Populate code metadata only for genuine `reverse_lookup` candidates in `wubi86_pinyin`; leave table/completion/punctuation/Lua/T9/plugin candidates undecorated and preserve metadata for plugin engine-reference items.
- [x] T017 Render `文字(完整五笔码)` independently of ordinary comment visibility in `CandidateBar.kt` and `HardwareKeyboardCandidateBar.kt`; keep selected/committed text undecorated. Add focused display-model tests.
- [x] T018 Implement native Xime-scoped four-key uniqueness: after the fourth letter, count exact Wubi table-origin candidates only and commit only when exactly one exists.
- [x] T019 Implement fifth-key behavior: before a fifth letter, commit the current first candidate only when it is exact Wubi table-origin; then retain/process the fifth letter as new raw input. Otherwise continue the existing Pinyin composition.
- [ ] T020 Add minimal native/instrumented regression coverage for unique/ambiguous four-key Wubi, fifth-key Wubi split, Pinyin-origin fifth-key continuation, and long Pinyin such as `jiang`/`guang`.

## Phase 4: Validation And Convergence

- [x] T021 Run focused JVM tests for changed config, key mapping/routing, state restoration, candidate metadata/display, and fuzzy algebra; fix failures and rerun.
- [x] T022 Run `./gradlew :app:testDebugUnitTest`.
- [x] T023 Run `./gradlew assembleDebug` to compile Kotlin, JNI, Lua integration, and bundled schema assets.
- [ ] T024 Deploy on Android and execute the physical-keyboard matrix from `.supermax/specs/wubi-pinyin-input/spec.md`, including immediate key sequences, both candidate surfaces, all fuzzy pairs, `发(ntcy)`, and Pinyin non-truncation.
- [ ] T025 Check spec/design/tasks/code consistency and record exact automated/manual validation evidence in change frontmatter.
- [ ] T026 Promote `.supermax/specs/wubi-pinyin-input/spec.md` to `authority: normative`, `status: active` only after implementation and validation converge.
- [ ] T027 Mark proposal/design/tasks/delta `status: accepted`, add `accepted_at`, `merged_to`, validation results, and `archive_state: retained`; keep this workspace in place.
- [ ] T028 Update `.supermax/specs/index.md` and `.supermax/specs/log.md` with final lifecycle and validation status.

## Dependencies

- T001 precedes every implementation edit.
- T002–T008 establish configuration/state semantics before key-routing validation.
- T009 precedes T010–T013 and T018–T020.
- T014–T016 precede T017–T020.
- T018 and T019 share the source-aware candidate inspection helper but must satisfy separate acceptance cases.
- T021–T024 precede T025–T028.

## Validation

- Focused tests named by each task.
- `./gradlew :app:testDebugUnitTest`
- `./gradlew assembleDebug`
- Android physical-keyboard manual matrix in the stable spec.
- Expected result: all automated checks pass; every manual acceptance case is recorded as passed before promotion/acceptance.
