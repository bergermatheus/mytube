package app.mytube.tv

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun ProfileCircle(profile: LocalProfile,modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(SearchSurface),contentAlignment = Alignment.Center) {
        @Composable fun Initial() { Box(Modifier.fillMaxSize(),contentAlignment = Alignment.Center) { Text(profile.initial,fontSize = 20.sp,fontWeight = FontWeight.SemiBold) } }
        if(profile.photo.isBlank()) Initial() else SubcomposeAsyncImage(profile.photo,contentDescription = "Profile photo",contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),loading = { Initial() },error = { Initial() })
    }
}

@Composable internal fun ProfileSettings(store: ProfileStore) {
    val profile by store.state.collectAsStateWithLifecycle()
    var name by remember(profile.name) { mutableStateOf(profile.name) }
    var photoUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    fun save(action: () -> Unit) {
        if(busy) return
        busy = true;message = null
        scope.launch {
            try { withContext(Dispatchers.IO) { action() }; message = "Profile saved" }
            catch(e: Exception) { if(e is kotlinx.coroutines.CancellationException) throw e;message = e.message ?: "Could not save profile." }
            finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri != null) save { store.importPhoto(uri) } }
    Column(Modifier.fillMaxWidth().testTag("profile-settings"),verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Profile",fontSize = 22.sp,fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ProfileCircle(profile,Modifier.size(56.dp))
            Text(profile.displayName,fontSize = 18.sp)
        }
        QueryInput(name,{ name = it },"Your name",Modifier.fillMaxWidth().testTag("profile-name")) { save { store.saveName(name) } }
        Action("Save name",!busy) { save { store.saveName(name) } }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Action("Choose photo",!busy) {
                try { picker.launch(arrayOf("image/*")) }
                catch(_: ActivityNotFoundException) { message = "No photo picker is installed. Paste an image URL below." }
            }
            if(profile.photo.isNotBlank()) Action("Remove photo",!busy) { save { store.removePhoto() } }
        }
        QueryInput(photoUrl,{ photoUrl = it },"Photo URL (https://…)",Modifier.fillMaxWidth().testTag("profile-photo-url")) { save { store.savePhotoUrl(photoUrl) } }
        Action("Use photo URL",!busy && photoUrl.isNotBlank()) { save { store.savePhotoUrl(photoUrl) } }
        if(busy) LoadingSpinner("profile-saving","Saving profile")
        message?.let { Text(it,color = Muted,fontSize = 13.sp) }
    }
}
