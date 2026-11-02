package app.mytube.tv

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*

internal fun navigationDrawable(screen: Screen): Int = when(screen) {
    Screen.SEARCH -> R.drawable.ic_nav_search
    Screen.HOME -> R.drawable.ic_nav_home
    Screen.SUBSCRIPTIONS,Screen.CHANNEL -> R.drawable.ic_nav_subscriptions
    Screen.HISTORY -> R.drawable.ic_nav_library
    Screen.LIVE -> R.drawable.ic_nav_live
    Screen.SETTINGS,Screen.INTERESTS -> R.drawable.ic_nav_settings
}

@Composable internal fun NavItem(screen: Screen,current: Screen,select: (Screen) -> Unit,modifier: Modifier = Modifier,expanded: Boolean = true,
    focusSelect: Boolean = true) {
    val selected = screen == current || screen == Screen.SUBSCRIPTIONS && current == Screen.CHANNEL
    Button(onClick = { select(screen) },modifier = modifier.fillMaxWidth().height(38.dp)
        .onFocusChanged { if(it.isFocused && focusSelect) select(screen) }.testTag("nav-${screen.name.lowercase()}"),
        colors = ButtonDefaults.colors(containerColor = if(selected) OffWhite else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if(selected) Background else androidx.compose.ui.graphics.Color.White,focusedContainerColor = OffWhite,focusedContentColor = Background),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp)),scale = ButtonDefaults.scale(focusedScale = 1f),
        contentPadding = PaddingValues(horizontal = 12.dp,vertical = 0.dp)) {
        Icon(painterResource(navigationDrawable(screen)),contentDescription = screen.title,modifier = Modifier.size(22.dp))
        if(expanded) { Spacer(Modifier.width(13.dp));Text(screen.title,fontSize = 15.sp,fontWeight = FontWeight.Medium) }
    }
}

@Composable internal fun Sidebar(current: Screen,expanded: Boolean,profile: LocalProfile,navFocus: Map<Screen,FocusRequester>,expand: () -> Unit,select: (Screen) -> Unit,enterContent: () -> Boolean) {
    Column(Modifier.width(if(expanded) 208.dp else 64.dp).fillMaxHeight()
        .testTag(if(expanded) "sidebar-expanded" else "sidebar-collapsed")
        .onPreviewKeyEvent { it.nativeKeyEvent.action == KeyEvent.ACTION_DOWN && it.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && enterContent() }
        .padding(horizontal = 6.dp,vertical = 10.dp),verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Button(onClick = { select(Screen.SETTINGS) },modifier = Modifier.fillMaxWidth().height(46.dp).testTag("sidebar-profile")
            .onFocusChanged { if(it.isFocused) expand() }.semantics { contentDescription = "Profile: ${profile.displayName}" },
            contentPadding = PaddingValues(horizontal = 8.dp,vertical = 0.dp),scale = ButtonDefaults.scale(focusedScale = 1f),
            colors = ButtonDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent,focusedContainerColor = Panel)) {
            ProfileCircle(profile,Modifier.size(32.dp))
            if(expanded) { Spacer(Modifier.width(12.dp));Text(profile.displayName,fontSize = 16.sp,fontWeight = FontWeight.SemiBold,maxLines = 1,overflow = TextOverflow.Ellipsis) }
        }
        Spacer(Modifier.height(8.dp))
        Screen.navigation.forEach { screen ->
            if(screen == Screen.LIVE) Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp,vertical = 10.dp).height(1.dp).background(Muted.copy(alpha = .45f)).testTag("sidebar-separator"))
            NavItem(screen,current,select,Modifier
                .focusRequester(navFocus.getValue(screen))
                .onFocusChanged { if(it.isFocused) expand() },expanded)
        }
        Spacer(Modifier.weight(1f))
        NavItem(Screen.SETTINGS,current,select,Modifier.focusRequester(navFocus.getValue(Screen.SETTINGS)).onFocusChanged { if(it.isFocused) expand() },expanded)
    }
}
