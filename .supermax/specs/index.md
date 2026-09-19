# Specifications

Purpose: route to verified behavior specifications and spec-driven change artifacts.

## Active Specifications

- [`arm64-release-build/spec.md`](arm64-release-build/spec.md): draft engineering contract for `targetAbi`, local ARM64 release assembly, artifact discovery, ABI checks and signature verification.
- [`extension-dictionaries/spec.md`](extension-dictionaries/spec.md): normative contract for weighted Pinyin extensions, unweighted Wubi extensions, base Wubi candidate priority, synchronized regeneration, and deployment rollback.
- [`extension-dictionary-management/spec.md`](extension-dictionary-management/spec.md): draft contract for the fixed catalog, download conversion, enable/disable/delete operations, serialized deployment and settings UI.
- [`ime-session-lifecycle/spec.md`](ime-session-lifecycle/spec.md): draft contract for new-target initialization, same-target `restartInput` preservation, recent-clipboard initialization and session cleanup.
- [`physical-keyboard-input/spec.md`](physical-keyboard-input/spec.md): draft contract for physical-key translation/routing, candidate shortcuts, mode and voice shortcuts, cursor-anchored candidates and transient status.
- [`wubi-pinyin-input/spec.md`](wubi-pinyin-input/spec.md): draft behavior contract for `wubi86_pinyin` default mode, source-aware Wubi/Pinyin behavior, candidate annotation and composition editing.

## Retained Change Evidence

- [`changes/complete-wubi-pinyin-input/proposal.md`](changes/complete-wubi-pinyin-input/proposal.md): accepted change merged into `wubi-pinyin-input/spec.md`; device/manual validation remains not run, so the stable owner remains draft. Historical `design.md` and `tasks.md` are retained as evidence, not current workflow templates.

## Routing Rules

- Add specifications only through the applicable spec workflow.
- Summarize scope, status, source-of-truth paths, and validation entrypoints here.
- Record curation or archival changes in `log.md`.
