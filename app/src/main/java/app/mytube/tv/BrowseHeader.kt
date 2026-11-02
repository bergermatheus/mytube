package app.mytube.tv

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import java.util.Locale

internal val SearchSurface = Color(0xFF303037)

@Composable internal fun BrowseHeader(query: String, open: () -> Unit, submit: (String) -> Unit,
    enter: () -> Unit, searchFocus: FocusRequester) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp).padding(bottom = 8.dp)
        .onFocusChanged { if(it.hasFocus) enter() }, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        VoiceSearchButton(submit)
        SearchPill(query,open,Modifier.fillMaxWidth(.38f).widthIn(max = 340.dp).focusRequester(searchFocus))
        Spacer(Modifier.weight(1f))
        Row(Modifier.testTag("header-brand"),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Image(painterResource(R.drawable.ic_mytube_mark),contentDescription = null,modifier = Modifier.size(38.dp,27.dp))
            Text("MyTube",fontWeight = FontWeight.Bold,fontSize = 24.sp)
        }
    }
}

@Composable internal fun SearchPill(query: String, click: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = click, modifier = modifier.height(40.dp).testTag("search-input"),
        shape = ButtonDefaults.shape(shape = CircleShape),
        scale = ButtonDefaults.scale(focusedScale = 1f),
        colors = ButtonDefaults.colors(containerColor = SearchSurface, focusedContainerColor = OffWhite, focusedContentColor = Background),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 0.dp)) {
        Text(query.ifBlank { "Search" },Modifier.fillMaxWidth(),fontSize = 18.sp,fontWeight = FontWeight.SemiBold,
            maxLines = 1,overflow = TextOverflow.Ellipsis)
    }
}

@Composable internal fun VoiceSearchButton(result: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { reply ->
        if(reply.resultCode == Activity.RESULT_OK) reply.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull { it.isNotBlank() }?.let(result)
    }
    Button(onClick = {
        try { voice.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_PROMPT,"Search MyTube")) }
        catch(_: ActivityNotFoundException) { Toast.makeText(context,"Voice search is unavailable. Use the on-screen keyboard.",Toast.LENGTH_LONG).show() }
    }, modifier = modifier.size(40.dp).testTag("voice-search").semantics { contentDescription = "Voice search" },
        shape = ButtonDefaults.shape(shape = CircleShape), contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.colors(containerColor = SearchSurface, focusedContainerColor = OffWhite, focusedContentColor = Background)) {
        Box(Modifier.fillMaxSize(),contentAlignment = Alignment.Center) {
            val color = LocalContentColor.current
            Canvas(Modifier.size(23.dp)) {
                drawRoundRect(color,Offset(size.width*.38f,size.height*.08f),Size(size.width*.24f,size.height*.5f),androidx.compose.ui.geometry.CornerRadius(size.width*.12f))
                drawArc(color,0f,180f,false,Offset(size.width*.22f,size.height*.26f),Size(size.width*.56f,size.height*.48f),style = Stroke(2.dp.toPx()))
                drawLine(color,Offset(size.width*.5f,size.height*.74f),Offset(size.width*.5f,size.height*.9f),2.dp.toPx())
                drawLine(color,Offset(size.width*.33f,size.height*.91f),Offset(size.width*.67f,size.height*.91f),2.dp.toPx())
            }
        }
    }
}
