package app.mytube.tv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen(val title: String) { SEARCH("Search"),HOME("Home"),SUBSCRIPTIONS("Subscriptions"),HISTORY("Library"),LIVE("Live"),SETTINGS("Settings"),INTERESTS("Interests"),CHANNEL("Channel");
    companion object { val navigation = listOf(SEARCH,HOME,SUBSCRIPTIONS,HISTORY,LIVE) }
}
data class BrowseState(val screen: Screen = Screen.HOME, val rows: List<VideoRow> = emptyList(), val busy: Boolean = false,
    val error: String? = null, val hasMore: Boolean = false, val subscriptions: List<Subscription> = emptyList(),
    val interests: List<Interest> = emptyList(), val seeds: List<Video> = emptyList(), val channel: Subscription? = null, val homeSession: Int = 0,val channelAvatars: Map<String,String> = emptyMap(),val liveChecked: Int = 0,val liveTotal: Int = 0)

class BrowseViewModel(private val gateway: ExtractorGateway, private val library: LibraryStore) : ViewModel() {
    private val pages = BrowsePages()
    private val currentPage get() = pages.active
    val state = pages.state
    internal val pageStates = pages.states
    private val suggestionQueries = Channel<String>(Channel.CONFLATED)
    private var suggestionQuery = ""
    private val mutableSuggestions = MutableStateFlow(SearchSuggestions())
    val suggestions = mutableSuggestions.asStateFlow()
    private val mutableSearch = MutableStateFlow(SearchState())
    val searchState = mutableSearch.asStateFlow()
    init {
        viewModelScope.launch { library.progressRevision.collectLatest { refreshProgress() } }
        viewModelScope.launch {
            pageStates.collect { routes ->
                val browse = state.value
                val recommendations = browse.rows.flatMap { it.videos }.ifEmpty { routes[Screen.HOME]?.rows.orEmpty().flatMap { it.videos } }
                mutableSearch.update { search -> if(search.submittedQuery.isBlank())
                    search.copy(videos = recommendations.distinctBy(Video::url).take(10)) else search }
            }
        }
        viewModelScope.launch {
            for (query in suggestionQueries) {
                delay(300)
                if(query != suggestionQuery || query.isBlank()) continue
                try {
                    val results = gateway.suggestions(query)
                    if(query == suggestionQuery) mutableSuggestions.value = SearchSuggestions(query, results)
                } catch(e: CancellationException) { throw e }
                catch(e: Exception) {
                    if(query == suggestionQuery) mutableSuggestions.value = SearchSuggestions(query, error = "Suggestions unavailable. You can still search.")
                }
            }
        }
    }
    fun suggest(input: String) {
        val query = input.trim().takeUnless { VideoUrls.canonical(it) != null || it.startsWith("http", true) }.orEmpty()
        suggestionQuery = query
        mutableSuggestions.value = SearchSuggestions(query, busy = query.isNotBlank())
        suggestionQueries.trySend(query)
    }
    fun dismissSuggestions() { suggest("") }
    private val requests = mutableMapOf<Screen, Job>()
    private val generations = mutableMapOf<Screen, Int>()
    private val loadedAt = mutableMapOf<Screen, Long>()
    private var preferenceRevision = 0
    private var homePager: HomePager? = null
    private val cachedHome get() = pageStates.value[Screen.HOME]
    fun selectScreen(screen: Screen) {
        if(screen == currentPage.value.screen && !mutableSearch.value.open) return
        if(mutableSearch.value.open) closeSearch()
        pages.select(screen)
        val cached = loadedAt[screen]
        val expired = screen == Screen.LIVE && cached != null && android.os.SystemClock.elapsedRealtime()-cached > 60_000
        if(screen == Screen.SEARCH) openSearch(fullscreen = false)
        if(screen == Screen.SUBSCRIPTIONS && cached != null) refreshChannelAvatars(currentPage.value.subscriptions)
        if(requests[screen]?.isActive != true && (cached == null || expired || screen == Screen.HISTORY)) navigate(screen)
    }
    fun navigate(screen: Screen) {
        if(screen == Screen.SEARCH) openSearch(fullscreen = false)
        load(screen, preserveRows = true) { pageState ->
        val history = local { library.history() }
        val subscriptions = local { library.subscriptions() }
        val interests = local { library.interests() }
        val seeds = local { library.seeds() }
        pages.updateAll { it.copy(subscriptions = subscriptions, interests = interests, seeds = seeds) }
        when(screen) {
            Screen.HOME -> {
                val hidden = local { library.hidden() }
                val sources = mutableListOf<VideoSource>()
                val channels = subscriptions.shuffled()
                // Start with distinct followed channels; topic searches may overlap those channels.
                channels.take(4).forEach { channel -> sources += VideoSource(channel.name) { gateway.channelCandidates(channel.url) } }
                interests.shuffled().forEach { interest -> sources += VideoSource(interest.label) { gateway.searchCandidates(interest.label).videos } }
                channels.drop(4).forEach { channel -> sources += VideoSource(channel.name) { gateway.channelCandidates(channel.url) } }
                val liked = local { library.likedVideos() }
                for(seed in (seeds + liked + history.take(2)).distinctBy { it.url }) {
                    sources += VideoSource(seed.title) { gateway.related(gateway.details(seed.url)) }
                }
                if(sources.isEmpty()) sources += VideoSource("Discover") { gateway.searchCandidates("science technology documentaries").videos }
                val pager = HomePager(sources,{ candidates -> Recommendations.rank(candidates,history,subscriptions,hidden,interests) },System.currentTimeMillis(),kotlin.random.Random.nextLong(),{ videos -> val originals = gateway.originalTitles(videos);local { library.withProgress(originals) } })
                homePager = pager
                pageState.update { it.copy(homeSession = it.homeSession+1) }
                val page = pager.next()
                pageState.update { it.copy(rows = listOf(VideoRow("Recommended for you",page.videos,"home")),hasMore = page.hasMore,error = page.warnings.takeIf { it.isNotEmpty() }?.joinToString("\n")) }
            }
            Screen.SEARCH -> Unit
            Screen.LIVE -> loadLive(pageState,subscriptions,interests,history,seeds)
            Screen.HISTORY -> pageState.update { it.copy(rows = listOf(VideoRow("Your watch history", history))) }
            Screen.SUBSCRIPTIONS -> refreshChannelAvatars(subscriptions)
            Screen.CHANNEL -> {
                val channel = pageState.value.channel ?: return@load
                val candidates = gateway.channelVideos(channel.url).sortedByDescending { it.publishedAtMs ?: Long.MIN_VALUE }
                val videos = local { library.withProgress(candidates) }
                pageState.update { it.copy(rows = listOf(VideoRow(channel.name, videos, "channel:${channel.url}"))) }
            }
            Screen.SETTINGS, Screen.INTERESTS -> Unit
        }
    }
    }
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun loadLive(pageState: BrowsePages.Page,subscriptions: List<Subscription>,interests: List<Interest>,history: List<Video>,seeds: List<Video>) {
        val hidden = local { library.hidden() }
        val sources = subscriptions.map { channel -> VideoSource(channel.name) { gateway.channelLiveCandidates(channel.url) } }.toMutableList()
        val followed = subscriptions.map { it.url }.toSet()
        val examples = (seeds + history.take(5) + cachedHome?.rows.orEmpty().flatMap { it.videos })
            .filter { it.channelUrl.isNotBlank() && it.channelUrl !in followed }.distinctBy { it.channelUrl }.take(4)
        examples.forEach { channel -> sources += VideoSource(channel.channel) { gateway.channelLiveCandidates(channel.channelUrl) } }
        interests.forEach { topic -> sources += VideoSource(topic.label) { gateway.liveSearchCandidates(topic.label) } }
        if(sources.isEmpty()) sources += VideoSource("Recommended live") { gateway.liveSearchCandidates("science technology news") }
        pageState.update { it.copy(liveChecked = 0,liveTotal = sources.size) }
        var failures = 0
        var collected = emptyList<Video>()
        sources.asFlow().flatMapMerge(concurrency = 3) { source -> flow {
            val result = try { source.fetch() to false } catch(e: CancellationException) { throw e } catch(_: Exception) { emptyList<Video>() to true }
            emit(result)
        } }.collect { (candidates,failed) ->
            if(failed) failures++
            val existing = collected
            val additions = LiveFeed.select(candidates,subscriptions,hidden).filter { candidate -> existing.none { it.url == candidate.url } }
            val originals = gateway.originalTitles(additions)
            collected = existing + originals
            pageState.update { it.copy(rows = listOf(VideoRow("Live now",collected,"live")),liveChecked = it.liveChecked+1) }
        }
        if(failures > 0) pageState.update { it.copy(error = "$failures live sources could not be checked. Try again to check them.") }
    }
    private var channelImagesRequest: Job? = null
    private fun refreshChannelAvatars(channels: List<Subscription>) {
        channelImagesRequest?.cancel()
        channelImagesRequest = viewModelScope.launch {
            kotlinx.coroutines.coroutineScope {
                channels.map { channel -> launch {
                    try {
                        val avatar = gateway.channelAvatar(channel.url)
                        if(avatar.isNotBlank())
                            pages.updateAll { it.copy(channelAvatars = it.channelAvatars + (channel.url to avatar)) }
                    } catch(e: CancellationException) { throw e }
                    catch(_: Exception) { /* Keep a cached image/placeholder while this channel is unavailable. */ }
                } }
            }
        }
    }
    private suspend fun refreshProgress() {
        val urls = (pageStates.value.values.flatMap { page -> page.rows.flatMap { it.videos } } + mutableSearch.value.videos).map { it.url }
        if(urls.isEmpty()) return
        val snapshot = local { library.watchProgress(urls) }
        pages.updateAll { state -> state.copy(rows = state.rows.map { it.copy(videos = snapshot.apply(it.videos)) }) }
        mutableSearch.update { it.copy(videos = snapshot.apply(it.videos)) }
    }
    fun resume() {
        val current = currentPage.value
        if(current.screen == Screen.HISTORY || current.screen == Screen.HOME && current.rows.all { it.videos.isEmpty() }) {
            if(!current.busy) navigate(current.screen)
        } else viewModelScope.launch {
            refreshProgress()
            val channels = local { library.subscriptions() }
            if(current.screen == Screen.SUBSCRIPTIONS) refreshChannelAvatars(channels)
            val interests = local { library.interests() }; val seeds = local { library.seeds() }
            if(channels != current.subscriptions || interests != current.interests || seeds != current.seeds) invalidateRecommendations()
            pages.updateAll { it.copy(subscriptions = channels,interests = interests,seeds = seeds) }
        }
    }
    fun moreHome() {
        if(currentPage.value.screen != Screen.HOME || currentPage.value.busy || !currentPage.value.hasMore) return
        val pager = homePager ?: return
        load(Screen.HOME,preserveRows = true) { pageState ->
            val page = pager.next()
            pageState.update { current -> current.copy(rows = listOf(VideoRow("Recommended for you",current.rows.flatMap { it.videos } + page.videos,"home")),hasMore = page.hasMore,error = page.warnings.takeIf { it.isNotEmpty() }?.joinToString("\n")) }
        }
    }
    fun openChannel(channel: Subscription) {
        pages.select(Screen.CHANNEL)
        pages.page(Screen.CHANNEL).update { it.copy(channel = channel,rows = emptyList()) }; navigate(Screen.CHANNEL)
    }
    private var searchRequest: Job? = null
    private var searchGeneration = 0
    private var searchPage: ExtractorGateway.SearchPage? = null
    private var searchRemaining = emptyList<Video>()
    fun openSearch(fullscreen: Boolean = true) {
        if(mutableSearch.value.open && fullscreen) return
        val recommended = currentPage.value.rows.flatMap { it.videos }.ifEmpty { cachedHome?.rows.orEmpty().flatMap { it.videos } }.distinctBy { it.url }.take(10)
        mutableSearch.update { it.copy(open = fullscreen,videos = if(it.submittedQuery.isBlank()) recommended else it.videos) }
        viewModelScope.launch {
            val history = local { library.searchHistory() }
            mutableSearch.update { it.copy(history = history) }
        }
    }
    fun closeSearch() {
        ++searchGeneration
        searchRequest?.cancel()
        dismissSuggestions()
        mutableSearch.update { it.copy(open = false, busy = false) }
    }
    fun editSearch(query: String) {
        mutableSearch.update { it.copy(query = query) }
        suggest(query)
    }
    fun clearSearches() = viewModelScope.launch {
        local { library.clearSearchHistory() }
        mutableSearch.update { it.copy(history = emptyList()) }
    }
    fun search(input: String) {
        val query = input.trim()
        if(query.isBlank()) return
        if(currentPage.value.screen != Screen.SEARCH) openSearch()
        dismissSuggestions()
        val current = ++searchGeneration
        searchRequest?.cancel()
        mutableSearch.update { it.copy(query = query, submittedQuery = query, busy = true, error = null, videos = emptyList(),hasMore = false) }
        searchRequest = viewModelScope.launch {
            try {
                local { library.rememberSearch(query) }
                val history = local { library.searchHistory() }
                mutableSearch.update { it.copy(history = history) }
                val direct = VideoUrls.canonical(query)
                searchPage = null
                searchRemaining = emptyList()
                val videos = if(direct != null) listOf(gateway.video(gateway.details(direct))) else {
                    val page = gateway.searchPage(query)
                    if(current != searchGeneration) return@launch
                    searchPage = page; searchRemaining = page.videos.drop(10)
                    gateway.originalTitles(page.videos.take(10))
                }
                val decorated = local { library.withProgress(videos) }
                if(current == searchGeneration) mutableSearch.update { it.copy(videos = decorated,hasMore = searchRemaining.isNotEmpty() || searchPage?.hasMore == true) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(current == searchGeneration) mutableSearch.update { it.copy(error = readableError(e)) } }
            finally { if(current == searchGeneration) mutableSearch.update { it.copy(busy = false) } }
        }
    }
    fun moreSearch() {
        val state = mutableSearch.value
        if(state.busy || !state.hasMore) return
        val current = searchGeneration
        mutableSearch.update { it.copy(busy = true,error = null) }
        searchRequest = viewModelScope.launch {
            try {
                if(searchRemaining.isEmpty()) {
                    val page = gateway.searchPage(state.submittedQuery,searchPage ?: return@launch)
                    if(current != searchGeneration) return@launch
                    searchPage = page; searchRemaining = page.videos
                }
                val originals = gateway.originalTitles(searchRemaining.take(10))
                val batch = local { library.withProgress(originals) }
                if(current == searchGeneration) {
                    searchRemaining = searchRemaining.drop(10)
                    mutableSearch.update { it.copy(videos = (it.videos + batch).distinctBy(Video::url),
                        hasMore = searchRemaining.isNotEmpty() || searchPage?.hasMore == true) }
                }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(current == searchGeneration) mutableSearch.update { it.copy(error = readableError(e)) } }
            finally { if(current == searchGeneration) mutableSearch.update { it.copy(busy = false) } }
        }
    }
    suspend fun originalTitle(video: Video) {
        val title = gateway.originalTitles(listOf(video)).single().title
        pages.page(Screen.HISTORY).update { state -> state.copy(rows = state.rows.map { row ->
            row.copy(videos = row.videos.map { if(it.url == video.url) it.copy(title = title) else it })
        }) }
    }
    fun toggle(channel: Subscription) { viewModelScope.launch { local { library.toggleSubscription(channel) }; val subscriptions = local { library.subscriptions() }; pages.updateAll { it.copy(subscriptions = subscriptions) }; invalidateRecommendations() } }
    fun saveInterest(input: String) { if(Interest.fromInput(input) != null) editPreferences { library.addInterest(input) } }
    fun removeInterest(id: String) = editPreferences { library.removeInterest(id) }
    fun setSeed(video: Video, saved: Boolean) = editPreferences { library.setSeed(video, saved) }
    private fun editPreferences(edit: () -> Unit) { viewModelScope.launch {
        local(edit)
        val interests = local { library.interests() }; val seeds = local { library.seeds() }
        pages.updateAll { it.copy(interests = interests, seeds = seeds) }; invalidateRecommendations()
    } }
    fun hide(video: Video) { viewModelScope.launch {
        local { library.hide(video) }; val seeds = local { library.seeds() }
        pages.updateAll { s -> s.copy(seeds = seeds, rows = s.rows.map { row -> row.copy(videos = row.videos.filter { it.url != video.url }) }) }; invalidateRecommendations()
    } }
    fun clearHistory() { viewModelScope.launch { local { library.clearHistory() };invalidateRecommendations() } }
    fun resetRecommendations() { viewModelScope.launch { local { library.resetRecommendations() };invalidateRecommendations() } }
    private fun invalidateRecommendations() { preferenceRevision++;loadedAt.remove(Screen.HOME);loadedAt.remove(Screen.LIVE) }
    private fun load(screen: Screen, preserveRows: Boolean = false, block: suspend (BrowsePages.Page) -> Unit) {
        pages.select(screen)
        val page = pages.page(screen)
        val revision = preferenceRevision
        val current = (generations[screen] ?: 0)+1
        generations[screen] = current
        requests[screen]?.cancel()
        page.update { it.copy(rows = if(preserveRows) it.rows else emptyList(),busy = true,error = null,hasMore = preserveRows && it.hasMore) }
        requests[screen] = viewModelScope.launch {
            try {
                block(page)
                if(screen !in listOf(Screen.HOME,Screen.LIVE) || revision == preferenceRevision) loadedAt[screen] = android.os.SystemClock.elapsedRealtime()
            }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(current == generations[screen]) page.update { it.copy(error = readableError(e)) } }
            finally { if(current == generations[screen]) page.update { it.copy(busy = false) } }
        }
    }
    private suspend fun <T> local(block: () -> T): T = withContext(Dispatchers.IO) { block() }
}

fun readableError(error: Throwable): String {
    val name = error.javaClass.simpleName
    return when {
        name.contains("Captcha", true) || name.contains("Bot", true) -> "YouTube is asking this network to verify requests. Try again later or use another network."
        error is java.io.IOException -> "Couldn't reach YouTube. Check your internet connection and try again."
        else -> "YouTube couldn't load this content ($name). Try again; an extractor update may be needed."
    }
}

data class SearchSuggestions(val query: String = "", val items: List<String> = emptyList(), val busy: Boolean = false, val error: String? = null)

/** Search owns a separate session so opening/closing it never resets Home's stable page or focus. */
data class SearchState(val open: Boolean = false, val query: String = "", val submittedQuery: String = "",
    val history: List<String> = emptyList(), val videos: List<Video> = emptyList(),
    val busy: Boolean = false, val error: String? = null,val hasMore: Boolean = false)
