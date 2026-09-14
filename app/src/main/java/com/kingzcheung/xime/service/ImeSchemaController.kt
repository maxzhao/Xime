package com.kingzcheung.xime.service

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import android.widget.Toast
import com.kingzcheung.xime.MainActivity
import com.kingzcheung.xime.keyboard.HANDWRITING_SCHEMA_ID
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.settings.SchemaConfigHelper
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.util.FileLogger
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 方案管理与输入模式切换。
 *
 * 承载方案切换（switchSchema/applyPageSizeSetting）、部署（reloadConfig/deploy/deploySchema/downloadSchema）、
 * 中英切换（switchInputMethod）、工具栏编辑动作、键盘高度与浮动模式调整。
 * 共享状态通过 service 引用访问。
 */
internal class ImeSchemaController(private val service: XimeInputMethodService) {
    internal suspend fun switchInputMethod(): Boolean {
        val candState = service.candidateState.value
        val pendingEnglish = candState.pendingEnglishText
        FileLogger.i(XimeInputMethodService.TAG, "switchInputMethod: start, pendingEnglish='${if (pendingEnglish.isEmpty()) '-' else pendingEnglish}', isComposing=${candState.isComposing}, candidates=${candState.candidates.size}")
        if (pendingEnglish.isNotEmpty()) {
            // 英文直接上屏模式：编码字符已逐字落盘，切模式只需结束本轮输入（清状态），
            // 不可再 commitText 否则会重复输出整个词。
            withContext(Dispatchers.Main) {
                service.candidateState.value = service.candidateState.value.copy(
                    pendingEnglishText = "",
                    associationCandidates = emptyList()
                )
            }
        } else {
            // 软键盘显式切换也以 Rime 当前 raw input 为权威，避免 UI 快照尚未传播时漏提交。
            val input = service.rimeEngine.getInput()
            if (input.isNotEmpty()) {
                withContext(Dispatchers.Main) { service.commitText(input) }
                service.rimeEngine.clearComposition()
            }
        }
        // 由 ImeKeyRouter 在 key-processing 线程调用：toggleAsciiMode 阻塞等待 rimeLock
        // （部署/维护持锁时排队，完成后自动切换），不静默失败、不阻塞主线程。
        // 仅在 session 创建失败（引擎真正不可用）时返回 false。
        val t0 = System.nanoTime()
        if (!service.rimeEngine.toggleAsciiMode()) {
            FileLogger.e(XimeInputMethodService.TAG, "switchInputMethod: toggleAsciiMode FAILED (engine unavailable)")
            Toast.makeText(service, "输入法引擎不可用，请稍后再试", Toast.LENGTH_SHORT).show()
            return false
        }
        FileLogger.i(XimeInputMethodService.TAG, "switchInputMethod: toggleAsciiMode ok, took ${(System.nanoTime() - t0) / 1_000_000}ms, rime ascii=${service.rimeEngine.isAsciiMode()}, thread=${Thread.currentThread().name}")
        service.sessionController.persistSchemaOption("ascii_mode", service.rimeEngine.isAsciiMode())
        withContext(Dispatchers.Main) {
            // 显式同步 uiState.isAsciiMode（权威源 = rime 引擎状态），
            // 不依赖 updateUI 链路异步回写，避免键盘 UI 与 rime 状态脱钩。
            val ascii = service.rimeEngine.isAsciiMode()
            FileLogger.i(XimeInputMethodService.TAG, "switchInputMethod: rime ascii=$ascii, ui before=${service.uiState.value.isAsciiMode}")
            service.uiState.value = service.uiState.value.copy(isAsciiMode = ascii)
            service.updateUI()
            // 主线程直接权威下发键盘布局切换（与 rime 状态一致），
            // 不依赖 Compose LaunchedEffect 侦测 uiState 后再异步 dispatch（部分机型调度延迟导致 UI 不更新）。
            val schemaId = service.rimeEngine.getCurrentSchema()
            service.keyboardViewModel.dispatch(
                com.kingzcheung.xime.ui.keyboard.KeyboardDispatchAction.AsciiModeChanged(ascii, schemaId)
            )
        }
        return true
    }
    
    internal fun reloadConfig() {
        
        service.mainHandler.post {
            service.requestHideSelf(0)
            android.widget.Toast.makeText(service, "方案部署中...", android.widget.Toast.LENGTH_SHORT).show()
        }
        
        // 部署投递到 key-processing 队列：与按键/切换同队列串行执行，
        // 部署期间输入/切换操作排队等待，完成后自动恢复，
        // 不再因 rimeLock 被部署占用而失败或静默丢弃。
        service.keyRouter.postRimeJob {
            try {
                KeysConfigHelper.loadConfig(service)
                // 重新加载配色方案（用户可能在 xime.custom.yaml 中修改了 color_schemes）
                KeyboardThemes.reload(service)
                
                val userDataDir = File(service.filesDir, "rime")
                
                // 清空 build 目录，强制 Rime 全量重新编译
                val buildDir = File(userDataDir, "build")
                if (buildDir.exists()) {
                    buildDir.deleteRecursively()
                }
                
                service.rimeEngine.deploy()
                // 部署后记录 hash 与完成标记，否则下次启动会因 hash 不一致再次全量编译
                RimeConfigHelper.storeDeploymentHash(service)
                SettingsPreferences.setDeploymentDone(service, true)
                
                // 部署完成后重新加载配置（Rime 可能在部署过程中改写文件）
                KeysConfigHelper.loadConfig(service)
                KeyboardThemes.reload(service)
                
                val availableSchemas = service.rimeEngine.getAvailableSchemas()
                
                val savedSchema = SettingsPreferences.getCurrentSchema(service)
                if (savedSchema in availableSchemas) {
                    applyPageSizeSetting(savedSchema)
                    service.rimeEngine.switchSchema(savedSchema)
                } else {
                    Log.w(XimeInputMethodService.TAG, "Schema $savedSchema not found in available schemas")
                }
                
                // 直接在 key-processing 线程同步读取 name，避免嵌套协程的时序问题
                val currentSchemaId = service.rimeEngine.getCurrentSchema()
                val schemaName = SchemaManager.getSchemaDisplayName(
                    service, currentSchemaId
                ) ?: currentSchemaId

                withContext(Dispatchers.Main) {
                    service.uiState.value = service.uiState.value.copy(
                        schemaName = schemaName,
                        currentSchemaId = currentSchemaId,
                    )
                    service.updateUI()
                    android.widget.Toast.makeText(service, "方案部署完成", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(XimeInputMethodService.TAG, "Failed to reload config", e)
            }
        }
    }
    
    private fun deploySchema() {
        try {
            service.rimeEngine.deploy()
            // 部署后记录 hash 与完成标记，避免下次启动再次全量编译
            RimeConfigHelper.storeDeploymentHash(service)
            SettingsPreferences.setDeploymentDone(service, true)
            val savedSchema = SettingsPreferences.getCurrentSchema(service)
            applyPageSizeSetting(savedSchema)
            service.rimeEngine.switchSchema(savedSchema)
            val currentSchemaId = service.rimeEngine.getCurrentSchema()
            service.uiState.value = service.uiState.value.copy(
                schemaName = SchemaManager.getSchemaDisplayName(service, currentSchemaId) ?: currentSchemaId,
                currentSchemaId = currentSchemaId,
            )
            service.updateUI()
        } catch (e: Exception) {
            Log.e(XimeInputMethodService.TAG, "Failed to deploy schema", e)
        }
    }
    
    internal fun openSettings() {
        try {
            val intent = Intent(service, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            service.startActivity(intent)
        } catch (e: Exception) {
            Log.e(XimeInputMethodService.TAG, "Failed to open settings", e)
        }
    }
    
    private var editSelAnchor = -1

    internal fun handleToolbarEditingAction(action: String) {
        val ic = service.currentInputConnection ?: return
        when (action) {
            "select_all" -> ic.performContextMenuAction(android.R.id.selectAll)
            "copy" -> ic.performContextMenuAction(android.R.id.copy)
            "cut" -> ic.performContextMenuAction(android.R.id.cut)
            "paste" -> ic.performContextMenuAction(android.R.id.paste)
            "home" -> service.keyRouter.handleSoftRimeEditingKey(0xff50) { ic.setSelection(0, 0) }
            "end" -> service.keyRouter.handleSoftRimeEditingKey(0xff57) {
                val before = ic.getTextBeforeCursor(XimeInputMethodService.SAFE_TEXT_LIMIT, 0) ?: ""
                val after = ic.getTextAfterCursor(XimeInputMethodService.SAFE_TEXT_LIMIT, 0) ?: ""
                ic.setSelection(before.length + after.length, before.length + after.length)
            }
            "arrow_up" -> service.keyRouter.handleSoftRimeEditingKey(0xff52) {
                sendHostEditingKey(ic, KeyEvent.KEYCODE_DPAD_UP)
            }
            "arrow_down" -> service.keyRouter.handleSoftRimeEditingKey(0xff54) {
                sendHostEditingKey(ic, KeyEvent.KEYCODE_DPAD_DOWN)
            }
            "arrow_left" -> service.keyRouter.handleSoftRimeEditingKey(0xff51) {
                sendHostEditingKey(ic, KeyEvent.KEYCODE_DPAD_LEFT)
            }
            "arrow_right" -> service.keyRouter.handleSoftRimeEditingKey(0xff53) {
                sendHostEditingKey(ic, KeyEvent.KEYCODE_DPAD_RIGHT)
            }

            "select_begin" -> {
                editSelAnchor = (ic.getTextBeforeCursor(XimeInputMethodService.SAFE_TEXT_LIMIT, 0) ?: "").length
            }
            "select_end" -> {
                editSelAnchor = -1
            }
            "select_arrow_left" -> extendSelection(ic, -1)
            "select_arrow_right" -> extendSelection(ic, 1)
            "select_arrow_up" -> {
                val before = ic.getTextBeforeCursor(XimeInputMethodService.SAFE_TEXT_LIMIT, 0) ?: ""
                val pos = before.length
                if (pos > 0) {
                    val prevNewline = before.lastIndexOf('\n', pos - 2)
                    val lineStart = if (prevNewline >= 0) prevNewline + 1 else 0
                    ic.beginBatchEdit()
                    ic.setSelection(editSelAnchor, lineStart)
                    ic.endBatchEdit()
                }
            }
            "select_arrow_down" -> {
                val before = ic.getTextBeforeCursor(XimeInputMethodService.SAFE_TEXT_LIMIT, 0) ?: ""
                val after = ic.getTextAfterCursor(XimeInputMethodService.SAFE_TEXT_LIMIT, 0) ?: ""
                val pos = before.length
                val total = before.length + after.length
                if (pos < total) {
                    val nextNewline = after.indexOf('\n')
                    val lineEnd = if (nextNewline >= 0) pos + nextNewline else total
                    ic.beginBatchEdit()
                    ic.setSelection(editSelAnchor, lineEnd)
                    ic.endBatchEdit()
                }
            }
        }
    }

    private fun sendHostEditingKey(ic: InputConnection, keyCode: Int) {
        val t = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, keyCode, 0))
        ic.sendKeyEvent(KeyEvent(t, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0))
    }

    private fun extendSelection(ic: InputConnection, direction: Int) {
        val before = ic.getTextBeforeCursor(XimeInputMethodService.SAFE_TEXT_LIMIT, 0) ?: ""
        val after = ic.getTextAfterCursor(XimeInputMethodService.SAFE_TEXT_LIMIT, 0) ?: ""
        val pos = before.length
        val total = before.length + after.length
        val next = (pos + direction).coerceIn(0, total)
        if (next != pos) {
            ic.beginBatchEdit()
            ic.setSelection(editSelAnchor, next)
            ic.endBatchEdit()
        }
    }

    internal fun applyPageSizeSetting(schemaId: String) {
        val userPageSize = SettingsPreferences.getPageSize(service)
        if (userPageSize > 0) {
            service.rimeEngine.setPageSize(schemaId, userPageSize)
        }
    }

    internal fun switchSchema(schemaId: String) {
        if (schemaId == HANDWRITING_SCHEMA_ID) {
            // 检查手写模型文件是否已下载
            if (!com.kingzcheung.xime.handwriting.HandwritingEngine.hasModel(service)) {
                Log.w(XimeInputMethodService.TAG, "Handwriting model not found, redirecting to download")
                android.widget.Toast.makeText(
                    service, "请先下载手写模型", android.widget.Toast.LENGTH_LONG
                ).show()
                val intent = android.content.Intent(
                    service, com.kingzcheung.xime.MainActivity::class.java
                ).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra("open_fragment", "model_management")
                }
                service.startActivity(intent)
                return
            }
            service.previousSchemaId = service.rimeEngine.getCurrentSchema()
            SettingsPreferences.setCurrentSchema(service, schemaId)
            service.keyboardViewModel.switchMain(com.kingzcheung.xime.keyboard.MainType.HANDWRITING)
            // 手写模型按"用键盘时加载"管理：切到手写方案即展示手写键盘，
            // 由 HandwritingKeyboardLayout 创建时（LaunchedEffect）负责加载，
            // 此处不重复加载（避免与布局初始化并发双 bind/load）
            service.sessionController.updateSchemaName()
            return
        }
        // 切离手写方案时释放手写引擎
        com.kingzcheung.xime.handwriting.HandwritingEngine.release()
        service.keyboardViewModel.switchMain(com.kingzcheung.xime.keyboard.MainType.FULL)
        try {
            SettingsPreferences.setCurrentSchema(service, schemaId)
            // 用户自定义候选词数：先写 custom.yaml 再切方案，Rime 会自动加载
            applyPageSizeSetting(schemaId)
            // 部署/编译进行中 switchSchema 返回 false（不阻塞等待），
            // 此时不应继续触发其他 native 调用进入编译中的引擎
            if (!service.rimeEngine.switchSchema(schemaId)) {
                if (service.rimeEngine.isMaintaining()) {
                    Log.w(XimeInputMethodService.TAG, "switchSchema skipped: deployment in progress")
                    Toast.makeText(service, "词库部署中，请稍后再切换方案", Toast.LENGTH_SHORT).show()
                    return
                }
                // 引擎切换失败（常见：方案未部署/不在 schema_list，老版本升级残留）。
                // 必须回滚偏好到引擎实际方案：偏好留着目标值会让 UI 状态与引擎
                // 永久脱节（键盘已显示九键但数字键无人处理，候选栏始终 IDLE）。
                // getCurrentSchema 无会话时返回空串——此时不动偏好，保留原值由
                // IME 重启的恢复逻辑兜底，避免把空串写进偏好与 user.yaml
                val actual = service.rimeEngine.getCurrentSchema()
                if (actual.isNotEmpty()) {
                    SettingsPreferences.setCurrentSchema(service, actual)
                }
                Log.w(XimeInputMethodService.TAG, "switchSchema failed: target=$schemaId actual=$actual")
                Toast.makeText(service, "方案未部署，请在方案管理中部署后再试", Toast.LENGTH_SHORT).show()
                return
            }
            service.sessionController.restorePersistedSchemaOptions()
            if (!service.rimeEngine.isAsciiMode() &&
                service.rimeEngine.getUserConfigBool("var/option/ascii_punct") == null) {
                service.rimeEngine.setOption("ascii_punct", false)
            }
            service.sessionController.updateSchemaName()
            service.updateUI()
            // 确保键盘布局与方案匹配（如 T9 九键不应被 switchMain 重置为全键盘）
            service.keyboardViewModel.dispatch(
                com.kingzcheung.xime.ui.keyboard.KeyboardDispatchAction.AsciiModeChanged(
                    service.rimeEngine.isAsciiMode(), schemaId
                )
            )
            Toast.makeText(service, "已切换输入方案", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(XimeInputMethodService.TAG, "Failed to switch schema", e)
        }
    }
    
    private fun downloadSchema(schemaId: String) {
        service.serviceScope.launch(Dispatchers.IO) {
            service.notifyDeploymentStatus(true, "正在下载 $schemaId...")
            
            val success = SchemaConfigHelper.downloadSchema(service, schemaId)
            
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(service, "$schemaId 下载成功，请点击部署", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(service, "$schemaId 下载失败", Toast.LENGTH_SHORT).show()
                }
                service.notifyDeploymentStatus(false, "")
            }
        }
    }
    
    private fun deploy() {
        // 部署投递到 key-processing 队列，与输入/切换串行执行，避免持锁饿死输入
        service.keyRouter.postRimeJob {
            // 部署前刷新手势配置和配色方案缓存
            KeysConfigHelper.loadConfig(service)
            KeyboardThemes.reload(service)
            
            service.notifyDeploymentStatus(true, "正在部署...")
            
            val success = service.rimeEngine.deploy()
            
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(service, "部署成功", Toast.LENGTH_SHORT).show()
                    service.updateUI()
                } else {
                    Toast.makeText(service, "部署失败", Toast.LENGTH_SHORT).show()
                }
                service.notifyDeploymentStatus(false, "")
            }
        }
    }
    
    private fun updateKeyboardHeightPreview(heightDp: Int) {
        service.keyboardContainer.updateHeight(heightDp)
    }
    
    internal fun setKeyboardHeight(heightDp: Int) {
        val isLandscape = service.resources.configuration.screenWidthDp > service.resources.configuration.screenHeightDp
        SettingsPreferences.setKeyboardHeightDp(service, heightDp, isLandscape)
        service.uiState.value = service.uiState.value.copy(keyboardHeightDp = heightDp)
        Toast.makeText(service, "键盘高度已调整", Toast.LENGTH_SHORT).show()
    }

    internal fun toggleFloatingMode(enabled: Boolean, navBarDp: Int = 0) {
        val isLandscape = service.resources.configuration.screenWidthDp > service.resources.configuration.screenHeightDp
        SettingsPreferences.setFloatingMode(service, enabled, isLandscape)
        SettingsPreferences.setFloatingMode(service, enabled, !isLandscape)
        val loadedX = SettingsPreferences.getFloatingOffsetX(service, isLandscape)
        val loadedY = SettingsPreferences.getFloatingOffsetY(service, isLandscape)
        val screenW = service.resources.configuration.screenWidthDp
        val screenH = service.resources.configuration.screenHeightDp
        val portraitWidth = minOf(screenW, screenH)
        val cardWidth = (portraitWidth * 0.85f).roundToInt()
        val halfMargin = maxOf(0, (screenW - cardWidth) / 2)
        val cappedKbH = SettingsPreferences.getKeyboardHeightDp(service, isLandscape).coerceAtMost((screenH * 8) / 10)
        val clampedX = loadedX.coerceIn(-halfMargin, halfMargin)
        service.uiState.value = service.uiState.value.copy(
            isFloatingMode = enabled,
            floatingOffsetX = clampedX,
            floatingOffsetY = 0,
        )
        if (enabled) {
            service.closeToolPanel()
            service.currentEffectiveKeyboardHeight = cappedKbH + 18 + 50 + service.uiState.value.keyboardBottomPaddingDp
        }
        service.applyWindowBackground()
    }

}