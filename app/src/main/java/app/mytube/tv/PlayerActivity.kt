package app.mytube.tv

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import kotlinx.coroutines.*
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    private val app get() = application as MyTubeApplication
    private var player: ExoPlayer? = null
    private var session: MediaSession? = null
    private var view: PlayerView? = null
    private var loading by mutableStateOf(true)
    private var failure by mutableStateOf<String?>(null)
    private var video by mutableStateOf<Video?>(null)
    private var relatedVideos by mutableStateOf<List<Video>>(emptyList())
    private var panel by mutableStateOf(PlayerPanel.HIDDEN)
    private var playing by mutableStateOf(false)
    private var buffering by mutableStateOf(false)
    private var avatar by mutableStateOf("")
    private var audioOptions by mutableStateOf<List<AudioLanguage>>(emptyList())
    private var selectedAudio by mutableStateOf<String?>(null)
    private var currentInfo: StreamInfo? = null
    private var chapters by mutableStateOf(VideoChapters.Empty)
    private var captions by mutableStateOf<List<CaptionOption>>(emptyList())
    private var selectedCaption by mutableStateOf<String?>(null)
    private var availableQualities by mutableStateOf(listOf(480,720,1080))
    private var speed by mutableFloatStateOf(1f)
    private val previousVideos = mutableStateListOf<String>()
    private val forwardVideos = mutableStateListOf<String>()
    private var comments by mutableStateOf<List<VideoComment>>(emptyList())
    private var commentsBusy by mutableStateOf(false)
    private var commentsTotal by mutableStateOf<Int?>(null)
    private var commentsError by mutableStateOf<String?>(null)
    private var commentsLoaded = false
    private var commentsJob: Job? = null
    private var clockPosition by mutableLongStateOf(0)
    private var clockDuration by mutableLongStateOf(0)
    private var localRating by mutableIntStateOf(0)
    private var subscribed by mutableStateOf(false)
    private var lastInteraction by mutableLongStateOf(0)
    private var loadJob: Job? = null
    private var progressJob: Job? = null
    private var relatedTitlesJob: Job? = null
    private var upNext by mutableStateOf<UpNextCountdown?>(null)
    private var upNextSeconds by mutableIntStateOf(10)
    private var autoplayJob: Job? = null
    private var endedHandled = false
    private var position = -1L
    private var played = false
    private var quality by mutableIntStateOf(1080)
    private val preferences by lazy { getSharedPreferences("player", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        quality = preferences.getInt("max_height", 1080)
        speed = preferences.getFloat("speed", 1f).coerceIn(.25f,2f)
        previousVideos.addAll(savedInstanceState?.getStringArrayList("previous").orEmpty())
        forwardVideos.addAll(savedInstanceState?.getStringArrayList("forward").orEmpty())
        position = savedInstanceState?.getLong("position") ?: -1L
        setContent { MyTubeTheme {
            var seekFocused by remember { mutableStateOf(true) }
            var enterOnProgress by remember { mutableStateOf(true) }
            val screenFocus = remember { FocusRequester() }
            val playFocus = remember { FocusRequester() }
            val seekFocus = remember { FocusRequester() }
            val firstRelated = remember { FocusRequester() }
            val drawerFocus = remember { FocusRequester() }
            val upNextFocus = remember { FocusRequester() }
            LaunchedEffect(panel, loading, failure, panel == PlayerPanel.RELATED && relatedVideos.isNotEmpty()) {
                when {
                    loading || failure != null -> screenFocus.requestFocus()
                    panel == PlayerPanel.CONTROLS -> if(enterOnProgress) seekFocus.requestFocus() else playFocus.requestFocus()
                    panel == PlayerPanel.RELATED -> if(relatedVideos.isNotEmpty()) firstRelated.requestFocus() else screenFocus.requestFocus()
                    panel == PlayerPanel.HIDDEN -> screenFocus.requestFocus()
                    panel == PlayerPanel.UP_NEXT -> upNextFocus.requestFocus()
                    panel == PlayerPanel.SETTINGS -> Unit // The settings submenu owns its focus.
                    panel == PlayerPanel.COMMENTS -> Unit // Comment loading/list states own their focus.
                }
            }
            LaunchedEffect(panel, playing, lastInteraction) {
                if(panel == PlayerPanel.CONTROLS && playing) { delay(5000); panel = PlayerPanel.HIDDEN }
            }
            BackHandler(onBack = ::handleBack)
            Box(Modifier.fillMaxSize().background(Color.Black).onPreviewKeyEvent { event ->
                if(handleRemoteBack(event.nativeKeyEvent)) true else if(event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN || loading || failure != null) false else {
                    lastInteraction = SystemClock.uptimeMillis()
                    if(panel == PlayerPanel.UP_NEXT) cancelAutoplay()
                    when(event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> { toggle(); true }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> { playCurrent(); true }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> { player?.pause(); true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> when(panel) {
                            PlayerPanel.HIDDEN -> { enterOnProgress = true;panel = PlayerPanel.CONTROLS; true }
                            PlayerPanel.CONTROLS -> { if(seekFocused) playFocus.requestFocus() else panel = PlayerPanel.RELATED;true }
                            PlayerPanel.UP_NEXT -> { panel = PlayerPanel.RELATED;true }
                            else -> false
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> when(panel) {
                            PlayerPanel.UP_NEXT -> { enterOnProgress = true;panel = PlayerPanel.CONTROLS; true }
                            PlayerPanel.RELATED -> if(relatedVideos.isEmpty()) { panel = PlayerPanel.CONTROLS;true } else false
                            PlayerPanel.SETTINGS, PlayerPanel.COMMENTS -> false
                            PlayerPanel.CONTROLS -> { if(seekFocused) panel = PlayerPanel.HIDDEN else seekFocus.requestFocus();true }
                            PlayerPanel.HIDDEN -> true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                            if(panel == PlayerPanel.HIDDEN) { toggle(); true } else false
                        KeyEvent.KEYCODE_DPAD_LEFT -> panel == PlayerPanel.HIDDEN
                        KeyEvent.KEYCODE_DPAD_RIGHT -> panel == PlayerPanel.HIDDEN
                        KeyEvent.KEYCODE_MEDIA_REWIND,KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            if(panel == PlayerPanel.CONTROLS && seekFocused) seek(if(event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) -10_000 else 10_000)
                            true
                        }
                        else -> false
                    }
                }
            }.focusRequester(screenFocus).focusable()) {
                AndroidView(factory = { context -> PlayerView(context).also { pv ->
                    view = pv
                    pv.useController = false
                    pv.isFocusable = false
                    pv.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    pv.setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    pv.player = player
                    pv.setOnClickListener { cancelAutoplay();panel = when(panel) { PlayerPanel.HIDDEN -> PlayerPanel.CONTROLS;PlayerPanel.UP_NEXT -> PlayerPanel.RELATED;else -> PlayerPanel.HIDDEN } }
                } }, update = { it.player = player }, modifier = Modifier.fillMaxSize().padding(end = when(panel) { PlayerPanel.COMMENTS -> 400.dp; PlayerPanel.SETTINGS -> 360.dp; else -> 0.dp }))
                Box(Modifier.align(Alignment.BottomCenter)) {
                    when {
                        failure != null -> Column(Modifier.fillMaxWidth().background(Color(0xDD0F0F0F)).padding(30.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(failure.orEmpty())
                            Action("Retry") { open() }; Action("Back to videos") { finish() }
                        }
                        panel == PlayerPanel.RELATED -> RelatedPlayerVideos(relatedVideos, firstRelated, onReturnToControls = { enterOnProgress = false;panel = PlayerPanel.CONTROLS },onPlay = ::playRelated)
                        panel == PlayerPanel.CONTROLS -> BottomPlayerControls(
                            video?.title.orEmpty(), video?.channel.orEmpty(), playing, clockPosition, clockDuration,
                            localRating, subscribed, !video?.channelUrl.isNullOrBlank(), playFocus, seekFocus,
                            ::toggle, ::seek, ::subscribe, ::rate, avatar,
                            previousVideos.isNotEmpty(), forwardVideos.isNotEmpty() || relatedVideos.isNotEmpty(),
                            ::previousVideo, ::nextVideo, ::openChannel, { panel = PlayerPanel.SETTINGS }, ::showComments,chapters,{ seekFocused = it })
                    }
                }
                if(panel == PlayerPanel.UP_NEXT) upNext?.let { countdown ->
                    UpNextPanel(countdown,upNextSeconds,upNextFocus,::playUpNext) { cancelAutoplay();panel = PlayerPanel.RELATED }
                }
                if((loading || buffering) && failure == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingSpinner("player-loading", "Loading video")
                }
                if(panel == PlayerPanel.SETTINGS) Box(Modifier.align(Alignment.CenterEnd)) {
                    PlayerSettings(drawerFocus,quality,availableQualities,speed,captions,selectedCaption,
                        { height -> quality = height; preferences.edit().putInt("max_height",height).apply(); reconfigure() },
                        { value -> speed = value; player?.setPlaybackSpeed(value); preferences.edit().putFloat("speed",value).apply() },
                        { id -> selectedCaption = id; reconfigure() }, { panel = PlayerPanel.CONTROLS },audioOptions,selectedAudio,{ id -> selectedAudio = id;reconfigure() })
                }
                if(panel == PlayerPanel.COMMENTS) Box(Modifier.align(Alignment.CenterEnd)) {
                    VideoComments(drawerFocus,comments,commentsTotal,commentsBusy,commentsError,::showComments)
                }
            }
        } }
    }
    override fun onStart() { super.onStart(); open() }
    override fun onStop() { stopPlayer(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("position", player?.currentPosition ?: position)
        outState.putStringArrayList("previous",ArrayList(previousVideos)); outState.putStringArrayList("forward",ArrayList(forwardVideos))
        super.onSaveInstanceState(outState)
    }
    // TV remotes send physical key events inside the Compose focus tree. Route them
    // through the registered callback; system/predictive Back still uses BackHandler.
    @android.annotation.SuppressLint("GestureBackNavigation")
    private fun handleRemoteBack(event: KeyEvent): Boolean {
        val tv = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
        if(!tv || event.keyCode != KeyEvent.KEYCODE_BACK) return false
        if(event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) onBackPressedDispatcher.onBackPressed()
        return true
    }
    private fun handleBack() {
        lastInteraction = SystemClock.uptimeMillis()
        cancelAutoplay()
        if(loading || failure != null) { finish(); return }
        panel = when(panel) {
            PlayerPanel.UP_NEXT -> PlayerPanel.RELATED
            PlayerPanel.SETTINGS, PlayerPanel.COMMENTS, PlayerPanel.RELATED -> PlayerPanel.CONTROLS
            PlayerPanel.CONTROLS -> PlayerPanel.HIDDEN
            PlayerPanel.HIDDEN -> { finish(); PlayerPanel.HIDDEN }
        }
    }
    private fun cancelAutoplay() {
        autoplayJob?.cancel();autoplayJob = null
        upNext = upNext?.cancel()
    }
    private fun playUpNext() {
        val target = upNext?.target ?: return
        cancelAutoplay();playRelated(target)
    }
    private fun videoEnded() {
        if(endedHandled) return
        endedHandled = true
        saveProgress()
        if(panel !in listOf(PlayerPanel.HIDDEN,PlayerPanel.CONTROLS)) return
        val target = relatedVideos.firstOrNull()
        val source = video?.url
        if(target == null || source == null) { panel = PlayerPanel.RELATED;return }
        cancelAutoplay()
        upNext = UpNextCountdown.start(target,source,SystemClock.elapsedRealtime())
        upNextSeconds = 10;panel = PlayerPanel.UP_NEXT
        autoplayJob = lifecycleScope.launch {
            while(isActive) {
                val countdown = upNext ?: break
                if(countdown.deadlineMs == null || video?.url != countdown.sourceUrl) break
                val now = SystemClock.elapsedRealtime()
                if(countdown.isDue(now)) {
                    // Clear the owned countdown before switching; it can be consumed once.
                    autoplayJob = null;upNext = null
                    if(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED) && player?.playbackState == Player.STATE_ENDED)
                        playRelated(countdown.target)
                    break
                }
                upNextSeconds = countdown.remainingSeconds(now) ?: 0
                delay(100)
            }
        }
    }
    private fun playCurrent() {
        cancelAutoplay()
        player?.let {
            if(it.playbackState == Player.STATE_ENDED) { endedHandled = false;it.seekTo(0) }
            it.play()
        }
        if(panel == PlayerPanel.UP_NEXT) panel = PlayerPanel.CONTROLS
    }
    private fun toggle() {
        player?.let { if(it.playbackState == Player.STATE_ENDED || !it.playWhenReady) playCurrent() else it.pause() }
        lastInteraction = SystemClock.uptimeMillis()
    }
    private fun seek(delta: Long) { player?.let { p ->
        cancelAutoplay()
        val max = p.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        val at = (p.currentPosition + delta).coerceIn(0,max)
        if(at < max) endedHandled = false
        p.seekTo(at); clockPosition = p.currentPosition
    } }
    private fun subscribe() {
        val item = video ?: return
        if(item.channelUrl.isBlank()) return
        lifecycleScope.launch {
            val followed = withContext(Dispatchers.IO) {
                app.library.toggleSubscription(Subscription(item.channelUrl,item.channel))
                app.library.subscriptions().any { it.url == item.channelUrl }
            }
            if(video?.url == item.url) subscribed = followed
        }
    }
    private fun rate(rating: Int) {
        val item = video ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { app.library.setRating(item,rating) }
            if(video?.url == item.url) localRating = rating
        }
    }
    private fun playRelated(item: Video) {
        video?.url?.let { previousVideos.add(it) }
        forwardVideos.clear()
        switchVideo(item.url)
    }
    private fun previousVideo() {
        if(previousVideos.isEmpty()) return
        video?.url?.let { forwardVideos.add(it) }
        switchVideo(previousVideos.removeAt(previousVideos.lastIndex))
    }
    private fun nextVideo() {
        val next = if(forwardVideos.isNotEmpty()) forwardVideos.removeAt(forwardVideos.lastIndex) else relatedVideos.firstOrNull()?.url ?: return
        video?.url?.let { previousVideos.add(it) }
        switchVideo(next)
    }
    private fun switchVideo(url: String) {
        stopPlayer()
        intent.putExtra("video_url",url).putExtra("from_start",false)
        position = -1; played = false; video = null; relatedVideos = emptyList(); currentInfo = null
        selectedCaption = null; commentsLoaded = false; comments = emptyList(); commentsTotal = null; commentsError = null
        open()
    }
    private fun openChannel() {
        val item = video ?: return
        if(item.channelUrl.isBlank()) return
        startActivity(Intent(this,MainActivity::class.java).putExtra("channel_url",item.channelUrl).putExtra("channel_name",item.channel))
    }
    private fun showComments() {
        panel = PlayerPanel.COMMENTS
        if(commentsLoaded || commentsBusy) return
        val url = video?.url ?: return
        commentsBusy = true; commentsError = null
        commentsJob = lifecycleScope.launch {
            try {
                val result = app.gateway.comments(url)
                if(video?.url == url) { comments = result.items; commentsTotal = result.total; commentsLoaded = true }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(video?.url == url) commentsError = readableError(e) }
            finally { if(video?.url == url) commentsBusy = false }
        }
    }
    private fun reconfigure() {
        val current = player ?: return
        val info = currentInfo ?: return
        val at = current.currentPosition
        val play = current.playWhenReady
        try {
            current.trackSelectionParameters = current.trackSelectionParameters.buildUpon()
                .setMaxVideoSize(Int.MAX_VALUE,quality).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,selectedCaption == null)
                .setPreferredTextLanguage(captions.firstOrNull { it.id == selectedCaption }?.language)
                .setPreferredAudioLanguage(audioOptions.firstOrNull { it.id == selectedAudio }?.language ?: audioOptions.firstOrNull { it.original }?.language).build()
            current.setMediaSource(mediaSource(info),at)
            buffering = true; current.prepare(); current.playWhenReady = play
        } catch(e: Exception) { failure = readableError(e) }
    }
    private fun open() {
        stopPlayer()
        loading = true; failure = null; panel = PlayerPanel.HIDDEN;endedHandled = false;chapters = VideoChapters.Empty
        val url = VideoUrls.canonical(intent.getStringExtra("video_url").orEmpty())
        if(url == null) { loading = false; failure = "This is not a supported YouTube video link."; return }
        loadJob = lifecycleScope.launch {
            try {
                val info = app.gateway.details(url, fresh = true)
                ensureActive()
                currentInfo = info
                selectedAudio = null
                audioOptions = audioLanguages(info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP })
                chapters = app.gateway.chapters(info)
                avatar = info.uploaderAvatars.firstOrNull()?.url.orEmpty()
                captions = info.subtitles.filter { it.isUrl }.mapNotNull { stream ->
                    val mime = when(stream.format) {
                        MediaFormat.VTT -> MimeTypes.TEXT_VTT
                        MediaFormat.TTML -> MimeTypes.APPLICATION_TTML
                        MediaFormat.SRT -> MimeTypes.APPLICATION_SUBRIP
                        else -> null
                    } ?: return@mapNotNull null
                    CaptionOption(stream.content,stream.displayLanguageName + if(stream.isAutoGenerated) " (auto)" else "",stream.languageTag,stream.content,mime)
                }.distinctBy { it.id }
                if(captions.none { it.id == selectedCaption }) selectedCaption = null
                availableQualities = ((info.videoStreams + info.videoOnlyStreams).map { height(it.resolution) }.filter { it > 0 } +
                    if(info.hlsUrl.isNullOrBlank()) emptyList() else listOf(480,720,1080,2160)).distinct().sorted()
                video = app.gateway.video(info)
                val local = withContext(Dispatchers.IO) {
                    Triple(app.library.rating(url), app.library.subscriptions().any { it.url == video?.channelUrl }, app.library.hidden())
                }
                localRating = local.first; subscribed = local.second
                val hidden = local.third
                val candidates = app.gateway.related(info).filter { it.url != url && it.url !in hidden }.distinctBy { it.url }
                val relatedCandidates = withContext(Dispatchers.IO) { app.library.withProgress(candidates) }
                relatedVideos = relatedCandidates
                if(position < 0) position = if(intent.getBooleanExtra("from_start",false)) 0 else withContext(Dispatchers.IO) { app.library.resumePosition(url) }
                if(info.duration > 0 && position >= info.duration * 1000) position = 0
                val source = mediaSource(info)
                val current = ExoPlayer.Builder(this@PlayerActivity).build()
                current.trackSelectionParameters = current.trackSelectionParameters.buildUpon().setMaxVideoSize(Int.MAX_VALUE,quality).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,selectedCaption == null)
                .setPreferredTextLanguage(captions.firstOrNull { it.id == selectedCaption }?.language)
                .setPreferredAudioLanguage(audioOptions.firstOrNull { it.id == selectedAudio }?.language ?: audioOptions.firstOrNull { it.original }?.language).build()
                player = current
                session = MediaSession.Builder(this@PlayerActivity,current).build()
                view?.player = current
                current.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        if(player !== current) return
                        playing = isPlaying;if(isPlaying) played = true
                        saveProgress()
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        if(player !== current) return
                        buffering = state == Player.STATE_BUFFERING
                        if(state == Player.STATE_READY && current.duration > 0 && current.currentPosition < current.duration) endedHandled = false
                        if(state == Player.STATE_ENDED) videoEnded()
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        if(player !== current) return
                        cancelAutoplay();failure = "Playback failed (${error.errorCodeName}). Retry or choose a lower quality."
                    }
                })
                buffering = true
                current.setPlaybackSpeed(speed)
                current.setMediaSource(source)
                current.seekTo(position.coerceAtLeast(0)); current.prepare(); current.playWhenReady = true
                clockPosition = position.coerceAtLeast(0); clockDuration = (info.duration * 1000).coerceAtLeast(0)
                loading = false; lastInteraction = SystemClock.uptimeMillis()
                relatedTitlesJob = lifecycleScope.launch {
                    val originals = app.gateway.originalTitles(relatedCandidates)
                    val decorated = withContext(Dispatchers.IO) { app.library.withProgress(originals) }
                    if(video?.url == url) {
                        relatedVideos = decorated
                        upNext = upNext?.let { countdown -> decorated.firstOrNull { it.url == countdown.target.url }?.let { countdown.copy(target = it) } ?: countdown }
                    }
                }
                progressJob = launch { var ticks = 0; while(isActive) {
                    delay(500); clockPosition = current.currentPosition.coerceAtLeast(0)
                    if(current.duration > 0) clockDuration = current.duration
                    if(++ticks % 10 == 0 && current.isPlaying) saveProgress()
                } }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { loading = false; failure = readableError(e) }
        }
    }
    private fun mediaSource(info: StreamInfo): MediaSource {
        val data = DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")
            .setConnectTimeoutMs(15_000).setReadTimeoutMs(25_000)
        val subtitles = captions.firstOrNull { it.id == selectedCaption }?.let { caption ->
            listOf(MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(caption.url))
                .setId(caption.id).setMimeType(caption.mime).setLanguage(caption.language)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build())
        }.orEmpty()
        val factory = DefaultMediaSourceFactory(data)
        fun item(url: String, mime: String? = null) = MediaItem.Builder().setUri(url).setMimeType(mime).setSubtitleConfigurations(subtitles).build()
        val audio = chooseAudio(info.audioStreams.filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP },selectedAudio)
        if(!info.hlsUrl.isNullOrBlank() && (audio == null || info.videoOnlyStreams.none { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }))
            return factory.createMediaSource(item(info.hlsUrl,MimeTypes.APPLICATION_M3U8))
        val candidates = (if(audio != null && info.videoOnlyStreams.any { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }) info.videoOnlyStreams else info.videoStreams + info.videoOnlyStreams).filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
        val chosen = candidates.filter { height(it.resolution) <= quality }.maxWithOrNull(compareBy({ height(it.resolution) }, { it.format == MediaFormat.MPEG_4 }))
            ?: candidates.minByOrNull { height(it.resolution) } ?: error("No supported video streams")
        val videoSource = factory.createMediaSource(item(chosen.content))
        if(!chosen.isVideoOnly) return videoSource
        requireNotNull(audio) { "No supported audio stream" }
        return MergingMediaSource(videoSource, factory.createMediaSource(MediaItem.fromUri(audio.content)))
    }
    private fun height(resolution: String) = Regex("[0-9]+").find(resolution)?.value?.toIntOrNull() ?: 0
    private fun saveProgress() {
        if(!played) return
        val item = video ?: return
        val current = player ?: return
        val completed = current.playbackState == Player.STATE_ENDED
        val durationMs = current.duration.takeIf { it > 0 }
        val at = if(completed && durationMs != null) durationMs else current.currentPosition.coerceAtLeast(0)
        val metadata = durationMs?.let { item.copy(durationSeconds = (it+999)/1000) } ?: item
        position = at
        app.storageScope.launch { app.library.record(metadata,at,completed) }
    }
    private fun stopPlayer() {
        cancelAutoplay();upNext = null
        relatedTitlesJob?.cancel(); relatedTitlesJob = null
        commentsJob?.cancel(); commentsJob = null; commentsBusy = false
        buffering = false
        loadJob?.cancel(); loadJob = null
        progressJob?.cancel(); progressJob = null
        saveProgress()
        session?.release(); session = null
        view?.player = null
        val retiring = player;player = null;retiring?.release()
        playing = false
    }
}
