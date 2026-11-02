package app.mytube.tv

import android.app.Activity
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.media3.ui.PlayerView
import androidx.media3.exoplayer.ExoPlayer
import java.io.File
import java.io.FileOutputStream
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in: plays a public test video on the connected test device. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlaybackSmokeTest {
    @Test fun videoActuallyPlaysAndPersistsProgress() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("livePlayback") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val activity = instrumentation.startActivitySync(Intent(context, PlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("video_url", "https://www.youtube.com/watch?v=jNQXAC9IVRw")
            .putExtra("from_start", true))
        try {
            val deadline = SystemClock.elapsedRealtime() + 90_000
            var playing = false
            var position = 0L
            var renderedFrames = 0
            while(SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync {
                    val player = findPlayer(activity.window.decorView)?.player
                    playing = player?.isPlaying == true
                    position = player?.currentPosition ?: 0
                    val counters = (player as? ExoPlayer)?.videoDecoderCounters
                    counters?.ensureUpdated(); renderedFrames = counters?.renderedOutputBufferCount ?: 0
                }
                if(playing && position > 2000 && renderedFrames > 0) break
                SystemClock.sleep(250)
            }
            assertTrue("Video did not render frames within 90 seconds", playing && position > 2000 && renderedFrames > 0)
            println("Live Chromecast playback advanced to ${position}ms; rendered frames=$renderedFrames")
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
            instrumentation.waitForIdleSync()
            val directory = File(context.getExternalFilesDir(null),"test-screenshots").apply { mkdirs() }
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            FileOutputStream(File(directory,"player-rendered-video.png")).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        } finally {
            instrumentation.runOnMainSync { activity.finish() }
        }
        val deadline = SystemClock.elapsedRealtime() + 10_000
        val app = context.applicationContext as MyTubeApplication
        var saved = 0L
        while(SystemClock.elapsedRealtime() < deadline) {
            saved = app.library.progress("https://www.youtube.com/watch?v=jNQXAC9IVRw")
            if(saved > 0) break
            SystemClock.sleep(100)
        }
        assertTrue("Playback progress was not persisted", saved > 0)
    }
    private fun findPlayer(view: View): PlayerView? {
        if(view is PlayerView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) findPlayer(view.getChildAt(i))?.let { return it }
        return null
    }
}
