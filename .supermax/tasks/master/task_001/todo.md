# Task TODO

## Current State

- Status: done
- Current phase: completed
- Next action: none.

## Checklist

- [x] 读取 `plan.md`、相关规格要求及 `RimeEngine.kt`、`ImeKeyRouter.kt`、`XimeInputMethodService.kt`、`RimeCandidateTest.kt` 的当前内容和 diff；记录并保留重叠的用户修改。
- [x] 完成普通 Space 的权威兜底规则：native 未处理但前/后仍有组合态时禁止宿主空格；有候选则选择索引 0，无候选则安全消费；仅前后均无组合态时返回 `Unhandled`。
- [x] 核对软键盘 Space 与未修饰实体 Space 都使用同一规则，且无组合态路径仍只输入一个空格；无需追加路由修改。
- [x] 完善最小回归测试；当前 `RimeCandidateTest` 已覆盖组合态有/无候选、空状态宿主兜底及带修饰 Space 边界，无需增加无关测试。
- [x] 运行针对性 JVM 测试并修复实际失败。
- [x] 运行 `./gradlew assembleDebug` 并修复本任务导致的编译/构建失败。
- [x] 在 Android 设备上验证软键盘与实体键盘的 `w → Space`（立即输入和候选可见后输入）及无组合态空格；记录实际结果。
- [x] 对照验收条件检查无双提交、无空格泄漏、无无关行为变化；更新本 TODO 的改动文件、验证证据、错误/阻塞与下一步。

## Loaded Context

- Plan: `plan.md`
- Rules/specs/knowledge:
  - `.supermax/AGENTS.md`
  - `.supermax/specs/wubi-pinyin-input/spec.md`
  - `.supermax/specs/changes/complete-wubi-pinyin-input/design.md`
  - `.supermax/specs/changes/complete-wubi-pinyin-input/tasks.md`
- Supporting task documents: none.

## Changed Files

| Path | Change | Notes |
| --- | --- | --- |
| `app/src/main/java/com/kingzcheung/xime/rime/RimeEngine.kt` | 已有工作区修补经检查和验证，未追加改动 | `dispatchKey()`/`resolveUnhandledSpace()` 在 native 未处理普通 Space 时根据权威组合态选择首候选、消费或允许宿主兜底。 |
| `app/src/test/java/com/kingzcheung/xime/rime/RimeCandidateTest.kt` | 已有工作区回归测试经检查和验证，未追加改动 | 覆盖普通 Space 泄漏决策分支。 |
| `app/src/main/java/com/kingzcheung/xime/service/ImeKeyRouter.kt` | 仅检查，无任务新增改动 | 软/实体 Space 的 `Handled`/`Unhandled` 路由已共用 `dispatchKey()` 结果。 |
| `app/src/main/java/com/kingzcheung/xime/service/XimeInputMethodService.kt` | 仅检查，无任务新增改动 | 实体 Space 已映射到统一 Rime 路径。 |
| `.supermax/tasks/master/task_001/todo.md` | 更新 | 记录完成状态、验证证据和环境说明。 |

## Validation

| Command / check | Result | Evidence |
| --- | --- | --- |
| `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew :app:testDebugUnitTest --tests 'com.kingzcheung.xime.rime.RimeCandidateTest' --tests 'com.kingzcheung.xime.service.KeyCodeMapperTest'` | Passed | `BUILD SUCCESSFUL in 4s`; 48 actionable tasks: 1 executed, 47 up-to-date. |
| `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew :app:testDebugUnitTest --tests 'com.kingzcheung.xime.service.SoftCompositionRouteTest'` | Passed | `BUILD SUCCESSFUL in 2s`; 48 actionable tasks: 1 executed, 47 up-to-date. |
| `ANDROID_HOME="$HOME/Android/Sdk" ANDROID_SDK_ROOT="$HOME/Android/Sdk" ./gradlew assembleDebug` | Passed | `BUILD SUCCESSFUL in 2s`; 84 actionable tasks: 8 executed, 76 up-to-date. |
| Android API 36 AVD：软键盘 `w → Space`，候选可见后输入 | Passed | `w` 后第一候选为“人”；点击软空格后 `compose_message_text` 为 `text="人"`，候选关闭，无 U+0020。 |
| Android API 36 AVD：软键盘立即 `w → Space` | Passed | 连续坐标点击后 `compose_message_text` 为 `text="人"`，无空格泄漏或双提交。 |
| Android API 36 AVD：未修饰实体键盘 `w → Space`，候选可见后输入 | Passed | 浮动候选第一项为“人”；实体 Space 后 `compose_message_text` 为 `text="人"`，候选关闭，无 U+0020。 |
| Android API 36 AVD：未修饰实体键盘立即 `w → Space` | Passed | 连续 KEYCODE_W/KEYCODE_SPACE 后 `compose_message_text` 为 `text="人"`，无空格泄漏或双提交。 |
| Android API 36 AVD：无组合态时软/实体 Space | Passed | 两条路径分别得到 `compose_message_text` 的 `text=" "`，每次仅一个普通空格。 |

## Current Errors / Blockers

- None.
- 环境说明：直接运行 Gradle 会报 `SDK location not found`；本机 Linux SDK 位于 `$HOME/Android/Sdk`，验证命令已显式设置 `ANDROID_HOME` 与 `ANDROID_SDK_ROOT`。
- Windows Gradle 不能从项目的 WSL UNC 路径启动，曾报 `java.io.IOException: 函数不正确。`；最终验证均使用 Linux SDK 并通过。
- 设备验证使用 `godot-api36-x86_64` AVD。实体键盘阶段使用默认 PS/2 设备；软键盘阶段临时解绑 AVD 的 `atkbd`，仅改变运行中的模拟器，模拟器随后已关闭。

## Decisions / Findings

- 根因不是候选框 UI：`RimeProcessResult.processed == false` 被错误当成“无组合态、允许宿主兜底”，但该返回值不能证明原始输入/候选已消失。
- 修复所有者是 `RimeEngine.dispatchKey()` 的普通 Space 兜底授权；UI `candidateState` 不参与该决策。
- 当前工作区已有未提交的 `resolveUnhandledSpace()`、dispatch 恢复逻辑及 `RimeCandidateTest` 用例；检查和验证证明已满足任务目标，因此未追加重构或无关测试。
- 软键盘与实体键盘均通过实际 `w → Space` 及无组合态 Space 验证，快速输入不依赖 UI 候选传播。
- 现有 `.supermax/specs/wubi-pinyin-input/spec.md` 已规定 Space 提交第一候选及禁止重复宿主兜底；本任务不改规格。
- 范围仅覆盖普通 Space 的软/实体输入路径，不扩展到“任何键”，不重构其他组合键语义。

## Next Actions

1. None.

## Do Not Re-Explore

- 不再讨论是否“任何键”都应选择候选；已确定只有按键各自的既定组合态语义，当前缺陷只涉及普通 Space。
- 不再重新定位症状所有者；已确定是 `processed=false` 到宿主 Space 的错误授权，不是候选栏渲染问题。
- 不再提议规格变更；既有规格已完整覆盖目标行为。
- 不重新设计全局 tri-state/JNI 架构；本任务在现有 `RimeKeyDispatch` 和当前修补上完成闭环。
