# TODO: 回填 init supermax 后的行为规格

## Current State

- Status: done
- Phase: completed
- Next action: none; manual/device/network checks remain explicit draft-promotion gaps, not blockers for this backfill.

## Checklist

- [x] Create/resolve one linked TaskAdmin task and confirm workspace/dependencies/status.
- [x] Build a complete commit-to-capability evidence map for `df0b83d8..HEAD`.
- [x] Deep-read physical keyboard routing, shortcuts, candidate/status UI and tests.
- [x] Deep-read IME restart/focus/initial-candidate lifecycle branches and tests/evidence.
- [x] Deep-read extension dictionary catalog/download/convert/enable/delete/deploy flows and tests.
- [x] Verify ARM64 release build behavior in `justfile` and `app/build.gradle.kts`.
- [x] Reconcile current Wubi/Pinyin code/tests with stable draft and historical delta.
- [x] Write four linked `authority: draft` behavior specs.
- [x] Update Wubi/Pinyin stable draft and accept/retain the historical change workspace.
- [x] Update `.supermax/specs/index.md`, `.supermax/specs/log.md` and stale Justfile routing in `.supermax/AGENTS.md`.
- [x] Run structural checks, JVM unit tests, debug build and ARM64 release-build check.
- [x] Review final diff, record residual gaps, complete spec convergence and mark task done.

## Required Context

- `.supermax/specs/index.md`
- `.supermax/specs/wubi-pinyin-input/spec.md`
- `.supermax/specs/extension-dictionaries/spec.md`
- `.supermax/specs/changes/complete-wubi-pinyin-input/proposal.md`
- `.supermax/specs/changes/complete-wubi-pinyin-input/specs/wubi-pinyin-input/spec.md`
- Git range `df0b83d8d0f42db6126d5ef43d8a3d68b2fd083c..HEAD`

## Changed Files

| Path | State | Notes |
| --- | --- | --- |
| `.supermax/AGENTS.md` | changed | Routes Agents to current `justfile`/`build-release-arm64` |
| `.supermax/specs/physical-keyboard-input/spec.md` | added | General physical-key routing, shortcuts, candidate/status UI |
| `.supermax/specs/ime-session-lifecycle/spec.md` | added | New-target vs `restartInput`, clipboard initialization, cleanup |
| `.supermax/specs/extension-dictionary-management/spec.md` | added | Catalog, conversion, mutations, deploy rollback, settings UI |
| `.supermax/specs/arm64-release-build/spec.md` | added | `xime.abi`, local-install signing, output/signature/digest contract |
| `.supermax/specs/wubi-pinyin-input/spec.md` | changed | Current evidence, task link, validation and ownership boundary |
| `.supermax/specs/changes/complete-wubi-pinyin-input/proposal.md` | changed | Accepted/retained lifecycle and convergence evidence |
| `.supermax/specs/changes/complete-wubi-pinyin-input/specs/wubi-pinyin-input/spec.md` | changed | Accepted/retained lifecycle and merged owner |
| `.supermax/specs/index.md` | changed | Routes all active specs and retained change evidence |
| `.supermax/specs/log.md` | changed | Records this backfill/convergence |
| `.supermax/tasks/master/task_003/_meta.yaml` | changed | TaskAdmin lifecycle status |
| `.supermax/tasks/master/task_003/plan.md` | added/updated | Canonical spec ownership and project-rule correction |
| `.supermax/tasks/master/task_003/todo.md` | added/updated | Final recovery, validation and completion evidence |

## Validation

| Check | Result | Evidence |
| --- | --- | --- |
| `git diff --check` | passed | Final run produced no output |
| Spec lifecycle/index target checks | passed | Both historical change artifacts are `accepted`/`retained`, cache `master/3`, and every indexed target exists |
| Requirement/scenario structure check | passed | Each new spec contains operation-level requirements and scenarios plus coverage/validation/source sections |
| `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew :app:testDebugUnitTest` | passed | `BUILD SUCCESSFUL`, 48 actionable tasks |
| `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew :app:assembleDebug` | passed | `BUILD SUCCESSFUL`, all four configured native ABIs assembled |
| `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" just build-release-arm64` | passed | Release assembly succeeded; `apksigner` v2 verification passed; versioned ARM64 APK path and SHA-256 were printed |
| Physical-device/UI/network-failure matrix | not-run | Explicit coverage gaps remain in draft specs; no normative-authority claim was made |

## Errors And Blockers

- Resolved: the first `./gradlew :app:testDebugUnitTest` attempt failed with `SDK location not found`; rerunning with the existing `$HOME/Android/Sdk` as `ANDROID_HOME` and `ANDROID_SDK_ROOT` passed.
- Optional YAML parser check was unavailable because `ruby` is not installed; scoped frontmatter/lifecycle/task-link checks passed instead.
- No current blockers.

## Decisions And Findings

- `fdcb8032`: general physical-key mapping/routing, Android-layout Unicode, modifiers, editing/special-key fallback, voice shortcut/Space ownership; owner `.supermax/specs/physical-keyboard-input/spec.md`.
- `d81c4cc3`: Wubi/Pinyin behavior and native candidate metadata; owner `.supermax/specs/wubi-pinyin-input/spec.md`, with the accepted retained historical change workspace.
- `19d41aaa`, `8dd2b3a1`: current contract uses `xime.abi`, `xime.localInstall`, versioned split output, exact ARM64 artifact discovery, latest `apksigner` and SHA-256 reporting; owner `.supermax/specs/arm64-release-build/spec.md`.
- `9f367922`: hardware option/status feedback, cursor-anchored candidate placement and restart-input preservation; owners physical-keyboard and IME-session specs.
- `05030c14`: extension catalog/download/conversion/enable/delete UI plus hardware-status refinements; owners extension-management and physical-keyboard specs.
- `d3dbe89c`: weighted Pinyin/unweighted Wubi aggregate behavior remains normative in `.supermax/specs/extension-dictionaries/spec.md`; no duplicate requirements were created.
- All four brownfield owners and refreshed Wubi/Pinyin owner remain `authority: draft` because their recorded manual/device/network coverage gaps are truthful and unresolved.
- Extension aggregation remains separate from extension management; general hardware behavior remains separate from schema-specific Wubi/Pinyin behavior.
- Old SpecAdmin `design.md` and `tasks.md` remain only as retained historical evidence.
