# TODO

## Current State

- Status: complete; ready for TaskAdmin `done`
- Phase: specification convergence complete
- Next action: none.

## Checklist

- [x] Reproduce the ownership cause from current source and Rime sorting behavior.
- [x] Split weighted Pinyin and unweighted Wubi active dictionaries.
- [x] Preserve base Wubi import order and add regression assertions.
- [x] Run targeted and full Android unit tests.
- [x] Publish `.supermax/specs/extension-dictionaries/spec.md` with TaskAdmin identity `master/2`.
- [x] Update `.supermax/specs/index.md` and `.supermax/specs/log.md`.
- [x] Verify spec/task references, formatting and worktree diff.
- [x] Record final convergence and complete the linked task.

## Required Context

- `.supermax/specs/extension-dictionaries/spec.md`
- `.supermax/specs/index.md`
- `.supermax/specs/log.md`
- `app/src/main/java/com/kingzcheung/xime/settings/ExtensionDictionaryManager.kt`
- `app/src/test/java/com/kingzcheung/xime/settings/ExtensionDictionaryCatalogTest.kt`

## Changed Files

| Path | State | Purpose |
| --- | --- | --- |
| `app/src/main/java/com/kingzcheung/xime/settings/ExtensionDictionaryManager.kt` | modified | Split weighted Pinyin and unweighted Wubi generated dictionaries. |
| `app/src/test/java/com/kingzcheung/xime/settings/ExtensionDictionaryCatalogTest.kt` | modified | Assert separate imports and omitted Wubi weights. |
| `.supermax/specs/extension-dictionaries/spec.md` | created | Persist normative behavior contract and evidence. |
| `.supermax/specs/index.md` | modified | Route to the active specification. |
| `.supermax/specs/log.md` | modified | Record specification creation and validation state. |
| `.supermax/tasks/master/task_002/plan.md` | created | Own implementation intent and canonical spec association. |
| `.supermax/tasks/master/task_002/todo.md` | created/updated | Own execution, validation and convergence state. |

## Validation Evidence

| Check | Result |
| --- | --- |
| `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew :app:testDebugUnitTest --tests com.kingzcheung.xime.settings.ExtensionDictionaryCatalogTest` | passed before and after specification publication |
| `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./run_tests.sh unit` | passed |
| `git diff --check` | passed after specification publication |
| Exact plan/spec path, frontmatter identity, index and log checks with `rg` | passed |
| Human Android input check | not run; recorded as a non-blocking coverage gap in the specification |

## Errors And Blockers

- Initial Gradle run failed because `ANDROID_HOME` and `local.properties` were unset; resolved by using existing `$HOME/Android/Sdk` explicitly.
- Windows UNC Gradle fallback failed and is not required after Linux validation passed.
- No remaining blocker.

## Decisions And Findings

- The base Wubi source was not physically overwritten; the generated aggregate imported extension entries with larger weights.
- Pinyin preserves extension source frequencies through `xime_extension_words`.
- Wubi uses `xime_extension_words_wubi` without external weights, imported after `wubi86` and `wubi86_extra`.
- The Wubi aggregate remains `sort: by_weight`; changing it globally to `sort: original` would alter existing `wubi86_extra` behavior.
- `.supermax/specs/extension-dictionaries/spec.md` is the normative stable owner; no proposal/delta workspace is required for this new directly authored capability contract.
- Do not re-investigate unrelated IME candidate UI or plugin transforms.
