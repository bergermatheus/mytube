package app.mytube.tv

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class SearchPageTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun remoteKeyboardHistoryResultsAndBackPreserveHome() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val activity = i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        fun focus(tag: String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() }; compose.waitForIdle() }
        fun select() { i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER); compose.waitForIdle() }
        try {
            compose.waitUntil(90_000) { compose.onAllNodesWithTag("browse-video-0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("browse-grid").performScrollToIndex(6)
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val pill = compose.onNodeWithTag("search-input").fetchSemanticsNode().boundsInRoot
            assertTrue("Home search should be compact",pill.width < root.width*.45f)
            focus("search-input")
            val beforeScroll = compose.onNodeWithTag("browse-grid").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
            select()
            compose.onNodeWithTag("search-page").assertIsDisplayed()
            compose.onNodeWithTag("sidebar-collapsed").assertDoesNotExist()
            compose.onNodeWithTag("sidebar-expanded").assertDoesNotExist()
            compose.onNodeWithTag("search-back").assertIsDisplayed()
            compose.onNodeWithTag("key-0-0").assertIsFocused()
            select() // A
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT); select() // B
            compose.onNodeWithTag("search-input").assertTextContains("ab")
            focus("key-action-0"); select()
            compose.onNodeWithTag("search-input").assertTextContains("a")
            focus("key-action-1"); select() // numbers
            focus("key-0-0"); select()
            compose.onNodeWithTag("search-input").assertTextContains("a1")
            focus("key-action-1"); select() // letters
            focus("keyboard-space"); select()
            compose.onNodeWithTag("search-input").assertTextContains("a1 ")
            focus("keyboard-clear"); select()
            i.sendStringSync("lucas montano")
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("search-suggestion").fetchSemanticsNodes().isNotEmpty() }
            val keyboard = compose.onNodeWithTag("search-keyboard").fetchSemanticsNode().boundsInRoot
            val history = compose.onNodeWithTag("search-history").fetchSemanticsNode().boundsInRoot
            val videos = compose.onNodeWithTag("search-videos").fetchSemanticsNode().boundsInRoot
            assertTrue(history.right <= keyboard.left)
            assertTrue(videos.top >= keyboard.bottom)
            screenshot("search-typing.png")
            focus("keyboard-submit"); select()
            compose.waitUntil(90_000) { compose.onAllNodesWithTag("search-video-0").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("search-loading").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("search-video-0").assertIsFocused()
            screenshot("search-results.png")
            val prefix = compose.onNodeWithTag("search-video-0").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text]
            compose.onNodeWithTag("search-videos").performScrollToNode(hasText("More results"))
            compose.onNodeWithText("More results").performSemanticsAction(SemanticsActions.RequestFocus) { it() }; select()
            compose.waitUntil(60_000) { compose.onAllNodesWithTag("search-loading").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("search-videos").performScrollToIndex(10)
            compose.onNodeWithTag("search-video-10").assertIsDisplayed()
            compose.onNodeWithTag("search-videos").performScrollToIndex(0)
            assertEquals(prefix,compose.onNodeWithTag("search-video-0").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text])
            focus("search-video-0")
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_UP)
            compose.onNodeWithTag("keyboard-space").assertIsFocused()
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitUntil(5000) { compose.onAllNodesWithTag("browse-grid").fetchSemanticsNodes().isNotEmpty() }
            val afterScroll = compose.onNodeWithTag("browse-grid").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange].value()
            assertEquals("Search reset Home scrolling",beforeScroll,afterScroll,1f)
            compose.onNodeWithTag("search-input").assertIsFocused()
            select()
            compose.onNodeWithTag("search-input").assertTextContains("lucas montano")
            compose.onNodeWithTag("search-video-0").assertIsDisplayed()
            assertEquals(prefix,compose.onNodeWithTag("search-video-0").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text])
            screenshot("search-retained-results.png")
            focus("search-back"); select()
            compose.onNodeWithTag("browse-grid").assertIsDisplayed()
        } finally { i.runOnMainSync { activity.finish() } }
    }
    private fun screenshot(name: String) {
        val i = InstrumentationRegistry.getInstrumentation()
        val dir = java.io.File(i.targetContext.getExternalFilesDir(null),"search-0.6").apply { mkdirs() }
        val image = i.uiAutomation.takeScreenshot()
        java.io.FileOutputStream(java.io.File(dir,name)).use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }
    @Test fun searchHistorySurvivesUpgradeAndDoesNotChangePreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "search-history-test.db"
        createLegacyLibrary(context,name,3)
        var store = LibraryStore(context,name)
        try {
            store.addPreferences(listOf(Subscription("channel-url","Original channel")),listOf("Technology"))
            assertTrue(store.searchHistory().isEmpty())
            repeat(25) { store.rememberSearch("topic $it") }
            store.rememberSearch("  TOPIC   20  ")
            store.rememberSearch("https://www.youtube.com/watch?v=jNQXAC9IVRw")
            store.close(); store = LibraryStore(context,name)
            assertEquals(20,store.searchHistory().size)
            assertEquals("TOPIC 20",store.searchHistory().first())
            assertEquals(1,store.searchHistory().count { it.equals("topic 20",true) })
            assertEquals("Original channel",store.subscriptions().single().name)
            assertEquals("Technology",store.interests().single().label)
            store.clearSearchHistory()
            assertTrue(store.searchHistory().isEmpty())
            assertEquals(1,store.subscriptions().size)
        } finally { store.close(); context.deleteDatabase(name) }
    }
}
