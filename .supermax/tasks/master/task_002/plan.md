# Plan

## Goal

将当前已实现并通过自动化测试的扩展词库候选优先级行为固化为可验证规格，确保启用扩展词库后，拼音保留外部词频，而五笔86原始候选不会被扩展词频覆盖或降序。

## Observable Outcome

- 新增 `.supermax/specs/extension-dictionaries/spec.md`，定义扩展词库聚合、拼音词频、五笔优先级、重建部署和失败行为。
- `.supermax/specs/index.md` 与 `.supermax/specs/log.md` 可路由并追踪该规格。
- 规格、实现、测试和 TaskAdmin 状态一致。

## Scope

### In Scope

- 记录并核对 `ExtensionDictionaryManager` 的拼音/五笔分离词典行为。
- 记录五笔扩展词条不得借助外部正权重抢占基础五笔候选。
- 记录词库启停后的重建和 Rime 部署要求。
- 保存当前实现与自动化验证证据。

### Out Of Scope

- 新增扩展词库来源或修改下载协议。
- 改变 `wubi86`、`wubi86_extra` 自身词条和既有权重。
- 修改用户词典学习排序、拼音基础词库词频或 UI。
- 人工设备输入体验验收。

## Spec References

- Proposal/change: N/A（新增直接行为规格）
- Delta specs: N/A
- Stable owner: `.supermax/specs/extension-dictionaries/spec.md`

## Authoritative Inputs

- 用户报告与修复授权：启用扩展词库后，五笔拼音混打的五笔86原始候选疑似被覆盖或后移。
- `app/src/main/java/com/kingzcheung/xime/settings/ExtensionDictionaryManager.kt`
- `app/src/main/java/com/kingzcheung/xime/settings/ExtensionDictionaryConverter.kt`
- `app/src/main/assets/rime/wubi86.dict.yaml`
- `app/src/main/assets/rime/wubi86_pinyin.schema.yaml`
- `app/src/main/jni/librime/src/rime/dict/entry_collector.cc`
- `app/src/main/jni/librime/src/rime/dict/dict_compiler.cc`
- `app/src/main/jni/librime/src/rime/dict/vocabulary.cc`
- `app/src/test/java/com/kingzcheung/xime/settings/ExtensionDictionaryCatalogTest.kt`
- `.supermax/specs/index.md`
- `.supermax/specs/log.md`

## Current Evidence And Constraints

- 原实现让 `xime_wubi86` 直接导入带正权重的 `xime_extension_words`；基础五笔多数无显式权重。
- Rime 对同码候选按权重降序稳定排序，扩展词频可把基础五笔候选挤到后面。
- 当前工作已将活动词库拆分为带权重的 `xime_extension_words` 和不带权重的 `xime_extension_words_wubi`。
- `xime_wubi86` 仍按顺序导入 `wubi86`、`wubi86_extra`、五笔扩展词库，不修改基础词库。
- 工作树中的现有修改属于本次工作，不得回退。

## Implementation Approach And Decisions

1. 保留拼音活动词库文件名和外部词频，避免改变拼音排序。
2. 为五笔生成独立活动词库，仅写 `text` 列，不写外部 `weight`；聚合词典继续由既有 encoder 自动生成五笔编码。
3. 五笔聚合词典在基础词典之后导入活动词库，使同权重冲突保持基础来源优先。
4. 启停扩展词库时同步重建两份活动词典；任一缺失或 enabled marker 变化均触发重建。
5. 规格只记录可观察行为、约束和验收；技术步骤与详细验证留在本任务。

## Affected Paths

- `app/src/main/java/com/kingzcheung/xime/settings/ExtensionDictionaryManager.kt`
- `app/src/test/java/com/kingzcheung/xime/settings/ExtensionDictionaryCatalogTest.kt`
- `.supermax/specs/extension-dictionaries/spec.md`
- `.supermax/specs/index.md`
- `.supermax/specs/log.md`
- TaskAdmin published `plan.md` and `todo.md`

## Risks And Failure Handling

- 风险：五笔扩展词典误写权重或误导入拼音活动词典会复现排序问题；由格式和聚合引用回归测试约束。
- 风险：生成文件变化未触发部署；Rime deployment hash 已包含所有 `*.dict.yaml`，规格要求词库状态变化后重部署。
- 风险：自动化测试不等同于真机输入体验；规格记录人工验证未运行，不虚构结论。
- 规格、代码或测试不一致时，先修复所属工件再完成任务。

## Acceptance Criteria

- 规格明确：拼音保留扩展词频，五笔扩展词条不携带可抢占基础候选的外部权重。
- 规格明确：基础 `wubi86`/`wubi86_extra` 不被覆盖，且优先于同码、同权重的扩展词条。
- 规格包含启停、空扩展集、缺失生成文件、部署失败等场景。
- 规格具有可解析的 TaskAdmin tag/id，并被规格索引和维护日志引用。
- 当前实现与回归测试满足规格；自动化单元测试和 `git diff --check` 通过。

## Validation Plan

- `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew :app:testDebugUnitTest --tests com.kingzcheung.xime.settings.ExtensionDictionaryCatalogTest`
- `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./run_tests.sh unit`
- `git diff --check`
- 检查 `.supermax/specs/index.md`、`.supermax/specs/log.md`、规格 frontmatter 和本任务中的精确路径均可解析。
