# Implementation Plan

## Goal

- 修复 `wubi86_pinyin` 组合态下普通 Space 泄漏到宿主编辑器的问题。
- 可观察结果：输入 `w` 且首候选为“人”时，软键盘空格和未带修饰键的实体空格都只提交“人”一次，不插入 U+0020，候选/组合态随后关闭；无组合态时仍正常输入一个空格。

## Scope

- In scope:
  - 修正 Rime 返回 `processed=false` 但按键前后仍存在组合态时的 Space 兜底判定。
  - 组合态且已有候选时提交候选索引 0；组合态但候选尚未准备时消费 Space，禁止宿主空格。
  - 让软键盘 Space 与实体键盘普通 Space 共用同一结果规则。
  - 保留并完善当前工作区中已有的 `resolveUnhandledSpace`/`dispatchKey` 针对性修补。
  - 添加最小回归覆盖并完成针对性 JVM、Debug 构建和设备复现验证。
- Out of scope:
  - 将“任何键”都改为候选选择键。
  - 修改候选排序、五笔/拼音编码规则、标点、Enter、删除、导航或其他按键语义。
  - 重构整套 IME 键路由、Rime JNI 接口或候选系统。
  - 修改或推广现有五笔拼音规格状态。
  - 清理、回滚或顺带完成工作区中的其他用户修改。

## Authoritative Inputs

- Read first:
  - `.supermax/AGENTS.md`
  - `.supermax/specs/wubi-pinyin-input/spec.md`
  - `.supermax/specs/changes/complete-wubi-pinyin-input/design.md`
  - `app/src/main/java/com/kingzcheung/xime/rime/RimeEngine.kt`
  - `app/src/main/java/com/kingzcheung/xime/service/ImeKeyRouter.kt`
  - `app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt`
  - `app/src/test/java/com/kingzcheung/xime/rime/RimeCandidateTest.kt`
  - `app/src/test/java/com/kingzcheung/xime/service/KeyCodeMapperTest.kt`
- Constraints:
  - 当前工作区在上述及其他文件中已有未提交的用户修改；先读取当前 diff，并以现状为基线做窄改动，禁止还原或覆盖无关修改。
  - `candidateState`、候选 UI 和异步 Compose 状态不是宿主兜底的授权依据。
  - 只有 Rime 权威结果表明按键前后均无组合态时，未处理的普通 Space 才可转发给宿主。
  - 已处理的一个 Space 只能产生一次 Rime 提交/组合态更新或一次宿主空格，不能二者同时发生。

## Spec References

- Change/proposal: `.supermax/specs/changes/complete-wubi-pinyin-input/proposal.md`
- Delta spec: `.supermax/specs/changes/complete-wubi-pinyin-input/specs/wubi-pinyin-input/spec.md`
- Stable owner: `.supermax/specs/wubi-pinyin-input/spec.md`
- Relevant requirement: `Composition Editing Key Semantics` 中 Space 提交当前第一候选，以及 `No Duplicate Commit Or Fallback`。
- Spec impact: 无；现有规格已经完整描述目标行为，本任务修复实现偏差。

## Current Facts

- `ImeKeyRouter.handleSoftCompositionKey()` 将软键盘 `space` 映射为 Rime 空格键；实体键盘普通 Space 也进入 `RimeEngine.dispatchKey()`。两条路径只有收到 `RimeKeyDispatch.Unhandled` 后才应执行宿主空格。
- 根因已经确定：原实现把 native `RimeProcessResult.processed == false` 等同于“没有组合态，可以宿主兜底”。该布尔值只表示本次 Rime 按键未被消费，并不证明原始输入/候选已经消失；五笔首码候选延迟准备时，按键前后仍可能有组合态。
- 结果是 Space 被发送给 Android `InputConnection`，而 Rime 组合态未清除，因此出现“空白上屏、候选框持续显示”。
- 当前未提交工作区已包含针对性候选修补：`RimeEngine.dispatchKey()` 在 native 未处理普通 Space 时读取按键前后组合态，通过 `resolveUnhandledSpace()` 决定选择首候选、消费按键或允许宿主兜底；`RimeCandidateTest.kt` 已有对应纯逻辑用例。该代码属于用户工作，必须保留并在其上完成任务。
- 现有规格和 `WubiPinyinNativeIntegrationTest` 已表达 `w → Space` 应提交首候选，但设备级用例当前未形成这次缺陷的最终验证证据。

## Approach

1. 以当前 diff 为基线，保留 `RimeEngine.dispatchKey()` 的有序、同锁前后状态读取；不得退回使用 UI 快照判断组合态。
2. 完成普通 Space 的唯一兜底规则：
   - native 已处理：原样返回 `Handled`；
   - native 未处理，且按键前后均无组合态：返回 `Unhandled`，允许既有宿主空格路径；
   - native 未处理，且按键前或按键后仍有组合态、当前存在候选：在同一 Rime 临界区选择候选索引 0，并返回 `Handled`；
   - native 未处理，且组合态仍存在但候选尚未准备：返回 `Handled` 并携带当前状态，禁止 U+0020 泄漏。
3. 核对软键盘 Space 和未修饰实体 Space 均消费上述 `dispatchKey()` 结果；只在发现绕过路径时做最小路由调整。保留 Shift/Ctrl/Alt/Meta 组合键及无组合态宿主行为。
4. 保留/整理现有最小纯逻辑回归测试，覆盖：有组合态且有候选、组合态但无候选、按键前后均无组合态、非普通 Space 不套用该规则。不要为静态映射或无关按键增加测试。
5. 运行针对性 JVM 测试与 `assembleDebug`；在 Android 设备上分别验证软键盘和实体键盘的普通速度、紧邻 `w → Space` 输入及无组合态空格。

## Decisions

- Decision: 修复宿主兜底授权，而不是改候选 UI。
  - Choice: 使用同一次有序 Rime 调度的按键前/后状态决定是否允许空格落入宿主。
  - Rationale: 症状由 `processed=false` 的语义被错误扩大造成，候选框只是组合态仍存的表现。
  - Rejected alternative: 根据 `candidateState.isComposing` 或候选框可见性拦截；这些是异步 UI 快照，会保留竞态。
- Decision: 候选存在时直接选择第一候选。
  - Choice: 在 Rime 锁内调用现有候选选择路径并返回其结果。
  - Rationale: 满足 Space 的规格语义，同时保证提交文本、学习状态和候选索引由 Rime 管理。
  - Rejected alternative: Kotlin 直接 `commitText(candidate.text)`；会绕过 Rime 状态和学习，并可能留下组合态。
- Decision: 不修改规格。
  - Choice: 将工作登记为既有行为合同的实现缺陷。
  - Rationale: 规格已经明确 Space、权威状态和禁止重复兜底。

## Affected Paths

- `app/src/main/java/com/kingzcheung/xime/rime/RimeEngine.kt` — 普通 Space 未处理时的权威组合态恢复/兜底决策；预计主要实现所有者。
- `app/src/main/java/com/kingzcheung/xime/service/ImeKeyRouter.kt` — 仅核对软/硬 Space 对 `Handled`/`Unhandled` 的消费；必要时做窄调整。
- `app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt` — 仅核对实体 Space 的 KeyDown/KeyUp 路由，不进行无关重构。
- `app/src/test/java/com/kingzcheung/xime/rime/RimeCandidateTest.kt` — 最小回归测试。
- `app/src/test/java/com/kingzcheung/xime/service/KeyCodeMapperTest.kt` — 复用既有普通实体 Space 映射验证；无新增需求时不改。

## Risks And Failure Handling

- Risk: 首次 native Space 实际已提交，但返回值异常，恢复逻辑再次选候选导致双提交。
  - Mitigation: 只有 `processed=false` 且返回状态仍显示组合态/候选时才进入恢复；提交后的空状态不得再次选择。
- Risk: 组合态存在但候选尚未准备时错误插入空格。
  - Mitigation: 消费按键并保留当前 Rime 状态，不做宿主兜底；后续按键仍按正常有序队列处理。
- Risk: 无组合态时普通空格被吞。
  - Mitigation: 前后状态都为空时必须保留 `Unhandled`，并通过单测及设备验证确认只输入一个空格。
- Risk: 与当前用户修改冲突。
  - Failure response: 停止扩大改动，重新读取目标函数及当前 diff，以语义级窄改方式合并；禁止 checkout/reset/revert 用户文件。
- Validation failure response: 根据实际失败修复并重跑最近的失败检查；设备不可用时记录准确命令/环境阻塞，不得宣称完成。

## Acceptance Criteria

- [ ] `wubi86_pinyin` 中文模式输入 `w`，首候选为“人”时，点击软键盘 Space 只提交“人”一次，不插入空白，候选/组合态关闭。
- [ ] 同一场景使用未修饰实体 Space，结果相同。
- [ ] 快速连续输入 `w → Space` 不依赖候选 UI 刷新时机，结果仍为“人”且无空白泄漏。
- [ ] Rime 未处理 Space 但按键前后任一状态仍有组合态时，不向宿主发送空格；有候选时选择索引 0，无候选时安全消费。
- [ ] 按键前后均无组合态时，软键盘和未修饰实体 Space 仍各输入一个普通空格。
- [ ] 一个 Space 不会同时触发 Rime 提交和宿主空格，也不会重复提交候选。
- [ ] 其他按键、候选排序和既有用户工作不被有意改变。
- [ ] 针对性 JVM 测试和 `./gradlew assembleDebug` 通过；设备验证结果记录在任务 TODO。

## Validation Plan

- Command: `./gradlew :app:testDebugUnitTest --tests 'com.kingzcheung.xime.rime.RimeCandidateTest' --tests 'com.kingzcheung.xime.service.KeyCodeMapperTest'`
  - Expected: 相关普通 Space 决策与键码测试全部通过。
- Command: `./gradlew assembleDebug`
  - Expected: Android/Kotlin/JNI 与资源构建成功。
- Manual device check:
  - `wubi86_pinyin` 中文模式，分别用软键盘和未修饰实体键盘执行 `w → Space`，包括立即按 Space 与候选可见后按 Space。
  - Expected: 仅提交“人”一次，无 U+0020，候选/组合态关闭。
  - 清空组合态后分别按软/实体 Space。
  - Expected: 每次仅输入一个普通空格。
