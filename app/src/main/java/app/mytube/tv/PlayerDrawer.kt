package app.mytube.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

private enum class SettingSection(val title: String) { QUALITY("Quality"), SUBTITLES("Subtitles"), SPEED("Playback speed"), AUDIO("Audio language") }

@Composable fun PlayerSettings(
    firstFocus: FocusRequester, quality: Int, qualities: List<Int>, speed: Float,
    captions: List<CaptionOption>, selectedCaption: String?,
    setQuality: (Int) -> Unit, setSpeed: (Float) -> Unit, setCaption: (String?) -> Unit, close: () -> Unit,
    audio: List<AudioLanguage> = emptyList(),selectedAudio: String? = null,setAudio: (String?) -> Unit = {},
) {
    var section by remember { mutableStateOf<SettingSection?>(null) }
    val optionFocus = remember { FocusRequester() }
    BackHandler { if(section != null) section = null else close() }
    LaunchedEffect(section) { if(section == null) firstFocus.requestFocus() else optionFocus.requestFocus() }
    LazyColumn(Modifier.width(360.dp).fillMaxHeight().background(Color(0xFF202027)).testTag("player-settings").padding(24.dp).focusGroup(),
        verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Text(section?.title ?: "Settings",fontSize = 25.sp,fontWeight = FontWeight.Bold,modifier = Modifier.padding(bottom = 16.dp)) }
        when(section) {
            null -> {
                item { SettingRow("Quality","Up to ${quality}p","HD",Modifier.focusRequester(firstFocus)) { section = SettingSection.QUALITY } }
                item { SettingRow("Audio language",audio.firstOrNull { it.id == selectedAudio }?.label ?: audio.firstOrNull { it.original }?.let { it.label + " (original)" } ?: "Original when identified","♪") { section = SettingSection.AUDIO } }
                item { SettingRow("Subtitles",captions.firstOrNull { it.id == selectedCaption }?.label ?: "Off","CC") { section = SettingSection.SUBTITLES } }
                item { SettingRow("Playback speed",if(speed == 1f) "Normal" else "${speed}×","speed") { section = SettingSection.SPEED } }
            }
            SettingSection.QUALITY -> {
                itemsIndexed(qualities) { index,height ->
                    SettingOption("${height}p",height == quality,if(index == 0) Modifier.focusRequester(optionFocus) else Modifier) { setQuality(height); section = null }
                }
                if(qualities.isEmpty()) item { SettingOption("No alternate qualities",true,Modifier.focusRequester(optionFocus)) { section = null } }
            }
            SettingSection.SUBTITLES -> {
                item { SettingOption("Off",selectedCaption == null,Modifier.focusRequester(optionFocus)) { setCaption(null); section = null } }
                itemsIndexed(captions,key = { _,caption -> caption.id }) { _,caption ->
                    SettingOption(caption.label,caption.id == selectedCaption) { setCaption(caption.id); section = null }
                }
                if(captions.isEmpty()) item { Text("No subtitles provided for this video.",color = Muted,fontSize = 14.sp) }
            }
            SettingSection.AUDIO -> {
                item { SettingOption("Original (automatic)",selectedAudio == null,Modifier.focusRequester(optionFocus)) { setAudio(null);section = null } }
                itemsIndexed(audio,key = { _,track -> track.id }) { _,track ->
                    SettingOption(track.label + if(track.original) " (original)" else "",selectedAudio == track.id) { setAudio(track.id);section = null }
                }
                if(audio.isEmpty()) item { Text("No separately selectable audio languages supplied for this video.",color = Muted) }
            }
            SettingSection.SPEED -> {
                itemsIndexed(listOf(.25f,.5f,.75f,1f,1.25f,1.5f,1.75f,2f)) { index,value ->
                    SettingOption(if(value == 1f) "Normal" else "${value}×",value == speed,if(index == 0) Modifier.focusRequester(optionFocus) else Modifier) { setSpeed(value); section = null }
                }
            }
        }
    }
}

@Composable private fun SettingRow(title: String,value: String,symbol: String,modifier: Modifier = Modifier,onClick: () -> Unit) {
    Button(onClick,modifier.fillMaxWidth().height(74.dp),shape = ButtonDefaults.shape(shape = RoundedCornerShape(13.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(containerColor = Color(0xFF383840),focusedContainerColor = OffWhite,focusedContentColor = Background),contentPadding = PaddingValues(18.dp,12.dp)) {
        Row(Modifier.fillMaxSize(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            if(symbol == "speed") {
                val color = LocalContentColor.current
                Canvas(Modifier.size(26.dp)) {
                    drawArc(color,35f,290f,false,style = Stroke(2.dp.toPx()))
                    drawLine(color,androidx.compose.ui.geometry.Offset(size.width*.5f,size.height*.5f),androidx.compose.ui.geometry.Offset(size.width*.85f,size.height*.25f),2.dp.toPx())
                }
            } else Box(Modifier.size(28.dp,22.dp).border(1.5.dp,LocalContentColor.current,RoundedCornerShape(3.dp)),contentAlignment = Alignment.Center) {
                Text(symbol,fontSize = 12.sp,fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title,fontSize = 19.sp,fontWeight = FontWeight.SemiBold)
                Text(value,fontSize = 13.sp,color = LocalContentColor.current.copy(alpha = .7f))
            }
            Text("›",fontSize = 29.sp)
        }
    }
}
@Composable private fun SettingOption(title: String,selected: Boolean,modifier: Modifier = Modifier,onClick: () -> Unit) {
    Button(onClick,modifier.fillMaxWidth().heightIn(min = 52.dp),shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = ButtonDefaults.scale(focusedScale = 1f), colors = ButtonDefaults.colors(containerColor = Color(0xFF383840),focusedContainerColor = OffWhite,focusedContentColor = Background)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically) {
            Text(title,fontSize = 17.sp,modifier = Modifier.weight(1f))
            if(selected) Text("✓",fontSize = 21.sp)
        }
    }
}

@Composable fun VideoComments(firstFocus: FocusRequester,comments: List<VideoComment>,total: Int?,busy: Boolean,error: String?,retry: () -> Unit) {
    val firstComment = remember { FocusRequester() }
    LaunchedEffect(comments.firstOrNull(),busy,error) {
        if(comments.isNotEmpty()) firstComment.requestFocus() else firstFocus.requestFocus()
    }
    LazyColumn(Modifier.width(400.dp).fillMaxHeight().background(Color(0xFF202027)).testTag("player-comments")
        .padding(horizontal = 16.dp).focusRequester(firstFocus).focusGroup().focusable(),
        verticalArrangement = Arrangement.spacedBy(10.dp),contentPadding = PaddingValues(top = 20.dp,bottom = 16.dp)) {
        item { Text("Comments" + (total?.let { " " + java.text.NumberFormat.getIntegerInstance().format(it) } ?: ""),
            fontSize = 25.sp,fontWeight = FontWeight.Bold,modifier = Modifier.padding(bottom = 12.dp)) }
        if(busy) item { LoadingSpinner("comments-loading","Loading comments") }
        if(error != null) item { Text(error,color = Muted);Action("Try again",onClick = retry) }
        if(!busy && error == null && comments.isEmpty()) item { Text("Comments are disabled or unavailable for this video.",color = Muted) }
        itemsIndexed(comments) { index,comment -> CommentCard(comment,
            Modifier.fillMaxWidth().then(if(index == 0) Modifier.focusRequester(firstComment) else Modifier).testTag("comment-card-$index")) }
        if(comments.isNotEmpty()) item { Text("Showing the first page of comments.",color = Muted,fontSize = 12.sp,modifier = Modifier.padding(4.dp)) }
    }
}

@Composable private fun CommentCard(comment: VideoComment,modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val foreground = if(focused) Background else Color.White
    val secondary = foreground.copy(alpha = .72f)
    Column(modifier.onFocusChanged { focused = it.isFocused }
        .background(if(focused) OffWhite else Color(0xFF383840),RoundedCornerShape(14.dp))
        .semantics(mergeDescendants = true) {}.focusable().padding(16.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if(comment.avatar.isNotBlank()) coil.compose.AsyncImage(comment.avatar,null,
                modifier = Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape),contentScale = androidx.compose.ui.layout.ContentScale.Crop)
            else Box(Modifier.size(32.dp).background(Color(0xFF62626B),androidx.compose.foundation.shape.CircleShape),contentAlignment = Alignment.Center) {
                Icon(androidx.compose.material.icons.Icons.Default.Person,null,Modifier.size(22.dp),tint = Color.White)
            }
            Text(androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold));append(comment.author.ifBlank { "Author unavailable" });pop()
                if(comment.published.isNotBlank()) append(" · " + comment.published)
            },color = foreground,fontSize = 13.sp,maxLines = 2,overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,modifier = Modifier.weight(1f))
            if(comment.pinned) Text("📌",fontSize = 15.sp,modifier = Modifier.semantics { contentDescription = "Pinned comment" })
        }
        Text(comment.text,color = foreground,fontSize = 14.sp,lineHeight = 20.sp)
        Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if(comment.likes != null || comment.likesText.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(androidx.compose.material.icons.Icons.Default.ThumbUp,null,Modifier.size(14.dp),tint = secondary)
                Text(comment.likesText.ifBlank { java.text.NumberFormat.getIntegerInstance().format(comment.likes) },color = secondary,fontSize = 12.sp)
            }
            comment.replies?.let { replies -> Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                CommentBubbleIcon(Modifier.size(14.dp),secondary,true)
                Text(java.text.NumberFormat.getIntegerInstance().format(replies),color = secondary,fontSize = 12.sp)
            } }
        }
    }
}

data class CaptionOption(val id: String, val label: String, val language: String, val url: String, val mime: String)
