package app.mytube.tv

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerUpgradeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun select(node: SemanticsNodeInteraction) {
        node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER)
        compose.waitForIdle()
    }
    private fun key(code: Int) { instrumentation.sendKeyDownUpSync(code); compose.waitForIdle() }
    private fun findPlayer(view: View): ExoPlayer? {
        if(view is PlayerView) return view.player as? ExoPlayer
        if(view is ViewGroup) for(i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null),"test-screenshots").apply { mkdirs() }
        FileOutputStream(File(folder,name)).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    @Test fun searchSuggestsWhileTypingAndSelectRunsSearch() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        try {
            compose.waitUntil(90_000) { compose.onAllNodesWithTag("initial-loading").fetchSemanticsNodes().isEmpty() }
            select(compose.onNodeWithTag("search-input"))
            instrumentation.sendStringSync("lucas mont")
            compose.waitUntil(45_000) { compose.onAllNodesWithTag("search-suggestion").fetchSemanticsNodes().isNotEmpty() }
            screenshot("search-autocomplete.png")
            val suggestion = compose.onAllNodesWithTag("search-suggestion").onFirst()
            select(suggestion)
            compose.waitUntil(90_000) { compose.onAllNodesWithTag("search-video-0").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("search-loading").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("search-keyboard").assertIsDisplayed()
            compose.onAllNodesWithText("Results for", substring = true).onFirst().assertIsDisplayed()
            println("Real autocomplete results appeared during typing and selecting a suggestion loaded video results.")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun playerDrawerTransportCommentsAndChannel() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("livePlayback") == "true")
        val original = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,PlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("video_url",original).putExtra("from_start",true))
        fun waitPlaying() {
            val deadline = SystemClock.elapsedRealtime()+90_000
            var ready = false
            while(SystemClock.elapsedRealtime()<deadline) {
                instrumentation.runOnMainSync { ready = findPlayer(activity.window.decorView)?.isPlaying == true }
                if(ready) break
                SystemClock.sleep(200)
            }
            assertTrue("Player failed to play", ready)
        }
        try {
            waitPlaying(); key(KeyEvent.KEYCODE_MEDIA_PAUSE)
            compose.onNodeWithTag("player-controls").assertDoesNotExist()
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            val transport = compose.onNodeWithTag("player-transport").fetchSemanticsNode().boundsInRoot
            assertEquals("Transport isn't centered",root.center.x,transport.center.x,3f)
            compose.onNodeWithText("↓ Videos").assertDoesNotExist()
            compose.onNodeWithContentDescription("Previous video").assertIsNotEnabled()
            screenshot("player-updated-controls.png")
            select(compose.onNodeWithContentDescription("Settings"))
            val drawer = compose.onNodeWithTag("player-settings").fetchSemanticsNode().boundsInRoot
            assertTrue(drawer.left > root.width*.5f); assertEquals(root.right,drawer.right,2f)
            compose.onNodeWithText("Close").assertDoesNotExist()
            val before = instrumentation.targetContext.getSharedPreferences("player",0).getFloat("speed",1f)
            select(compose.onNodeWithText("Playback speed"))
            val changed = if(before < 2f) before+.25f else before-.25f
            select(compose.onNodeWithText(if(changed == 1f) "Normal" else "${changed}×").performScrollTo())
            instrumentation.runOnMainSync { assertEquals(changed,findPlayer(activity.window.decorView)!!.playbackParameters.speed,.001f) }
            select(compose.onNodeWithText("Playback speed"))
            select(compose.onNodeWithText(if(before == 1f) "Normal" else "${before}×").performScrollTo())
            val gateway = (instrumentation.targetContext.applicationContext as MyTubeApplication).gateway
            val info = runBlocking { gateway.details(original) }
            val subtitle = info.subtitles.firstOrNull { it.isUrl && it.format in listOf(org.schabi.newpipe.extractor.MediaFormat.TTML,org.schabi.newpipe.extractor.MediaFormat.VTT,org.schabi.newpipe.extractor.MediaFormat.SRT) }
            assertNotNull("Fixture must expose a real subtitle stream",subtitle)
            val captionLabel = subtitle!!.displayLanguageName + if(subtitle.isAutoGenerated) " (auto)" else ""
            select(compose.onNodeWithText("Subtitles"))
            select(compose.onNodeWithText(captionLabel).performScrollTo())
            val captionDeadline = SystemClock.elapsedRealtime()+30_000
            var captionSelected = false
            while(SystemClock.elapsedRealtime()<captionDeadline) {
                instrumentation.runOnMainSync { captionSelected = findPlayer(activity.window.decorView)!!.currentTracks.groups.any { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT && it.isSelected } }
                if(captionSelected) break
                SystemClock.sleep(200)
            }
            assertTrue("Selected caption track was not prepared",captionSelected)
            val cuesSeen = java.util.concurrent.atomic.AtomicBoolean(false)
            instrumentation.runOnMainSync {
                findPlayer(activity.window.decorView)!!.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) { if(cueGroup.cues.isNotEmpty()) cuesSeen.set(true) }
                })
                findPlayer(activity.window.decorView)!!.play()
            }
            val cuesDeadline = SystemClock.elapsedRealtime()+12_000
            while(!cuesSeen.get() && SystemClock.elapsedRealtime()<cuesDeadline) SystemClock.sleep(100)
            instrumentation.runOnMainSync { findPlayer(activity.window.decorView)!!.pause() }
            assertTrue("Selected subtitles did not produce any cues",cuesSeen.get())
            screenshot("player-settings-drawer.png")
            select(compose.onNodeWithText("Subtitles"))
            select(compose.onNodeWithText("Off").performScrollTo())
            key(KeyEvent.KEYCODE_BACK)
            select(compose.onNodeWithContentDescription("Comments"))
            compose.waitUntil(45_000) { compose.onAllNodesWithTag("comments-loading").fetchSemanticsNodes().isEmpty() }
            compose.onNodeWithTag("player-comments").assertIsDisplayed()
            compose.onNodeWithText("Close").assertDoesNotExist()
            compose.onNodeWithTag("comment-card-0").assertIsDisplayed()
            screenshot("player-comments-drawer.png")
            key(KeyEvent.KEYCODE_BACK)
            select(compose.onNodeWithContentDescription("Next video"))
            compose.waitUntil(5000) { activity.intent.getStringExtra("video_url") != original }
            waitPlaying(); key(KeyEvent.KEYCODE_MEDIA_PAUSE); key(KeyEvent.KEYCODE_DPAD_DOWN)
            select(compose.onNodeWithContentDescription("Previous video"))
            compose.waitUntil(5000) { activity.intent.getStringExtra("video_url") == original }
            waitPlaying(); key(KeyEvent.KEYCODE_MEDIA_PAUSE); key(KeyEvent.KEYCODE_DPAD_DOWN)
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name,null,false)
            try {
                select(compose.onNodeWithContentDescription("Open channel"))
                val channelActivity = instrumentation.waitForMonitorWithTimeout(monitor,15_000)
                assertNotNull("Channel avatar did not open channel",channelActivity)
                compose.waitUntil(60_000) { compose.onAllNodesWithTag("initial-loading").fetchSemanticsNodes().isEmpty() }
                assertTrue(channelActivity.intent.getStringExtra("channel_url").orEmpty().contains("youtube.com/"))
                instrumentation.runOnMainSync { channelActivity.finish() }
            } finally { instrumentation.removeMonitor(monitor) }
            println("Centered transport, right settings drawer, actual speed, comments drawer, next/previous playback, and channel navigation passed.")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
    @Test fun liveSuggestionsCommentsAndCaptionInventory() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("livePlayback") == "true")
        val gateway = (instrumentation.targetContext.applicationContext as MyTubeApplication).gateway
        assertTrue(gateway.suggestions("lucas mont").isNotEmpty())
        val comments = gateway.comments("https://www.youtube.com/watch?v=jNQXAC9IVRw").items
        assertTrue("No real comments extracted",comments.any { it.text.isNotBlank() })
        assertTrue("Public comment avatars were not decoded",comments.take(3).all { it.avatar.startsWith("https://") })
        val info = gateway.details("https://www.youtube.com/watch?v=jNQXAC9IVRw")
        val inventory = "Live data: ${comments.size} comments; ${info.subtitles.size} subtitle streams; channel avatar=${info.uploaderAvatars.isNotEmpty()}"
        File(instrumentation.targetContext.getExternalFilesDir(null),"live-data-inventory.txt").writeText(inventory)
        println(inventory)
        assertTrue(info.uploaderAvatars.isNotEmpty())
    }
}
