package app.mytube.tv

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal val PlaybackRed = Color(0xFFFF0033)

/** One focus stop for the whole timeline. Chapter boundaries change drawing, never remote routing. */
@Composable internal fun ChapterSeekBar(positionMs: Long,durationMs: Long,chapters: VideoChapters,
    focus: FocusRequester,toggle: () -> Unit,seek: (Long) -> Unit,modifier: Modifier = Modifier,onFocus: (Boolean) -> Unit = {}) {
    var focused by remember { mutableStateOf(false) }
    val spans = remember(chapters,durationMs) { chapters.spans(durationMs) }
    val progress = if(durationMs > 0) (positionMs.toDouble()/durationMs).coerceIn(0.0,1.0).toFloat() else 0f
    val chapter = chapters.current(positionMs,durationMs)
    Canvas(modifier.height(26.dp).focusRequester(focus).onFocusChanged { focused = it.isFocused;onFocus(it.isFocused) }
        .onPreviewKeyEvent { event ->
            if(event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) false else when(event.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> { seek(-10_000);true }
                KeyEvent.KEYCODE_DPAD_RIGHT -> { seek(10_000);true }
                KeyEvent.KEYCODE_DPAD_CENTER,KeyEvent.KEYCODE_ENTER -> { toggle();true }
                else -> false
            }
        }.focusable().semantics {
            contentDescription = "Seek position; left or right seeks ten seconds"
            progressBarRangeInfo = ProgressBarRangeInfo(progress,0f..1f)
            stateDescription = listOfNotNull(chapter?.title?.takeIf { it.isNotBlank() },duration(positionMs.coerceAtLeast(0)/1000)).joinToString(" · ")
        }.testTag("player-seek")) {
        val height = (if(focused) 6.dp else 4.dp).toPx()
        val top = (size.height-height)/2
        val playedX = size.width*progress
        val ranges = if(spans.isEmpty()) listOf(ChapterSpan(0,1)) else spans
        val timelineDuration = durationMs.coerceAtLeast(1).toDouble()
        ranges.forEachIndexed { index,span ->
            val start = if(spans.isEmpty()) 0f else (size.width*span.startMs/timelineDuration).toFloat()
            val end = if(spans.isEmpty()) size.width else (size.width*span.endMs/timelineDuration).toFloat()
            // Keep even very short chapters drawable without inverting their rectangles.
            val halfGap = minOf(1.5.dp.toPx(),(end-start)/4)
            val left = start + if(index > 0) halfGap else 0f
            val right = end - if(index < ranges.lastIndex) halfGap else 0f
            drawRoundRect(Color(0xFF666666),Offset(left,top),Size(right-left,height),CornerRadius(height/2))
            val playedEnd = playedX.coerceIn(left,right)
            if(playedEnd > left) drawRoundRect(PlaybackRed,Offset(left,top),Size(playedEnd-left,height),CornerRadius(height/2))
        }
        drawCircle(if(focused) Color.White else PlaybackRed,(if(focused) 6.dp else 4.dp).toPx(),Offset(playedX,size.height/2))
    }
}
