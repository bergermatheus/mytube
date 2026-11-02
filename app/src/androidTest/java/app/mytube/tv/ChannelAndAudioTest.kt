package app.mytube.tv
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
class ChannelAndAudioTest {
 @get:Rule val compose = createEmptyComposeRule()
 @Test fun channelIntentKeepsVideoGridWhenEnteringFromSubscriptionsLabel() {
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
  val i=InstrumentationRegistry.getInstrumentation();val app=i.targetContext.applicationContext as MyTubeApplication
  val channel=app.library.subscriptions().first()
  val a=i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("channel_url",channel.url).putExtra("channel_name",channel.name))
  try {
   compose.waitUntil(90_000) { compose.onAllNodesWithTag("browse-video-0").fetchSemanticsNodes().isNotEmpty() }
   compose.onNodeWithTag("browse-video-0").assertIsFocused()
   i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_LEFT);compose.waitForIdle()
   compose.onNodeWithTag("nav-subscriptions").assertIsFocused()
   i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);compose.waitForIdle()
   compose.onNodeWithTag("browse-video-0").assertIsFocused()
   compose.onNodeWithTag("subscription-grid").assertDoesNotExist()
   compose.onAllNodesWithText(channel.name).onFirst().assertExists()
   i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);compose.waitForIdle()
   compose.onNodeWithTag("browse-video-3").assertIsFocused()
  } finally { i.runOnMainSync { a.finish() } }
 }
 @Test fun pythonCourseExposesOriginalEnglishAndAlternateAudio() = runBlocking {
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("livePlayback") == "true")
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MyTubeApplication
  val info=app.gateway.details("https://www.youtube.com/watch?v=rfscVS0vtbw",true)
  val audio=info.audioStreams.filter { it.isUrl && it.deliveryMethod==org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP }
  val selected=chooseAudio(audio,null)
  println("Audio choices: "+audioLanguages(audio))
  assertNotNull(selected)
  assertEquals(org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL,selected!!.audioTrackType)
  assertEquals("en",selected.audioLocale?.language)
  val alternate=audioLanguages(audio).firstOrNull { !it.original }
  if(alternate!=null) assertEquals(alternate.id,audioIdentity(chooseAudio(audio,alternate.id)!!))
 }
}
