# Plan: 回填 init supermax 后的行为规格

## Goal

将 Git 基线 `df0b83d8d0f42db6126d5ef43d8a3d68b2fd083c`（`init supermax`）之后至当前 `HEAD` 的新增和修改行为提取为可审查、可验证的草案规格，并收敛已有五笔拼音变更工作区。

## Observable Outcome

- 新增并索引以下草案规格：
  - `.supermax/specs/physical-keyboard-input/spec.md`
  - `.supermax/specs/ime-session-lifecycle/spec.md`
  - `.supermax/specs/extension-dictionary-management/spec.md`
  - `.supermax/specs/arm64-release-build/spec.md`
- 更新 `.supermax/specs/wubi-pinyin-input/spec.md` 的当前证据、任务关联和验证状态。
- 收敛 `.supermax/specs/changes/complete-wubi-pinyin-input/` 的 proposal/delta 生命周期；保留旧 `design.md`、`tasks.md` 作为历史证据，不创建新的同类文件。
- Update `.supermax/specs/index.md` and `.supermax/specs/log.md`.
- Correct the stale `.supermax/AGENTS.md` command-routing claim now that the repository owns a `justfile`.

## Scope

### In Scope

- `df0b83d8..HEAD` 七个提交中的实体键盘映射与路由、候选/模式 UI、IME 会话重启与焦点切换、五笔拼音行为、扩展词典管理与聚合、ARM64 发布构建入口。
- 每项规格的 actors/triggers、正常行为、状态效果、失败/边界、并发/幂等、配置影响、验证和源码证据。
- 仅依据当前代码、测试、构建脚本、现有规格与已提交 TaskAdmin 证据。

### Out Of Scope

- 修改应用、构建或测试代码。
- 推测未被实现或测试支持的产品行为。
- 将旧式 SpecAdmin `design.md`/`tasks.md` 迁移为新模板。
- 将尚未人工体验验证的行为提升为 `authority: normative`。

## Authoritative Inputs

- Git range: `df0b83d8d0f42db6126d5ef43d8a3d68b2fd083c..HEAD`
- Project rules: current chat project supplement and `.supermax/index.md`
- Existing specs: `.supermax/specs/wubi-pinyin-input/spec.md`, `.supermax/specs/extension-dictionaries/spec.md`
- Existing change workspace: `.supermax/specs/changes/complete-wubi-pinyin-input/`
- Relevant source/tests under `app/src/main/`, `app/src/test/`, `app/src/androidTest/`, `app/src/test/cpp/`
- Build contract: `justfile`, `app/build.gradle.kts`

## Current Evidence And Constraints

- Worktree was clean on branch `v2.8.0` before this task.
- Existing extension dictionary engine spec is normative and linked to TaskAdmin task 2; its explicit scope excludes catalog/UI/download management.
- Existing Wubi/Pinyin spec is a draft authored before the final implementation and later fixes; its change workspace lacks current TaskAdmin lifecycle fields.
- Brownfield specs remain `authority: draft`; uncertainty is recorded rather than invented.

## Approach And Decisions

1. Deep-read each capability chain from trigger/UI or key event through service/domain/native/config and relevant tests.
2. Write operation-level requirements with GIVEN/WHEN/THEN scenarios and nearby source traces.
3. Keep extension dictionary aggregation in its existing normative owner; create a separate management capability spec.
4. Keep general hardware behavior separate from schema-specific Wubi/Pinyin semantics.
5. Treat ARM64 packaging as an engineering behavior contract rather than a user feature.
6. Reconcile the implemented Wubi/Pinyin change into the stable draft owner and logically accept the retained historical workspace only after validation evidence is recorded.

## Spec References

- Change/proposal: `.supermax/specs/changes/complete-wubi-pinyin-input/proposal.md`
- Delta specs: `.supermax/specs/changes/complete-wubi-pinyin-input/specs/wubi-pinyin-input/spec.md`
- Stable owners:
  - `.supermax/specs/wubi-pinyin-input/spec.md`
  - `.supermax/specs/extension-dictionaries/spec.md`
  - `.supermax/specs/physical-keyboard-input/spec.md`
  - `.supermax/specs/ime-session-lifecycle/spec.md`
  - `.supermax/specs/extension-dictionary-management/spec.md`
  - `.supermax/specs/arm64-release-build/spec.md`

## Affected Paths

- `.supermax/AGENTS.md`
- `.supermax/specs/index.md`
- `.supermax/specs/log.md`
- `.supermax/specs/wubi-pinyin-input/spec.md`
- `.supermax/specs/changes/complete-wubi-pinyin-input/proposal.md`
- `.supermax/specs/changes/complete-wubi-pinyin-input/specs/wubi-pinyin-input/spec.md`
- Four new spec paths listed above
- This task's `plan.md` and `todo.md`

## Risks And Failure Handling

- Broad file coverage can masquerade as behavior coverage: every requirement must name observable results and source evidence.
- Current tests do not cover every UI or lifecycle branch: mark those checks as source-reviewed/manual-not-run.
- Historical proposal terminology may not match the current SpecAdmin schema: preserve body/history, normalize only lifecycle metadata needed for convergence.
- Validation failure blocks accepted lifecycle status and task completion; record exact command/output in TODO.

## Acceptance Criteria

- All behavior-bearing commits in the target range map to a spec owner or explicit non-spec rationale.
- Each new spec satisfies brownfield quality gates and links to this task.
- Existing Wubi/Pinyin stable draft matches current implementation and later fixes without claiming unsupported behavior.
- Historical Wubi/Pinyin change lifecycle, merged owner, validation state and task link are internally consistent.
- Spec index/log route to every active owner and record this backfill.
- Project command routing names the current `justfile` and `build-release-arm64` recipe without weakening signing-credential boundaries.
- Relevant automated checks pass; manual checks not run are labeled accurately.

## Validation Plan

- Inspect `git diff --check` and the final spec diff.
- Verify frontmatter/task links/index links with scoped `rg` and file reads.
- Run `./gradlew :app:testDebugUnitTest` for JVM behavior coverage.
- Run `./gradlew :app:assembleDebug` for source/config/build integration.
- If available and practical, run `just build-release-arm64`; otherwise record it as not run with exact reason and retain the contract as source-derived draft.
