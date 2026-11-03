package app.mytube.tv

import java.io.IOException
import org.schabi.newpipe.extractor.exceptions.*

/** Exception identity survives optimization; class display names do not. */
fun readableError(error: Throwable): String = when {
    error is ReCaptchaException || error is SignInConfirmNotBotException ->
        "YouTube is asking this network to verify requests. Try again later or use another network."
    error is GeographicRestrictionException ->
        "YouTube says this video is unavailable in your country. If using a VPN, check that MyTube is routed through it."
    error is ContentNotAvailableException && error.message.orEmpty().contains("VPN or proxy",ignoreCase = true) ->
        "YouTube rejected this VPN or proxy connection. Try another VPN server, or turn off the VPN if this video is available in your country."
    error is AgeRestrictedContentException ->
        "YouTube requires age verification for this video. MyTube cannot open it without an account."
    error is PrivateContentException -> "This YouTube video is private."
    error is PaidContentException -> "This video requires paid access or channel membership on YouTube."
    error is IOException -> "Couldn't reach YouTube. Check your internet connection and try again."
    error.cause != null && error.cause !== error -> readableError(error.cause!!)
    error is ContentNotAvailableException -> "YouTube reports that this video is unavailable. Try another video or try again later."
    error is ParsingException ->
        "MyTube couldn't read YouTube's response. Try again; extractor compatibility may need an update."
    else -> "Couldn't load this content. Please try again."
}
