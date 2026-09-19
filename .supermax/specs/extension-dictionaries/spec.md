---
title: 扩展词库聚合与五笔候选优先级规格
created: 2026-09-19
updated: 2026-09-19
type: source
doc_role: spec
authority: normative
status: active
taskadmin_tag: master
taskadmin_id: 2
sources:
  - "User-reported Wubi candidate regression and fix authorization on 2026-09-19"
  - "app/src/main/java/com/kingzcheung/xime/settings/ExtensionDictionaryManager.kt"
  - "app/src/main/java/com/kingzcheung/xime/settings/ExtensionDictionaryConverter.kt"
  - "app/src/main/assets/rime/wubi86.dict.yaml"
  - "app/src/main/assets/rime/wubi86_pinyin.schema.yaml"
  - "app/src/main/jni/librime/src/rime/dict/entry_collector.cc"
  - "app/src/main/jni/librime/src/rime/dict/dict_compiler.cc"
  - "app/src/main/jni/librime/src/rime/dict/vocabulary.cc"
  - "app/src/test/java/com/kingzcheung/xime/settings/ExtensionDictionaryCatalogTest.kt"
derived_to: []
confidence: high
---

> **TLDR**: Xime SHALL preserve source frequencies for Pinyin extension words, but SHALL feed Wubi extension words through a separate unweighted dictionary imported after the built-in Wubi86 tables, so enabling extension dictionaries cannot displace original Wubi candidates solely because of external frequency values.

## Purpose

Govern how Xime materializes enabled extension dictionaries for Pinyin and `wubi86_pinyin`, with emphasis on preserving the original Wubi86 candidate order and four-/five-key commit behavior while retaining useful source frequencies for Pinyin.

## Scope

### In Scope

- Generated aggregate dictionaries used by `pinyin_simp`, `t9_pinyin`, and `wubi86_pinyin`.
- Separation of weighted Pinyin extension entries from unweighted Wubi extension entries.
- Import order and candidate-priority guarantees for `wubi86` and `wubi86_extra`.
- Regeneration and deployment when extension dictionaries are enabled, disabled, missing, or repaired after upgrade.
- Failure behavior when generated dictionaries or Rime deployment cannot be completed.

### Out Of Scope

- The approved extension dictionary catalog, source licensing, download endpoint selection, or UI presentation.
- Changes to source entries or intrinsic weights inside `wubi86`, `wubi86_extra`, `pinyin_simp`, or `pinyin_simp_ext`.
- User-dictionary learning, explicitly learned candidate priority, or personal dictionary migration.
- General candidate rendering, plugin candidate transforms, handwriting, voice input, or unrelated IME routing.

## Terms And Ownership

- **Pinyin active dictionary**: generated `xime_extension_words.dict.yaml`, containing enabled extension words and retained source weights.
- **Wubi active dictionary**: generated `xime_extension_words_wubi.dict.yaml`, containing the same enabled extension word set without external weights or explicit codes.
- **Pinyin aggregate**: generated `xime_pinyin.dict.yaml` used by Pinyin translators and Pinyin reverse lookup.
- **Wubi aggregate**: generated `xime_wubi86.dict.yaml` used by the table translator in `wubi86_pinyin`.
- **Base Wubi candidates**: entries originating from `wubi86` or `wubi86_extra` before generated extension entries are considered.
- `ExtensionDictionaryManager` owns generated active/aggregate sources and deployment coordination. The bundled `wubi86*.dict.yaml` sources remain separately owned and SHALL NOT be rewritten by extension dictionary operations.

## Requirements

### Requirement: Mode-Specific Active Dictionaries

The system SHALL materialize enabled extension words into separate Pinyin and Wubi active dictionaries.

The Pinyin active dictionary SHALL retain normalized source weights and SHALL declare a `text` and `weight` representation suitable for weight-based Pinyin ranking.

The Wubi active dictionary SHALL contain only text entries. It SHALL NOT carry extension source weights into Wubi candidate ranking and SHALL allow the Wubi aggregate encoder to derive Wubi phrase codes from the base character table.

#### Scenario: Weighted extension word is enabled

- GIVEN an enabled extension source contains `人工智能` with weight `998`
- WHEN Xime rebuilds managed dictionaries
- THEN the Pinyin active dictionary contains the word with weight `998`
- AND the Wubi active dictionary contains the word without that weight
- AND both generated dictionaries represent the same enabled extension set

#### Scenario: Word-only source has a synthesized Pinyin weight

- GIVEN an enabled source does not provide a usable frequency
- WHEN the source is converted and aggregated
- THEN the Pinyin active dictionary may use the managed default weight
- AND the Wubi active dictionary still omits that weight

### Requirement: Preserve Base Wubi Candidate Priority

The Wubi aggregate SHALL import sources in this order:

1. `wubi86`
2. `wubi86_extra`
3. `xime_extension_words_wubi`

The system SHALL NOT physically overwrite `wubi86.dict.yaml` or `wubi86_extra.dict.yaml` when extension dictionaries are enabled or disabled.

An extension word SHALL NOT move ahead of a base Wubi candidate solely because its external dictionary supplied a larger frequency. For equal compiled weights and matching codes, the declared import order SHALL preserve the base-source occurrence before the extension occurrence.

The Wubi aggregate SHALL retain the existing encoder rules and aggregate `sort: by_weight` behavior so intrinsic `wubi86_extra` weights continue to work.

#### Scenario: High-frequency extension entry collides with a base Wubi code

- GIVEN a base Wubi candidate and an extension phrase compile to the same input code
- AND the extension source supplied a positive or very large frequency
- WHEN `wubi86_pinyin` produces table candidates
- THEN the external frequency does not move the extension phrase ahead of the base candidate
- AND ordinary selection, unique four-code commit, and fifth-key commit are not changed merely by that external frequency

#### Scenario: Existing Wubi extra entry has an intrinsic weight

- GIVEN `wubi86_extra` defines an explicit intrinsic weight
- WHEN the Wubi aggregate is compiled
- THEN the aggregate continues to use weight-based ordering for that built-in source
- AND Xime does not replace the aggregate with global `sort: original`

### Requirement: Preserve Pinyin Frequency Ranking

The Pinyin aggregate SHALL continue to import `pinyin_simp`, `pinyin_simp_ext`, and the weighted Pinyin active dictionary. The Wubi-specific weight suppression SHALL NOT remove or flatten extension frequencies used by standalone Pinyin, T9 Pinyin, or Pinyin reverse lookup in mixed input.

#### Scenario: Same extension set serves Pinyin and Wubi

- GIVEN one or more extension dictionaries are enabled
- WHEN Pinyin and `wubi86_pinyin` are deployed
- THEN Pinyin lookup can rank extension words using retained source frequencies
- AND the Wubi table path consumes the unweighted representation

### Requirement: Synchronized Regeneration And Deployment

Enabling or disabling an extension dictionary SHALL rebuild both active dictionaries from the same validated enabled-ID set before Rime deployment. Missing generated active files or an enabled marker mismatch in either file SHALL trigger regeneration of both files.

Changes to generated `*.dict.yaml` files SHALL participate in Xime's deployment hash so the next deployment recompiles affected Rime dictionaries.

The persisted enabled-ID set SHALL be updated only after managed sources are written and Rime deployment succeeds.

#### Scenario: First upgrade to split active dictionaries

- GIVEN an installation has the legacy Pinyin active dictionary but no Wubi active dictionary
- WHEN Xime initializes managed artifacts
- THEN it generates both current active dictionaries
- AND the changed dictionary set causes Rime deployment maintenance

#### Scenario: Extension dictionary is disabled

- GIVEN an extension dictionary is currently enabled
- WHEN the user disables it
- THEN both active dictionaries are rebuilt without its words
- AND Rime is deployed before the new enabled-ID set is persisted

#### Scenario: No extension dictionary is enabled

- GIVEN the validated enabled-ID set is empty
- WHEN managed artifacts are ensured
- THEN both active dictionary files remain valid, compilable dictionaries with no extension entries
- AND the Wubi aggregate still imports the built-in Wubi sources in their declared order

### Requirement: Deployment Failure Preserves Previous State

If dictionary generation or Rime deployment fails during an enable/disable operation, the operation SHALL report failure and SHALL NOT persist the requested enabled-ID change. Xime SHOULD restore generated dictionaries for the previous enabled-ID set and attempt to redeploy that previous state.

#### Scenario: Rime deployment fails after regeneration

- GIVEN a previously deployed extension set
- WHEN a requested enable/disable operation regenerates files but Rime deployment fails
- THEN the operation returns an unsuccessful result
- AND persisted enabled IDs remain unchanged
- AND Xime attempts to restore and deploy managed dictionaries for the previous set

## Edge Cases And Failure Behavior

- Extension entries remain limited to converted multi-character Han words; malformed or unsupported source rows are excluded before active dictionary generation.
- Duplicate extension words are emitted once. Existing catalog order determines which normalized source weight is retained for the Pinyin active dictionary.
- A Wubi extension phrase that cannot be encoded from available base character codes may be rejected by Rime compilation; it SHALL NOT cause base dictionary files to be rewritten.
- A missing or stale generated dictionary is repairable state, not evidence that the bundled base dictionary was deleted.
- Automated source-format tests do not replace a real-device typing check; human validation remains optional unless a release process separately requires it.

## Data / Entity Constraints

- `xime_extension_words.dict.yaml` columns: `text`, `weight`.
- `xime_extension_words_wubi.dict.yaml` columns: `text` only.
- Enabled markers SHALL be derived from sorted enabled IDs so generation checks are deterministic.
- Both active dictionaries SHALL be written from one deduplicated word set during an operation.
- The Wubi aggregate SHALL reference `xime_extension_words_wubi`, not `xime_extension_words`.
- The Pinyin aggregate SHALL reference `xime_extension_words`.

## Behavior Coverage

| Behavior Surface | Normal Behavior | Authorization / Actors | State / Data Effects | Failure / Edge Cases | External Dependencies | Concurrency / Idempotency | Validation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Pinyin active dictionary | covered | user enables catalog entry | weighted generated entries | malformed/default weights covered | Rime dictionary compiler | operation mutex; deterministic rebuild | covered |
| Wubi active dictionary | covered | user enables catalog entry | unweighted generated entries | unencodable phrases documented | Wubi encoder and base tables | operation mutex; deterministic rebuild | covered |
| Wubi candidate priority | covered | n/a | base imports precede extension import | weight collision covered | Rime stable weight sort | rebuild is idempotent for one enabled set | covered |
| Enable/disable deployment | covered | user action or startup repair | both files and enabled IDs | rollback requirement covered | Rime deployment | serialized by manager mutex | covered |
| Real-device typing experience | partial | end user | no additional persisted state | device/IME integration not exercised | Android device and Rime runtime | n/a | partial |

## Coverage Gaps

- No real-device manual typing evidence is recorded for the split active-dictionary implementation.
- Current automated coverage verifies generated format and aggregate references; it does not compile a synthetic collision through an instrumented Rime deployment.

## Acceptance Criteria

- Pinyin active entries preserve normalized source weights.
- Wubi active entries omit external source weights and explicit source codes.
- `xime_wubi86` imports `wubi86`, `wubi86_extra`, then `xime_extension_words_wubi`.
- `xime_wubi86` does not import the weighted `xime_extension_words` dictionary.
- Enabling or disabling extensions rebuilds both generated active dictionaries from one enabled set and deploys before persisting the new state.
- Targeted extension dictionary tests, the complete Android unit-test suite, and `git diff --check` pass.

## Validation Plan And Evidence

- Passed: `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew :app:testDebugUnitTest --tests com.kingzcheung.xime.settings.ExtensionDictionaryCatalogTest`
- Passed: `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./run_tests.sh unit`
- Passed before specification publication: `git diff --check`
- Not run: real-device `wubi86_pinyin` collision and four-/five-key typing check.

## Assumptions And Open Questions

- Assumption: Rime continues to apply stable ordering among equal-weight homophones, as implemented by the vendored `Vocabulary::SortHomophones()` path.
- Assumption: Existing deployment-hash coverage of all top-level `*.dict.yaml` files remains active.
- Open question: none blocking this contract. A future release gate may require the optional real-device collision scenario.

## Source Trace

- User report: enabling extension dictionaries caused original Wubi86 candidates to appear overwritten or move behind extension candidates in mixed Wubi/Pinyin input.
- Root-cause evidence: `ExtensionDictionaryManager` previously imported the weighted Pinyin active dictionary into `xime_wubi86`; vendored Rime compiles missing Wubi weights as zero and sorts same-code entries by descending weight.
- Current implementation: `app/src/main/java/com/kingzcheung/xime/settings/ExtensionDictionaryManager.kt`.
- Regression coverage: `app/src/test/java/com/kingzcheung/xime/settings/ExtensionDictionaryCatalogTest.kt`.
- Related mixed-input contract: `.supermax/specs/wubi-pinyin-input/spec.md`.
- TaskAdmin task: `master/2`.
