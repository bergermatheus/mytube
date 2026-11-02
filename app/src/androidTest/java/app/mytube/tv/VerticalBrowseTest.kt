package app.mytube.tv
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
class VerticalBrowseTest {
 @get:Rule val compose=createEmptyComposeRule()
 @Test fun searchAndLibraryNavigateVertically() {
  org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("uiTests") == "true")
  val i=InstrumentationRegistry.getInstrumentation()
  val a=i.startActivitySync(Intent(i.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
  fun key(k:Int) { i.sendKeyDownUpSync(k);compose.waitForIdle() }
  fun focus(tag:String) { compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.RequestFocus) { it() };compose.waitForIdle() }
  try {
   focus("nav-search");key(KeyEvent.KEYCODE_DPAD_RIGHT)
   compose.onNodeWithTag("key-0-0").assertIsFocused()
   i.sendStringSync("python tutorial");key(KeyEvent.KEYCODE_SEARCH)
   compose.waitUntil(90_000) { compose.onAllNodesWithTag("search-video-0").fetchSemanticsNodes().isNotEmpty() }
   focus("search-video-0");key(KeyEvent.KEYCODE_DPAD_DOWN)
   compose.onNodeWithTag("search-video-3").assertIsFocused()
   fun wholeCard(index: Int) {
    val grid=compose.onNodeWithTag("search-videos").fetchSemanticsNode().boundsInRoot
    val card=compose.onNodeWithTag("search-video-$index").fetchSemanticsNode().boundsInRoot
    org.junit.Assert.assertTrue("Focused card is clipped: $card in $grid",card.top >= grid.top-8 && card.bottom <= grid.bottom+8)
    compose.onNode(hasTestTag("video-title") and hasAnyAncestor(hasTestTag("search-video-$index")),useUnmergedTree=true).assertIsDisplayed()
    compose.onNode(hasTestTag("video-publication") and hasAnyAncestor(hasTestTag("search-video-$index")),useUnmergedTree=true).assertIsDisplayed()
   }
   wholeCard(3)
   val screenshot=i.uiAutomation.takeScreenshot()
   java.io.File(i.targetContext.getExternalFilesDir(null),"search-grid-0.13.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };screenshot.recycle()
   key(KeyEvent.KEYCODE_DPAD_DOWN);compose.onNodeWithTag("search-video-6").assertIsFocused();wholeCard(6)
   key(KeyEvent.KEYCODE_DPAD_UP);compose.onNodeWithTag("search-video-3").assertIsFocused();wholeCard(3)
   key(KeyEvent.KEYCODE_DPAD_UP);compose.onNodeWithTag("search-video-0").assertIsFocused();wholeCard(0)
   compose.onAllNodesWithText("Save interest").assertCountEquals(0)
   compose.onAllNodes(hasText("Results for",substring=true)).assertCountEquals(0)
   key(KeyEvent.KEYCODE_DPAD_UP);compose.onNodeWithTag("keyboard-space").assertIsFocused()
   focus("nav-history");key(KeyEvent.KEYCODE_DPAD_RIGHT)
   compose.waitUntil(15_000) { compose.onAllNodesWithTag("browse-video-0").fetchSemanticsNodes().isNotEmpty() }
   compose.onNodeWithTag("browse-video-0").assertIsFocused()
   key(KeyEvent.KEYCODE_DPAD_RIGHT);compose.onNodeWithTag("browse-video-1").assertIsFocused()
  } finally { i.runOnMainSync { a.finish() } }
 }
}
