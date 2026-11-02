package app.mytube.tv

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Same on-device workload before/after the retained-page change. Includes instrumentation idle waits. */
class PageNavigationPerformanceTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun homeSearch() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("profileNavigation") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val activity = i.startActivitySync(Intent(i.targetContext,MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        fun shell(command: String) = i.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().readText()
        }
        fun focus(label: String) {
            compose.onNodeWithContentDescription(label).performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            compose.waitForIdle(); SystemClock.sleep(120)
        }
        fun cycle() { focus("Search"); focus("Home") }
        fun sample(label: String, count: Int) {
            shell("dumpsys gfxinfo app.mytube.tv reset")
            val start = SystemClock.elapsedRealtime(); val cpu = android.os.Process.getElapsedCpuTime()
            repeat(count) { cycle() }
            val elapsed = SystemClock.elapsedRealtime()-start; val cpuMs = android.os.Process.getElapsedCpuTime()-cpu
            val memory = File("/proc/self/status").readLines().filter { it.startsWith("VmHWM:") || it.startsWith("VmRSS:") }.joinToString("\n")
            val prefix = InstrumentationRegistry.getArguments().getString("profileLabel") ?: "pages"
            File(i.targetContext.getExternalFilesDir(null),"$prefix-$label.txt").writeText("elapsed_ms=$elapsed\ncpu_ms=$cpuMs\n$memory\n${shell("dumpsys gfxinfo app.mytube.tv framestats")}")
            println("$prefix $label: elapsed_ms=$elapsed cpu_ms=$cpuMs $memory")
        }
        try {
            compose.waitUntil(90_000) { compose.onAllNodesWithTag("browse-video-0").fetchSemanticsNodes().isNotEmpty() }
            SystemClock.sleep(1500)
            sample("first-search",1)
            sample("warm-search",6)
        } finally { i.runOnMainSync { activity.finish() } }
    }
}
