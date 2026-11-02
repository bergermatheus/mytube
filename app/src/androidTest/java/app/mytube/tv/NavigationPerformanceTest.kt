package app.mytube.tv

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Opt-in, comparable warm navigation workload; no feed requests are made during measurement. */
class NavigationPerformanceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun warmSidebarAndGrid() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("profileNavigation") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        fun key(code: Int) { instrumentation.sendKeyDownUpSync(code); compose.waitForIdle(); SystemClock.sleep(120) }
        fun focus(node: SemanticsNodeInteraction) { node.performSemanticsAction(SemanticsActions.RequestFocus) { it() }; compose.waitForIdle() }
        fun cycle() {
            focus(compose.onNodeWithContentDescription("Home")); key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_LEFT)
            compose.onNodeWithTag("browse-grid").performScrollToIndex(0)
            focus(compose.onNodeWithTag("browse-video-0")); key(KeyEvent.KEYCODE_DPAD_DOWN); key(KeyEvent.KEYCODE_DPAD_UP)
        }
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText()
        }
        try {
            compose.waitUntil(90_000) { compose.onAllNodesWithTag("browse-video-9").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("browse-video-0").fetchSemanticsNodes().isNotEmpty() }
            SystemClock.sleep(2000)
            repeat(2) { cycle() }
            shell("dumpsys gfxinfo app.mytube.tv reset")
            val start = SystemClock.elapsedRealtime(); val cpu = android.os.Process.getElapsedCpuTime()
            repeat(6) { cycle() }
            val elapsed = SystemClock.elapsedRealtime() - start; val cpuMs = android.os.Process.getElapsedCpuTime() - cpu
            val report = shell("dumpsys gfxinfo app.mytube.tv framestats")
            val memory = File("/proc/self/status").readLines().filter { it.startsWith("VmHWM:") || it.startsWith("VmRSS:") }.joinToString("\n")
            val label = InstrumentationRegistry.getArguments().getString("profileLabel") ?: "navigation"
            File(instrumentation.targetContext.getExternalFilesDir(null),"$label-gfxinfo.txt").writeText("elapsed_ms=$elapsed\ncpu_ms=$cpuMs\n$memory\n$report")
            println("Navigation completed: elapsed_ms=$elapsed cpu_ms=$cpuMs $memory\n${report.take(900)}")
        } finally { instrumentation.runOnMainSync { activity.finish() } }
    }
}
