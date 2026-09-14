package com.kingzcheung.xime.service

import android.view.KeyCharacterMap
import android.view.KeyEvent

internal const val RIME_SHIFT_MASK = 1 shl 0
internal const val RIME_LOCK_MASK = 1 shl 1
internal const val RIME_CONTROL_MASK = 1 shl 2
internal const val RIME_ALT_MASK = 1 shl 3
internal const val RIME_MOD2_MASK = 1 shl 4
internal const val RIME_MOD3_MASK = 1 shl 5
internal const val RIME_SUPER_MASK = 1 shl 26
internal const val RIME_RELEASE_MASK = 1 shl 30

internal const val RIME_KEY_SHIFT_L = 0xffe1
internal const val RIME_KEY_SHIFT_R = 0xffe2
internal const val RIME_KEY_CONTROL_L = 0xffe3
internal const val RIME_KEY_CONTROL_R = 0xffe4
internal const val RIME_KEY_CAPS_LOCK = 0xffe5
internal const val RIME_KEY_ALT_L = 0xffe9
internal const val RIME_KEY_ALT_R = 0xffea
internal const val RIME_KEY_SUPER_L = 0xffeb
internal const val RIME_KEY_SUPER_R = 0xffec

private const val RIME_KEY_BACKSPACE = 0xff08
private const val RIME_KEY_TAB = 0xff09
private const val RIME_KEY_CLEAR = 0xff0b
private const val RIME_KEY_RETURN = 0xff0d
private const val RIME_KEY_PAUSE = 0xff13
private const val RIME_KEY_SCROLL_LOCK = 0xff14
private const val RIME_KEY_SYS_REQ = 0xff15
private const val RIME_KEY_ESCAPE = 0xff1b
private const val RIME_KEY_HOME = 0xff50
private const val RIME_KEY_LEFT = 0xff51
private const val RIME_KEY_UP = 0xff52
private const val RIME_KEY_RIGHT = 0xff53
private const val RIME_KEY_DOWN = 0xff54
private const val RIME_KEY_PAGE_UP = 0xff55
private const val RIME_KEY_PAGE_DOWN = 0xff56
private const val RIME_KEY_END = 0xff57
private const val RIME_KEY_PRINT = 0xff61
private const val RIME_KEY_INSERT = 0xff63
private const val RIME_KEY_MENU = 0xff67
private const val RIME_KEY_HELP = 0xff6a
private const val RIME_KEY_BREAK = 0xff6b
private const val RIME_KEY_NUM_LOCK = 0xff7f
private const val RIME_KEY_F1 = 0xffbe
private const val RIME_KEY_DELETE = 0xffff

/** 物理键码 → 软键盘输入路径使用的按键文本。 */
internal fun keyCodeToKey(keyCode: Int, isShifted: Boolean): String? {
    if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
        val letter = ('a'.code + keyCode - KeyEvent.KEYCODE_A).toChar()
        return if (isShifted) letter.uppercase() else letter.toString()
    }
    if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
        val digit = ('0'.code + keyCode - KeyEvent.KEYCODE_0).toChar()
        return if (isShifted) shiftedDigit(digit) else digit.toString()
    }
    if (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
        return ('0'.code + keyCode - KeyEvent.KEYCODE_NUMPAD_0).toChar().toString()
    }
    return when (keyCode) {
        KeyEvent.KEYCODE_SPACE -> "space"
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> "enter"
        KeyEvent.KEYCODE_DEL -> "delete"
        KeyEvent.KEYCODE_COMMA -> if (isShifted) "<" else ","
        KeyEvent.KEYCODE_PERIOD -> if (isShifted) ">" else "."
        KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> if (isShifted) "_" else "-"
        KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_NUMPAD_EQUALS -> if (isShifted) "+" else "="
        KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_NUMPAD_DIVIDE -> if (isShifted) "?" else "/"
        KeyEvent.KEYCODE_BACKSLASH -> if (isShifted) "|" else "\\"
        KeyEvent.KEYCODE_SEMICOLON -> if (isShifted) ":" else ";"
        KeyEvent.KEYCODE_APOSTROPHE -> if (isShifted) "\"" else "'"
        KeyEvent.KEYCODE_LEFT_BRACKET -> if (isShifted) "{" else "["
        KeyEvent.KEYCODE_RIGHT_BRACKET -> if (isShifted) "}" else "]"
        KeyEvent.KEYCODE_GRAVE -> if (isShifted) "~" else "`"
        KeyEvent.KEYCODE_TAB -> "\t"
        KeyEvent.KEYCODE_NUMPAD_ADD -> "+"
        KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> "*"
        KeyEvent.KEYCODE_NUMPAD_DOT -> "."
        KeyEvent.KEYCODE_NUMPAD_COMMA -> ","
        KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN -> "("
        KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN -> ")"
        else -> null
    }
}

/**
 * 优先使用 Android 键盘布局解析后的 Unicode 字符，支持非美式实体键盘；
 * 无可打印字符时回退到稳定的 Android keyCode 映射。
 */
internal fun keyEventToKey(event: KeyEvent): String? {
    when (event.keyCode) {
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_DEL,
        KeyEvent.KEYCODE_TAB,
        -> return keyCodeToKey(event.keyCode, event.isShiftPressed)
    }

    val unicode = event.unicodeChar
    if (unicode != 0) {
        val codePoint = unicode and KeyCharacterMap.COMBINING_ACCENT_MASK.inv()
        if (Character.isValidCodePoint(codePoint) && !Character.isISOControl(codePoint)) {
            return String(Character.toChars(codePoint))
        }
    }
    return keyCodeToKey(event.keyCode, event.isShiftPressed)
}

private fun shiftedDigit(digit: Char): String = when (digit) {
    '0' -> ")"
    '1' -> "!"
    '2' -> "@"
    '3' -> "#"
    '4' -> "$"
    '5' -> "%"
    '6' -> "^"
    '7' -> "&"
    '8' -> "*"
    '9' -> "("
    else -> digit.toString()
}

/** 物理修饰键 → Rime/X11 keysym。 */
internal fun keyCodeToRimeModifierKeyCode(keyCode: Int): Int? = when (keyCode) {
    KeyEvent.KEYCODE_SHIFT_LEFT -> RIME_KEY_SHIFT_L
    KeyEvent.KEYCODE_SHIFT_RIGHT -> RIME_KEY_SHIFT_R
    KeyEvent.KEYCODE_CTRL_LEFT -> RIME_KEY_CONTROL_L
    KeyEvent.KEYCODE_CTRL_RIGHT -> RIME_KEY_CONTROL_R
    KeyEvent.KEYCODE_ALT_LEFT -> RIME_KEY_ALT_L
    KeyEvent.KEYCODE_ALT_RIGHT -> RIME_KEY_ALT_R
    KeyEvent.KEYCODE_META_LEFT -> RIME_KEY_SUPER_L
    KeyEvent.KEYCODE_META_RIGHT -> RIME_KEY_SUPER_R
    KeyEvent.KEYCODE_CAPS_LOCK -> RIME_KEY_CAPS_LOCK
    else -> null
}

/** Android metaState → RimeModifier；两套位定义除 Shift 外并不兼容，不能直接透传。 */
internal fun androidMetaStateToRimeMask(metaState: Int): Int {
    var mask = 0
    if (metaState and (
            KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_RIGHT_ON
        ) != 0
    ) mask = mask or RIME_SHIFT_MASK
    if (metaState and KeyEvent.META_CAPS_LOCK_ON != 0) mask = mask or RIME_LOCK_MASK
    if (metaState and (
            KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_CTRL_RIGHT_ON
        ) != 0
    ) mask = mask or RIME_CONTROL_MASK
    if (metaState and (
            KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_RIGHT_ON
        ) != 0
    ) mask = mask or RIME_ALT_MASK
    if (metaState and KeyEvent.META_NUM_LOCK_ON != 0) mask = mask or RIME_MOD2_MASK
    if (metaState and KeyEvent.META_SCROLL_LOCK_ON != 0) mask = mask or RIME_MOD3_MASK
    if (metaState and (
            KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON or KeyEvent.META_META_RIGHT_ON
        ) != 0
    ) mask = mask or RIME_SUPER_MASK
    return mask
}

internal fun hasRimeChordModifier(mask: Int): Boolean =
    mask and (RIME_SHIFT_MASK or RIME_CONTROL_MASK or RIME_ALT_MASK or RIME_SUPER_MASK) != 0

internal fun hasRimeCommandModifier(mask: Int): Boolean =
    mask and (RIME_CONTROL_MASK or RIME_ALT_MASK or RIME_SUPER_MASK) != 0

private const val ANDROID_CTRL_MASK =
    KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON or KeyEvent.META_CTRL_RIGHT_ON
private const val ANDROID_SHIFT_MASK =
    KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON or KeyEvent.META_SHIFT_RIGHT_ON
private const val ANDROID_ALT_MASK =
    KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON or KeyEvent.META_ALT_RIGHT_ON
private const val ANDROID_META_MASK =
    KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON or KeyEvent.META_META_RIGHT_ON
private const val ANDROID_CHORD_MASK =
    ANDROID_CTRL_MASK or ANDROID_SHIFT_MASK or ANDROID_ALT_MASK or ANDROID_META_MASK

/** Ctrl+0（主键区或数字键盘）专用于切换 Xime 常驻语音输入。 */
internal fun isVoiceToggleShortcut(keyCode: Int, metaState: Int): Boolean =
    keyCode in setOf(KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0) &&
        metaState and ANDROID_CTRL_MASK != 0 &&
        metaState and (ANDROID_SHIFT_MASK or ANDROID_ALT_MASK or ANDROID_META_MASK) == 0

/** 只有无 Shift/Ctrl/Alt/Meta 修饰的实体空格才参与长按语音判定。 */
internal fun isUnmodifiedHardwareSpace(keyCode: Int, metaState: Int): Boolean =
    keyCode == KeyEvent.KEYCODE_SPACE && metaState and ANDROID_CHORD_MASK == 0

/** 普通、组合及特殊实体键 → Rime/X11 keysym。 */
internal fun keyCodeToRimeKeyCode(keyCode: Int): Int? {
    if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
        return 'a'.code + keyCode - KeyEvent.KEYCODE_A
    }
    if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9) {
        return '0'.code + keyCode - KeyEvent.KEYCODE_0
    }
    if (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9) {
        // Android 已依据 NumLock 把小键盘导航键报告为对应 DPAD/Page 键；此处用 ASCII
        // 数字保证 Rime 的 speller、候选选择和数字输入均可识别。
        return '0'.code + keyCode - KeyEvent.KEYCODE_NUMPAD_0
    }
    if (keyCode in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12) {
        return RIME_KEY_F1 + keyCode - KeyEvent.KEYCODE_F1
    }
    if (keyCode in KeyEvent.KEYCODE_F13..KeyEvent.KEYCODE_F24) {
        // Android 把 F13..F24 放在 326..337，不能按 F1 的 keyCode 连续偏移。
        return RIME_KEY_F1 + 12 + keyCode - KeyEvent.KEYCODE_F13
    }
    return keyCodeToRimeModifierKeyCode(keyCode) ?: when (keyCode) {
        KeyEvent.KEYCODE_SPACE -> ' '.code
        KeyEvent.KEYCODE_TAB -> RIME_KEY_TAB
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> RIME_KEY_RETURN
        KeyEvent.KEYCODE_DEL -> RIME_KEY_BACKSPACE
        KeyEvent.KEYCODE_FORWARD_DEL -> RIME_KEY_DELETE
        KeyEvent.KEYCODE_ESCAPE -> RIME_KEY_ESCAPE
        KeyEvent.KEYCODE_MOVE_HOME -> RIME_KEY_HOME
        KeyEvent.KEYCODE_DPAD_LEFT -> RIME_KEY_LEFT
        KeyEvent.KEYCODE_DPAD_UP -> RIME_KEY_UP
        KeyEvent.KEYCODE_DPAD_RIGHT -> RIME_KEY_RIGHT
        KeyEvent.KEYCODE_DPAD_DOWN -> RIME_KEY_DOWN
        KeyEvent.KEYCODE_PAGE_UP -> RIME_KEY_PAGE_UP
        KeyEvent.KEYCODE_PAGE_DOWN -> RIME_KEY_PAGE_DOWN
        KeyEvent.KEYCODE_MOVE_END -> RIME_KEY_END
        KeyEvent.KEYCODE_INSERT -> RIME_KEY_INSERT
        KeyEvent.KEYCODE_CLEAR -> RIME_KEY_CLEAR
        KeyEvent.KEYCODE_BREAK -> RIME_KEY_BREAK
        KeyEvent.KEYCODE_NUM_LOCK -> RIME_KEY_NUM_LOCK
        KeyEvent.KEYCODE_SCROLL_LOCK -> RIME_KEY_SCROLL_LOCK
        KeyEvent.KEYCODE_SYSRQ -> RIME_KEY_SYS_REQ
        KeyEvent.KEYCODE_MENU -> RIME_KEY_MENU
        KeyEvent.KEYCODE_HELP -> RIME_KEY_HELP
        KeyEvent.KEYCODE_COMMA -> ','.code
        KeyEvent.KEYCODE_PERIOD -> '.'.code
        KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> '-'.code
        KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_NUMPAD_EQUALS -> '='.code
        KeyEvent.KEYCODE_SLASH, KeyEvent.KEYCODE_NUMPAD_DIVIDE -> '/'.code
        KeyEvent.KEYCODE_BACKSLASH -> '\\'.code
        KeyEvent.KEYCODE_SEMICOLON -> ';'.code
        KeyEvent.KEYCODE_APOSTROPHE -> '\''.code
        KeyEvent.KEYCODE_LEFT_BRACKET -> '['.code
        KeyEvent.KEYCODE_RIGHT_BRACKET -> ']'.code
        KeyEvent.KEYCODE_GRAVE -> '`'.code
        KeyEvent.KEYCODE_NUMPAD_ADD -> '+'.code
        KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> '*'.code
        KeyEvent.KEYCODE_NUMPAD_DOT -> '.'.code
        KeyEvent.KEYCODE_NUMPAD_COMMA -> ','.code
        KeyEvent.KEYCODE_NUMPAD_LEFT_PAREN -> '('.code
        KeyEvent.KEYCODE_NUMPAD_RIGHT_PAREN -> ')'.code
        else -> null
    }
}

/** 组合态优先送 Rime、非组合态才允许宿主回退的编辑键。 */
internal fun isCompositionEditingKey(keyCode: Int): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_DEL,
    KeyEvent.KEYCODE_FORWARD_DEL,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_MOVE_HOME,
    KeyEvent.KEYCODE_MOVE_END,
    KeyEvent.KEYCODE_PAGE_UP,
    KeyEvent.KEYCODE_PAGE_DOWN,
    KeyEvent.KEYCODE_SPACE,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
    KeyEvent.KEYCODE_ESCAPE,
    -> true
    else -> false
}

/** 无修饰键时也应由 Rime 处理、未处理则回送编辑器的非文本键。 */
internal fun isRimeSpecialKey(keyCode: Int): Boolean = when (keyCode) {
    KeyEvent.KEYCODE_TAB,
    KeyEvent.KEYCODE_ESCAPE,
    KeyEvent.KEYCODE_FORWARD_DEL,
    KeyEvent.KEYCODE_MOVE_HOME,
    KeyEvent.KEYCODE_MOVE_END,
    KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_UP,
    KeyEvent.KEYCODE_DPAD_RIGHT,
    KeyEvent.KEYCODE_DPAD_DOWN,
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_PAGE_UP,
    KeyEvent.KEYCODE_PAGE_DOWN,
    KeyEvent.KEYCODE_INSERT,
    KeyEvent.KEYCODE_CLEAR,
    KeyEvent.KEYCODE_BREAK,
    KeyEvent.KEYCODE_NUM_LOCK,
    KeyEvent.KEYCODE_SCROLL_LOCK,
    KeyEvent.KEYCODE_SYSRQ,
    KeyEvent.KEYCODE_MENU,
    KeyEvent.KEYCODE_HELP,
    in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F24,
    -> true
    else -> false
}
