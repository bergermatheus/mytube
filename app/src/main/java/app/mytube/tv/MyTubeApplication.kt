package app.mytube.tv

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

class MyTubeApplication : Application() {
    val storageScope = CoroutineScope(SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    val profile by lazy { ProfileStore(this) }
    val library by lazy { LibraryStore(this) }
    val gateway by lazy { ExtractorGateway() }
    override fun onCreate() { super.onCreate(); ExtractorGateway.initialize() }
}
