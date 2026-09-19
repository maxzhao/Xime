package com.kingzcheung.xime.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.inputmethod.InputConnection
import android.widget.Toast
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.speech.AsrBackendFactory
import com.kingzcheung.xime.speech.RecognitionState
import com.kingzcheung.xime.speech.SpeechRecognitionManager
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.FileLogger

class VoiceRecognitionHandler(
    private val context: Context,
    private val onStateChanged: (InputUIState) -> Unit,
    private val getState: () -> InputUIState,
    private val getInputConnection: () -> InputConnection?,
    private val onVoiceComplete: () -> Unit = {},
    private val onAmplitudeChanged: (Float) -> Unit = {},
    private val onSpectrumChanged: (FloatArray) -> Unit = {},
    /** 语音向输入框写入 composing 文本时回调（标记 composing 区域存在，供 endComposingInputBox 判断）。 */
    private val onComposingWritten: () -> Unit = {},
    /** TYPE_NULL 等受限宿主不支持富文本 composing/回删改写，只能一次性提交最终文本。 */
    private val isRawInputTarget: () -> Boolean = { false },
    private val mainHandlerFactory: () -> Handler = { Handler(Looper.getMainLooper()) }
) {
    companion object {
        private const val TAG = "VoiceRecognition"
        private const val RAW_PAUSE_COMMIT_DELAY_MS = 1000L
    }

    private lateinit var speechRecognitionManager: SpeechRecognitionManager

    var textBeforeVoiceInput = ""
    var textLengthBeforeVoiceInput = 0

    fun initialize() {
        FileLogger.i(TAG, "Initializing speech recognition system")

        speechRecognitionManager = SpeechRecognitionManager(context)

        speechRecognitionManager.setCallbacks(
            onResult = { text ->
                handleSpeechResult(text)
            },
            onPartialResult = { text ->
                handlePartialResult(text)
            },
            onStateChange = { state ->
                handleSpeechStateChange(state)
            },
            onError = { error, userVisible ->
                handleSpeechError(error, userVisible)
            },
            onAmplitude = { amplitude ->
                handleAmplitudeUpdate(amplitude)
            },
            onSpectrum = { spectrum ->
                handleSpectrumUpdate(spectrum)
            }
        )

        val providerName = resolveProviderName()

        onStateChanged(getState().copy(voicePluginName = providerName))
        FileLogger.i(TAG, "STT provider: $providerName")

        // 若"使用本地模型"开关已开启，启动时即加载模型并常驻，
        // 保证语音时绝不现场加载模型（避免丢开头音频）
        if (SettingsPreferences.isSttUseLocal(context) &&
            AsrBackendFactory.getLocalName() != null
        ) {
            Thread {
                AsrBackendFactory.warmup(context)
            }.start()
        }
    }

    private val mainHandler by lazy { mainHandlerFactory() }
    private val delayedPreStartRunnable = Runnable {
        if (::speechRecognitionManager.isInitialized) {
            speechRecognitionManager.startPreStart()
        }
    }

    fun startDelayedPreStart(delayMs: Long = 150) {
        mainHandler.removeCallbacks(delayedPreStartRunnable)
        mainHandler.postDelayed(delayedPreStartRunnable, delayMs)
    }

    fun cancelPreStart() {
        mainHandler.removeCallbacks(delayedPreStartRunnable)
        if (::speechRecognitionManager.isInitialized) {
            speechRecognitionManager.cancelPreStart()
        }
    }

    fun startRecognition() {
        if (!::speechRecognitionManager.isInitialized) {
            Log.e(TAG, "speechRecognitionManager not initialized")
            onStateChanged(getState().copy(
                isVoiceMode = false,
                voiceSticky = false,
                voiceRecognitionState = RecognitionState.ERROR
            ))
            return
        }

        if (isRawInputTarget()) {
            cancelRawPauseCommit()
            rawCommittedPartial = ""
            textBeforeVoiceInput = ""
            textLengthBeforeVoiceInput = 0
        } else {
            textBeforeVoiceInput = getInputConnection()?.getTextBeforeCursor(1000, 0)?.toString() ?: ""
            textLengthBeforeVoiceInput = textBeforeVoiceInput.length
        }

        val providerName = resolveProviderName()
        onStateChanged(getState().copy(voicePluginName = providerName))

        speechRecognitionManager.startRecognition()
    }

    fun stopRecognition() {
        if (::speechRecognitionManager.isInitialized) {
            speechRecognitionManager.stopRecognition()
        }
        // handleFinalResult is now called from within handleSpeechResult
        // when the final stopRecognition result arrives
    }

    fun release() {
        cancelRawPauseCommit()
        if (::speechRecognitionManager.isInitialized) {
            speechRecognitionManager.release()
        }
    }

    fun isInitialized(): Boolean = ::speechRecognitionManager.isInitialized

    private fun resolveProviderName(): String {
        // 用户开启"本地识别"且当前构建支持离线语音时，优先显示本地引擎名
        if (SettingsPreferences.isSttUseLocal(context)) {
            val localName = AsrBackendFactory.getLocalName()
            if (localName != null) return localName
        }
        val enabledPlugins = ExtensionManager.getEnabledAsrPlugins(context)
        if (enabledPlugins.isNotEmpty()) {
            val selectedId = SettingsPreferences.getSttOnlinePluginId(context)
            val selected = enabledPlugins.firstOrNull { it.first == selectedId }
                ?: enabledPlugins.firstOrNull()
            if (selected != null) {
                return ExtensionManager.getAllInstalledPlugins()
                    .firstOrNull { it.id == selected.first }?.name ?: selected.first
            }
        }
        return "未配置"
    }

    private var lastPartialText = ""
    private var lastAmplitudeUpdate = 0L
    private var smoothedAmplitude = 0f
    private var smoothedSpectrum = FloatArray(16)
    // 抬起时已提交当前识别文本后，置真以忽略随后可能迟到的重复最终结果
    private var suppressDuplicateFinal = false
    // TYPE_NULL 会话已自动提交的 partial 前缀；后续累计 partial 只展示/提交新增部分。
    private var rawCommittedPartial = ""
    private val rawPauseCommitRunnable = Runnable { commitRawPendingAfterPause() }
    // 输入法窗口隐藏等场景：丢弃本会话，迟到结果不得写入任何输入框
    private var sessionAbandoned = false
    private var errorToast: Toast? = null

    /** 输入法隐藏/切换输入框时调用：丢弃当前会话的未识别文本，忽略迟到的最终结果 */
    fun abandonSession() {
        cancelRawPauseCommit()
        sessionAbandoned = true
        lastPartialText = ""
        rawCommittedPartial = ""
    }

    // 语音按钮长按抬起时调用：立即提交当前已识别的文本（不依赖可能被断连竞态吞掉的异步最终结果）
    fun commitPendingOnRelease() {
        if (sessionAbandoned) return
        cancelRawPauseCommit()
        val ic = getInputConnection()
        val partial = lastPartialText
        Log.d(TAG, "commitPendingOnRelease: ic=${ic != null}, partial='$partial', suppress=$suppressDuplicateFinal")
        if (ic == null) return
        if (isRawInputTarget()) {
            val pending = pendingRawText(partial)
            if (pending.isNotBlank()) {
                ic.commitText(pending, 1)
                rawCommittedPartial = partial
            }
            // 即使停顿时已自动提交完，也要忽略 stop 后迟到的同段 final。
            suppressDuplicateFinal = partial.isNotBlank() || rawCommittedPartial.isNotBlank()
            lastPartialText = ""
            onStateChanged(getState().copy(voiceRecognizedText = ""))
            return
        }
        if (partial.isEmpty()) return
        val punctuatedText = addPunctuation(partial)
        commitFinal(ic, punctuatedText, partial)
        suppressDuplicateFinal = true
        lastPartialText = ""
    }

    private fun cancelRawPauseCommit() {
        mainHandler.removeCallbacks(rawPauseCommitRunnable)
    }

    /** 返回 TYPE_NULL 当前累计结果中尚未写入宿主的部分。 */
    private fun pendingRawText(text: String): String {
        if (rawCommittedPartial.isEmpty()) return text
        if (text.startsWith(rawCommittedPartial)) {
            return text.substring(rawCommittedPartial.length)
        }
        // ASR 开始新一句时 partial 通常从头计数；不能把上一句前缀带入新一句。
        rawCommittedPartial = ""
        return text
    }

    private fun commitRawPendingAfterPause() {
        if (sessionAbandoned || !isRawInputTarget()) return
        val partial = lastPartialText
        val pending = pendingRawText(partial)
        if (pending.isBlank()) return
        val ic = getInputConnection() ?: return
        ic.commitText(pending, 1)
        rawCommittedPartial = partial
        onStateChanged(getState().copy(voiceRecognizedText = ""))
        Log.d(TAG, "TYPE_NULL pause commit: '$pending'")
    }

    internal fun handleSpeechResult(text: String) {
        Log.d(TAG, "Speech result (final): $text")

        if (sessionAbandoned) {
            sessionAbandoned = false
            lastPartialText = ""
            onVoiceComplete()
            return
        }

        if (suppressDuplicateFinal) {
            // 抬起时已提交，忽略迟到的重复最终结果
            suppressDuplicateFinal = false
            lastPartialText = ""
            onVoiceComplete()
            return
        }

        val ic = getInputConnection()
        if (isRawInputTarget()) {
            cancelRawPauseCommit()
            val pending = pendingRawText(text)
            if (ic != null && pending.isNotBlank() && !text.startsWith("错误:")) {
                ic.commitText(pending, 1)
                rawCommittedPartial = text
            }
            lastPartialText = ""
            onStateChanged(getState().copy(voiceRecognizedText = ""))
            // TYPE_NULL 常驻语音按句 final 后继续监听；Ctrl+0/普通按键仍可显式结束会话。
            if (getState().isVoiceMode) return
        } else {
            val cleanText = text.replace(" ", "")
            if (ic != null && cleanText.isNotEmpty() && !cleanText.startsWith("错误:")) {
                val punctuatedText = addPunctuation(cleanText)
                commitFinal(ic, punctuatedText, lastPartialText)
            }
        }
        lastPartialText = ""
        onVoiceComplete()
    }
    
    // 增量语音模式：先结束 composing，再只提交增量，避免重复与整段重写。
    private fun commitFinal(ic: InputConnection, finalText: String, partial: String) {
        ic.finishComposingText()
        if (partial.isNotEmpty() && finalText.startsWith(partial)) {
            val remainder = finalText.substring(partial.length)
            if (remainder.isNotEmpty()) {
                ic.commitText(remainder, 1)
            } else {
                Log.d(TAG, "commitFinal: remainder empty, only finished composing")
            }
        } else {
            // 最终结果与部分结果不一致：删除已上屏的部分，再提交完整结果
            if (partial.isNotEmpty()) {
                ic.deleteSurroundingText(partial.length, 0)
            }
            ic.commitText(finalText, 1)
        }
        Log.d(TAG, "commitFinal: final='$finalText', partial='$partial'")
    }
    
    private fun addPunctuation(text: String): String {
        val cleanText = text.trim().replace(" ", "")
        if (cleanText.isEmpty()) return text

        // 若文本末尾已带句末标点（如 funasr/volc 等自带标点的后端），不再追加，避免"。。"
        if (cleanText.last() in "。！？；：，、；：,.!?;:，") return cleanText

        return "$cleanText${heuristicPunctuation(cleanText)}"
    }

    private fun heuristicPunctuation(text: String): String {
        return when {
            text.any { it in "吗呢么吧" } || text.contains("什么") || text.contains("怎么") || text.contains("为什么") || text.contains("如何") || text.contains("哪") -> "？"
            text.length < 4 -> "，"
            else -> "。"
        }
    }

    internal fun handlePartialResult(text: String) {
        if (sessionAbandoned || suppressDuplicateFinal) return
        if (text == lastPartialText) return
        lastPartialText = text
        Log.d(TAG, "Speech result (partial): $text")

        if (isRawInputTarget()) {
            cancelRawPauseCommit()
            val pending = pendingRawText(text)
            if (pending.isBlank()) {
                onStateChanged(getState().copy(voiceRecognizedText = ""))
                return
            }
            onStateChanged(getState().copy(voiceRecognizedText = pending))
            mainHandler.postDelayed(rawPauseCommitRunnable, RAW_PAUSE_COMMIT_DELAY_MS)
            return
        }

        // 普通文本框保持现有行为：过滤空格后把 partial 写入 composing 区域。
        val cleanText = text.replace(" ", "")
        if (cleanText.isEmpty()) return

        val ic = getInputConnection()
        if (ic != null) {
            onComposingWritten()
            ic.setComposingText(cleanText, 1)
        }
        onStateChanged(getState().copy(voiceRecognizedText = cleanText))
    }

    private fun handleSpeechStateChange(state: RecognitionState) {
        Log.d(TAG, "Speech state changed: $state")
        if (state == RecognitionState.LISTENING) {
            lastPartialText = ""
            suppressDuplicateFinal = false
            sessionAbandoned = false
        }
        onStateChanged(getState().copy(voiceRecognitionState = state))
    }

    private fun handleSpeechError(error: String, userVisible: Boolean) {
        Log.e(TAG, "Speech error: $error")
        FileLogger.e(TAG, "Speech error: $error")
        cancelRawPauseCommit()
        rawCommittedPartial = ""
        lastPartialText = ""
        if (userVisible && error.isNotBlank()) {
            errorToast?.cancel()
            errorToast = Toast.makeText(context, error, Toast.LENGTH_LONG)
            errorToast?.show()
        }
        onVoiceComplete()
    }

    private fun handleAmplitudeUpdate(amplitude: Float) {
        val now = System.currentTimeMillis()
        if (now - lastAmplitudeUpdate < 80) return
        lastAmplitudeUpdate = now
        smoothedAmplitude = smoothedAmplitude * 0.45f + amplitude * 0.55f
        onAmplitudeChanged(smoothedAmplitude)
    }

    private fun handleSpectrumUpdate(spectrum: FloatArray) {
        val smoothed = smoothedSpectrum
        for (i in spectrum.indices) {
            smoothed[i] = smoothed[i] * 0.5f + spectrum[i] * 0.5f
        }
        onSpectrumChanged(smoothed.copyOf())
    }
}