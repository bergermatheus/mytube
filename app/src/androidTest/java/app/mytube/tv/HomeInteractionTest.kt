package app.mytube.tv

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class HomeInteractionTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun navigationAndPlaybackRemainAvailableUntilTheNextPageIsReady() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val activity = i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val release = CompletableDeferred<Unit>()
        var requests = 0
        var played: Video? = null
        val initial = (0..9).map { Video("video-$it","Video $it","Channel",publishedAtMs = System.currentTimeMillis()-86_400_000L) }
        try {
            i.runOnMainSync { activity.setContent { MyTubeTheme {
                var videos by remember { mutableStateOf(initial) }
                var busy by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val grid = rememberLazyGridState()
                val first = remember { FocusRequester() }
                HomeVideoGrid(videos,grid,first,true,true,busy,true,null,more = {
                    if(!busy) {
                        requests++; busy = true
                        scope.launch {
                            release.await()
                            videos += (10..19).map { Video("video-$it","Video $it","Channel") }
                            busy = false
                        }
                    }
                },options = {},play = { played = it })
            } } }
            compose.onNodeWithTag("browse-grid").performScrollToIndex(9)
            compose.onNodeWithTag("browse-video-9").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            fun key(code: Int,expected: Int) {
                i.sendKeyDownUpSync(code); compose.waitForIdle()
                compose.onNodeWithTag("browse-video-$expected").assertIsFocused()
            }
            key(KeyEvent.KEYCODE_DPAD_DOWN,9)
            compose.onNodeWithTag("page-loading").assertExists()
            val start = SystemClock.elapsedRealtime()
            val cpu = android.os.Process.getElapsedCpuTime()
            key(KeyEvent.KEYCODE_DPAD_UP,6)
            key(KeyEvent.KEYCODE_DPAD_RIGHT,7)
            key(KeyEvent.KEYCODE_DPAD_LEFT,6)
            key(KeyEvent.KEYCODE_DPAD_DOWN,9)
            key(KeyEvent.KEYCODE_DPAD_CENTER,9)
            assertEquals("video-9",played?.url)
            assertEquals("Paging was requested more than once while pending",1,requests)
            assertFalse(release.isCompleted)
            compose.onNodeWithTag("page-loading").assertExists()
            println("Five remote actions while page pending: elapsed_ms=${SystemClock.elapsedRealtime()-start}, app_cpu_ms=${android.os.Process.getElapsedCpuTime()-cpu}")
            release.complete(Unit)
            compose.waitUntil(10_000) { compose.onNodeWithTag("browse-grid").fetchSemanticsNode().config[SemanticsProperties.StateDescription] == "20 videos" }
            compose.onNodeWithTag("browse-video-9").assertIsFocused()
            key(KeyEvent.KEYCODE_DPAD_DOWN,12)
            key(KeyEvent.KEYCODE_DPAD_UP,9)
            key(KeyEvent.KEYCODE_DPAD_UP,6)
            key(KeyEvent.KEYCODE_DPAD_UP,3)
            key(KeyEvent.KEYCODE_DPAD_UP,0)
            assertTrue(compose.onNodeWithTag("browse-video-0").fetchSemanticsNode().config[SemanticsProperties.Text].any { it.text == "Video 0" })
        } finally { release.complete(Unit); i.runOnMainSync { activity.finish() } }
    }
    @Test fun liveHomeHasCenteredLoadingMetadataAndCircularChannelGrid() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val activity = i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        try {
            compose.waitUntil(5000) { compose.onAllNodesWithTag("initial-loading").fetchSemanticsNodes().isNotEmpty() }
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val spinner = compose.onNodeWithTag("initial-loading").fetchSemanticsNode().boundsInRoot
            assertEquals(root.center.x,spinner.center.x,2f)
            assertEquals(root.center.y,spinner.center.y,2f)
            compose.waitUntil(90_000) { compose.onAllNodesWithTag("browse-video-0").fetchSemanticsNodes().isNotEmpty() }
            val date = compose.onAllNodesWithTag("video-publication",useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
            val channel = compose.onAllNodesWithTag("video-channel",useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
            val title = compose.onAllNodesWithTag("video-title",useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot
            assertTrue("Publication date overlaps the thumbnail/title",date.top >= title.bottom)
            assertTrue(date.left >= channel.right)
            assertEquals(channel.center.y,date.center.y,4f)
            screenshot("home-relative-dates.png")
            compose.onNodeWithContentDescription("Subscriptions").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("subscription-0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("subscription-grid").assertIsDisplayed()
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT)
            compose.onNodeWithTag("subscription-0").assertIsFocused()
            val image = compose.onNodeWithTag("subscription-avatar-0",useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(image.width,image.height,1f)
            // Fetch actual image metadata from the production gateway; Coil renders it in the live grid.
            val app = i.targetContext.applicationContext as MyTubeApplication
            val channel0 = app.library.subscriptions().first()
            val avatar = kotlinx.coroutines.runBlocking { app.gateway.channelAvatar(channel0.url) }
            assertTrue("Expected an actual YouTube channel avatar",avatar.startsWith("https://"))
            android.os.SystemClock.sleep(750)
            screenshot("subscriptions-circle-grid.png")
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
            compose.waitUntil(90_000) { compose.onAllNodesWithTag("browse-video-0").fetchSemanticsNodes().isNotEmpty() }
            assertTrue(compose.onAllNodesWithText(channel0.name).fetchSemanticsNodes().isNotEmpty())
        } finally { i.runOnMainSync { activity.finish() } }
    }
    private fun screenshot(name: String) {
        val i = InstrumentationRegistry.getInstrumentation()
        val folder = java.io.File(i.targetContext.getExternalFilesDir(null),"home-0.7").apply { mkdirs() }
        val bitmap = i.uiAutomation.takeScreenshot()
        java.io.FileOutputStream(java.io.File(folder,name)).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
}
