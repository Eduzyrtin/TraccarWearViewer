package io.github.eduzyrtin.traccarwear.viewer

import android.app.Application
import io.github.eduzyrtin.traccarwear.viewer.data.NotificationMode
import io.github.eduzyrtin.traccarwear.viewer.data.ViewerStore
import io.github.eduzyrtin.traccarwear.viewer.map.TileLoader
import io.github.eduzyrtin.traccarwear.viewer.notify.EventNotifier
import io.github.eduzyrtin.traccarwear.viewer.notify.EventsWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ViewerApplication : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        EventNotifier.createChannel(this)
        TileLoader.init(this)
        scope.launch {
            val loggedIn = ViewerStore.account(this@ViewerApplication).first() != null
            if (loggedIn && ViewerStore.notificationMode(this@ViewerApplication).first() != NotificationMode.OFF) {
                EventsWorker.schedule(this@ViewerApplication)
            }
        }
    }
}
