package app.mytube.tv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.IOException
import java.net.URI
import java.util.UUID

data class LocalProfile(val name: String = "",val photo: String = "") {
    val displayName: String get() = name.ifBlank { "User" }
    val initial: String get() = displayName.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "U"
}

/** Owns local display identity and imported photos; it never creates a Google account. */
class ProfileStore(private val context: Context,preferencesName: String = "local-profile") {
    private val preferences = context.getSharedPreferences(preferencesName,Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(LocalProfile(preferences.getString("name","").orEmpty(),preferences.getString("photo","").orEmpty()))
    val state = mutable.asStateFlow()
    private val directory = File(context.filesDir,"profile-photos")
    @Synchronized fun saveName(name: String) = persist(mutable.value.copy(name = name.trim()))
    @Synchronized fun savePhotoUrl(input: String) {
        val url = input.trim()
        if(url.isNotEmpty()) {
            val parsed = try { URI(url) } catch(_: Exception) { null }
            require(parsed?.scheme == "https" && !parsed.host.isNullOrBlank() && parsed.userInfo == null) { "Use a public HTTPS image URL." }
        }
        replacePhoto(url)
    }
    @Synchronized fun importPhoto(uri: Uri) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a supported image file." }
        var sample = 1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample > 1024) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,options) }
            ?: throw IOException("Could not read the selected image.")
        directory.mkdirs()
        val file = File(directory,"${UUID.randomUUID()}.jpg")
        try {
            file.outputStream().use { if(!bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)) throw IOException("Could not save the photo.") }
            replacePhoto(file.toURI().toString())
        } catch(e: Exception) { file.delete();throw e }
        finally { bitmap.recycle() }
    }
    @Synchronized fun removePhoto() = replacePhoto("")
    private fun replacePhoto(photo: String) {
        val previous = mutable.value.photo
        persist(mutable.value.copy(photo = photo))
        val old = try { if(previous.startsWith("file:")) File(URI(previous)) else null } catch(_: Exception) { null }
        if(old?.parentFile == directory && previous != photo) old.delete()
    }
    private fun persist(profile: LocalProfile) {
        if(!preferences.edit().putString("name",profile.name).putString("photo",profile.photo).commit()) throw IOException("Could not save your profile.")
        mutable.value = profile
    }
}
