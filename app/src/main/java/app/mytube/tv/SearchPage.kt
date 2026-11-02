package app.mytube.tv

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.*

/** The keyboard is ordinary page content: no IME, dialog, or account is needed. */
@Composable internal fun SearchPage(model: BrowseViewModel, back: () -> Unit, play: (Video) -> Unit,embedded: Boolean = false,keyboardEntry: FocusRequester? = null,sidebarEntry: FocusRequester? = null,active: Boolean = true) {
    val state by model.searchState.collectAsStateWithLifecycle()
    val suggestions by model.suggestions.collectAsStateWithLifecycle()
    var editing by rememberSaveable { mutableStateOf(true) }
    var returnToKeyboard by remember { mutableStateOf(false) }
    val editorState = rememberSaveableStateHolder()
    val resultsGrid = rememberLazyGridState()
    val ownFirstKey = remember { FocusRequester() }
    val firstKey = keyboardEntry ?: ownFirstKey
    val firstSuggestion = remember { FocusRequester() }
    val firstVideo = remember { FocusRequester() }
    val firstKeyInLastRow = remember { FocusRequester() }
    LaunchedEffect(editing,returnToKeyboard,active) {
        if(active && editing && returnToKeyboard) { withFrameNanos { };firstKeyInLastRow.requestFocus();returnToKeyboard = false }
    }
    var focusResults by remember { mutableStateOf(false) }
    fun submit(query: String) { if(query.isNotBlank()) { model.search(query); focusResults = true } }
    fun edit(query: String) { if(!editing) { editing = true;returnToKeyboard = true };focusResults = false; model.editSearch(query) }
    fun delete() { edit(state.query.dropLastCharacter()) }
    LaunchedEffect(active,embedded) { if(active && !embedded) { withFrameNanos { }; firstKey.requestFocus() } }
    LaunchedEffect(active,focusResults,state.busy,state.videos) {
        if(active && focusResults && !state.busy && state.videos.isNotEmpty()) {
            resultsGrid.scrollToItem(0)
            withFrameNanos { }; firstVideo.requestFocus(); focusResults = false
        }
    }
    val choices = if(state.query.isBlank()) state.history.take(5) else
        (suggestions.items + state.history.filter { it.contains(state.query,true) }).distinct().take(5)
    Row(Modifier.fillMaxSize().background(Background).testTag("search-page").onPreviewKeyEvent { event ->
        val key = event.nativeKeyEvent
        if(key.action != KeyEvent.ACTION_DOWN) false
        else when {
            key.keyCode == KeyEvent.KEYCODE_DEL -> { delete(); true }
            key.keyCode == KeyEvent.KEYCODE_SEARCH -> { submit(state.query); true }
            key.unicodeChar > 0 && !key.isCtrlPressed && !key.isAltPressed -> {
                val c = key.unicodeChar
                if(!Character.isISOControl(c)) { edit(state.query + String(Character.toChars(c))); true } else false
            }
            else -> false
        }
    }) {
        if(!embedded) Box(Modifier.width(64.dp).fillMaxHeight(),contentAlignment = Alignment.Center) {
            Button(onClick = back,modifier = Modifier.size(40.dp).testTag("search-back").semantics { contentDescription = "Back to browsing" },
                shape = ButtonDefaults.shape(shape = CircleShape),contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.colors(containerColor = SearchSurface,focusedContainerColor = OffWhite,focusedContentColor = Background)) {
                Box(Modifier.fillMaxSize(),contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.ArrowBack,null,Modifier.size(24.dp)) }
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = 22.dp,end = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp),horizontalArrangement = Arrangement.spacedBy(10.dp),verticalAlignment = Alignment.CenterVertically) {
                VoiceSearchButton(::submit)
                SearchPill(state.query,{ editing = true;returnToKeyboard = true },Modifier.fillMaxWidth(.78f)
                    .then(if(!editing) Modifier.focusRequester(firstKey) else Modifier)
                    .onFocusChanged { if(it.isFocused && !editing) { editing = true;returnToKeyboard = true } })
            }
            if(editing) editorState.SaveableStateProvider("search-editor") {
            Row(Modifier.fillMaxWidth().height(180.dp),horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.width(280.dp).testTag("search-history")) {
                    if(state.query.isBlank() && choices.isEmpty()) Text("Your searches appear here",color = Muted,fontSize = 15.sp,modifier = Modifier.padding(top = 8.dp))
                    if(state.query.isNotBlank() && suggestions.busy) LoadingSpinner("suggestions-loading","Finding search suggestions")
                    LazyColumn(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        itemsIndexed(choices,key = { _, text -> text }) { index,text ->
                            Button(onClick = { submit(text) },modifier = Modifier.fillMaxWidth().height(34.dp)
                                .then(if(index == 0) Modifier.focusRequester(firstSuggestion) else Modifier)
                                .focusProperties { right = firstKey;if(sidebarEntry != null) left = sidebarEntry }.testTag("search-suggestion"),
                                scale = ButtonDefaults.scale(focusedScale = 1f), contentPadding = PaddingValues(horizontal = 8.dp,vertical = 0.dp),
                                colors = ButtonDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent,focusedContainerColor = OffWhite,focusedContentColor = Background)) {
                                if(state.query.isBlank()) Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_history_clock),contentDescription = "Recent search",modifier = Modifier.size(21.dp).testTag("search-history-clock")) else Icon(Icons.Default.Search,null,Modifier.size(19.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(text,Modifier.weight(1f),fontSize = 16.sp,fontWeight = FontWeight.Medium,maxLines = 1,overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    suggestions.error?.takeIf { state.query.isNotBlank() }?.let { Text(it,color = Muted,fontSize = 11.sp) }
                }
                SearchKeyboard(append = { edit(state.query + it) },delete = ::delete,clear = { edit("") },
                    submit = { submit(state.query) },firstKey = firstKey,lastRow = firstKeyInLastRow,
                    left = firstSuggestion.takeIf { choices.isNotEmpty() } ?: sidebarEntry,down = firstVideo.takeIf { state.videos.isNotEmpty() })
                Spacer(Modifier.width(30.dp))
            }
            }
            if(state.busy) LoadingSpinner("search-loading","Loading search results")
            state.error?.let { Text(it,color = Muted,fontSize = 13.sp); Action("Try again") { submit(state.submittedQuery) } }
            if(!state.busy && state.videos.isEmpty() && state.submittedQuery.isNotBlank() && state.error == null) Text("No videos found. Try another search.",color = Muted)
            var focusedResult by remember { mutableIntStateOf(0) }
            LaunchedEffect(editing,focusedResult/3) {
                if(!editing && state.videos.isNotEmpty()) resultsGrid.scrollToItem((focusedResult/3)*3)
            }
            LazyVerticalGrid(state = resultsGrid,columns = GridCells.Fixed(3),modifier = Modifier.fillMaxWidth().weight(1f).testTag("search-videos")
                .onPreviewKeyEvent { event ->
                    if(event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP && focusedResult < 3) {
                        editing = true;returnToKeyboard = true;true
                    } else if(event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        if(focusedResult >= state.videos.size-6 && state.hasMore && !state.busy) model.moreSearch()
                        focusedResult/3 == (state.videos.size-1)/3
                    } else false
                },horizontalArrangement = Arrangement.spacedBy(16.dp),verticalArrangement = Arrangement.spacedBy(18.dp),contentPadding = PaddingValues(4.dp)) {
                itemsIndexed(state.videos,key = { _, video -> video.url }) { index,video ->
                    VideoCard(video,Modifier.fillMaxWidth().then(if(index == 0) Modifier.focusRequester(firstVideo) else Modifier)
                        .onFocusChanged { if(it.isFocused) { focusedResult = index;editing = false } }.testTag("search-video-$index")) { play(video) }
                }
                if(state.hasMore) item(key = "more-results",span = { GridItemSpan(maxLineSpan) }) { if(state.busy) LoadingSpinner("search-page-loading","Loading more results") else Action("More results",true,model::moreSearch) }
            }
        }
    }
}

@Composable private fun SearchKeyboard(append: (String) -> Unit,delete: () -> Unit,clear: () -> Unit,submit: () -> Unit,
    firstKey: FocusRequester,lastRow: FocusRequester,left: FocusRequester?,down: FocusRequester?) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val letters = when(mode) {
        1 -> listOf("1234567","890@#&%","+=/()!?",".,:;-_'")
        2 -> listOf("ÁÀÂÃÄÉÈ","ÊËÍÌÎÏÓ","ÒÔÕÖÚÙÛ","ÜÇÑ-'. ")
        else -> listOf("ABCDEFG","HIJKLMN","OPQRSTU","VWXYZ-'")
    }
    Column(Modifier.width(332.dp).testTag("search-keyboard").focusGroup(),verticalArrangement = Arrangement.spacedBy(4.dp)) {
        letters.forEachIndexed { row,chars ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                chars.take(7).forEachIndexed { col,char ->
                    KeyboardKey(char.toString(),"key-$row-$col",Modifier.size(38.dp,32.dp)
                        .then(if(row == 0 && col == 0) Modifier.focusRequester(firstKey) else Modifier)
                        .focusProperties { if(col == 0 && left != null) this.left = left }) { append(char.lowercaseChar().toString()) }
                }
                KeyboardKey(when(row) { 0 -> "⌫"; 1 -> if(mode == 1) "ABC" else "&123"; 2 -> "◎"; else -> "." },"key-action-$row",Modifier.size(38.dp,32.dp),
                    description = when(row) { 0 -> "Delete character"; 1 -> "Letters or numbers"; 2 -> "Accented letters"; else -> "Period" }) {
                    when(row) { 0 -> delete(); 1 -> mode = if(mode == 1) 0 else 1; 2 -> mode = if(mode == 2) 0 else 2; else -> append(".") }
                }
            }
        }
        Row(Modifier.padding(top = 4.dp),horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyboardKey("SPACE","keyboard-space",Modifier.weight(1f).height(32.dp).focusRequester(lastRow)
                .focusProperties { if(down != null) this.down = down; if(left != null) this.left = left },filled = true) { append(" ") }
            KeyboardKey("CLEAR","keyboard-clear",Modifier.weight(1f).height(32.dp).focusProperties { if(down != null) this.down = down },filled = true,click = clear)
            KeyboardKey("SEARCH","keyboard-submit",Modifier.weight(1.2f).height(32.dp).focusProperties { if(down != null) this.down = down },filled = true,click = submit)
        }
    }
}

@Composable private fun KeyboardKey(label: String,tag: String,modifier: Modifier,description: String? = null,filled: Boolean = false,click: () -> Unit) {
    Button(onClick = click,modifier = modifier.testTag(tag).semantics { if(description != null) contentDescription = description },
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(7.dp)),scale = ButtonDefaults.scale(focusedScale = 1f),
        contentPadding = PaddingValues(0.dp),colors = ButtonDefaults.colors(containerColor = if(filled) SearchSurface else androidx.compose.ui.graphics.Color.Transparent,
            focusedContainerColor = OffWhite,focusedContentColor = Background)) {
        Box(Modifier.fillMaxSize(),contentAlignment = Alignment.Center) { Text(label,fontSize = if(label.length > 1) 12.sp else 20.sp,fontWeight = if(filled) FontWeight.SemiBold else FontWeight.Normal) }
    }
}

internal fun String.dropLastCharacter(): String = if(isEmpty()) this else substring(0,offsetByCodePoints(length,-1))
