package app.mytube.tv

/** Live flags originate at the extractor; zero duration or a title containing 'live' is insufficient. */
object LiveFeed {
    fun select(videos: List<Video>,subscriptions: List<Subscription>,hidden: Set<String>): List<Video> {
        val followed = subscriptions.map { it.url }.toSet()
        return videos.filter { it.isLive && it.url !in hidden }.distinctBy { it.url }
            .sortedByDescending { it.channelUrl in followed }
    }
}
