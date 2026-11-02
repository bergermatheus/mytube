package app.mytube.tv

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owns route identity and snapshots. Each loader captures its route, never the current selection. */
internal class BrowsePages {
    private val snapshots = MutableStateFlow(mapOf(Screen.HOME to BrowseState()))
    val states = snapshots.asStateFlow()
    private val selected = MutableStateFlow(BrowseState())
    val state = selected.asStateFlow()
    private val records = mutableMapOf<Screen, Page>()
    val active: Page get() = page(selected.value.screen)
    fun page(screen: Screen): Page = records.getOrPut(screen) { Page(screen) }
    fun select(screen: Screen) { val state = page(screen).value;snapshots.value = snapshots.value + (screen to state);selected.value = state }
    fun updateAll(transform: (BrowseState) -> BrowseState) {
        snapshots.value = snapshots.value.mapValues { transform(it.value) }
        selected.value = snapshots.value.getValue(selected.value.screen)
    }
    inner class Page(private val screen: Screen) {
        var value: BrowseState
            get() = snapshots.value[screen] ?: selected.value.copy(screen = screen,rows = emptyList(),busy = false,error = null,hasMore = false,liveChecked = 0,liveTotal = 0)
            set(value) {
                snapshots.value = snapshots.value + (screen to value)
                if(selected.value.screen == screen) selected.value = value
            }
        fun update(transform: (BrowseState) -> BrowseState) { value = transform(value) }
    }
}
