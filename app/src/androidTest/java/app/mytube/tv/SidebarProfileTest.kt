package app.mytube.tv

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class SidebarProfileTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val i get() = InstrumentationRegistry.getInstrumentation()
    private fun key(code: Int) { i.sendKeyDownUpSync(code);compose.waitForIdle() }
    private fun start() = i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    @Test fun focusLoadsSidebarPagesAndSearchHistoryUsesClock() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
        val activity = start()
        try {
            compose.waitUntil(90_000) { compose.onAllNodesWithTag("browse-video-0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav-home").assertIsFocused()
            compose.onNodeWithText("LOCAL LIBRARY\nNo Google sign-in").assertDoesNotExist()
            compose.onNodeWithTag("sidebar-profile").assertIsDisplayed()
            val positions = listOf("search","home","subscriptions","history","live").map { compose.onNodeWithTag("nav-$it").fetchSemanticsNode().boundsInRoot.top }
            assertEquals(positions.sorted(),positions)
            val gap = positions[2]-positions[1]
            val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
            assertTrue("Sidebar rows should be compact",gap < root.height*.09f)
            compose.onNodeWithTag("sidebar-separator").assertIsDisplayed()
            val before = compose.onNodeWithTag("browse-video-0").fetchSemanticsNode().config[SemanticsProperties.Text]
            key(KeyEvent.KEYCODE_DPAD_UP)
            compose.onNodeWithTag("nav-search").assertIsFocused()
            compose.onNodeWithTag("search-keyboard").assertIsDisplayed()
            compose.onNodeWithTag("sidebar-expanded").assertIsDisplayed()
            compose.onNodeWithTag("key-0-0").assertIsNotFocused()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("search-history-clock",useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
            screenshot("search-history-clock-sidebar.png")
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            compose.onNodeWithTag("key-0-0").assertIsFocused()
            compose.onNodeWithTag("sidebar-collapsed").assertIsDisplayed()
            compose.onNodeWithTag("nav-search").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("nav-home").assertIsFocused()
            compose.onNodeWithTag("browse-video-0").assertIsDisplayed()
            assertEquals(before,compose.onNodeWithTag("browse-video-0").fetchSemanticsNode().config[SemanticsProperties.Text])
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("nav-subscriptions").assertIsFocused()
            compose.waitUntil(10_000) { compose.onAllNodesWithTag("subscription-grid").fetchSemanticsNodes().isNotEmpty() }
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("nav-history").assertIsFocused()
            compose.onAllNodesWithText("Library",useUnmergedTree = true).onLast().assertIsDisplayed()
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("nav-live").assertIsFocused()
            compose.onAllNodesWithText("Live",useUnmergedTree = true).onLast().assertIsDisplayed()
            compose.onNodeWithTag("nav-settings").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.onNodeWithTag("profile-settings").assertIsDisplayed()
            screenshot("profile-settings.png")
        } finally { i.runOnMainSync { activity.finish() } }
    }
    @Test fun localProfileNamePhotoAndRemovalSurviveReopen() {
        val context = i.targetContext
        val prefs = "profile-test-only"
        context.deleteSharedPreferences(prefs)
        val store = ProfileStore(context,prefs)
        val original = File(context.cacheDir,"profile-test-source.png")
        val bitmap = Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
        original.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        try {
            assertEquals("User",store.state.value.displayName)
            store.saveName("  Matheus  ")
            store.importPhoto(Uri.fromFile(original))
            val photo = File(java.net.URI(store.state.value.photo))
            assertTrue(photo.isFile)
            val reopened = ProfileStore(context,prefs)
            assertEquals("Matheus",reopened.state.value.name)
            assertEquals("M",reopened.state.value.initial)
            assertEquals(store.state.value.photo,reopened.state.value.photo)
            try { reopened.savePhotoUrl("http://example.com/image.jpg");fail("Expected HTTPS validation") } catch(_: IllegalArgumentException) { }
            assertTrue(photo.isFile)
            reopened.removePhoto()
            assertFalse(photo.exists())
            assertEquals("",ProfileStore(context,prefs).state.value.photo)
            assertEquals("Matheus",ProfileStore(context,prefs).state.value.name)
        } finally { original.delete();context.deleteSharedPreferences(prefs) }
    }
    @Test fun profileFormSavesToItsLocalStore() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
        val prefs = "profile-form-test-only"
        i.targetContext.deleteSharedPreferences(prefs)
        val store = ProfileStore(i.targetContext,prefs)
        val activity = start()
        try {
            i.runOnMainSync { activity.setContent { MyTubeTheme { ProfileSettings(store) } } }
            compose.onNodeWithTag("profile-name").performTextInput("My profile")
            compose.onNodeWithTag("profile-name").assertTextContains("My profile")
            compose.onNodeWithTag("profile-name").performImeAction()
            compose.waitUntil(5000) { store.state.value.name == "My profile" }
            assertEquals("My profile",ProfileStore(i.targetContext,prefs).state.value.name)
        } finally { i.runOnMainSync { activity.finish() };i.targetContext.deleteSharedPreferences(prefs) }
    }
    @Test fun followedChannelLiveTabReturnsOnlyCurrentlyLiveStreams() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveTests") == "true")
        val app = i.targetContext.applicationContext as MyTubeApplication
        val cnn = app.library.subscriptions().first { it.name.contains("CNN",true) }
        val videos = app.gateway.channelLiveCandidates(cnn.url)
        assertTrue("Expected a currently live CNN Brasil broadcast",videos.isNotEmpty())
        assertTrue(videos.all { it.isLive })
        val current = app.gateway.details(videos.first().url,fresh = true)
        assertTrue("Live details: type=${current.streamType}, availability=${current.contentAvailability}, duration=${current.duration}, hls=${!current.hlsUrl.isNullOrBlank()}, title=${current.name}",ExtractorGateway.isActiveLive(current.streamType,current.contentAvailability))
        assertTrue("Live stream needs a playable manifest or format",!current.hlsUrl.isNullOrBlank() || !current.dashMpdUrl.isNullOrBlank() || current.videoStreams.isNotEmpty())
        val activity = start()
        try {
            compose.onNodeWithTag("nav-live").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.waitUntil(120_000) { compose.onAllNodesWithTag("browse-video-0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("nav-live").assertIsFocused()
            assertTrue(compose.onNodeWithTag("browse-video-0").fetchSemanticsNode().config[SemanticsProperties.Text].any { it.text == "LIVE" })
            screenshot("personalized-live-page.png")
        } finally { i.runOnMainSync { activity.finish() } }
    }
    private fun screenshot(name: String) {
        val dir = File(i.targetContext.getExternalFilesDir(null),"sidebar-0.8").apply { mkdirs() }
        val image = i.uiAutomation.takeScreenshot()
        File(dir,name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
    }
}
