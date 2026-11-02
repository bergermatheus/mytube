package app.mytube.tv

import org.junit.Assert.*
import org.junit.Test

class BrowsePagesTest {
    @Test fun pendingPageCompletionDoesNotReplaceTheSelectedRoute() {
        val pages = BrowsePages()
        val home = pages.active
        home.update { it.copy(busy = true) }
        pages.select(Screen.SEARCH)
        val row = VideoRow("Home",listOf(Video("v1","Video","Channel")))
        home.update { it.copy(rows = listOf(row),busy = false) }
        assertEquals(Screen.SEARCH,pages.state.value.screen)
        assertTrue(pages.state.value.rows.isEmpty())
        pages.select(Screen.HOME)
        assertEquals(listOf(row),pages.state.value.rows)
        assertFalse(pages.state.value.busy)
    }
    @Test fun sharedLibraryChangesReachCachedAndNewDestinations() {
        val pages = BrowsePages()
        pages.select(Screen.SUBSCRIPTIONS)
        pages.select(Screen.HOME)
        val subscriptions = listOf(Subscription("https://www.youtube.com/@example","Example"))
        pages.updateAll { it.copy(subscriptions = subscriptions) }
        pages.select(Screen.SUBSCRIPTIONS)
        assertEquals(subscriptions,pages.state.value.subscriptions)
        pages.select(Screen.SETTINGS)
        assertEquals(subscriptions,pages.state.value.subscriptions)
        assertTrue(pages.state.value.rows.isEmpty())
    }
}
