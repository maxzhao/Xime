# Specification Maintenance Log

| Date | Action | Result |
| --- | --- | --- |
| 2026-09-12 | Initialized specification entrypoint. | No specifications imported. |
| 2026-09-14 | Added draft `wubi-pinyin-input` stable spec and `complete-wubi-pinyin-input` change workspace after repository exploration and user decisions. | Proposal, design, tasks checklist, and delta spec are ready for review; implementation and validation not run. |
| 2026-09-19 | Added normative `extension-dictionaries` specification linked to TaskAdmin `master/2`. | Captured the validated split between weighted Pinyin and unweighted Wubi extension dictionaries, base Wubi candidate priority, regeneration, deployment, and rollback behavior; automated unit validation passed, human device validation not run. |
| 2026-09-19 | Backfilled post-`df0b83d8` behavior specs through TaskAdmin `master/3`. | Added draft `physical-keyboard-input`, `ime-session-lifecycle`, `extension-dictionary-management`, and `arm64-release-build` owners; refreshed `wubi-pinyin-input`; accepted and retained its historical change workspace after JVM/build validation. Human/device validation remains not run. |
