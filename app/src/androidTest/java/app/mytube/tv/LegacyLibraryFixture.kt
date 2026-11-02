package app.mytube.tv

import android.content.Context

/** Frozen historical schema 3/4, independent of today's SQLiteOpenHelper.onCreate. */
internal fun createLegacyLibrary(context: Context,name: String,version: Int) {
    require(version == 3 || version == 4)
    context.deleteDatabase(name)
    context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).use { db ->
        db.execSQL("CREATE TABLE videos(url TEXT PRIMARY KEY,title TEXT NOT NULL,channel TEXT NOT NULL,channel_url TEXT NOT NULL,thumbnail TEXT NOT NULL,duration INTEGER NOT NULL,views INTEGER NOT NULL,watched_at INTEGER NOT NULL,position INTEGER NOT NULL,hidden INTEGER NOT NULL DEFAULT 0,saved_seed INTEGER NOT NULL DEFAULT 0,published_at INTEGER,published_text TEXT NOT NULL DEFAULT '',published_approximate INTEGER NOT NULL DEFAULT 0,rating INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX watched_at_idx ON videos(watched_at DESC)")
        db.execSQL("CREATE TABLE subscriptions(url TEXT PRIMARY KEY,name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE interests(id TEXT PRIMARY KEY,label TEXT NOT NULL)")
        if(version == 4) db.execSQL("CREATE TABLE search_history(id TEXT PRIMARY KEY,query TEXT NOT NULL,searched_at INTEGER NOT NULL)")
        db.version = version
    }
}
