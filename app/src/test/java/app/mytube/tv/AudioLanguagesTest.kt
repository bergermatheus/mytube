package app.mytube.tv
import org.junit.Test
import org.junit.Assert.*
import org.schabi.newpipe.extractor.stream.*
import org.schabi.newpipe.extractor.MediaFormat
import java.util.Locale
class AudioLanguagesTest {
    private fun track(id: String,type: AudioTrackType,bitrate: Int) = AudioStream.Builder().setId(id).setContent("https://example.org/$id",true)
        .setMediaFormat(MediaFormat.M4A).setAverageBitrate(bitrate).setAudioTrackId(id).setAudioTrackType(type).setAudioLocale(Locale.forLanguageTag(id)).build()
    @Test fun originalWinsOverHigherBitrateDubAndExplicitSelectionWinsOverOriginal() {
        val original=track("en",AudioTrackType.ORIGINAL,128);val dub=track("pt",AudioTrackType.DUBBED,256)
        assertSame(original,chooseAudio(listOf(dub,original),null))
        assertSame(dub,chooseAudio(listOf(dub,original),"pt"))
        assertSame(original,chooseAudio(listOf(dub,original),"missing"))
        assertTrue(audioLanguages(listOf(dub,original)).first().original)
    }
}
