package app.mytube.tv

import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType

data class AudioLanguage(val id: String,val label: String,val language: String?,val original: Boolean)
internal fun audioIdentity(stream: AudioStream): String = stream.audioTrackId?.takeIf { it.isNotBlank() }
    ?: "${stream.audioLocale?.toLanguageTag().orEmpty()}:${stream.audioTrackType}"
internal fun audioLanguages(streams: List<AudioStream>): List<AudioLanguage> = streams.distinctBy(::audioIdentity).map { stream ->
    val original = stream.audioTrackType == AudioTrackType.ORIGINAL
    AudioLanguage(audioIdentity(stream),stream.audioTrackName?.takeIf { it.isNotBlank() }
        ?: stream.audioLocale?.getDisplayLanguage(stream.audioLocale)?.takeIf { it.isNotBlank() } ?: "Audio",stream.audioLocale?.toLanguageTag(),original)
}.sortedByDescending { it.original }
internal fun chooseAudio(streams: List<AudioStream>,selected: String?): AudioStream? {
    val preferred = streams.filter { audioIdentity(it) == selected }.ifEmpty {
        streams.filter { it.audioTrackType == AudioTrackType.ORIGINAL }.ifEmpty { streams }
    }
    return preferred.maxWithOrNull(compareBy({ it.format == org.schabi.newpipe.extractor.MediaFormat.M4A },{ it.averageBitrate }))
}
