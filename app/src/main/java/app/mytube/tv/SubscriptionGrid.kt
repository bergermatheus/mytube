package app.mytube.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.view.KeyEvent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import coil.compose.SubcomposeAsyncImage

@Composable internal fun SubscriptionGrid(channels: List<Subscription>,avatars: Map<String,String>,state: LazyGridState,first: FocusRequester,
    open: (Subscription) -> Unit,exitLeft: (() -> Unit)? = null) {
    var focusedIndex by remember { mutableIntStateOf(-1) }
    if(channels.isEmpty()) {
        Text("Long-press a video card or use Subscribe in the player.",color = Muted)
        return
    }
    LazyVerticalGrid(state = state,columns = GridCells.Adaptive(150.dp),modifier = Modifier.fillMaxSize().testTag("subscription-grid").onPreviewKeyEvent { event ->
            if(event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && exitLeft != null && state.layoutInfo.visibleItemsInfo.any { it.index == focusedIndex && it.column == 0 }) { exitLeft();true } else false
        },
        contentPadding = PaddingValues(8.dp),horizontalArrangement = Arrangement.spacedBy(14.dp),verticalArrangement = Arrangement.spacedBy(18.dp)) {
        itemsIndexed(channels,key = { _,channel -> channel.url }) { index,channel ->
            Card(onClick = { open(channel) },modifier = Modifier.fillMaxWidth().testTag("subscription-$index")
                .then(if(index == 0) Modifier.focusRequester(first) else Modifier).onFocusChanged { if(it.isFocused) focusedIndex = index },
                border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp,OffWhite))),
                colors = CardDefaults.colors(containerColor = Color.Transparent,focusedContainerColor = Panel),
                scale = CardDefaults.scale(focusedScale = 1.02f)) {
                Column(Modifier.fillMaxWidth().padding(12.dp),horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SubcomposeAsyncImage(model = avatars[channel.url],contentDescription = "${channel.name} channel photo",
                        contentScale = ContentScale.Crop,modifier = Modifier.size(100.dp).clip(CircleShape).background(Panel)
                            .testTag("subscription-avatar-$index").semantics { contentDescription = "${channel.name} channel photo" },
                        loading = { AvatarPlaceholder() },error = { AvatarPlaceholder() })
                    Text(channel.name,fontSize = 14.sp,fontWeight = FontWeight.Medium,maxLines = 2,minLines = 2,
                        overflow = TextOverflow.Ellipsis,modifier = Modifier.fillMaxWidth(),textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}
@Composable private fun AvatarPlaceholder() {
    Box(Modifier.fillMaxSize(),contentAlignment = Alignment.Center) { Icon(Icons.Default.Person,null,Modifier.size(42.dp),tint = Muted) }
}
