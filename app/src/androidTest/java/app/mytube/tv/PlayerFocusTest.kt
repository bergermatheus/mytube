package app.mytube.tv

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerFocusTest {
 @get:Rule val compose=createEmptyComposeRule()
 @Test fun onlyTimelineFocusSeeksAndVerticalFocusIsReversible() {
  assumeTrue(InstrumentationRegistry.getArguments().getString("livePlayback")=="true")
  val i=InstrumentationRegistry.getInstrumentation()
  val a=i.startActivitySync(Intent(i.targetContext,PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("video_url","https://www.youtube.com/watch?v=rfscVS0vtbw").putExtra("from_start",true))
  fun find(v: View):PlayerView? { if(v is PlayerView)return v;if(v is ViewGroup)for(n in 0 until v.childCount)find(v.getChildAt(n))?.let{return it};return null }
  fun key(k:Int) { i.sendKeyDownUpSync(k);compose.waitForIdle() }
  fun pos():Long {var p=0L;i.runOnMainSync { p=find(a.window.decorView)!!.player!!.currentPosition };return p}
  try {
   val end=SystemClock.elapsedRealtime()+90_000;var ready=false
   while(!ready && SystemClock.elapsedRealtime()<end) {i.runOnMainSync {ready=find(a.window.decorView)?.player?.isPlaying==true};if(!ready)SystemClock.sleep(200)}
   assertTrue(ready);key(KeyEvent.KEYCODE_MEDIA_PAUSE)
   val before=pos();key(KeyEvent.KEYCODE_DPAD_RIGHT);key(KeyEvent.KEYCODE_DPAD_LEFT)
   assertEquals(before,pos());compose.onNodeWithTag("player-controls").assertDoesNotExist()
   key(KeyEvent.KEYCODE_DPAD_DOWN);compose.onNodeWithTag("player-seek").assertIsFocused()
   key(KeyEvent.KEYCODE_DPAD_RIGHT);assertTrue(pos()>=before+9500)
   key(KeyEvent.KEYCODE_DPAD_LEFT);assertEquals(before,pos())
   key(KeyEvent.KEYCODE_DPAD_DOWN);compose.onNodeWithContentDescription("Play / pause").assertIsFocused()
   key(KeyEvent.KEYCODE_DPAD_UP);compose.onNodeWithTag("player-seek").assertIsFocused()
   key(KeyEvent.KEYCODE_DPAD_UP);compose.onNodeWithTag("player-controls").assertDoesNotExist()
   key(KeyEvent.KEYCODE_DPAD_CENTER)
   var playing=false;i.runOnMainSync{playing=find(a.window.decorView)!!.player!!.playWhenReady};assertTrue(playing)
   key(KeyEvent.KEYCODE_DPAD_CENTER)
   key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN);key(KeyEvent.KEYCODE_DPAD_DOWN)
   compose.onNodeWithTag("related-video-0").assertIsFocused()
   key(KeyEvent.KEYCODE_DPAD_UP);compose.onNodeWithContentDescription("Play / pause").assertIsFocused()
  } finally {i.runOnMainSync{a.finish()}}
 }
}
