package com.kingzcheung.xime.rime

import com.kingzcheung.xime.settings.SchemaManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * default.custom.yaml 维护逻辑（纯函数）：
 * - [RimeConfigHelper.patchDefaultCustomContent]：把用户目录文件的 menu/page_size
 *   强制对齐为 app 设置值（app 固定 assets/default.custom.yaml 模板的同步规则，
 *   .custom.yaml 可以被覆盖、不保留旧的 PC 遗留值 5）；
 * - 与 [SchemaManager.replaceSchemaListBlock]（setEnabledSchemas 只替换 schema_list 块）
 *   的组合：启停方案后 menu 等其余 patch 必须保留。
 *
 * 每页候选数的最终生效在 JNI 层 setPageSize 直接注入引擎
 * （rime_jni.cc，配置缓存 + 会话幂等刷新），不依赖本文件内容。
 */
class DefaultCustomYamlTest {

    private val builtinTemplate = """
        patch:
          schema_list:
            - schema: wubi86
          menu:
            page_size: 20
          switcher:
            caption: "〔方案选单〕"
    """.trimIndent()

    /** submodule 版：page_size: 5 带 PC 风格行内注释。 */
    private val submoduleCopy = """
        patch:
          schema_list:
            - schema: wubi86
          menu:
            page_size: 5                    # 候选词数量
          switcher:
            caption: "〔方案选单〕"
    """.trimIndent()

    /** 旧版 setEnabledSchemas 整文件重写后的空壳。 */
    private val legacyShell = """
        patch:
          schema_list:
            - schema: wubi86
    """.trimIndent()

    private fun extractPageSize(text: String): Int? =
        text.lines().firstOrNull { it.trimStart().startsWith("page_size:") }
            ?.trimStart()?.removePrefix("page_size:")
            ?.substringBefore('#')?.trim()?.toIntOrNull()

    // ---- patchDefaultCustomContent（default 层对齐）----

    @Test
    fun `仅page_size已对齐仍补齐输入法固定绑定`() {
        val patched = RimeConfigHelper.patchDefaultCustomContent(builtinTemplate, 20)!!
        assertEquals(20, extractPageSize(patched))
        assertTrue(patched.contains("Shift_L: noop"))
        assertTrue(patched.contains("Control+period"))
    }

    @Test
    fun `旧值5强制对齐设置值`() {
        val patched = RimeConfigHelper.patchDefaultCustomContent(submoduleCopy, 20)!!
        assertEquals(20, extractPageSize(patched))
        assertFalse("行内注释随旧值一并移除", patched.contains("# 候选词数量"))
        assertTrue("menu 节保留", patched.contains("menu:"))
        assertTrue("switcher 节保留", patched.contains("switcher:"))
    }

    @Test
    fun `page_size缺失时在patch下补menu节`() {
        val patched = RimeConfigHelper.patchDefaultCustomContent(legacyShell, 20)!!
        assertEquals(20, extractPageSize(patched))
        val menuIdx = patched.lines().indexOfFirst { it.trim() == "menu:" }
        val patchIdx = patched.lines().indexOfFirst { it.trim() == "patch:" }
        assertTrue("menu 节应在 patch: 之内", menuIdx > patchIdx)
        assertTrue("schema_list 保留", patched.contains("- schema: wubi86"))
    }

    @Test
    fun `用户改过的值也会被设置值覆盖`() {
        // .custom.yaml 可以被覆盖：app 设置值（prefs）是权威
        val customized = builtinTemplate.replace("page_size: 20", "page_size: 15")
        val patched = RimeConfigHelper.patchDefaultCustomContent(customized, 20)!!
        assertEquals(20, extractPageSize(patched))
    }

    @Test
    fun `非数值的page_size自愈为设置值`() {
        // page_size: abc 是无效配置（引擎读不出会兜底 5），对齐为设置值是自愈
        val patched = RimeConfigHelper.patchDefaultCustomContent("patch:\n  page_size: abc\n", 20)!!
        assertEquals(20, extractPageSize(patched))
    }

    @Test
    fun `无patch根且无page_size的纯文本不动`() {
        assertNull(RimeConfigHelper.patchDefaultCustomContent("# just a comment\n", 20))
    }

    @Test
    fun `CRLF文件修补后保留CRLF`() {
        val crlf = "patch:\r\n  schema_list:\r\n    - schema: wubi86\r\n  menu:\r\n    page_size: 5\r\n"
        val patched = RimeConfigHelper.patchDefaultCustomContent(crlf, 20)!!
        assertTrue("CRLF 必须保留", patched.contains("\r\n"))
        assertFalse("无裸 LF 残留", patched.replace("\r\n", "").contains("\n"))
        assertEquals(20, extractPageSize(patched))
    }

    @Test
    fun `accepted hardware bindings are aligned while schema list is preserved`() {
        val legacy = """
            patch:
              schema_list:
                - schema: user_schema
              ascii_composer:
                switch_key:
                  Shift_L: commit_code
                  Shift_R: noop
              key_binder:
                bindings:
                  - { when: has_menu, accept: semicolon, send: 9 }
        """.trimIndent()
        val patched = RimeConfigHelper.patchDefaultCustomContent(legacy, 20)!!
        assertTrue(patched.contains("- schema: user_schema"))
        assertTrue(patched.contains("Shift_L: noop"))
        assertTrue(patched.contains("Shift_R: commit_code"))
        assertTrue(patched.contains("Control+period, toggle: ascii_punct"))
        assertTrue(patched.contains("accept: semicolon, send: 2"))
        assertTrue(patched.contains("accept: apostrophe, send: 3"))
        assertTrue(patched.contains("accept: bracketleft, send: Page_Up"))
        assertTrue(patched.contains("accept: bracketright, send: Page_Down"))
    }

    // ---- 与 replaceSchemaListBlock 的组合（setEnabledSchemas 真实路径）----

    @Test
    fun `替换patch缩进的schema_list后menu节保留`() {
        // 模拟 setEnabledSchemas：模板/修补后的文件上替换启停后的方案列表
        val result = SchemaManager.replaceSchemaListBlock(
            builtinTemplate, listOf("pinyin_simp", "t9_pinyin")
        )
        assertTrue(result.contains("  - schema: pinyin_simp"))
        assertTrue(result.contains("  - schema: t9_pinyin"))
        assertFalse("旧列表清掉", result.contains("- schema: wubi86"))
        assertEquals(20, extractPageSize(result))
        assertTrue("menu 节保留", result.contains("menu:"))
    }

    @Test
    fun `空壳先修补再替换schema_list结果完整`() {
        val patched = RimeConfigHelper.patchDefaultCustomContent(legacyShell, 20)!!
        val result = SchemaManager.replaceSchemaListBlock(patched, listOf("pinyin_simp"))
        assertEquals(20, extractPageSize(result))
        assertTrue(result.contains("  - schema: pinyin_simp"))
    }
}
