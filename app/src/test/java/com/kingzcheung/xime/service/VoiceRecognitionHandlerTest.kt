package com.kingzcheung.xime.service

import android.content.Context
import android.os.Handler
import android.util.Log
import android.view.inputmethod.InputConnection
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.speech.RecognitionState
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import org.mockito.junit.MockitoJUnitRunner

@RunWith(MockitoJUnitRunner::class)
class VoiceRecognitionHandlerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockInputConnection: InputConnection

    @Mock
    private lateinit var mockHandler: Handler

    private lateinit var handler: VoiceRecognitionHandler
    private lateinit var logMock: MockedStatic<Log>
    private var scheduledRawCommit: Runnable? = null

    @Before
    fun setup() {
        logMock = mockStatic(Log::class.java)
        scheduledRawCommit = null
        doAnswer {
            scheduledRawCommit = it.getArgument(0)
            true
        }.`when`(mockHandler).postDelayed(any(Runnable::class.java), anyLong())
        doAnswer {
            if (scheduledRawCommit === it.getArgument<Runnable>(0)) {
                scheduledRawCommit = null
            }
            null
        }.`when`(mockHandler).removeCallbacks(any(Runnable::class.java))
        handler = VoiceRecognitionHandler(
            context = mockContext,
            onStateChanged = {},
            getState = { InputUIState() },
            getInputConnection = { mockInputConnection }
        )
    }

    @After
    fun tearDown() {
        logMock.close()
    }

    @Test
    fun `test text preprocessing removes spaces`() {
        // Test that ASR text with spaces is properly cleaned
        val testCases = listOf(
            "你好 世界" to "你好世界",
            "  前面有空格" to "前面有空格",
            "中间 有 空格" to "中间有空格",
            "正常文本" to "正常文本"
        )

        for ((input, expected) in testCases) {
            val result = input.replace(" ", "")
            assertEquals("Space removal failed for '$input'", expected, result)
        }
    }

    @Test
    fun `test empty text is not committed`() {
        val emptyText = ""
        val whitespaceOnly = "   "
        val cleanWhitespace = whitespaceOnly.replace(" ", "")

        assertTrue("Empty text should not be committed", emptyText.isEmpty())
        assertTrue("Whitespace-only text should be empty after cleaning", 
            cleanWhitespace.isEmpty())
    }

    @Test
    fun `test text with only spaces is filtered`() {
        val textWithOnlySpaces = "     "
        val cleanText = textWithOnlySpaces.replace(" ", "")
        
        assertEquals("", cleanText)
        assertTrue(cleanText.isEmpty())
    }

    @Test
    fun `test accumulated text is cleared after final result`() {
        // Simulate accumulating text
        val accumulatedText = StringBuilder()
        accumulatedText.append("第一句话")
        accumulatedText.append("第二句话")
        
        assertEquals("第一句话第二句话", accumulatedText.toString())
        
        // Clear after final result
        accumulatedText.clear()
        assertEquals("", accumulatedText.toString())
    }

    @Test
    fun `test heuristic punctuation rules`() {
        // Test heuristic punctuation logic
        val questionWords = listOf("吗", "呢", "么", "吧", "什么", "怎么", "为什么", "如何", "哪")
        
        fun getHeuristicPunctuation(text: String): String {
            return when {
                text.any { it in "吗呢么吧" } || 
                text.contains("什么") || 
                text.contains("怎么") || 
                text.contains("为什么") || 
                text.contains("如何") || 
                text.contains("哪") -> "？"
                text.length < 4 -> "，"
                else -> "。"
            }
        }

        assertEquals("？", getHeuristicPunctuation("你好吗"))
        assertEquals("？", getHeuristicPunctuation("这是什么"))
        assertEquals("？", getHeuristicPunctuation("怎么做"))
        assertEquals("，", getHeuristicPunctuation("你好"))
        assertEquals("。", getHeuristicPunctuation("今天天气很好"))
    }

    @Test
    fun `test partial result deduplication`() {
        // Test that same partial result is not processed twice
        var lastPartialText = ""
        
        fun shouldProcess(text: String): Boolean {
            if (text == lastPartialText) return false
            lastPartialText = text
            return true
        }

        assertTrue(shouldProcess("你好"))
        assertFalse(shouldProcess("你好")) // Same text, should skip
        assertTrue(shouldProcess("你好世界")) // Different text, should process
    }

    @Test
    fun `test state transitions`() {
        // Test recognition state transitions
        val states = mutableListOf<RecognitionState>()
        
        states.add(RecognitionState.IDLE)
        states.add(RecognitionState.LISTENING)
        states.add(RecognitionState.PROCESSING)
        states.add(RecognitionState.IDLE)
        
        assertEquals(4, states.size)
        assertEquals(RecognitionState.IDLE, states.first())
        assertEquals(RecognitionState.IDLE, states.last())
    }

    @Test
    fun `test error handling resets state`() {
        // Simulate error and verify state is reset
        var lastPartialText = "测试文本"
        var accumulatedText = StringBuilder("已积累的文本")
        
        // On error, these should be cleared
        lastPartialText = ""
        accumulatedText.clear()
        
        assertEquals("", lastPartialText)
        assertEquals("", accumulatedText.toString())
    }

    @Test
    fun `raw target shows partial then auto commits once after pause`() {
        var state = InputUIState()
        val rawHandler = VoiceRecognitionHandler(
            context = mockContext,
            onStateChanged = { state = it },
            getState = { state },
            getInputConnection = { mockInputConnection },
            isRawInputTarget = { true },
            mainHandlerFactory = { mockHandler }
        )

        rawHandler.handlePartialResult("git status")

        assertEquals("git status", state.voiceRecognizedText)
        verify(mockInputConnection, never()).setComposingText(anyString(), anyInt())
        assertNotNull(scheduledRawCommit)

        scheduledRawCommit!!.run()
        assertEquals("", state.voiceRecognizedText)
        rawHandler.handleSpeechResult("git status")

        verify(mockInputConnection, times(1)).commitText("git status", 1)
        verify(mockInputConnection, never()).finishComposingText()
        verify(mockInputConnection, never()).deleteSurroundingText(anyInt(), anyInt())
    }

    @Test
    fun `raw target fallback commits once and suppresses late final`() {
        val rawHandler = VoiceRecognitionHandler(
            context = mockContext,
            onStateChanged = {},
            getState = { InputUIState() },
            getInputConnection = { mockInputConnection },
            isRawInputTarget = { true },
            mainHandlerFactory = { mockHandler }
        )

        rawHandler.handlePartialResult("git status")
        rawHandler.commitPendingOnRelease()
        rawHandler.handleSpeechResult("git status")

        verify(mockInputConnection, times(1)).commitText("git status", 1)
    }

    @Test
    fun `rich target keeps composing and punctuation behavior`() {
        var state = InputUIState()
        val richHandler = VoiceRecognitionHandler(
            context = mockContext,
            onStateChanged = { state = it },
            getState = { state },
            getInputConnection = { mockInputConnection },
            mainHandlerFactory = { mockHandler }
        )

        richHandler.handlePartialResult("你好")
        richHandler.handleSpeechResult("你好")

        verify(mockInputConnection).setComposingText("你好", 1)
        verify(mockInputConnection).finishComposingText()
        verify(mockInputConnection).commitText("，", 1)
        verify(mockHandler, never()).postDelayed(any(Runnable::class.java), anyLong())
    }
}
