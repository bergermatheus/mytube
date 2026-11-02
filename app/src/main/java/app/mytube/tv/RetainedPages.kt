package app.mytube.tv

import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout

/** Keeps visited page compositions, but measures/places only the active page.
 * Unplaced pages cannot draw, receive focus, or appear in accessibility. The fixed set of
 * routes bounds retention; Channel reuses one slot. No bitmap or stale screenshot cache.
 */
@Composable internal fun <T> RetainedPages(active: T, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    val visited = remember { mutableStateListOf(active) }
    val entries = remember { mutableMapOf<T,FocusRequester>() }
    var focusedPage by remember { mutableStateOf<T?>(null) }
    val transferFocus = focusedPage != null && focusedPage != active
    LaunchedEffect(active) {
        // Sidebar focus stays in the sidebar. A page-owned transition (e.g. opening a channel
        // or Interests) must transfer its old descendant focus into the new visible page.
        if(transferFocus) { withFrameNanos { };if(focusedPage != active) entries.getValue(active).requestFocus() }
    }
    // Include a new destination in this composition without writing state during composition.
    val routes = if(active in visited) visited.toList() else visited.toList() + active
    SideEffect { if(active !in visited) visited.add(active) }
    Layout(modifier = modifier,content = {
        routes.forEach { route -> key(route) {
            val visible = route == active
            val entry = entries.getOrPut(route) { FocusRequester() }
            // Unplaced nodes retain their last bounds. Explicitly remove inactive semantics and
            // prevent focus entering their group, including explicit descendant requests.
            val visibility = if(visible) Modifier else Modifier.clearAndSetSemantics { }
            Box(Modifier.fillMaxSize().then(visibility).focusRequester(entry).onFocusChanged {
                if(it.hasFocus) focusedPage = route else if(focusedPage == route) focusedPage = null
            }.focusProperties {
                onEnter = { if(!visible) cancelFocusChange() }
            }.focusGroup()) { content(route) }
        } }
    }) { children,constraints ->
        val page = children[routes.indexOf(active)].measure(constraints)
        layout(page.width,page.height) { page.placeRelative(0,0) }
    }
}
