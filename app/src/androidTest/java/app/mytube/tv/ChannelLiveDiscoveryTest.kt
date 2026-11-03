package app.mytube.tv

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ChannelLiveDiscoveryTest {
 @Test fun requestedVideoHasPlayableStreams() = runBlocking {
  val args=InstrumentationRegistry.getArguments()
  assumeTrue(args.getString("livePlayback")=="true")
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MyTubeApplication
  try {
   val info=app.gateway.details(requireNotNull(args.getString("videoUrl")),true)
   assertTrue("No playable stream metadata",info.videoStreams.isNotEmpty() || info.videoOnlyStreams.isNotEmpty() || !info.hlsUrl.isNullOrBlank())
   println("PLAYBACK_METADATA_OK ${info.name}")
  } catch(e: Exception) {
   println("PLAYBACK_METADATA_ERROR ${e.javaClass.simpleName}: ${readableError(e)}; upstream=${e.message}")
   throw e
  }
 }
 @Test fun requestedBroadcastAppearsInCorrectFeed() = runBlocking {
  val args=InstrumentationRegistry.getArguments()
  assumeTrue(args.getString("liveTests")=="true")
  val channelUrl=requireNotNull(args.getString("channelUrl"))
  val videoUrl=requireNotNull(args.getString("videoUrl"))
  val expectedLive=args.getString("expectedLive")?.toBooleanStrict() ?: true
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MyTubeApplication
  val channelVideos=app.gateway.channelCandidates(channelUrl)
  channelVideos.forEach { println("CHANNEL ${it.url} | live=${it.isLive} | ${it.title}") }
  val live=app.gateway.channelLiveCandidates(channelUrl)
  live.forEach { println("LIVE ${it.url} | ${it.title}") }
  val requested=channelVideos.firstOrNull { it.url==videoUrl }
  assertNotNull("Requested broadcast missing from channel",requested)
  assertEquals("Broadcast state differs from the supplied current fixture state",expectedLive,requested!!.isLive)
  assertEquals("Live feed must include active streams and exclude completed broadcasts",expectedLive,live.any { it.url==videoUrl })
 }
}
