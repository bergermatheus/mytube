package app.mytube.tv

import android.content.res.ColorStateList
import android.widget.ProgressBar
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable fun LoadingSpinner(tag: String, description: String, modifier: Modifier = Modifier) {
    AndroidView(factory = { context -> ProgressBar(context).apply {
        isIndeterminate = true
        indeterminateTintList = ColorStateList.valueOf(android.graphics.Color.WHITE)
        isFocusable = false
    } }, modifier = modifier.size(36.dp).testTag(tag).semantics { contentDescription = description })
}
