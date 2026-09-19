---
title: 扩展词典目录、下载与启停管理规格
created: 2026-09-19
updated: 2026-09-19
type: source
doc_role: spec
authority: draft
status: experimental
taskadmin_tag: master
taskadmin_id: 3
sources:
  - git:05030c147707b23210805287e9bbc2481e029a7f
  - app/src/main/java/com/kingzcheung/xime/settings/ExtensionDictionaryCatalog.kt
  - app/src/main/java/com/kingzcheung/xime/settings/ExtensionDictionaryConverter.kt
  - app/src/main/java/com/kingzcheung/xime/settings/ExtensionDictionaryManager.kt
  - app/src/main/java/com/kingzcheung/xime/viewmodel/ExtensionDictionaryViewModel.kt
  - app/src/main/java/com/kingzcheung/xime/ui/settings/ExtensionDictionaryPanel.kt
derived_to:
  - .supermax/specs/extension-dictionaries/spec.md
confidence: high
---

> **TLDR**: Xime SHALL expose a fixed extension-dictionary catalog, download and normalize selected sources, enable or disable them through serialized deploy operations with rollback, and present recoverable busy/error state in Dictionary Settings.

## Purpose

Define the management capability that feeds the generated dictionary behavior owned by `.supermax/specs/extension-dictionaries/spec.md`: catalog definitions, download conversion, local state, UI operations, deployment and failure recovery.

## Scope

### In Scope

- First-party extension dictionary catalog metadata and source candidates.
- Download, mirror fallback and conversion into managed local source files.
- Snapshot state for downloaded/current/enabled/entry-count information.
- Enable, disable and delete operations.
- Regeneration/deployment integration and rollback.
- Dictionary Settings UI busy, progress, action and message behavior.

### Out Of Scope

- Candidate ranking and generated aggregate dictionary format; owned by `.supermax/specs/extension-dictionaries/spec.md`.
- Adding arbitrary user-defined URLs or catalog entries.
- Background auto-update scheduling.
- Third-party source licensing beyond exposing catalog metadata and license URLs.
- General Rime deployment behavior unrelated to extension dictionaries.

## Actors And Triggers

- Actor: a user opening Dictionary Settings and downloading, enabling, disabling or deleting a catalog entry.
- System actor: Xime initializes managed artifacts before/while Rime is available.
- External actors: the catalog's remote source and fallback mirror hosts.

## Requirements

### Requirement: Catalog Definitions Are Fixed And Reviewable

The system SHALL expose only entries declared by `ExtensionDictionaryCatalog`. Each definition SHALL have a stable ID, display name/description resource, source URL, declared input format, extension type (`PINYIN` or `WUBI`), default weight, license name/URL and source-signature marker used to determine whether a local download is current.

#### Scenario: Settings opens

- GIVEN the application catalog is available
- WHEN the dictionary management panel loads
- THEN one snapshot SHALL be produced for every declared definition in catalog order

#### Scenario: Unknown dictionary ID is requested

- GIVEN an operation receives an ID not present in the catalog
- WHEN the manager resolves the definition
- THEN the operation SHALL fail with an unsuccessful result and SHALL NOT mutate enabled/downloaded state

### Requirement: Snapshot Separates Downloaded, Current And Enabled State

For every catalog entry, the system SHALL report independently whether a managed source file exists, whether it matches the current source signature, whether its ID is enabled, and how many normalized entries were downloaded when that count is available.

#### Scenario: Catalog metadata changes after a prior download

- GIVEN a local file exists with an older source signature
- WHEN snapshots are refreshed
- THEN the entry SHALL remain `downloaded=true` but SHALL report `isCurrent=false`

#### Scenario: Enabled marker exists without a current download

- GIVEN persisted enabled IDs include an entry whose local source is missing or stale
- WHEN the snapshot is produced
- THEN enabled state SHALL remain observable while enable/regeneration logic determines whether a fresh download is required

### Requirement: Download Uses Ordered Source Fallbacks

A download SHALL try the definition's original source URL and any deterministic proxy/mirror candidates returned for that URL in order, without duplicate candidates. Failure of one candidate SHALL advance to the next. The operation SHALL fail only after every candidate fails or conversion yields no accepted entries.

#### Scenario: Primary source succeeds

- GIVEN the original URL returns readable source content
- WHEN download is requested
- THEN Xime SHALL convert that response and SHALL not require a mirror

#### Scenario: Primary source fails

- GIVEN the original URL throws or returns unusable data
- WHEN a configured fallback candidate succeeds
- THEN the operation SHALL complete from the fallback and expose the same managed local result

#### Scenario: All candidates fail

- GIVEN every candidate fails to download or convert
- WHEN the operation completes
- THEN it SHALL report failure, remove temporary partial output, preserve the previous managed source if any, and leave active enablement unchanged

### Requirement: Download Conversion Accepts Only Valid Han Entries

The converter SHALL parse the definition's declared format: Rime tabular dictionary, space-separated frequency, tab-separated frequency, or word-only. It SHALL ignore blank/comment/header lines, reject non-pure-Han words, normalize missing or invalid/non-positive frequency to the catalog default, write accepted entries as `word<TAB>weight`, and record accepted/rejected counts.

#### Scenario: Mixed valid and invalid source rows

- GIVEN source data contains comments, pure-Han words, non-Han words and invalid frequency fields
- WHEN conversion runs
- THEN only pure-Han words SHALL be emitted, invalid/non-positive frequency SHALL use the default weight, and rejected rows SHALL contribute to the rejected count

#### Scenario: No valid entries remain

- GIVEN conversion accepts zero entries
- WHEN download finalization runs
- THEN the download SHALL fail and SHALL NOT replace a previously valid managed source

### Requirement: Download Replacement Is Staged

The manager SHALL stream conversion into a temporary file and replace the managed source only after successful conversion. A successful replacement SHALL update the source signature and downloaded entry count used by snapshots. Download alone SHALL NOT enable the dictionary or deploy Rime.

#### Scenario: Conversion succeeds

- GIVEN at least one row is accepted
- WHEN temporary conversion completes
- THEN Xime SHALL replace the managed source, persist its current signature/count and refresh snapshots while preserving existing enabled IDs

#### Scenario: Conversion fails after an older download exists

- GIVEN a valid managed source already exists
- WHEN the new download fails
- THEN the older source SHALL remain available and no partial file SHALL be exposed as current

### Requirement: Enable Ensures A Current Download

Enabling an entry SHALL first ensure its managed source is current. If the file is missing or stale, Xime SHALL download/convert it before changing the enabled set. The operation SHALL then regenerate active managed dictionaries, persist the new enabled-ID set and deploy Rime.

#### Scenario: Enable a current download

- GIVEN the selected entry has a current managed source
- WHEN the user enables it
- THEN Xime SHALL regenerate active artifacts including that ID, save the enabled set and deploy

#### Scenario: Enable a missing or stale download

- GIVEN the selected entry is missing or not current
- WHEN the user enables it
- THEN Xime SHALL obtain a current source first; failed download SHALL abort enablement without changing the prior active set

### Requirement: Disable Regenerates And Deploys Without Deleting Source

Disabling an entry SHALL remove its ID from the enabled set, regenerate active dictionaries from the remaining IDs, persist the new set and deploy Rime. It SHALL retain the downloaded managed source so the entry can be enabled again without re-download while its signature remains current.

#### Scenario: User disables an active dictionary

- GIVEN an enabled entry has a downloaded source
- WHEN the user turns its switch off
- THEN it SHALL disappear from generated active content after deployment but remain `downloaded=true`

### Requirement: Failed Enable Or Disable Rolls Back Active State

If regeneration, preference persistence or deployment fails during enable/disable, Xime SHALL attempt to regenerate and deploy the previous enabled set, restore the previous preference set, refresh snapshots and return an unsuccessful result. Rollback failure SHALL be included in the returned error rather than hidden.

#### Scenario: Deployment fails after active files changed

- GIVEN the prior enabled set is known
- WHEN deployment of the requested set fails
- THEN Xime SHALL restore files/preferences for the prior set, redeploy that set when possible, and report the requested operation as failed

#### Scenario: Rollback also fails

- GIVEN the requested operation fails and restoration throws
- WHEN the result is produced
- THEN the user-visible error SHALL include both the original failure and rollback failure, and the spec SHALL not claim state convergence

### Requirement: Delete Is Allowed Only While Disabled

Deleting a managed download SHALL be rejected while the entry is enabled. For a disabled entry, deletion SHALL remove the managed source and its signature/count metadata, refresh snapshots and leave other entries unchanged.

#### Scenario: Delete disabled download

- GIVEN an entry is downloaded and disabled
- WHEN the user confirms deletion
- THEN the local managed source and its download metadata SHALL be removed

#### Scenario: Delete enabled download

- GIVEN an entry is enabled
- WHEN delete is requested through any caller
- THEN the operation SHALL fail and SHALL preserve both source and enabled state

### Requirement: Mutating Operations Are Serialized

Download, enable/disable and delete operations SHALL execute under one manager-level mutex. Concurrent callers SHALL not interleave managed-file replacement, enabled-set persistence or deployment. Snapshot reads MAY occur independently and SHALL reflect the latest completed persistent state.

#### Scenario: Two mutations are requested concurrently

- GIVEN one dictionary operation is running
- WHEN another mutating operation starts
- THEN the second operation SHALL wait for the mutex rather than racing file or preference updates

### Requirement: Initialization Repairs Managed Artifacts Idempotently

`ensureArtifacts` SHALL initialize Rime access, regenerate active dictionaries from persisted enabled IDs, ensure required aggregate source files and schema patch values exist, and avoid rewriting files whose content is already identical. Repeated calls with unchanged inputs SHALL preserve equivalent content.

#### Scenario: Required aggregate/patch file is missing

- GIVEN an enabled set exists but a managed aggregate or schema patch is absent
- WHEN artifact initialization runs
- THEN Xime SHALL recreate the required artifact before Rime uses the configuration

#### Scenario: Artifacts are already current

- GIVEN generated content and patch values already match
- WHEN initialization repeats
- THEN Xime SHALL avoid unnecessary file replacement while preserving behavior

### Requirement: Settings UI Exposes Recoverable Operation State

Dictionary Settings SHALL show each entry's description, source/license metadata, downloaded/current/enabled state and downloaded entry count when known. Undownloaded entries SHALL offer download; downloaded entries SHALL offer an enable switch; disabled downloaded entries SHALL offer delete. During an operation, the owning entry SHALL show progress and all entry actions SHALL be disabled until the operation completes. The resulting success/error message SHALL be dismissible.

#### Scenario: Download is running

- GIVEN the user starts a download
- WHEN progress is reported
- THEN the matching card SHALL show progress, the UI SHALL expose that dictionary as busy, and no second card action SHALL start concurrently

#### Scenario: Operation completes

- GIVEN a download, toggle or delete operation returns
- WHEN the ViewModel handles the result
- THEN it SHALL clear busy state, refresh snapshots and expose the localized success/error message for dismissal

## Edge Cases And Failure Behavior

- Network, conversion, filesystem and Rime deployment failures SHALL return unsuccessful operation results instead of crashing the settings flow.
- Temporary download files SHALL be removed after success or failure.
- Empty/invalid source content SHALL not replace a valid prior managed file.
- Unknown catalog IDs and deletion of enabled entries SHALL not mutate state.
- A stale download is not equivalent to a current download; enabling it triggers refresh.

## Data / Entity Constraints

- Catalog IDs are stable persistence keys in `extension_dictionary_prefs/enabled_ids`.
- Managed source files reside under the application files directory `extension-dictionaries/<id>.txt`.
- Currentness is defined by the catalog source signature, not merely file existence.
- Normalized source lines are `word<TAB>positive-weight`; words SHALL contain only Han script characters.
- UI supports one busy dictionary operation at a time; the manager independently serializes all mutations.
- Generated aggregation/ranking constraints are normative in `.supermax/specs/extension-dictionaries/spec.md`.

## Behavior Coverage

| Behavior Surface | Normal Behavior | Authorization / Actors | State / Data Effects | Failure / Edge Cases | External Dependencies | Concurrency / Idempotency | Validation |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Catalog/snapshots | covered | settings user/system | covered | covered | string resources | read-only | catalog unit tests |
| Download/conversion | covered | settings user/enable flow | covered | covered | HTTP sources/mirrors, filesystem | mutex + staged replacement | converter tests; network manual pending |
| Enable/disable/deploy | covered | settings user | covered | rollback covered | Rime deployment | mutex serialized | source review; integration pending |
| Delete | covered | settings user | covered | covered | filesystem | mutex serialized | source review |
| Initialization repair | covered | application/Rime init | covered | partial | filesystem/Rime | content-idempotent writes | aggregate/patch unit tests |
| Settings UI | covered | settings user | covered | covered | Compose/ViewModel | one busy operation | source review; UI manual pending |

## Coverage Gaps

- No automated manager test injects network/filesystem/deployment failures to verify full rollback and message text.
- No UI test verifies progress, global action disabling, message dismissal or license-link behavior.
- Remote source availability and license contents are external and were not fetched during this extraction.

## Acceptance Criteria

- Every catalog entry exposes stable, reviewable metadata and distinct downloaded/current/enabled state.
- Download conversion never publishes zero-entry or partial output and preserves prior valid data on failure.
- Enable/disable deployment either converges to the requested set or attempts explicit rollback to the previous set with truthful error reporting.
- Enabled downloads cannot be deleted; disabled downloads can be removed without affecting peers.
- Concurrent mutations cannot interleave managed state.
- Settings presents deterministic actions, progress and recoverable results.

## Validation Plan

- Run `./gradlew :app:testDebugUnitTest` and verify at least:
  - `ExtensionDictionaryCatalogTest`
  - `ExtensionDictionaryConverterTest`
- Run `./gradlew :app:assembleDebug` to validate manager/ViewModel/Compose integration.
- Manually test one successful and one failed download, enable, disable, delete, app restart and Rime redeploy.
- Add future injected-dependency tests for mirror exhaustion, failed replacement, failed deploy and failed rollback.

## Assumptions And Open Questions

- Assumption: application-private files/preferences provide the authorization boundary; no other app can invoke this manager directly.
- Open question: network, device storage and native deployment failure paths lack automated end-to-end coverage; keep `authority: draft`.

## Source Trace

- `app/src/main/java/com/kingzcheung/xime/settings/ExtensionDictionaryCatalog.kt`: fixed catalog definitions, formats, types and source signatures.
- `app/src/main/java/com/kingzcheung/xime/settings/ExtensionDictionaryConverter.kt`: parser, Han-only validation and weight normalization.
- `app/src/main/java/com/kingzcheung/xime/settings/ExtensionDictionaryManager.kt`: snapshots, source fallback, staged files, mutex, enabled-state deployment, rollback, deletion and artifact repair.
- `app/src/main/java/com/kingzcheung/xime/viewmodel/ExtensionDictionaryViewModel.kt`: busy/progress/result state and snapshot refresh.
- `app/src/main/java/com/kingzcheung/xime/ui/settings/ExtensionDictionaryPanel.kt` and `DictionarySettingsScreen.kt`: user-visible management actions.
- Tests: `app/src/test/java/com/kingzcheung/xime/settings/ExtensionDictionaryCatalogTest.kt`, `ExtensionDictionaryConverterTest.kt`.
- Related normative owner: `.supermax/specs/extension-dictionaries/spec.md`.
- Commit evidence: `05030c14`, with aggregation follow-up `d3dbe89c` owned by the related normative spec.
- Validation evidence: `./gradlew :app:testDebugUnitTest` and `./gradlew :app:assembleDebug` passed on 2026-09-19; network/deployment/UI manual validation remains not run.
- TaskAdmin task: `master/3`.
