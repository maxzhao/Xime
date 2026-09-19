package com.kingzcheung.xime.settings

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit


data class ExtensionDictionarySnapshot(
    val definition: ExtensionDictionaryDefinition,
    val downloaded: Boolean,
    val enabled: Boolean,
    val downloadedEntries: Long?,
)

data class ExtensionDictionaryOperationResult(
    val success: Boolean,
    val message: String,
)

/** Owns downloaded sources, enabled state, generated aggregate dictionaries, and deployment. */
object ExtensionDictionaryManager {
    private const val TAG = "ExtensionDictManager"
    private const val PREFS_NAME = "extension_dictionaries"
    private const val PREF_ENABLED_IDS = "enabled_ids"
    private const val PREF_COUNT_PREFIX = "entry_count_"
    private const val PREF_SOURCE_PREFIX = "source_url_"
    private const val ACTIVE_PINYIN_DICTIONARY_NAME = "xime_extension_words"
    private const val ACTIVE_WUBI_DICTIONARY_NAME = "xime_extension_words_wubi"
    private const val PINYIN_DICTIONARY_NAME = "xime_pinyin"
    private const val WUBI_DICTIONARY_NAME = "xime_wubi86"
    private const val MAX_SOURCE_BYTES = 100L * 1024L * 1024L

    private val operationMutex = Mutex()
    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun snapshots(context: Context): List<ExtensionDictionarySnapshot> {
        val enabled = enabledIds(context)
        return ExtensionDictionaryCatalog.dictionaries.map { definition ->
            val downloaded = isCurrentDownload(context, definition)
            ExtensionDictionarySnapshot(
                definition = definition,
                downloaded = downloaded,
                enabled = definition.id in enabled && downloaded,
                downloadedEntries = if (downloaded) downloadedEntryCount(context, definition.id) else null,
            )
        }
    }

    suspend fun download(
        context: Context,
        dictionaryId: String,
    ): ExtensionDictionaryOperationResult = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val definition = ExtensionDictionaryCatalog.byId[dictionaryId]
                ?: return@withContext ExtensionDictionaryOperationResult(false, "未知词库")
            downloadLocked(context.applicationContext, definition)
        }
    }

    suspend fun setEnabled(
        context: Context,
        dictionaryId: String,
        enabled: Boolean,
    ): ExtensionDictionaryOperationResult = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val definition = ExtensionDictionaryCatalog.byId[dictionaryId]
                ?: return@withContext ExtensionDictionaryOperationResult(false, "未知词库")
            val oldIds = enabledIds(appContext)
            val currentlyEnabled = dictionaryId in oldIds && isCurrentDownload(appContext, definition)
            if (enabled == currentlyEnabled) {
                return@withContext ExtensionDictionaryOperationResult(true, if (enabled) "词库已启用" else "词库已停用")
            }

            if (enabled && !isCurrentDownload(appContext, definition)) {
                val downloadResult = downloadLocked(appContext, definition)
                if (!downloadResult.success) return@withContext downloadResult
            }

            val newIds = oldIds.toMutableSet().apply {
                if (enabled) add(dictionaryId) else remove(dictionaryId)
            }.filterTo(linkedSetOf()) { id ->
                ExtensionDictionaryCatalog.byId[id]?.let { isCurrentDownload(appContext, it) } == true
            }

            try {
                ensureEngineInitialized(appContext)
                writeActiveDictionary(appContext, newIds)
                val deployed = RimeConfigHelper.ensureDeployment(appContext)
                if (!deployed) throw IOException("Rime 部署失败")
                saveEnabledIds(appContext, newIds)
                ExtensionDictionaryOperationResult(
                    true,
                    if (enabled) "${definition.name}已启用" else "${definition.name}已停用并清理",
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to ${if (enabled) "enable" else "disable"} $dictionaryId", e)
                runCatching {
                    writeActiveDictionary(appContext, oldIds)
                    RimeConfigHelper.ensureDeployment(appContext)
                }.onFailure { rollbackError -> Log.e(TAG, "Failed to restore previous dictionary state", rollbackError) }
                ExtensionDictionaryOperationResult(false, e.message ?: "词库部署失败")
            }
        }
    }

    suspend fun deleteDownload(
        context: Context,
        dictionaryId: String,
    ): ExtensionDictionaryOperationResult = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            if (dictionaryId in enabledIds(appContext)) {
                return@withContext ExtensionDictionaryOperationResult(false, "请先停用该词库")
            }
            val definition = ExtensionDictionaryCatalog.byId[dictionaryId]
                ?: return@withContext ExtensionDictionaryOperationResult(false, "未知词库")
            val deleted = !downloadedFile(appContext, dictionaryId).exists() || downloadedFile(appContext, dictionaryId).delete()
            if (deleted) {
                prefs(appContext).edit()
                    .remove(PREF_COUNT_PREFIX + dictionaryId)
                    .remove(PREF_SOURCE_PREFIX + dictionaryId)
                    .apply()
            }
            ExtensionDictionaryOperationResult(deleted, if (deleted) "已删除${definition.name}" else "删除失败")
        }
    }

    /** Ensures schemas always have valid aggregate dictionary sources, including after app upgrade. */
    fun ensureArtifacts(context: Context) {
        val appContext = context.applicationContext
        val validEnabled = enabledIds(appContext).filterTo(linkedSetOf()) { id ->
            ExtensionDictionaryCatalog.byId[id]?.let { isCurrentDownload(appContext, it) } == true
        }
        if (validEnabled != enabledIds(appContext)) saveEnabledIds(appContext, validEnabled)
        ensureAggregateSources(appContext)
        ensureSchemaPatches(appContext)
        val marker = enabledMarker(validEnabled)
        val activeFiles = listOf(
            activePinyinDictionaryFile(appContext),
            activeWubiDictionaryFile(appContext),
        )
        if (activeFiles.any { currentEnabledMarker(it) != marker }) {
            writeActiveDictionary(appContext, validEnabled)
        }
    }

    internal fun pinyinAggregateSource(): String = """
        # Xime managed aggregate dictionary. Do not edit manually.
        ---
        name: $PINYIN_DICTIONARY_NAME
        version: "1"
        sort: by_weight
        use_preset_vocabulary: false
        import_tables:
          - pinyin_simp
          - pinyin_simp_ext
          - $ACTIVE_PINYIN_DICTIONARY_NAME
        ...
    """.trimIndent() + "\n"

    internal fun wubiAggregateSource(): String = """
        # Xime managed aggregate dictionary. Do not edit manually.
        ---
        name: $WUBI_DICTIONARY_NAME
        version: "1"
        sort: by_weight
        use_preset_vocabulary: false
        import_tables:
          - wubi86
          - wubi86_extra
          - $ACTIVE_WUBI_DICTIONARY_NAME
        encoder:
          exclude_patterns:
            - "^z.*$"
          rules:
            - length_equal: 2
              formula: "AaAbBaBb"
            - length_equal: 3
              formula: "AaBaCaCb"
            - length_in_range: [4, 32]
              formula: "AaBaCaZa"
        ...
    """.trimIndent() + "\n"

    private fun downloadLocked(
        context: Context,
        definition: ExtensionDictionaryDefinition,
    ): ExtensionDictionaryOperationResult {
        val directory = sourceDirectory(context).apply { mkdirs() }
        val temp = File(directory, ".${definition.id}.download")
        var lastError: Exception? = null
        for (url in downloadCandidates(definition.sourceUrl)) {
            temp.delete()
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Xime/${android.os.Build.VERSION.SDK_INT}")
                    .build()
                val stats = downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("下载失败：HTTP ${response.code}")
                    val body = response.body
                    val length = body.contentLength()
                    if (length > MAX_SOURCE_BYTES) throw IOException("词库文件超过 100 MB")
                    body.byteStream().reader(Charsets.UTF_8).use { reader ->
                        temp.outputStream().bufferedWriter(Charsets.UTF_8).use { writer ->
                            ExtensionDictionaryConverter.convert(reader, writer, definition.format)
                        }
                    }
                }
                if (stats.accepted == 0L) throw IOException("没有找到符合要求的中文词汇")
                replaceFile(temp, downloadedFile(context, definition.id))
                prefs(context).edit()
                    .putLong(PREF_COUNT_PREFIX + definition.id, stats.accepted)
                    .putString(PREF_SOURCE_PREFIX + definition.id, definition.sourceUrl)
                    .apply()
                return ExtensionDictionaryOperationResult(true, "${definition.name}下载完成，共 ${stats.accepted} 条")
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Dictionary endpoint failed for ${definition.id}: $url", e)
            }
        }
        temp.delete()
        val error = lastError
        Log.e(TAG, "Failed to download ${definition.id} from all endpoints", error)
        val message = when (error) {
            is SocketTimeoutException -> "下载超时，请检查网络后重试"
            is UnknownHostException, is ConnectException -> "无法连接词库服务器，请检查网络"
            else -> error?.message ?: "下载失败"
        }
        return ExtensionDictionaryOperationResult(false, message)
    }

    internal fun downloadCandidates(sourceUrl: String): List<String> {
        val prefix = "https://raw.githubusercontent.com/"
        if (!sourceUrl.startsWith(prefix)) return listOf(sourceUrl)
        val parts = sourceUrl.removePrefix(prefix).split('/', limit = 4)
        if (parts.size != 4) return listOf(sourceUrl)
        val mirror = "https://cdn.jsdelivr.net/gh/${parts[0]}/${parts[1]}@${parts[2]}/${parts[3]}"
        return listOf(mirror, sourceUrl)
    }

    private fun writeActiveDictionary(context: Context, enabledIds: Set<String>) {
        ensureAggregateSources(context)
        ensureSchemaPatches(context)
        val rimeDir = File(context.filesDir, "rime").apply { mkdirs() }
        val tempDatabase = File(context.cacheDir, "extension_dictionary_merge.db")
        val tempPinyinDictionary = File(rimeDir, ".$ACTIVE_PINYIN_DICTIONARY_NAME.dict.yaml.tmp")
        val tempWubiDictionary = File(rimeDir, ".$ACTIVE_WUBI_DICTIONARY_NAME.dict.yaml.tmp")
        tempDatabase.delete()
        tempPinyinDictionary.delete()
        tempWubiDictionary.delete()

        val database = SQLiteDatabase.openOrCreateDatabase(tempDatabase, null)
        try {
            database.execSQL("CREATE TABLE words(word TEXT PRIMARY KEY, weight INTEGER NOT NULL) WITHOUT ROWID")
            val insert = database.compileStatement("INSERT OR IGNORE INTO words(word, weight) VALUES(?, ?)")
            try {
                ExtensionDictionaryCatalog.dictionaries
                    .asSequence()
                    .filter { it.id in enabledIds }
                    .forEach { definition ->
                        val source = downloadedFile(context, definition.id)
                        if (!source.isFile) return@forEach
                        database.beginTransaction()
                        try {
                            source.useLines(Charsets.UTF_8) { lines ->
                                lines.forEach { line ->
                                    val tab = line.lastIndexOf('\t')
                                    if (tab <= 0) return@forEach
                                    val word = line.substring(0, tab)
                                    val weight = line.substring(tab + 1).toLongOrNull() ?: 100L
                                    insert.clearBindings()
                                    insert.bindString(1, word)
                                    insert.bindLong(2, weight)
                                    insert.executeInsert()
                                }
                            }
                            database.setTransactionSuccessful()
                        } finally {
                            database.endTransaction()
                        }
                    }
            } finally {
                insert.close()
            }

            tempPinyinDictionary.bufferedWriter(Charsets.UTF_8).use { pinyinWriter ->
                tempWubiDictionary.bufferedWriter(Charsets.UTF_8).use { wubiWriter ->
                    pinyinWriter.append(
                        managedDictionaryHeader(
                            ACTIVE_PINYIN_DICTIONARY_NAME,
                            enabledIds,
                            includeWeights = true,
                        ),
                    )
                    wubiWriter.append(
                        managedDictionaryHeader(
                            ACTIVE_WUBI_DICTIONARY_NAME,
                            enabledIds,
                            includeWeights = false,
                        ),
                    )
                    database.rawQuery("SELECT word, weight FROM words ORDER BY word", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val word = cursor.getString(0)
                            val weight = cursor.getLong(1)
                            pinyinWriter.append(managedDictionaryEntryLine(word, weight, includeWeight = true))
                            wubiWriter.append(managedDictionaryEntryLine(word, weight, includeWeight = false))
                        }
                    }
                }
            }
        } finally {
            database.close()
            tempDatabase.delete()
        }
        replaceFile(tempPinyinDictionary, activePinyinDictionaryFile(context))
        replaceFile(tempWubiDictionary, activeWubiDictionaryFile(context))
    }

    /**
     * Pinyin keeps source frequencies, while Wubi deliberately omits them. Imported words are
     * auto-encoded by the Wubi encoder; giving them positive weights would move them ahead of the
     * original Wubi86 entries and break first-candidate and four/five-key commit behavior.
     */
    internal fun managedDictionaryHeader(
        dictionaryName: String,
        enabledIds: Set<String>,
        includeWeights: Boolean,
    ): String = buildString {
        appendLine("# Xime managed words. Rebuilt from enabled extension dictionaries.")
        appendLine(enabledMarker(enabledIds))
        appendLine("---")
        appendLine("name: $dictionaryName")
        appendLine("version: \"1\"")
        appendLine("sort: ${if (includeWeights) "by_weight" else "original"}")
        appendLine("use_preset_vocabulary: false")
        appendLine("columns:")
        appendLine("  - text")
        if (includeWeights) appendLine("  - weight")
        appendLine("...")
    }

    internal fun managedDictionaryEntryLine(
        word: String,
        weight: Long,
        includeWeight: Boolean,
    ): String = buildString {
        append(word)
        if (includeWeight) append('\t').append(weight)
        append('\n')
    }

    private fun ensureEngineInitialized(context: Context) {
        if (RimeEngine.isInitialized()) return
        val (userDataDir, sharedDataDir) = RimeConfigHelper.initializeRimeData(context)
        RimeEngine.getInstance().initialize(userDataDir, sharedDataDir)
        if (!RimeEngine.isInitialized()) throw IOException("Rime 引擎初始化失败")
    }

    private fun ensureAggregateSources(context: Context) {
        val rimeDir = File(context.filesDir, "rime").apply { mkdirs() }
        writeIfChanged(File(rimeDir, "$PINYIN_DICTIONARY_NAME.dict.yaml"), pinyinAggregateSource())
        writeIfChanged(File(rimeDir, "$WUBI_DICTIONARY_NAME.dict.yaml"), wubiAggregateSource())
    }

    private fun ensureSchemaPatches(context: Context) {
        val rimeDir = File(context.filesDir, "rime").apply { mkdirs() }
        ensurePatchValue(rimeDir, "pinyin_simp", "translator/dictionary", PINYIN_DICTIONARY_NAME)
        ensurePatchValue(rimeDir, "pinyin_simp", "translator/user_dict", "pinyin_simp")
        ensurePatchValue(rimeDir, "t9_pinyin", "translator/dictionary", PINYIN_DICTIONARY_NAME)
        ensurePatchValue(rimeDir, "t9_pinyin", "translator/user_dict", "pinyin_simp")
        ensurePatchValue(rimeDir, "wubi86_pinyin", "translator/dictionary", WUBI_DICTIONARY_NAME)
        ensurePatchValue(rimeDir, "wubi86_pinyin", "translator/user_dict", "wubi86")
        ensurePatchValue(rimeDir, "wubi86_pinyin", "reverse_lookup/dictionary", PINYIN_DICTIONARY_NAME)
    }

    private fun ensurePatchValue(rimeDir: File, schemaId: String, key: String, value: String) {
        val file = File(rimeDir, "$schemaId.custom.yaml")
        val original = if (file.isFile) file.readText(Charsets.UTF_8) else ""
        val updated = applyManagedPatchValue(original, key, value)
        if (updated != original) writeIfChanged(file, updated)
    }

    internal fun applyManagedPatchValue(original: String, key: String, value: String): String {
        val replacement = "  \"$key\": $value"
        val keyPattern = Regex(
            "^[ \\t]*[\\\"']?${Regex.escape(key)}[\\\"']?\\s*:.*$",
            RegexOption.MULTILINE,
        )
        if (keyPattern.containsMatchIn(original)) {
            return keyPattern.replace(original, replacement)
        }
        val cleaned = original.trimEnd('\n', '\r', ' ').removeSuffix("...").trimEnd()
        val patchLine = Regex("^patch:\\s*$", RegexOption.MULTILINE).find(cleaned)
        return if (patchLine != null) {
            val at = patchLine.range.last + 1
            cleaned.substring(0, at) + "\n$replacement" + cleaned.substring(at) + "\n"
        } else if (cleaned.isEmpty()) {
            "patch:\n$replacement\n"
        } else {
            "$cleaned\n\npatch:\n$replacement\n"
        }
    }

    private fun writeIfChanged(file: File, content: String) {
        if (file.isFile && runCatching { file.readText() }.getOrNull() == content) return
        val temp = File(file.parentFile, ".${file.name}.tmp")
        temp.writeText(content)
        replaceFile(temp, file)
    }

    private fun replaceFile(source: File, target: File) {
        target.parentFile?.mkdirs()
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun enabledMarker(ids: Set<String>): String = "# enabled: ${ids.sorted().joinToString(",")}"

    private fun sourceDirectory(context: Context): File = File(context.filesDir, "extension-dictionaries")

    private fun downloadedFile(context: Context, id: String): File = File(sourceDirectory(context), "$id.txt")

    private fun isCurrentDownload(context: Context, definition: ExtensionDictionaryDefinition): Boolean =
        downloadedFile(context, definition.id).isFile &&
            prefs(context).getString(PREF_SOURCE_PREFIX + definition.id, null) == definition.sourceUrl

    private fun activePinyinDictionaryFile(context: Context): File =
        File(context.filesDir, "rime/$ACTIVE_PINYIN_DICTIONARY_NAME.dict.yaml")

    private fun activeWubiDictionaryFile(context: Context): File =
        File(context.filesDir, "rime/$ACTIVE_WUBI_DICTIONARY_NAME.dict.yaml")

    private fun currentEnabledMarker(file: File): String? = if (file.isFile) {
        runCatching { file.bufferedReader().useLines { it.take(2).lastOrNull() } }.getOrNull()
    } else {
        null
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun enabledIds(context: Context): Set<String> =
        prefs(context).getStringSet(PREF_ENABLED_IDS, emptySet()).orEmpty().toSet()

    private fun saveEnabledIds(context: Context, ids: Set<String>) {
        prefs(context).edit().putStringSet(PREF_ENABLED_IDS, ids.toSet()).apply()
    }

    private fun downloadedEntryCount(context: Context, id: String): Long? {
        val value = prefs(context).getLong(PREF_COUNT_PREFIX + id, -1L)
        return value.takeIf { it >= 0L }
    }
}
