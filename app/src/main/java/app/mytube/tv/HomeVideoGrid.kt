package app.mytube.tv

import android.view.KeyEvent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text

/** Pagination changes data, never the focusability of committed video cards. */
@Composable internal fun HomeVideoGrid(videos: List<Video>,gridState: LazyGridState,firstVideo: FocusRequester,
    tv: Boolean,home: Boolean,busy: Boolean,hasMore: Boolean,error: String?,
    more: () -> Unit,options: (Video) -> Unit,play: (Video) -> Unit,exitLeft: (() -> Unit)? = null,resolveTitle: (suspend (Video) -> Unit)? = null) {
    var focusedUrl by remember { mutableStateOf<String?>(null) }
    LazyVerticalGrid(state = gridState,modifier = Modifier.fillMaxSize().testTag("browse-grid")
        .semantics { stateDescription = "${videos.size} videos" }.onPreviewKeyEvent { event ->
            val index = videos.indexOfFirst { it.url == focusedUrl }
            val columns = if(tv) 3 else gridState.layoutInfo.visibleItemsInfo.filter { it.column >= 0 }.maxOfOrNull { it.column+1 } ?: 3
            if(event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && index >= 0 && index%columns == 0 && exitLeft != null) {
                exitLeft();true
            } else if(event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if(home && index >= 0 && index >= videos.size-columns*2 && hasMore && !busy) more()
                // There is nowhere below the last row yet. Retain card focus so Up/Left/Right
                // continue to navigate loaded videos; the loading footer is never a focus stop.
                index >= 0 && index/columns == (videos.size-1)/columns
            } else false
        },columns = if(tv) GridCells.Fixed(3) else GridCells.Adaptive(220.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(6.dp,8.dp,6.dp,28.dp)) {
        itemsIndexed(videos,key = { _,video -> video.url }) { index,video ->
            VideoCard(video,Modifier.fillMaxWidth().then(if(index == 0) Modifier.focusRequester(firstVideo) else Modifier)
                .testTag("browse-video-$index").onFocusChanged { if(it.isFocused) focusedUrl = video.url },
                onLongClick = { options(video) },resolveTitle = resolveTitle) { play(video) }
        }
        if(home && error != null) item(key = "feed-warning",span = { GridItemSpan(maxLineSpan) }) {
            Text(error,color = androidx.compose.ui.graphics.Color(0xFFFFD7DF),fontSize = 13.sp)
        }
        if(home && hasMore) item(key = "next-page",span = { GridItemSpan(maxLineSpan) }) {
            Row(Modifier.height(56.dp).testTag("pagination-status"),verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("More videos ↓ · ${videos.size} shown",color = Muted,fontSize = 14.sp)
                if(busy) LoadingSpinner("page-loading","Loading more videos")
                if(error != null && !busy) Action("Retry loading more",onClick = more)
            }
        }
    }
}
