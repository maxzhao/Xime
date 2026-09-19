# Implementation Plan

## Goal

- 修复 `wubi86_pinyin` 组合态下普通 Space 泄漏到宿主编辑器的问题。
- 可观察结果：输入 `w` 且首候选为“人”时，软键盘空格和未带修饰键的实体空格都只提交“人”一次，不插入 U+0020，候选/组合态随后关闭；无组合态时仍正常输入一个空格。

## Scope

- In scope:
  - 保留 Rime 返回 `processed=false` 但按键前后仍存在组合态时的 Space 兜底保护。
  - 组合态且已有候选时提交候选索引 0；组合态但候选尚未准备时消费 Space，禁止宿主空格。
  - 修正 `handleSoftCompositionKey()` 的状态优先级：先向 Rime 派发 Space，再仅在权威 `Unhandled` 时处理 Xime 自有 `pendingEnglishText`。
  - 让软键盘 Space 与实体键盘普通 Space 共用权威 Rime 结果规则。
  - 将实体键盘模式/标点/全半角提示从候选框提取为独立 IME 浮层，状态更新不再修改候选 UI 状态。
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
- 上次两次修补分别覆盖 native `processed=false` 和实体 KeyUp，但用户真机复现证明二者都不是主根因；KeyUp 防御改动已撤回。
- 真正根因位于 `ImeKeyRouter.handleSoftCompositionKey()`：实体 Space 短按在启用语音长按功能时也会走该软键路径；函数先检查异步 UI 状态 `candidateState.pendingEnglishText`。只要该值非空，就直接向宿主提交空格并在调用 Rime 前返回。
- `pendingEnglishText` 可在中英文切换或 UI 更新延迟期间残留，而 Rime 已经处于中文组合态并显示候选。因此形成完全一致的结果：空格进入宿主，Rime 没收到 Space，候选框继续显示。
- `RimeEngine.dispatchKey()` 的 `resolveUnhandledSpace()` 仍是有效的第二道保护，但只有真正调用 Rime 后才生效；修复必须把 Rime 派发放到 `pendingEnglishText` 前面。
- `hardwareStatusMessage` 当前属于 `InputUIState` 并由 `HardwareKeyboardCandidateBar` 直接渲染；状态显示与清除都会重组候选区域。规格未要求该耦合，用户要求将其拆为独立浮层，且不能依赖 Termux 中不可见的系统 Toast。

## Approach

1. 保留 `RimeEngine.dispatchKey()` 的有序、同锁 Space 保护；不得退回使用 UI 快照判断组合态。
2. `handleSoftCompositionKey()` 必须先在有序 key-processing 队列中调用 `RimeEngine.dispatchKey()`；`Handled` 结果直接提交候选并更新 UI，`Unavailable` 不做宿主兜底。
3. 只有 `RimeKeyDispatch.Unhandled` 才允许读取并处理 `pendingEnglishText` 或发送普通宿主 Space/Enter；残留 UI 状态不得绕过权威 Rime 结果。
4. 从 `InputUIState` 和 `HardwareKeyboardCandidateBar` 移除状态提示；新增独立 `HardwareKeyboardStatusOverlay`，使用独立 Compose state，并把 state 读取限制在浮层组合域。模式/标点/全半角状态只更新该浮层，不使用系统 Toast。
5. 添加最小决策测试，覆盖残留 `pendingEnglishText` 在 `Handled`/`Unavailable` 时不能执行宿主兜底，仅 `Unhandled` 可执行；保留现有 Space dispatch 测试。
6. 运行针对性 JVM 测试与 `assembleDebug`；由用户在原设备验证启用语音长按功能后的实体 `w → Space` 和独立状态浮层。

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

- `app/src/main/java/com/kingzcheung/xime/rime/RimeEngine.kt` — 保留普通 Space 未处理时的权威组合态恢复/兜底决策。
- `app/src/main/java/com/kingzcheung/xime/service/ImeKeyRouter.kt` — Rime 派发优先于 `pendingEnglishText`；状态文案只更新独立浮层。
- `app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt` — 挂载并清理独立状态浮层 state。
- `app/src/main/java/com/kingzcheung/xime/service/InputUIState.kt` — 移除候选 UI 中的瞬时状态字段。
- `app/src/main/java/com/kingzcheung/xime/ui/keyboard/HardwareKeyboardCandidateBar.kt` — 移除状态提示参数和内容。
- `app/src/main/java/com/kingzcheung/xime/ui/keyboard/HardwareKeyboardStatusOverlay.kt` — 独立实体键盘状态浮层。
- `app/src/test/java/com/kingzcheung/xime/rime/RimeCandidateTest.kt` — 保留 Space dispatch 回归测试。
- `app/src/test/java/com/kingzcheung/xime/service/KeyCodeMapperTest.kt` — 增加按键所有权状态转换测试。

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
- [ ] 一个 Space 不会同时触发 Rime 提交和宿主空格，也不会重复提交候选；残留 `pendingEnglishText` 不能绕过 Rime 组合态。
- [ ] 右 Shift、`Ctrl+.`、`Shift+Space` 的瞬时状态通过独立 IME 浮层显示，不进入候选卡，也不更新候选 UI state；Termux 等目标不依赖系统 Toast。
- [ ] 其他按键、候选排序和既有用户工作不被有意改变。
- [ ] 针对性 JVM 测试和 `./gradlew assembleDebug` 通过；设备及用户原复现场景结果记录在任务 TODO。

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
