package app.mytube.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.AsyncImage

/** Deadline uses elapsed realtime, so wall-clock changes and delayed UI ticks cannot extend it. */
data class UpNextCountdown(val target: Video,val sourceUrl: String,val deadlineMs: Long?) {
    fun remainingSeconds(nowMs: Long): Int? = deadlineMs?.let { ((it-nowMs).coerceAtLeast(0)+999).div(1000).coerceAtMost(10).toInt() }
    fun isDue(nowMs: Long): Boolean = deadlineMs?.let { nowMs >= it } ?: false
    fun cancel(): UpNextCountdown = copy(deadlineMs = null)
    companion object { fun start(target: Video,sourceUrl: String,nowMs: Long) = UpNextCountdown(target,sourceUrl,nowMs+10_000) }
}

@Composable internal fun UpNextPanel(countdown: UpNextCountdown,seconds: Int,playFocus: FocusRequester,play: () -> Unit,cancel: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color(0xEE0F0F0F)).testTag("up-next"),contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 790.dp).padding(28.dp),verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text(if(countdown.deadlineMs != null) "Up next in $seconds" else "Autoplay canceled",fontSize = 24.sp,fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("up-next-status"))
            Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                AsyncImage(countdown.target.thumbnail,contentDescription = null,contentScale = ContentScale.Crop,
                    modifier = Modifier.width(300.dp).aspectRatio(16f/9).background(Panel,RoundedCornerShape(12.dp)))
                Column(Modifier.weight(1f).testTag("up-next-target").semantics { stateDescription = countdown.target.url },verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(countdown.target.title,fontSize = 21.sp,fontWeight = FontWeight.Medium,maxLines = 3,overflow = TextOverflow.Ellipsis)
                    Text(countdown.target.channel,color = Muted,fontSize = 15.sp,maxLines = 1,overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = play,modifier = Modifier.focusRequester(playFocus).testTag("up-next-play"),
                            colors = ButtonDefaults.colors(containerColor = OffWhite,contentColor = Background,focusedContainerColor = Color.White,focusedContentColor = Background)) { Text("Play next") }
                        Button(onClick = cancel,modifier = Modifier.testTag("up-next-cancel"),
                            colors = ButtonDefaults.colors(containerColor = SearchSurface,focusedContainerColor = OffWhite,focusedContentColor = Background)) { Text("Cancel") }
                    }
                }
            }
            Text("↓ Related videos",color = Muted,fontSize = 15.sp)
        }
    }
}
