package app.mytube.tv

import com.grack.nanojson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper

class CommentAvatarCompatibilityTest {
    @Test fun newerAuthorAvatarIsReadableByThePinnedDecoder() {
        val body = """{"frameworkUpdates":{"entityBatchUpdate":{"mutations":[{"payload":{"commentEntityPayload":{"author":{"displayName":"author","avatarThumbnailUrl":"https://yt3.ggpht.com/avatar=s88-c"}}}}]}}}"""
        val adapted = JsonParser.`object`().from(YouTubeCommentResponses.normalizeAvatars(body))
        val payload = adapted.getObject("frameworkUpdates").getObject("entityBatchUpdate").getArray("mutations").getObject(0).getObject("payload").getObject("commentEntityPayload")
        val images = YoutubeParsingHelper.getImagesFromThumbnailsArray(payload.getObject("avatar").getObject("image").getArray("sources"))
        assertEquals("https://yt3.ggpht.com/avatar=s88-c",images.single().url)
        assertEquals("author",payload.getObject("author").getString("displayName"))
    }
    @Test fun existingAvatarSourceRemainsUnchanged() {
        val body = """{"frameworkUpdates":{"entityBatchUpdate":{"mutations":[{"payload":{"commentEntityPayload":{"author":{"avatarThumbnailUrl":"https://yt3.ggpht.com/new"},"avatar":{"image":{"sources":[{"url":"https://yt3.ggpht.com/original","width":88,"height":88}]}}}}}]}}}"""
        assertEquals(body,YouTubeCommentResponses.normalizeAvatars(body))
    }
    @Test fun malformedOrUnrelatedResponsesRemainUnchanged() {
        assertEquals("{broken commentEntityPayload",YouTubeCommentResponses.normalizeAvatars("{broken commentEntityPayload"))
        assertEquals("{\"other\":true}",YouTubeCommentResponses.normalizeAvatars("{\"other\":true}"))
    }
}
