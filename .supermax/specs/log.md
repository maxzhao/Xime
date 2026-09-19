# Specification Maintenance Log

| Date | Action | Result |
| --- | --- | --- |
| 2026-09-12 | Initialized specification entrypoint. | No specifications imported. |
| 2026-09-14 | Added draft `wubi-pinyin-input` stable spec and `complete-wubi-pinyin-input` change workspace after repository exploration and user decisions. | Proposal, design, tasks checklist, and delta spec are ready for review; implementation and validation not run. |
| 2026-09-19 | Added normative `extension-dictionaries` specification linked to TaskAdmin `master/2`. | Captured the validated split between weighted Pinyin and unweighted Wubi extension dictionaries, base Wubi candidate priority, regeneration, deployment, and rollback behavior; automated unit validation passed, human device validation not run. |
| 2026-09-19 | Backfilled post-`df0b83d8` behavior specs through TaskAdmin `master/3`. | Added draft `physical-keyboard-input`, `ime-session-lifecycle`, `extension-dictionary-management`, and `arm64-release-build` owners; refreshed `wubi-pinyin-input`; accepted and retained its historical change workspace after JVM/build validation. Human/device validation remains not run. |
| 2026-09-19 | Added and converged normative `voice-input-host-compatibility` specification linked to TaskAdmin `master/4`. | Implemented capability-based `TYPE_NULL` handling, compact floating-card partial preview, raw-only one-second pause commits, explicit-stop commits, cumulative-result deduplication and command-text preservation. Focused/full JVM tests, signed ARM64 release build and Termux/ordinary-editor human review passed. |
