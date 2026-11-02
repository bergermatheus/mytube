package app.mytube.tv

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.input.key.onPreviewKeyEvent
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage

val Background = Color(0xFF0F0F0F)
val Panel = Color(0xFF1B1B1B)
val OffWhite = Color(0xFFECECEC)
val Muted = Color(0xFFAAAAAA)

class MainActivity : ComponentActivity() {
    @android.annotation.SuppressLint("GestureBackNavigation")
    internal fun handleRemoteBack(event: KeyEvent): Boolean {
        val tv = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
        if(tv && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if(event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) onBackPressedDispatcher.onBackPressed()
            return true
        }
        return false
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MyTubeApplication
        setContent {
            MyTubeTheme {
                val model: BrowseViewModel = viewModel(factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return BrowseViewModel(app.gateway, app.library) as T
                    }
                })
                LaunchedEffect(Unit) {
                    val url = intent.getStringExtra("channel_url").orEmpty()
                    val uri = android.net.Uri.parse(url)
                    if(uri.scheme == "https" && uri.host in listOf("www.youtube.com", "youtube.com") &&
                        (uri.path.orEmpty().startsWith("/channel/") || uri.path.orEmpty().startsWith("/@"))) {
                        model.openChannel(Subscription(url, intent.getStringExtra("channel_name").orEmpty()))
                    }
                }
                BrowseApp(model)
            }
        }
    }
}

@Composable fun MyTubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(primary = OffWhite, onPrimary = Background, background = Background,
        surface = Panel, onSurface = Color.White)) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable private fun BrowseApp(model: BrowseViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val pageStates by model.pageStates.collectAsStateWithLifecycle()
    val config = LocalConfiguration.current
    val tv = config.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION
    val context = LocalContext.current
    val app = context.applicationContext as MyTubeApplication
    val profile by app.profile.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Video?>(null) }
    fun playVideo(video: Video) { context.startActivity(Intent(context,PlayerActivity::class.java).putExtra("video_url",video.url)) }
    var focusFirstVideo by remember { mutableStateOf(false) }
    var interestInput by rememberSaveable { mutableStateOf("") }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var sidebarExpanded by remember { mutableStateOf(true) }

    val navFocus = remember { (Screen.navigation + Screen.SETTINGS).associateWith { FocusRequester() } }
    val firstNav = navFocus.getValue(Screen.HOME)
    val searchNav = navFocus.getValue(Screen.SEARCH)
    val searchKeyboard = remember { FocusRequester() }
    val videoFocus = remember { Screen.entries.associateWith { FocusRequester() } }
    val firstVideo = videoFocus.getValue(state.screen)
    val firstChannel = remember { FocusRequester() }
    val browseGrids = remember { Screen.entries.associateWith { androidx.compose.foundation.lazy.grid.LazyGridState() } }
    val browseGridState = browseGrids.getValue(state.screen)
    val channelGridState = rememberLazyGridState()
    val search by model.searchState.collectAsStateWithLifecycle()
    LaunchedEffect(focusFirstVideo,state.rows,search.open) {
        if(!search.open && focusFirstVideo && state.rows.any { it.videos.isNotEmpty() }) {
            if(state.screen in listOf(Screen.HOME,Screen.CHANNEL,Screen.LIVE,Screen.HISTORY)) browseGridState.scrollToItem(0)
            withFrameNanos { }
            firstVideo.requestFocus()
            focusFirstVideo = false
        }
    }
    var focusFirstChannel by remember { mutableStateOf(false) }
    LaunchedEffect(focusFirstChannel,state.subscriptions,search.open) {
        if(focusFirstChannel && !search.open && state.screen == Screen.SUBSCRIPTIONS && state.subscriptions.isNotEmpty()) {
            channelGridState.scrollToItem(0)
            withFrameNanos { }; firstChannel.requestFocus(); focusFirstChannel = false
        }
    }
    // Data refreshes can reset their own page; returning to a route preserves its scroll.
    var menuSelection by remember { mutableStateOf(state.screen) }
    var pendingNavigation by remember { mutableStateOf<Screen?>(null) }
    LaunchedEffect(state.screen) { if(pendingNavigation == null) menuSelection = if(state.screen == Screen.CHANNEL) Screen.SUBSCRIPTIONS else state.screen }
    LaunchedEffect(pendingNavigation) {
        val target = pendingNavigation ?: return@LaunchedEffect
        // Paint focus first; fast remote repeats cancel destinations before their first mount.
        withFrameNanos { };withFrameNanos { }
        if(target != state.screen && !(state.screen == Screen.CHANNEL && target == Screen.SUBSCRIPTIONS)) model.selectScreen(target)
        pendingNavigation = null
    }
    LaunchedEffect(state.screen,state.channel?.url) {
        if(state.screen == Screen.CHANNEL) {
            pendingNavigation = null;menuSelection = Screen.SUBSCRIPTIONS
            sidebarExpanded = false;focusFirstVideo = true
        }
    }
    val searchEntries = remember { Screen.entries.associateWith { FocusRequester() } }
    val searchEntry = searchEntries.getValue(state.screen)
    var restoreSearchFocus by remember { mutableStateOf(false) }
    var restoreNav by remember { mutableStateOf<Screen?>(null) }
    LaunchedEffect(restoreNav,state.screen,search.open) {
        restoreNav?.takeIf { it == state.screen && !search.open }?.let { screen -> withFrameNanos { };navFocus.getValue(screen).requestFocus();restoreNav = null }
    }
    fun returnToSidebar() { sidebarExpanded = true;navFocus.getValue(if(state.screen == Screen.CHANNEL) Screen.SUBSCRIPTIONS else state.screen).requestFocus() }
    fun selectNavigation(screen: Screen) {
        focusFirstVideo = false;focusFirstChannel = false
        sidebarExpanded = true
        menuSelection = screen;pendingNavigation = screen
    }
    var focusSearchKeyboard by remember { mutableStateOf(false) }
    LaunchedEffect(focusSearchKeyboard,state.screen,search.open) {
        if(focusSearchKeyboard && state.screen == Screen.SEARCH && !search.open) {
            withFrameNanos { };searchKeyboard.requestFocus();focusSearchKeyboard = false
        }
    }
    fun enterNavigationContent(): Boolean {
        pendingNavigation = null
        if(state.screen == Screen.CHANNEL) { sidebarExpanded = false;focusFirstVideo = true;return true }
        model.selectScreen(menuSelection)
        return when {
            menuSelection == Screen.SEARCH -> { sidebarExpanded = false;focusSearchKeyboard = true;true }
            menuSelection in listOf(Screen.HOME,Screen.CHANNEL,Screen.HISTORY,Screen.LIVE) -> {
                sidebarExpanded = false;focusFirstVideo = true;true
            }
            menuSelection == Screen.SUBSCRIPTIONS && state.subscriptions.isNotEmpty() -> { sidebarExpanded = false;focusFirstChannel = true;true }
            else -> false
        }
    }
    LaunchedEffect(search.open) {
        if(!search.open) {
            withFrameNanos { }
            if(state.screen == Screen.CHANNEL) { sidebarExpanded = false;focusFirstVideo = true }
            else if(restoreSearchFocus) { if(state.screen == Screen.SEARCH) searchNav.requestFocus() else searchEntry.requestFocus() } else navFocus.getValue(if(state.screen == Screen.CHANNEL) Screen.SUBSCRIPTIONS else state.screen).requestFocus()
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        model.resume()
    }
    BackHandler(search.open || state.screen != Screen.HOME) {
        if(search.open) { restoreSearchFocus = true; model.closeSearch() }
        else { val target = if(state.screen == Screen.CHANNEL) Screen.SUBSCRIPTIONS else Screen.HOME;navFocus.getValue(target).requestFocus();model.selectScreen(target);restoreNav = target;focusFirstVideo = false;sidebarExpanded = true }
    }
    // The grid state is hoisted, so the search route preserves Home scrolling.
    Box(Modifier.fillMaxSize().background(Background).onPreviewKeyEvent { (context as? MainActivity)?.handleRemoteBack(it.nativeKeyEvent) == true }) {
    Row(Modifier.fillMaxSize()) {
        if(tv && !search.open) Sidebar(menuSelection,sidebarExpanded,profile,navFocus,{ sidebarExpanded = true },::selectNavigation,::enterNavigationContent)
        RetainedPages(if(search.open) Screen.SEARCH else state.screen,Modifier.weight(1f).fillMaxHeight()) { route ->
        val pageActive = route == (if(search.open) Screen.SEARCH else state.screen)
        val state = pageStates[route] ?: state.copy(screen = route)
        val searchEntry = searchEntries.getValue(route)
        val firstVideo = videoFocus.getValue(route)
        val browseGridState = browseGrids.getValue(route)
        if(route == Screen.HOME) LaunchedEffect(state.homeSession) { browseGridState.scrollToItem(0) }
        if(route == Screen.CHANNEL) LaunchedEffect(state.channel?.url) { browseGridState.scrollToItem(0) }
        Column(Modifier.fillMaxSize()) {
            if(state.screen != Screen.SEARCH) BrowseHeader(search.query,{ restoreSearchFocus = true;model.openSearch() },model::search,
                { sidebarExpanded = false },searchEntry)
            if(!tv) LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(Screen.navigation + Screen.SETTINGS) { screen -> NavItem(screen,state.screen,::selectNavigation,
                    if(screen == Screen.HOME) Modifier.width(150.dp).focusRequester(firstNav) else Modifier.width(150.dp),focusSelect = false) }
            }
            if(state.screen == Screen.SEARCH) {
                Box(Modifier.weight(1f).onFocusChanged { if(it.hasFocus) sidebarExpanded = false }) {
                    SearchPage(model,{ if(search.open) { restoreSearchFocus = true;model.closeSearch() } else model.selectScreen(Screen.HOME) },::playVideo,embedded = !search.open,keyboardEntry = searchKeyboard,sidebarEntry = searchNav.takeUnless { search.open },active = pageActive)
                }
            } else Column(Modifier.weight(1f).fillMaxWidth().padding(start = if(tv) 12.dp else 0.dp)
                .onFocusChanged { if(it.hasFocus) sidebarExpanded = false }.focusGroup()) {
                Text(if(state.screen == Screen.CHANNEL) state.channel?.name.orEmpty() else state.screen.title,
                    fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 10.dp))
                state.error?.takeUnless { state.screen == Screen.HOME && state.rows.any { it.videos.isNotEmpty() } }?.let { message ->
                    Column(Modifier.fillMaxWidth().background(Color(0xFF35262C), RoundedCornerShape(12.dp)).padding(14.dp)) {
                        Text(message, color = Color(0xFFFFD7DF), fontSize = 14.sp)
                        Action("Try again", !state.busy) { model.navigate(state.screen) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if(state.screen == Screen.INTERESTS) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                        item { Text("Choose what Home suggests", color = OffWhite, fontSize = 21.sp) }
                        item { Text("Save topics or search phrases, follow channels, or choose ‘Use for suggestions’ on a video. Home combines these choices with your watch history. Your saved preferences stay on this device; fetching matching videos sends those topics to YouTube.", color = Muted) }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                val save = { model.saveInterest(interestInput); interestInput = "" }
                                QueryInput(interestInput, { interestInput = it }, "Topic, e.g. Android development", Modifier.weight(1f), save)
                                Spacer(Modifier.width(12.dp))
                                Action("Save topic", interestInput.isNotBlank(), save)
                            }
                        }
                        item { Text("Saved topics", fontSize = 20.sp) }
                        if(state.interests.isEmpty()) item { Text("No topics yet. You can also save a search as an interest.", color = Muted) }
                        items(state.interests, key = { "interest:${it.id}" }) { interest ->
                            PreferenceItem(interest.label, "Remove") { model.removeInterest(interest.id) }
                        }
                        item { Text("Followed channels", fontSize = 20.sp) }
                        if(state.subscriptions.isEmpty()) item { Text("Long-press a video card to subscribe to its channel, or use Subscribe in the player.", color = Muted) }
                        items(state.subscriptions, key = { "channel:${it.url}" }) { channel ->
                            PreferenceItem(channel.name, "Unfollow") { model.toggle(channel) }
                        }
                        item { Text("Videos used for suggestions", fontSize = 20.sp) }
                        if(state.seeds.isEmpty()) item { Text("Long-press a video card and choose ‘Use for suggestions’. You don't need to watch it first.", color = Muted) }
                        items(state.seeds, key = { "seed:${it.url}" }) { video ->
                            PreferenceItem(video.title, "Remove") { model.setSeed(video, false) }
                        }
                        item { Action("See your suggestions") { model.navigate(Screen.HOME) } }
                    }
                } else if(state.screen == Screen.SETTINGS) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item { ProfileSettings(app.profile) }
                        item { Text("Private by default", fontSize = 22.sp, color = OffWhite) }
                        item { Action("Manage interests & channels") { model.navigate(Screen.INTERESTS) } }
                        item { Text("History, subscriptions and recommendation preferences stay on this device. Video requests and thumbnails still connect to YouTube and its delivery services. No Google login, analytics SDK, or app telemetry is included.", color = Muted) }
                        item { Text("Playback quality", fontSize = 20.sp) }
                        item { Text("Change the quality cap in the player. Auto selects up to 1080p; available formats depend on the video and device.", color = Muted) }
                        item { Action("Clear watch history") { confirmation = "history" } }
                        item { Action("Clear search history") { confirmation = "search-history" } }
                        item { Action("Reset ‘Not interested’ choices") { confirmation = "recommendations" } }
                        item { notice?.let { Text(it, color = OffWhite) } }
                        item { Text("MyTube ${BuildConfig.VERSION_NAME} · GPL-3.0-or-later\nPlayback: AndroidX Media3 · Extraction: NewPipeExtractor v0.26.5\nUI: Compose for TV · Images: Coil · Networking: OkHttp\nLocal recommendations are independent of Google's account recommendations. YouTube changes may require an extractor update.\nAn independent client, not affiliated with YouTube or Google.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp) }
                    }
                } else if(state.screen == Screen.SUBSCRIPTIONS) {
                    Text("Select a channel to see its videos",color = Muted,modifier = Modifier.padding(bottom = 12.dp))
                    SubscriptionGrid(state.subscriptions,state.channelAvatars,channelGridState,firstChannel,model::openChannel,::returnToSidebar)
                } else if(state.screen in listOf(Screen.HOME,Screen.CHANNEL,Screen.LIVE)) {
                    val videos = remember(state.rows) { state.rows.flatMap { it.videos } }
                    if(state.screen == Screen.LIVE && state.busy) Text("Checking live sources ${state.liveChecked}/${state.liveTotal}",color = Muted,fontSize = 12.sp)
                    if(!state.busy && videos.isEmpty() && state.error == null) Box(Modifier.fillMaxSize(),contentAlignment = Alignment.Center) {
                        Text(if(state.screen == Screen.LIVE) "No live videos found right now." else "No unseen videos yet. Add interests or follow more channels.",color = Muted)
                    } else HomeVideoGrid(videos,browseGridState,firstVideo,tv,state.screen == Screen.HOME,state.busy,state.hasMore,state.error,
                        model::moreHome,{ selected = it },::playVideo,::returnToSidebar)
                } else if(state.screen == Screen.HISTORY) {
                    HomeVideoGrid(state.rows.flatMap { it.videos },browseGridState,firstVideo,tv,false,state.busy,false,state.error,
                        {},{ selected = it },::playVideo,::returnToSidebar,resolveTitle = model::originalTitle)
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(bottom = 30.dp)) {
                        items(state.rows, key = { it.key }) { row ->
                            if(row.videos.isNotEmpty()) Column {
                                Text(row.title, fontSize = 19.sp, modifier = Modifier.padding(bottom = 12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(5.dp)) {
                                    itemsIndexed(row.videos, key = { _, video -> video.url }) { index, video ->
                                        VideoCard(video, Modifier.width(245.dp).then(if(row == state.rows.firstOrNull() && index == 0) Modifier.focusRequester(firstVideo) else Modifier)
                                            .onPreviewKeyEvent { if(index == 0 && it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) { returnToSidebar();true } else false }, onLongClick = { selected = video }, resolveTitle = if(state.screen == Screen.HISTORY) model::originalTitle else null) { playVideo(video) }
                                    }
                                }
                            }
                        }
                        if(!state.busy && state.error == null && state.rows.all { it.videos.isEmpty() }) item {
                            Text(when(state.screen) {
                                Screen.SUBSCRIPTIONS -> "No subscriptions yet. Open a video and choose Subscribe to channel."
                                Screen.HISTORY -> "Videos appear here after playback starts."
                                else -> "No unseen suggestions yet. Add topics or videos in Interests, follow a channel, or try another search."
                            }, color = Muted)
                        }
                    }
                }
            }
        }
        }
    }
    if(!search.open && state.busy && state.rows.all { it.videos.isEmpty() } && state.screen in listOf(Screen.HOME,Screen.CHANNEL,Screen.LIVE)) {
        Box(Modifier.fillMaxSize().testTag("initial-loading-area"),contentAlignment = Alignment.Center) { LoadingSpinner("initial-loading","Loading videos") }
    }
    }
    selected?.let { video ->
        val followed = state.subscriptions.any { it.url == video.channelUrl }
        Dialog(onDismissRequest = { selected = null }) {
            Column(Modifier.widthIn(max = 560.dp).heightIn(max = (config.screenHeightDp - 48).dp)
                .background(Panel, RoundedCornerShape(24.dp)).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(video.title, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(video.channel, color = Muted)
                Action(if(video.positionMs > 10_000) "Resume video" else "Watch video") {
                    selected = null
                    context.startActivity(Intent(context, PlayerActivity::class.java).putExtra("video_url", video.url))
                }
                if(video.positionMs > 10_000) Action("Play from beginning") {
                    selected = null
                    context.startActivity(Intent(context, PlayerActivity::class.java).putExtra("video_url", video.url).putExtra("from_start", true))
                }
                if(video.channelUrl.isNotEmpty()) Action("View channel") { selected = null; model.openChannel(Subscription(video.channelUrl, video.channel)) }
                if(video.channelUrl.isNotEmpty()) Action(if(followed) "Unsubscribe from channel" else "Subscribe to channel") { model.toggle(Subscription(video.channelUrl, video.channel)) }
                val savedSeed = state.seeds.any { it.url == video.url }
                Action(if(savedSeed) "Stop using for suggestions" else "Use for suggestions") { model.setSeed(video, !savedSeed) }
                Action("Not interested") { model.hide(video); selected = null }
                Action("Close") { selected = null }
            }
        }
    }
    confirmation?.let { kind -> Dialog(onDismissRequest = { confirmation = null }) {
        Column(Modifier.background(Panel, RoundedCornerShape(20.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(when(kind) { "history" -> "Clear local watch history and resume positions?"; "search-history" -> "Clear local search history?"; else -> "Reset all ‘Not interested’ choices?" })
            Action("Confirm") { when(kind) { "history" -> model.clearHistory(); "search-history" -> model.clearSearches(); else -> model.resetRecommendations() }; notice = "Local data updated."; confirmation = null }
            Action("Cancel") { confirmation = null }
        }
    } }
}

@Composable internal fun QueryInput(value: String, onValueChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, submit: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(value, onValueChange, singleLine = true, cursorBrush = SolidColor(OffWhite),
        textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { if(value.isNotBlank()) submit() }),
        modifier = modifier.onFocusChanged { focused = it.isFocused }
            .border(if(focused) 2.dp else 1.dp, if(focused) OffWhite else Muted, RoundedCornerShape(16.dp)).padding(14.dp),
        decorationBox = { field -> Box { if(value.isEmpty()) Text(hint, color = Muted); field() } })
}

@Composable private fun PreferenceItem(label: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Action(action, onClick = onClick)
    }
}

@Composable fun Action(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, colors = ButtonDefaults.colors(containerColor = Color(0xFF303030),
        focusedContainerColor = OffWhite, focusedContentColor = Background)) { Text(label, fontSize = 14.sp) }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable fun VideoCard(video: Video, modifier: Modifier = Modifier.width(245.dp), onLongClick: (() -> Unit)? = null, resolveTitle: (suspend (Video) -> Unit)? = null, onClick: () -> Unit) {
    LaunchedEffect(video.url,resolveTitle) { resolveTitle?.invoke(video) }
    val context = LocalContext.current
    val thumbnail = remember(video.thumbnail, context) { coil.request.ImageRequest.Builder(context).data(video.thumbnail).size(640,360).build() }
    Card(onClick = onClick, onLongClick = onLongClick, modifier = modifier,
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White))),
        colors = CardDefaults.colors(containerColor = Panel, focusedContainerColor = Color(0xFF333333)),
        scale = CardDefaults.scale(focusedScale = 1.02f)) {
        Box {
            AsyncImage(model = thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f/9).background(Color(0xFF303030)))
            if(video.isLive) Text("LIVE",color = Color.White,fontSize = 11.sp,fontWeight = FontWeight.Bold,modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0xFFE62117),RoundedCornerShape(4.dp)).padding(4.dp))
            else if(video.durationSeconds > 0) Text(duration(video.durationSeconds), color = Color.White, fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).background(Color(0xDD000000), RoundedCornerShape(4.dp)).padding(4.dp))
            watchedFraction(video)?.let { fraction ->
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter)
                    .testTag("thumbnail-progress").semantics { progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(fraction,0f..1f) }) {
                    drawRect(Color(0xFF555555));drawRect(Color(0xFFFF0000),size = androidx.compose.ui.geometry.Size(size.width*fraction,size.height))
                }
            }
        }
        Column(Modifier.padding(12.dp)) {
            Text(video.title, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.testTag("video-title"))
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth().testTag("video-metadata"),horizontalArrangement = Arrangement.spacedBy(5.dp),verticalAlignment = Alignment.CenterVertically) {
                Text(video.channel,maxLines = 1,overflow = TextOverflow.Ellipsis,color = Muted,fontSize = 12.sp,modifier = Modifier.weight(1f,fill = false).testTag("video-channel"))
                Text("·",color = Muted,fontSize = 12.sp)
                Text(publicationLabel(video),maxLines = 1,color = Muted,fontSize = 11.sp,modifier = Modifier.testTag("video-publication"))
            }
            Text(if(video.positionMs > 0) "Watched ${duration(video.positionMs/1000)}" else "", color = OffWhite, fontSize = 11.sp, modifier = Modifier.height(16.dp))
        }
    }
}
fun duration(seconds: Long): String = if(seconds >= 3600) "%d:%02d:%02d".format(seconds/3600, seconds/60%60, seconds%60) else "%d:%02d".format(seconds/60, seconds%60)

