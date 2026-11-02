package app.mytube.tv

import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomePagingTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun tenVideoPagesAppendWithoutChangingFirstCard() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        fun scrollTo(index: Int) {
            // A focused lazy child stays pinned. Move focus outside before a test-driven scroll.
            compose.onNodeWithContentDescription("Home").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.onNodeWithTag("browse-grid").performScrollToIndex(index)
        }
        try {
            compose.waitForIdle()
            compose.waitUntil(90_000) { compose.onAllNodesWithTag("browse-video-0").fetchSemanticsNodes().isNotEmpty() }
            val first = compose.onNodeWithTag("browse-video-0").fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
            scrollTo(10)
            val more = compose.onNodeWithText("More videos ↓ · 10 shown")
            more.assertIsDisplayed()
            compose.onNodeWithTag("browse-video-9").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.waitUntil(60_000) { compose.onNodeWithTag("browse-grid").fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "20 videos" }
            scrollTo(20)
            compose.onNodeWithText("More videos ↓ · 20 shown").assertIsDisplayed()
            scrollTo(0)
            val after = compose.onNodeWithTag("browse-video-0").fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text }
            assertEquals("The first card changed after a page was appended",first,after)
            val monitor = instrumentation.addMonitor(PlayerActivity::class.java.name,null,false)
            try {
                compose.onNodeWithTag("browse-video-0").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
                val started = monitor.waitForActivityWithTimeout(30_000)
                assertNotNull("Selecting a video did not open the player directly",started)
                instrumentation.runOnMainSync { started!!.finish() }
            } finally { instrumentation.removeMonitor(monitor) }
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("browse-grid").fetchSemanticsNodes().isNotEmpty() }
            scrollTo(20)
            compose.onNodeWithText("More videos ↓ · 20 shown").assertIsDisplayed()
        } catch(error: Throwable) {
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"home-paging-failure.txt").writeText(compose.onRoot().printToString())
            throw error
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
