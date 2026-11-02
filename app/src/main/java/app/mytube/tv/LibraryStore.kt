package app.mytube.tv

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.asStateFlow

/** SQLite owns durable history/subscriptions. Only metadata is saved, never signed playback URLs. */
class LibraryStore(context: Context, name: String = "mytube.db") : SQLiteOpenHelper(context, name, null, 5) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE videos(url TEXT PRIMARY KEY,title TEXT NOT NULL,channel TEXT NOT NULL,channel_url TEXT NOT NULL,thumbnail TEXT NOT NULL,duration INTEGER NOT NULL,views INTEGER NOT NULL,watched_at INTEGER NOT NULL,position INTEGER NOT NULL,hidden INTEGER NOT NULL DEFAULT 0,saved_seed INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX watched_at_idx ON videos(watched_at DESC)")
        db.execSQL("CREATE TABLE subscriptions(url TEXT PRIMARY KEY,name TEXT NOT NULL)")
        createInterests(db)
        addPublicationAndRating(db)
        createSearchHistory(db)
        addCompletion(db)
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if(oldVersion < 2) {
            db.execSQL("ALTER TABLE videos ADD COLUMN saved_seed INTEGER NOT NULL DEFAULT 0")
            createInterests(db)
        }
        if(oldVersion < 3) addPublicationAndRating(db)
        if(oldVersion < 4) createSearchHistory(db)
        if(oldVersion < 5) addCompletion(db)
    }
    private val revision = kotlinx.coroutines.flow.MutableStateFlow(0L)
    val progressRevision = revision.asStateFlow()
    private fun addCompletion(db: SQLiteDatabase) { db.execSQL("ALTER TABLE videos ADD COLUMN completed INTEGER NOT NULL DEFAULT 0") }
    @Synchronized fun watchProgress(urls: Collection<String>): WatchProgressSnapshot {
        val records = urls.distinct().associateWith { null as WatchProgress? }.toMutableMap()
        // Android's oldest supported SQLite versions allow 999 bound parameters.
        for(batch in records.keys.toList().chunked(999)) {
            val placeholders = batch.joinToString(",") { "?" }
            readableDatabase.rawQuery("SELECT url,position,duration,completed,watched_at FROM videos WHERE url IN ($placeholders)",batch.toTypedArray()).use { c ->
                while(c.moveToNext()) records[c.getString(0)] = WatchProgress(c.getLong(1),c.getLong(2),c.getInt(3) != 0,c.getLong(4))
            }
        }
        return WatchProgressSnapshot(records)
    }
    @Synchronized fun withProgress(videos: List<Video>): List<Video> = watchProgress(videos.map { it.url }).apply(videos)
    @Synchronized fun resumePosition(url: String): Long = readableDatabase.rawQuery("SELECT position,completed FROM videos WHERE url=?",arrayOf(url)).use {
        if(it.moveToFirst() && it.getInt(1) == 0) it.getLong(0) else 0
    }
    private fun createSearchHistory(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE search_history(id TEXT PRIMARY KEY,query TEXT NOT NULL,searched_at INTEGER NOT NULL)")
    }
    @Synchronized fun searchHistory(): List<String> = readableDatabase.rawQuery(
        "SELECT query FROM search_history ORDER BY searched_at DESC,rowid DESC LIMIT 20", null
    ).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
    @Synchronized fun rememberSearch(input: String) {
        val query = input.trim().replace(Regex("\\s+"), " ")
        if(query.isBlank() || VideoUrls.canonical(query) != null) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertWithOnConflict("search_history", null, ContentValues().apply {
                put("id", query.lowercase(java.util.Locale.ROOT)); put("query", query); put("searched_at", System.currentTimeMillis())
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.execSQL("DELETE FROM search_history WHERE id NOT IN (SELECT id FROM search_history ORDER BY searched_at DESC,rowid DESC LIMIT 20)")
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun clearSearchHistory() { writableDatabase.delete("search_history", null, null) }
    private fun addPublicationAndRating(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE videos ADD COLUMN published_at INTEGER")
        db.execSQL("ALTER TABLE videos ADD COLUMN published_text TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE videos ADD COLUMN published_approximate INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE videos ADD COLUMN rating INTEGER NOT NULL DEFAULT 0")
    }
    @Synchronized fun likedVideos(): List<Video> = readableDatabase.rawQuery("SELECT * FROM videos WHERE rating=1 AND hidden=0 ORDER BY watched_at DESC", null).use { c ->
        buildList { while(c.moveToNext()) add(c.video()) }
    }
    @Synchronized fun rating(url: String): Int = readableDatabase.rawQuery("SELECT rating FROM videos WHERE url=?", arrayOf(url)).use { if(it.moveToFirst()) it.getInt(0) else 0 }
    @Synchronized fun setRating(video: Video, rating: Int) {
        require(rating in -1..1)
        val values = values(video).apply { put("rating", rating); put("hidden", if(rating == -1) 1 else 0); if(rating == -1) put("saved_seed", 0) }
        if(writableDatabase.update("videos", values, "url=?", arrayOf(video.url)) == 0) {
            values.put("watched_at", 0); values.put("position", 0)
            writableDatabase.insertOrThrow("videos", null, values)
        }
    }
    private fun createInterests(db: SQLiteDatabase) { db.execSQL("CREATE TABLE interests(id TEXT PRIMARY KEY,label TEXT NOT NULL)") }
    @Synchronized fun interests(): List<Interest> = readableDatabase.rawQuery("SELECT label FROM interests ORDER BY label COLLATE NOCASE", null).use { c ->
        buildList { while(c.moveToNext()) Interest.fromInput(c.getString(0))?.let { add(it) } }
    }
    @Synchronized fun addInterest(input: String) {
        val interest = requireNotNull(Interest.fromInput(input)) { "Enter a topic or search phrase." }
        writableDatabase.insertWithOnConflict("interests", null, ContentValues().apply { put("id", interest.id); put("label", interest.label) }, SQLiteDatabase.CONFLICT_IGNORE)
    }
    @Synchronized fun removeInterest(id: String) { writableDatabase.delete("interests", "id=?", arrayOf(id)) }
    @Synchronized fun seeds(): List<Video> = readableDatabase.rawQuery("SELECT * FROM videos WHERE saved_seed=1 AND hidden=0 ORDER BY title COLLATE NOCASE", null).use { c ->
        buildList { while(c.moveToNext()) add(c.video()) }
    }
    @Synchronized fun setSeed(video: Video, saved: Boolean) {
        val values = values(video).apply { put("saved_seed", if(saved) 1 else 0); if(saved) { put("hidden", 0); if(rating(video.url) == -1) put("rating", 0) } }
        if(writableDatabase.update("videos", values, "url=?", arrayOf(video.url)) == 0 && saved) {
            values.put("watched_at", 0); values.put("position", 0)
            writableDatabase.insertOrThrow("videos", null, values)
        }
    }
    @Synchronized fun history(): List<Video> = readableDatabase.rawQuery("SELECT * FROM videos WHERE watched_at>0 AND hidden=0 ORDER BY watched_at DESC LIMIT 200", null).use { c ->
        buildList { while (c.moveToNext()) add(c.video()) }
    }
    @Synchronized fun progress(url: String): Long = readableDatabase.rawQuery("SELECT position FROM videos WHERE url=?", arrayOf(url)).use { if (it.moveToFirst()) it.getLong(0) else 0 }
    @Synchronized fun record(video: Video, positionMs: Long, completed: Boolean = false) {
        val values = values(video).apply { put("watched_at", System.currentTimeMillis()); put("position", positionMs.coerceAtLeast(0)); put("completed",if(completed) 1 else 0) }
        // An UPDATE preserves a prior 'not interested' selection, unlike REPLACE.
        if (writableDatabase.update("videos", values, "url=?", arrayOf(video.url)) == 0) writableDatabase.insertOrThrow("videos", null, values)
        revision.value += 1
    }
    @Synchronized fun hide(video: Video) {
        val values = values(video).apply { put("hidden", 1); put("saved_seed", 0) }
        if(writableDatabase.update("videos", values, "url=?", arrayOf(video.url)) == 0) {
            values.put("watched_at", video.watchedAt); values.put("position", video.positionMs)
            writableDatabase.insertOrThrow("videos", null, values)
        }
    }
    @Synchronized fun hidden(): Set<String> = readableDatabase.rawQuery("SELECT url FROM videos WHERE hidden=1", null).use { c -> buildSet { while(c.moveToNext()) add(c.getString(0)) } }
    @Synchronized fun subscriptions(): List<Subscription> = readableDatabase.rawQuery("SELECT url,name FROM subscriptions ORDER BY name COLLATE NOCASE", null).use { c -> buildList { while(c.moveToNext()) add(Subscription(c.getString(0), c.getString(1))) } }
    @Synchronized fun toggleSubscription(channel: Subscription) {
        require(channel.url.isNotBlank())
        if (writableDatabase.delete("subscriptions", "url=?", arrayOf(channel.url)) == 0)
            writableDatabase.insertOrThrow("subscriptions", null, ContentValues().apply { put("url", channel.url); put("name", channel.name) })
    }
    @Synchronized fun addPreferences(channels: List<Subscription>, topics: List<String>) {
        require(channels.all { it.url.isNotBlank() && it.name.isNotBlank() })
        require(topics.all { Interest.fromInput(it) != null })
        val db = writableDatabase
        db.beginTransaction()
        try {
            channels.forEach { channel ->
                db.insertWithOnConflict("subscriptions", null, ContentValues().apply { put("url", channel.url); put("name", channel.name) }, SQLiteDatabase.CONFLICT_IGNORE)
            }
            topics.forEach(::addInterest)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun clearHistory() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE videos SET watched_at=0,position=0,completed=0")
            db.delete("videos", "hidden=0 AND saved_seed=0 AND rating=0", null)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        revision.value += 1
    }
    @Synchronized fun resetRecommendations() { writableDatabase.delete("videos", "hidden=1", null) }
    private fun values(v: Video) = ContentValues().apply {
        put("url", v.url); put("title", v.title); put("channel", v.channel); put("channel_url", v.channelUrl)
        put("thumbnail", v.thumbnail); put("duration", v.durationSeconds); put("views", v.views)
        if(v.publishedAtMs != null) { put("published_at", v.publishedAtMs); put("published_approximate", if(v.publishedApproximate) 1 else 0) }
        if(v.publishedText.isNotBlank()) put("published_text", v.publishedText)
    }
    private fun Cursor.video() = Video(getString(getColumnIndexOrThrow("url")), getString(getColumnIndexOrThrow("title")),
        getString(getColumnIndexOrThrow("channel")), getString(getColumnIndexOrThrow("channel_url")), getString(getColumnIndexOrThrow("thumbnail")),
        getLong(getColumnIndexOrThrow("duration")), getLong(getColumnIndexOrThrow("views")), getLong(getColumnIndexOrThrow("watched_at")),
        getLong(getColumnIndexOrThrow("position")), getInt(getColumnIndexOrThrow("hidden")) != 0,
        if(isNull(getColumnIndexOrThrow("published_at"))) null else getLong(getColumnIndexOrThrow("published_at")),
        getString(getColumnIndexOrThrow("published_text")), getInt(getColumnIndexOrThrow("published_approximate")) != 0,completed = getInt(getColumnIndexOrThrow("completed")) != 0)
}
