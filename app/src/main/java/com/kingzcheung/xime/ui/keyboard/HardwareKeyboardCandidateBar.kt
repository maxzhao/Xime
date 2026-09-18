package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private const val MAX_VISIBLE_CANDIDATES = 10
private const val ESTIMATED_CARD_HEIGHT_DP = 180

internal data class HardwareCandidatePosition(val x: Int, val y: Int)

/** 将屏幕光标锚点转换到候选层坐标，并用候选层及卡片真实尺寸严格限制位置。 */
internal fun calculateHardwareCandidatePosition(
    containerWidth: Int,
    containerHeight: Int,
    viewScreenX: Int,
    viewScreenY: Int,
    cursorScreenX: Int,
    cursorScreenTop: Int,
    cursorScreenBottom: Int,
    cursorVisible: Boolean,
    cardWidth: Int,
    cardHeight: Int,
    margin: Int,
    gap: Int,
    fallbackTop: Int,
): HardwareCandidatePosition {
    fun constrainStart(desired: Int, container: Int, element: Int): Int {
        val maxStart = (container - element - margin).coerceAtLeast(0)
        val minStart = margin.coerceAtMost(maxStart)
        return desired.coerceIn(minStart, maxStart)
    }

    val safeCardWidth = cardWidth.coerceAtLeast(1)
    val safeCardHeight = cardHeight.coerceAtLeast(1)
    val localCursorX = cursorScreenX - viewScreenX
    val desiredX = if (cursorVisible) {
        localCursorX - safeCardWidth / 2
    } else {
        (containerWidth - safeCardWidth) / 2
    }
    val x = constrainStart(desiredX, containerWidth, safeCardWidth)

    val desiredY = if (cursorVisible) {
        val localTop = cursorScreenTop - viewScreenY
        val localBottom = cursorScreenBottom - viewScreenY
        val below = localBottom + gap
        val above = localTop - gap - safeCardHeight
        val bottomLimit = containerHeight - margin
        when {
            below + safeCardHeight <= bottomLimit -> below
            above >= margin -> above
            localTop - margin >= bottomLimit - localBottom -> above
            else -> below
        }
    } else {
        fallbackTop
    }
    val y = constrainStart(desiredY, containerHeight, safeCardHeight)
    return HardwareCandidatePosition(x, y)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HardwareKeyboardCandidateBar(
    inputText: String,
    preeditText: String,
    candidates: List<String>,
    hasNextPage: Boolean,
    hasPrevPage: Boolean,
    cursorX: Int,
    cursorTopY: Int,
    cursorY: Int,
    cursorVisible: Boolean,
    highlightIndex: Int,
    statusMessage: String = "",
    isVoiceMode: Boolean = false,
    voicePluginName: String = "",
    cardBackgroundColor: Color,
    candidateTextColor: Color,
    activeColor: Color,
    selectedTextColor: Color = activeColor,
) {
    if (candidates.isEmpty() && inputText.isEmpty() && statusMessage.isEmpty() && !isVoiceMode) return

    val density = LocalDensity.current
    val displayText = if (preeditText.isNotEmpty()) preeditText else inputText

    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val fallbackWidthPx = with(density) { screenWidthDp.dp.toPx() }.roundToInt()
    val fallbackHeightPx = with(density) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }.roundToInt()

    val view = LocalView.current
    val viewLoc = remember { IntArray(2) }
    view.getLocationOnScreen(viewLoc)
    val containerWidthPx = view.width.takeIf { it > 0 } ?: fallbackWidthPx
    val containerHeightPx = view.height.takeIf { it > 0 } ?: fallbackHeightPx

    val marginPx = with(density) { 8.dp.toPx() }.roundToInt()
    val cardGapPx = with(density) { 16.dp.toPx() }.roundToInt()
    val fallbackTopPx = with(density) { 60.dp.toPx() }.roundToInt()
    val estCardHeightPx = with(density) { ESTIMATED_CARD_HEIGHT_DP.dp.toPx() }.roundToInt()
    val containerWidthDp = with(density) { containerWidthPx.toDp().value }
    val maxCardWidthDp = (containerWidthDp * 0.85f).roundToInt()
        .coerceAtLeast(1)
        .coerceAtMost(420)
    val minCardWidthDp = 160.coerceAtMost(maxCardWidthDp)
    val estCardWidthPx = with(density) { maxCardWidthDp.dp.toPx() }.roundToInt()

    var actualCardWidth by remember { mutableIntStateOf(estCardWidthPx) }
    var actualCardHeight by remember { mutableIntStateOf(estCardHeightPx) }
    val cardPosition = calculateHardwareCandidatePosition(
        containerWidth = containerWidthPx,
        containerHeight = containerHeightPx,
        viewScreenX = viewLoc[0],
        viewScreenY = viewLoc[1],
        cursorScreenX = cursorX,
        cursorScreenTop = cursorTopY,
        cursorScreenBottom = cursorY,
        cursorVisible = cursorVisible,
        cardWidth = actualCardWidth,
        cardHeight = actualCardHeight,
        margin = marginPx,
        gap = cardGapPx,
        fallbackTop = fallbackTopPx,
    )

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(cardPosition.x, cardPosition.y) }
                .widthIn(min = minCardWidthDp.dp, max = maxCardWidthDp.dp)
                .wrapContentWidth()
                .shadow(12.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(cardBackgroundColor)
                .onSizeChanged {
                    actualCardWidth = it.width
                    actualCardHeight = it.height
                }
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                    .widthIn(max = maxCardWidthDp.dp - 24.dp)
            ) {
                if (statusMessage.isNotEmpty()) {
                    Text(
                        text = statusMessage,
                        fontSize = 15.sp,
                        color = activeColor,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }

                if (isVoiceMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "语音输入",
                            tint = activeColor,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = if (voicePluginName.isBlank()) "语音输入" else "语音输入 · $voicePluginName",
                            fontSize = 13.sp,
                            color = activeColor,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (displayText.isNotEmpty()) {
                    Text(
                        text = displayText,
                        fontSize = 13.sp,
                        color = activeColor,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }

                if (candidates.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        candidates.take(MAX_VISIBLE_CANDIDATES).forEachIndexed { index, candidate ->
                            val isActive = index == highlightIndex
                            val label = (index + 1) % 10
                            val labelText = if (index == 9) "0" else "$label"
                            Column {
                                Text(
                                    text = "$labelText $candidate",
                                    fontSize = 15.sp,
                                    color = if (isActive) selectedTextColor else candidateTextColor,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    fontFamily = AppFonts.candidateFontFamily
                                )
                            }
                        }
                    }
                }
            }

            if (hasNextPage || hasPrevPage) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (hasPrevPage) {
                            Text("◀", fontSize = 10.sp, color = candidateTextColor.copy(alpha = 0.5f))
                        }
                        if (hasNextPage) {
                            Text("▶", fontSize = 10.sp, color = candidateTextColor.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}
