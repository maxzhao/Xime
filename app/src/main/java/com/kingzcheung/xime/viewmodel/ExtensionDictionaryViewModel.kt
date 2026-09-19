package com.kingzcheung.xime.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.settings.ExtensionDictionaryManager
import com.kingzcheung.xime.settings.ExtensionDictionarySnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


data class ExtensionDictionaryUiState(
    val items: List<ExtensionDictionarySnapshot> = emptyList(),
    val busyId: String? = null,
    val busyMessage: String? = null,
    val resultMessage: String? = null,
)

class ExtensionDictionaryViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val _uiState = MutableStateFlow(ExtensionDictionaryUiState())
    val uiState: StateFlow<ExtensionDictionaryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(items = ExtensionDictionaryManager.snapshots(context)) }
    }

    fun download(dictionaryId: String) {
        runOperation(dictionaryId, "正在下载并转换词库…") {
            ExtensionDictionaryManager.download(context, dictionaryId)
        }
    }

    fun setEnabled(dictionaryId: String, enabled: Boolean) {
        runOperation(
            dictionaryId,
            if (enabled) "正在下载、编译并部署词库…" else "正在清理并重新部署词库…",
        ) {
            ExtensionDictionaryManager.setEnabled(context, dictionaryId, enabled)
        }
    }

    fun deleteDownload(dictionaryId: String) {
        runOperation(dictionaryId, "正在删除词库…") {
            ExtensionDictionaryManager.deleteDownload(context, dictionaryId)
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(resultMessage = null) }
    }

    private fun runOperation(
        dictionaryId: String,
        message: String,
        block: suspend () -> com.kingzcheung.xime.settings.ExtensionDictionaryOperationResult,
    ) {
        if (_uiState.value.busyId != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(busyId = dictionaryId, busyMessage = message, resultMessage = null) }
            val result = block()
            _uiState.update {
                it.copy(
                    items = ExtensionDictionaryManager.snapshots(context),
                    busyId = null,
                    busyMessage = null,
                    resultMessage = result.message,
                )
            }
        }
    }
}
