package app.mytube.tv

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter

/** Adapts the newer author-avatar field to the schema consumed by the pinned extractor. */
internal object YouTubeCommentResponses {
    fun normalizeAvatars(body: String): String {
        if(!body.contains("commentEntityPayload")) return body
        val root = try { JsonParser.`object`().from(body) } catch(_: Exception) { return body }
        var changed = false
        val mutations = root.getObject("frameworkUpdates").getObject("entityBatchUpdate").getArray("mutations")
        for(value in mutations) {
            val payload = (value as? JsonObject)?.getObject("payload")?.getObject("commentEntityPayload") ?: continue
            val source = payload.getObject("author").getString("avatarThumbnailUrl").orEmpty()
            if(source.isBlank()) continue
            val avatar = payload.getObject("avatar")
            val image = avatar.getObject("image")
            if(image.getArray("sources").any { it is JsonObject && !it.getString("url").isNullOrBlank() }) continue
            image["sources"] = JsonArray().apply { add(JsonObject().apply { put("url",source) }) }
            avatar["image"] = image
            payload["avatar"] = avatar
            changed = true
        }
        return if(changed) JsonWriter.string(root) else body
    }
}
