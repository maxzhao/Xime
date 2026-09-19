# Task TODO

## Current State

- Status: review
- Current phase: true root-cause fix implemented; awaiting user verification
- Next action: 用户在原复现环境中启用语音长按功能，验证实体 `w → Space` 不再绕过候选框。

## Checklist

### Investigation History

- [x] 保留 `RimeEngine.dispatchKey()` 的普通 Space 组合态保护。
- [x] 用户验证 `processed=false` 修补无效，撤销其为主根因的结论。
- [x] 用户验证实体 KeyUp 所有权修补无效；撤回该非必要代码，不保留无关行为变化。
- [x] 沿启用 STT 时的实体 Space 短按路径重新追踪到 `handleSoftCompositionKey()`。
- [x] 找到真正根因：残留 `candidateState.pendingEnglishText` 在调用 Rime 前直接提交宿主空格并返回。

### Implementation

- [x] `handleSoftCompositionKey()` 改为先在有序 key-processing 队列中调用 `RimeEngine.dispatchKey()`。
- [x] `Handled` 始终优先提交 Rime 结果并更新候选；`Unavailable` 不做宿主兜底。
- [x] 仅权威 `RimeKeyDispatch.Unhandled` 允许处理 `pendingEnglishText` 或发送普通宿主 Space/Enter。
- [x] 添加回归测试：残留 `pendingEnglishText` 在 `Handled`/`Unavailable` 时不得绕过 Rime，仅 `Unhandled` 可执行英文兜底。
- [x] 从 `InputUIState` 和 `HardwareKeyboardCandidateBar` 移除状态提示。
- [x] 新增独立 `HardwareKeyboardStatusOverlay`；使用独立 Compose state，不触发候选状态更新，不依赖系统 Toast。
- [x] 根据用户反馈将浮层顶部安全间距调整为 `56.dp`；用户确认位置已正常。
- [x] 运行针对性 JVM 测试和 `assembleDebug`。
- [ ] 用户在原复现应用中确认启用语音长按功能后的实体 Space 不再泄漏。

## Loaded Context

- Plan: `plan.md`（已更新为真正根因和独立浮层方案）
- Rules/specs/knowledge:
  - `.supermax/AGENTS.md`
  - `.supermax/specs/wubi-pinyin-input/spec.md`
  - `.supermax/specs/changes/complete-wubi-pinyin-input/design.md`
  - `.supermax/specs/changes/complete-wubi-pinyin-input/tasks.md`
- Supporting task documents: none.

## Changed Files

| Path | Change | Notes |
| --- | --- | --- |
| `app/src/main/java/com/kingzcheung/xime/service/ImeKeyRouter.kt` | 修改 | Rime 派发优先于 `pendingEnglishText`；状态文案只更新独立浮层。 |
| `app/src/main/java/com/kingzcheung/xime/service/InputUIState.kt` | 修改 | 删除 `hardwareStatusMessage`，候选 UI state 不再承载瞬时状态提示。 |
| `app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt` | 修改 | 挂载并清理独立状态浮层 state。 |
| `app/src/main/java/com/kingzcheung/xime/ui/keyboard/HardwareKeyboardCandidateBar.kt` | 修改 | 删除 `statusMessage` 参数和候选卡内状态文本。 |
| `app/src/main/java/com/kingzcheung/xime/ui/keyboard/HardwareKeyboardStatusOverlay.kt` | 新增 | 独立状态浮层；独立组合域读取状态；使用 `56.dp` 顶部安全间距。 |
| `app/src/test/java/com/kingzcheung/xime/service/SoftCompositionRouteTest.kt` | 修改 | 覆盖残留英文状态不得绕过权威 Rime 结果。 |
| `.supermax/tasks/master/task_001/plan.md` | 更新 | 记录真正根因、实现方案和验收条件。 |
| `.supermax/tasks/master/task_001/todo.md` | 更新 | 记录失败假设、最终修复和用户复验门禁。 |

## Validation

| Command / check | Result | Evidence |
| --- | --- | --- |
| `git --no-pager diff --check` | Passed | 无空白错误。 |
| `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew :app:testDebugUnitTest --tests 'com.kingzcheung.xime.service.SoftCompositionRouteTest' --tests 'com.kingzcheung.xime.rime.RimeCandidateTest' --tests 'com.kingzcheung.xime.service.KeyCodeMapperTest'` | Passed | `BUILD SUCCESSFUL in 14s`; 48 actionable tasks: 7 executed, 41 up-to-date. |
| `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew assembleDebug` | Passed | 真正根因修复后 `BUILD SUCCESSFUL in 9s`; 84 actionable tasks: 11 executed, 73 up-to-date. |
| 用户独立状态浮层位置 | Passed | 用户确认调整后浮层位置正常。 |
| 用户原复现环境：启用语音长按后的实体 `w → Space` | Pending | 必须安装本次新构建复验；此前安装包尚未包含 Rime-first 修复。 |

## Current Errors / Blockers

- 无实现或自动验证阻塞。
- 最终完成门禁仅剩用户原设备复验。

## Decisions / Findings

- 真正根因与用户现象一一对应：
  1. 启用 STT 后，实体 Space 的 `onKeyDown()` 被长按检测消费；短按在 `onKeyUp()` 调用 `handleKeyPress("space")`。
  2. 普通五笔拼音路径进入 `handleSoftCompositionKey()`。
  3. 原代码先读取异步 UI 状态 `candidateState.pendingEnglishText`；该值在模式切换/UI 更新延迟期间可能残留。
  4. 只要非空，原代码直接 `commitText(" ")` 并在调用 Rime 前返回。
  5. 因此宿主收到空格，而 Rime 组合态和候选完全未处理，候选框持续显示。
- 修复原则：Xime 自有英文待处理状态只能作为 Rime 权威 `Unhandled` 后的兜底，不能拥有高于 Rime 组合态的优先级。
- `resolveUnhandledSpace()` 继续保留为 Rime 已被调用但返回未处理时的第二道保护。
- KeyUp 所有权不是本问题主因，相关试探性代码已撤回，避免扩大输入事件行为。
- 独立浮层是候选层的兄弟组件，状态显示/清除不改变候选列表、组合态或候选 UI state。

## Next Actions

1. 用户安装本次 Debug 包，在语音输入功能开启状态下多次快速执行 `w → Space`。
2. 同时验证语音功能关闭时、软键盘 Space 和无组合态 Space。
3. 用户确认后将任务从 `review` 标记为 `done`；若仍复现，再基于此确定路径采集 `pendingEnglishText + Rime dispatch` 日志，不再修改其他路径。

## Do Not Re-Explore

- 不再把 `processed=false` 或实体 KeyUp 当作已证实主根因；用户实测已否定。
- 不再允许 `pendingEnglishText` 在 Rime 派发前提交 Space/Enter。
- 不再把候选 UI 快照作为宿主兜底依据。
- 不把瞬时状态重新放入 `InputUIState` 或 `HardwareKeyboardCandidateBar`。
- 不改五笔/拼音编码、候选排序、Rime JNI 或其他按键产品语义。
