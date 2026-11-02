package app.mytube.tv

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class LayoutRefreshTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun sidebarReservesSpaceAndRightEntersFirstVideo() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        try {
            compose.waitUntil(90_000) { compose.onAllNodesWithTag("browse-video-0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Refresh").assertDoesNotExist()
            compose.onNodeWithContentDescription("Search").assertIsDisplayed()
            compose.onNodeWithText("Your videos. Your library.").assertDoesNotExist()
            compose.onNodeWithTag("voice-search").assertIsDisplayed()
            compose.onNodeWithTag("search-input").assertIsDisplayed()
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val brand = compose.onNodeWithTag("header-brand").fetchSemanticsNode().boundsInRoot
            assertTrue(brand.left > root.width*.65f)
            compose.onNodeWithContentDescription("Home").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            val menu = compose.onNodeWithTag("sidebar-expanded").fetchSemanticsNode().boundsInRoot
            val expandedGrid = compose.onNodeWithTag("browse-grid").fetchSemanticsNode().boundsInRoot
            assertTrue("Expanded sidebar overlaps videos",expandedGrid.left >= menu.right)
            assertEquals("Outer left page frame remains",root.left,menu.left,2f)
            assertEquals("Grid should reach the right viewport edge",root.right,expandedGrid.right,2f)
            assertEquals("Grid should reach the bottom viewport edge",root.bottom,expandedGrid.bottom,2f)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            compose.onNodeWithTag("browse-video-0").assertIsFocused()
            val collapsedGrid = compose.onNodeWithTag("browse-grid").fetchSemanticsNode().boundsInRoot
            assertTrue("Grid should use the freed sidebar space",collapsedGrid.left < expandedGrid.left)
            compose.onNodeWithContentDescription("Home").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            val again = compose.onNodeWithTag("browse-grid").fetchSemanticsNode().boundsInRoot
            assertEquals(expandedGrid.left,again.left,2f)
            compose.onNodeWithTag("browse-grid").performScrollToIndex(9)
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            compose.onNodeWithTag("browse-video-0").assertIsFocused()
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun displayedTitlesMatchOriginalVideoMetadata() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveTitles") == "true")
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MyTubeApplication
        val channel = app.library.subscriptions().first { it.name.contains("Renato",true) }
        val videos = app.gateway.channelCandidates(channel.url).take(2)
        assertTrue(videos.isNotEmpty())
        val originals = app.gateway.originalTitles(videos)
        originals.forEach { video -> assertEquals(app.gateway.details(video.url).name,video.title) }
        val english = app.gateway.originalTitles(listOf(Video("https://www.youtube.com/watch?v=jNQXAC9IVRw","a translated placeholder"))).single()
        assertEquals("Me at the zoo",english.title)
        println("Original titles verified against player metadata: ${originals.map { it.title }}; English title=${english.title}")
    }
    @Test fun microphoneUsesSystemRecognitionAndSearchesReturnedText() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val reply = Intent().putStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS,arrayListOf("Lucas Montano"))
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        val monitor = instrumentation.addMonitor(android.content.IntentFilter(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH),android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_OK,reply),true)
        try {
            compose.waitForIdle()
            compose.onNodeWithTag("voice-search").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Lucas Montano").fetchSemanticsNodes().isNotEmpty() }
            assertTrue("The microphone did not launch the system speech contract",monitor.hits > 0)
            compose.onNodeWithTag("search-input").assertTextContains("Lucas Montano")
        } finally { instrumentation.removeMonitor(monitor);instrumentation.runOnMainSync { activity.finish() } }
    }

}
