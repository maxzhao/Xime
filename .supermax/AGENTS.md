# Xime

> SuperMax project-level supplemental rules. Priority is lower than the main system prompt, Skill Gate, tool-safety rules, and loaded skill instructions.

## Project Scope

- Purpose: develop Xime, an Android input method based on Rime with first-party Lua plugin support and a wireless-import web client.
- Ownership boundary: Android application code, native Rime integration, plugin SDK/runtime, bundled plugins, web client, build logic, tests, and project documentation in this repository.

## Project Root: `<project_dir>`

- Treat the Git top-level directory as `<project_dir>`; do not hardcode a private absolute path in shared artifacts.

## Repository Map And Agent Routing

| Area | Inspect when | Source of truth / boundary |
| --- | --- | --- |
| `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties` | Resolving modules, Gradle plugins, dependency versions, or global build settings | Gradle workspace and version configuration |
| `app/` | Changing the Android IME, UI, services, persistence, or native integration | `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/`, and `app/src/main/jni/`; `app/build/` is generated |
| `plugin-core/` | Changing plugin APIs or shared plugin contracts | `plugin-core/build.gradle.kts` and `plugin-core/src/main/` |
| `plugins/` | Changing bundled Lua plugins | Each plugin's `manifest.yaml` and owned source files; package through `scripts/build-plugins.sh` |
| `web/` | Changing the wireless-import web client | `web/package.json`, `web/src/`, and `web/vite.config.js`; `web/dist/` is generated |
| `scripts/`, `run_tests.sh` | Discovering project automation and validation entrypoints | Scripts are command entrypoints; inspect before running or changing them |
| `app/src/main/jni/librime*`, `app/src/main/jni/snappy`, `app/src/main/assets/rime` | Working on vendored native/Rime content | Git submodules declared by `.gitmodules`; do not treat them as ordinary generated files or rewrite casually |
| `docs/`, `README*.md`, `CONTRIBUTING.md` | Confirming public behavior or contributor-facing contracts | Keep documentation aligned with implemented behavior when the requested change affects those contracts |

## Environment And Stack

- Android application written in Kotlin with Jetpack Compose, Gradle Kotlin DSL, Java/JVM 17, Android SDK, NDK, and CMake.
- Main Android module: `:app`; plugin API module: `:plugin-core`.
- Native input engine integration uses Rime and Git submodules under `app/src/main/jni/` and `app/src/main/assets/rime`.
- Bundled plugins are Lua packages; the wireless-import client under `web/` uses React, Vite, and Tailwind CSS.

## Commands And Validation

- Before shell commands, follow the current session Justfile check rules. This repository currently has no Justfile; inspect again if command entrypoints change.
- Android tests: `./run_tests.sh` or the closest targeted Gradle test task documented by that script.
- Android build: `./gradlew assembleDebug`; use a narrower declared Gradle task when sufficient.
- Lua plugin packages: `bash scripts/build-plugins.sh`.
- Web client: run `npm run build` from `web/` after dependency installation.
- After modifying behavior-affecting artifacts, run the closest relevant existing validation. Do not report unvalidated changes as complete.

## Execution Boundaries

- Preserve user-owned worktree changes and inspect relevant callers and shared boundaries before editing.
- Do not inspect or expose signing credentials. `app/keystore.properties` is local signing configuration, not shared source.
- Treat Git submodule contents and generated paths such as `**/build/` and `web/dist/` according to their ownership; change source/config rather than generated output unless the task explicitly requires artifacts.
- `.supermax/tasks/`: TaskAdmin internal storage. Load `task-admin` before task operations.
- `.supermax/drafts/`: temporary draft directory. It is not durable knowledge, task progress, or runtime source storage.
- `.supermax/`: project knowledge Vault. Read through the entry chain below; do not write task state or temporary logs into knowledge notes.

## Project Knowledge Vault

- External Vault ownership follows `project-admin`: only Git's verified primary checkout or an independent non-Git project may register/use Obsidian or manage Syncthing. Linked Worktrees keep local `.supermax/` files but do not perform external operations. Explicit setup must not silently retarget conflicts.
- `.supermax/` is an independent project-local Obsidian Vault. Required initialized zones are `inbox/`, `specs/`, `wiki/`, `tasks/`, `drafts/`, `translate-cache/`, `attachments/`, and `canvases/`.
- Start retrieval at `.supermax/index.md`, then follow the smallest relevant category index. Capture and feedback review starts at `.supermax/inbox/index.md`.
- Do not create `.supermax/knowledge/` or root `raw/`. Create optional categories such as `ideas/`, `reports/`, `research/`, or `rules/` only when actual project content needs them.
- Syncthing operations are owned by `project-admin`; do not force a scan after ordinary edits. Run a manual scan only when explicitly requested by the user.

## Project Knowledge And Source Entrypoints

- L0: `.supermax/AGENTS.md`
- L1: `.supermax/index.md`
- L1/L2: `.supermax/wiki/index.md`, `.supermax/specs/index.md`; capture/review: `.supermax/inbox/index.md`
- L2: child indexes and selected notes under maintained categories
- Retired: `.supermax/knowledge/` and root `raw/`

## Update Rules

- Update this file only when stable project scope, routing, source-of-truth files, commands, validation, or governance boundaries change.
- Verify new claims against current manifests, build files, scripts, and indexes; use `to be inspected` instead of guessing.
- Keep this file concise and Agent-facing. Store task progress, drafts, research bodies, and one-off logs in their owning workflows or Vault zones.
