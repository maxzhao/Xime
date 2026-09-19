package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.settings.ExtensionDictionarySnapshot
import com.kingzcheung.xime.viewmodel.ExtensionDictionaryViewModel

@Composable
fun ExtensionDictionaryPanel() {
    val viewModel: ExtensionDictionaryViewModel = viewModel(key = "extension_dictionary")
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "启用时会自动下载、转换为纯中文词表，并部署到五笔混拼、全拼和九键拼音。停用后会从活动词典中清理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                val status = uiState.busyMessage ?: uiState.resultMessage
                if (status != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.busyId != null) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            status,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        if (uiState.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            uiState.items.groupBy { it.definition.group }.forEach { (group, entries) ->
                item(key = "group_$group") {
                    Text(
                        group,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                items(entries, key = { it.definition.id }) { item ->
                    ExtensionDictionaryCard(
                        item = item,
                        globalBusy = uiState.busyId != null,
                        itemBusy = uiState.busyId == item.definition.id,
                        onDownload = { viewModel.download(item.definition.id) },
                        onEnabledChange = { viewModel.setEnabled(item.definition.id, it) },
                        onDelete = { viewModel.deleteDownload(item.definition.id) },
                        onOpenSource = { uriHandler.openUri(item.definition.sourcePage) },
                        onOpenLicense = { uriHandler.openUri(item.definition.licenseUrl) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExtensionDictionaryCard(
    item: ExtensionDictionarySnapshot,
    globalBusy: Boolean,
    itemBusy: Boolean,
    onDownload: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onOpenSource: () -> Unit,
    onOpenLicense: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.definition.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        item.definition.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (itemBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Switch(
                    checked = item.enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = !globalBusy,
                )
            }

            Spacer(Modifier.height(8.dp))
            val downloadState = when {
                item.downloadedEntries != null -> "已下载 ${item.downloadedEntries} 条纯中文词汇"
                item.downloaded -> "已下载"
                else -> "未下载"
            }
            Text(
                "${item.definition.approximateEntries} · ${item.definition.license} · $downloadState",
                style = MaterialTheme.typography.labelSmall,
                color = if (item.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onOpenSource, enabled = !globalBusy) { Text("来源") }
                TextButton(onClick = onOpenLicense, enabled = !globalBusy) { Text("许可") }
                if (!item.downloaded) {
                    TextButton(onClick = onDownload, enabled = !globalBusy) { Text("仅下载") }
                } else if (!item.enabled) {
                    TextButton(onClick = onDelete, enabled = !globalBusy) { Text("删除") }
                }
            }
        }
    }
}
