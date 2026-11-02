package app.mytube.tv

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

enum class PlayerPanel { HIDDEN, CONTROLS, RELATED, SETTINGS, COMMENTS, UP_NEXT }

@Composable fun BottomPlayerControls(
    title: String, channel: String, playing: Boolean, position: Long, durationMs: Long,
    rating: Int, subscribed: Boolean, canSubscribe: Boolean, playFocus: FocusRequester, seekFocus: FocusRequester,
    toggle: () -> Unit, seek: (Long) -> Unit, subscribe: () -> Unit, rate: (Int) -> Unit,
    avatar: String, canPrevious: Boolean, canNext: Boolean, previous: () -> Unit, next: () -> Unit,
    openChannel: () -> Unit, settings: () -> Unit, comments: () -> Unit, chapters: VideoChapters = VideoChapters.Empty,onSeekFocus: (Boolean) -> Unit = {},
) {
    val gradient = Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))
    Column(Modifier.fillMaxWidth().testTag("player-controls").background(gradient).padding(start = 32.dp, end = 32.dp, top = 35.dp, bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Text(channel,color = Muted,fontSize = 12.sp,maxLines = 1,overflow = TextOverflow.Ellipsis,modifier = Modifier.weight(1f,fill = false))
            chapters.current(position,durationMs)?.title?.takeIf { it.isNotBlank() }?.let { chapter ->
                Text(" · ",color = Muted,fontSize = 12.sp)
                Text(chapter,color = Color.White,fontSize = 12.sp,maxLines = 1,overflow = TextOverflow.Ellipsis,modifier = Modifier.weight(1f).testTag("player-chapter-title"))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(duration(position/1000), fontSize = 12.sp, modifier = Modifier.width(55.dp))
            ChapterSeekBar(position,durationMs,chapters,seekFocus,toggle,seek,Modifier.weight(1f),onSeekFocus)
            Text(if(durationMs > 0) duration(durationMs/1000) else "Live", fontSize = 12.sp, modifier = Modifier.padding(start = 12.dp))
        }
        Box(Modifier.fillMaxWidth().height(52.dp)) {
            Row(Modifier.align(Alignment.CenterStart).widthIn(max = 250.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerButton("Open channel", Modifier.size(44.dp).testTag("player-channel"), onClick = openChannel, enabled = canSubscribe) {
                    if(avatar.isNotBlank()) coil.compose.AsyncImage(avatar, channel, contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.size(36.dp).clip(androidx.compose.foundation.shape.CircleShape))
                    else Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(24.dp))
                }
                if(canSubscribe) PlayerButton(if(subscribed) "Unsubscribe" else "Subscribe", Modifier.width(104.dp).height(44.dp), onClick = subscribe) {
                    Text(if(subscribed) "Subscribed" else "Subscribe", fontSize = 12.sp)
                }
            }
            Row(Modifier.align(Alignment.Center).testTag("player-transport"), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerButton("Previous video", Modifier.size(44.dp), previous, enabled = canPrevious) { TransportGlyph(previous = true) }
                PlayerButton("Play / pause", Modifier.size(48.dp).focusRequester(playFocus), toggle) {
                    val color = LocalContentColor.current
                    Canvas(Modifier.size(26.dp)) {
                        if(playing) {
                            drawRoundRect(color,Offset(size.width*.25f,size.height*.2f),Size(size.width*.16f,size.height*.6f),androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                            drawRoundRect(color,Offset(size.width*.59f,size.height*.2f),Size(size.width*.16f,size.height*.6f),androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
                        } else {
                            drawPath(androidx.compose.ui.graphics.Path().apply {
                                moveTo(size.width*.35f,size.height*.2f);lineTo(size.width*.8f,size.height*.5f);lineTo(size.width*.35f,size.height*.8f);close()
                            },color)
                        }
                    }
                }
                PlayerButton("Next video", Modifier.size(44.dp), next, enabled = canNext) { TransportGlyph(previous = false) }
            }
            Row(Modifier.align(Alignment.CenterEnd), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerButton("Like locally", Modifier.size(40.dp), selected = rating == 1, onClick = { rate(if(rating == 1) 0 else 1) }) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(20.dp))
                }
                PlayerButton("Dislike locally", Modifier.size(40.dp), selected = rating == -1, onClick = { rate(if(rating == -1) 0 else -1) }) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(20.dp).rotate(180f))
                }
                PlayerButton("Comments", Modifier.size(40.dp), comments) { CommentBubbleIcon() }
                PlayerButton("Settings", Modifier.size(40.dp), settings) { Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(22.dp)) }
            }
        }
    }
}

@Composable private fun PlayerButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit, selected: Boolean = false, enabled: Boolean = true, content: @Composable () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = modifier.defaultMinSize(minWidth = 1.dp).semantics { contentDescription = label },
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(24.dp)),
        colors = ButtonDefaults.colors(containerColor = if(selected || label == "Play / pause") Color.White else Color(0xCC333333),
            contentColor = if(selected || label == "Play / pause") Color.Black else Color.White,
            focusedContainerColor = Color.White, focusedContentColor = Color.Black),
        contentPadding = PaddingValues(0.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

@Composable fun RelatedPlayerVideos(videos: List<Video>, firstFocus: FocusRequester, onReturnToControls: () -> Unit, onPlay: (Video) -> Unit) {
    val grid = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    var focusedIndex by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxWidth().height(340.dp).background(Color(0xF00F0F0F)).padding(24.dp).testTag("player-related")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Related videos", fontSize = 20.sp, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        if(videos.isEmpty()) Text("No related videos are available. Press Up to return.", color = Muted)
        LazyVerticalGrid(state = grid,columns = GridCells.Adaptive(210.dp),modifier = Modifier.onPreviewKeyEvent { event ->
            val row = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focusedIndex }?.row
            if(event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP && row == 0) {
                onReturnToControls();true
            } else false
        },verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            itemsIndexed(videos, key = { _, video -> video.url }) { index, video ->
                VideoCard(video, Modifier.fillMaxWidth().onFocusChanged { if(it.isFocused) focusedIndex = index }.then(if(index == 0) Modifier.focusRequester(firstFocus) else Modifier).testTag("related-video-$index")) { onPlay(video) }
            }
        }
    }
}

@Composable private fun TransportGlyph(previous: Boolean) {
    val color = LocalContentColor.current
    Canvas(Modifier.size(25.dp)) {
        fun x(value: Float) = size.width * if(previous) 1f-value else value
        drawPath(androidx.compose.ui.graphics.Path().apply {
            moveTo(x(.22f),size.height*.2f);lineTo(x(.68f),size.height*.5f);lineTo(x(.22f),size.height*.8f);close()
        },color)
        val bar = if(previous) .18f else .74f
        drawRoundRect(color,Offset(size.width*bar,size.height*.18f),Size(size.width*.08f,size.height*.64f),androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
    }
}

@Composable internal fun CommentBubbleIcon(modifier: Modifier = Modifier.size(20.dp),color: Color = LocalContentColor.current,outline: Boolean = false) {
    Canvas(modifier) {
        val bubble = androidx.compose.ui.graphics.Path().apply {
            moveTo(size.width*.2f,size.height*.08f);lineTo(size.width*.8f,size.height*.08f)
            quadraticTo(size.width*.94f,size.height*.08f,size.width*.94f,size.height*.22f)
            lineTo(size.width*.94f,size.height*.66f);quadraticTo(size.width*.94f,size.height*.8f,size.width*.8f,size.height*.8f)
            lineTo(size.width*.45f,size.height*.8f);lineTo(size.width*.2f,size.height*.98f);lineTo(size.width*.2f,size.height*.8f)
            quadraticTo(size.width*.06f,size.height*.8f,size.width*.06f,size.height*.66f)
            lineTo(size.width*.06f,size.height*.22f);quadraticTo(size.width*.06f,size.height*.08f,size.width*.2f,size.height*.08f);close()
        }
        drawPath(bubble,color,style = if(outline) androidx.compose.ui.graphics.drawscope.Stroke(1.4.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill)
    }
}
