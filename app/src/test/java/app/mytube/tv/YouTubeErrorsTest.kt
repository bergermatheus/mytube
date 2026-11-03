package app.mytube.tv

import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.exceptions.*

class YouTubeErrorsTest {
 @Test fun distinguishesVpnRejectionFromCompatibilityFailure() {
  val vpn=readableError(ContentNotAvailableException("Got error UNPLAYABLE: \"Unavailable with a VPN or proxy\""))
  assertTrue(vpn.contains("VPN or proxy"));assertFalse(vpn.contains("extractor"))
  assertTrue(readableError(ParsingException("changed response")).contains("extractor compatibility"))
 }
 @Test fun handlesWrappedRestrictionsAndTypedBotChecks() {
  assertTrue(readableError(ExtractionException("outer",GeographicRestrictionException("country"))).contains("routed through"))
  assertTrue(readableError(SignInConfirmNotBotException("verification")).contains("verify requests"))
 }
 @Test fun unavailableContentDoesNotClaimAnExtractorUpdateIsRequired() {
  assertFalse(readableError(ContentNotAvailableException("removed")).contains("extractor"))
  assertEquals("Couldn't load this content. Please try again.",readableError(IllegalStateException("internal detail")))
 }
}
