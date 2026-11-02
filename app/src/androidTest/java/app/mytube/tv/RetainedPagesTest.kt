package app.mytube.tv

import android.content.Intent
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class RetainedPagesTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun visitedPagesKeepCompositionAndScrollWithoutHiddenFocusTargets() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val activity = i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        val mounts = mutableMapOf<String,Int>()
        val selected = mutableStateOf("Home")
        val loaded = mutableStateOf(false)
        try {
            i.runOnMainSync { activity.setContent { MyTubeTheme {
                Column {
                    Row {
                        Action("Home") { selected.value = "Home" }
                        Action("Search") { selected.value = "Search" }
                    }
                    RetainedPages(selected.value,Modifier.weight(1f)) { route ->
                        DisposableEffect(Unit) { mounts[route] = (mounts[route] ?: 0)+1;onDispose { mounts[route] = -100 } }
                        val scroll = rememberLazyListState()
                        LazyColumn(Modifier.fillMaxSize().testTag("$route-list"),state = scroll) {
                            items((0..29).toList()) { n ->
                                androidx.tv.material3.Button(onClick = { selected.value = if(route == "Home") "Search" else "Home" },modifier = Modifier.fillMaxWidth().height(48.dp).testTag("$route-$n")) {
                                    androidx.tv.material3.Text("$route $n ${if(loaded.value) "ready" else "pending"}")
                                }
                            }
                        }
                    }
                }
            } } }
            compose.onNodeWithTag("Home-list").performScrollToIndex(20)
            val before = compose.onNodeWithTag("Home-20").fetchSemanticsNode().boundsInRoot
            compose.onNodeWithText("Search").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);compose.waitForIdle()
            assertEquals("Search",selected.value)
            compose.onNodeWithTag("Home-list").assertIsNotDisplayed()
            compose.onNodeWithTag("Search-0").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            i.runOnMainSync { loaded.value = true }
            compose.waitForIdle()
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN)
            compose.onNodeWithTag("Search-1").assertIsFocused()
            compose.onNodeWithText("Home").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);compose.waitForIdle()
            assertEquals("Home",selected.value)
            compose.onNodeWithTag("Home-20").assertIsDisplayed()
            assertEquals(before,compose.onNodeWithTag("Home-20").fetchSemanticsNode().boundsInRoot)
            compose.onNodeWithTag("Search-list").assertIsNotDisplayed()
            compose.onNodeWithTag("Home-20").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            i.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);compose.waitForIdle()
            compose.onNodeWithTag("Search-list").assertIsDisplayed()
            val focusedTag = compose.onAllNodes(isFocused()).fetchSemanticsNodes().single().config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]
            assertTrue("Focus remained in a hidden page: $focusedTag",focusedTag.startsWith("Search-"))
            assertEquals(mapOf("Home" to 1,"Search" to 1),mounts)
        } finally { i.runOnMainSync { activity.finish() } }
    }
    @Test fun relatedGridTraversesRowsAndExitsOnlyAboveTheFirstRow() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
        val i = InstrumentationRegistry.getInstrumentation()
        val activity = i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        var controls = 0
        val videos = (0..11).map { Video("v$it","Video $it","Channel") }
        try {
            i.runOnMainSync { activity.setContent { MyTubeTheme {
                val first = remember { FocusRequester() }
                Box(Modifier.fillMaxSize()) { RelatedPlayerVideos(videos,first,{ controls++ },{}) }
            } } }
            compose.onNodeWithTag("related-video-0").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            fun key(code: Int) { i.sendKeyDownUpSync(code);compose.waitForIdle() }
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            val secondRow = compose.onAllNodes(isFocused()).fetchSemanticsNodes().single()
            assertTrue(secondRow.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag].startsWith("related-video-"))
            assertNotEquals("related-video-0",secondRow.config[androidx.compose.ui.semantics.SemanticsProperties.TestTag])
            key(KeyEvent.KEYCODE_DPAD_UP)
            compose.onNodeWithTag("related-video-0").assertIsFocused();assertEquals(0,controls)
            key(KeyEvent.KEYCODE_DPAD_RIGHT);compose.onNodeWithTag("related-video-1").assertIsFocused()
            key(KeyEvent.KEYCODE_DPAD_LEFT);compose.onNodeWithTag("related-video-0").assertIsFocused()
            key(KeyEvent.KEYCODE_DPAD_UP);assertEquals(1,controls)
        } finally { i.runOnMainSync { activity.finish() } }
    }
}
