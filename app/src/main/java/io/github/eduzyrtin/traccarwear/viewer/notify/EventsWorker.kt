package io.github.eduzyrtin.traccarwear.viewer.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.eduzyrtin.traccarwear.viewer.PhoneProximity
import io.github.eduzyrtin.traccarwear.viewer.api.UnauthorizedException
import io.github.eduzyrtin.traccarwear.viewer.data.ViewerRepository
import io.github.eduzyrtin.traccarwear.viewer.data.ViewerStore
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Avisos propios del reloj, solo cuando el teléfono no está cerca (con él cerca ya avisa la
 * Traccar Manager oficial del móvil). Consulta los eventos del servidor de los tipos que el usuario
 * tiene configurados como notificación. No necesita configurar nada en el servidor ni Firebase.
 */
class EventsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!ViewerStore.notificationsEnabled(context).first()) return Result.success()
        val api = ViewerRepository.api(context) ?: return Result.success()
        val now = Instant.now()
        val lastCheck = ViewerStore.lastEventCheck(context)
        if (lastCheck == null || PhoneProximity.isNearby(context)) {
            ViewerStore.setLastEventCheck(context, now.toEpochMilli())
            return Result.success()
        }
        val from = maxOf(Instant.ofEpochMilli(lastCheck), now.minus(MAX_LOOKBACK))
        return try {
            val types = api.notificationRules().map { it.type }.toSet()
            if (types.isNotEmpty()) {
                val devices = api.devices()
                val events = if (devices.isEmpty()) emptyList() else api.events(devices.map { it.id }, types, from, now)
                val geofences = if (events.any { it.geofenceId != 0L }) {
                    api.geofences().associate { it.id to it.name }
                } else {
                    emptyMap()
                }
                EventNotifier.show(context, events, devices.associate { it.id to it.name }, geofences)
            }
            ViewerStore.setLastEventCheck(context, now.toEpochMilli())
            Result.success()
        } catch (_: UnauthorizedException) {
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "events"
        // Tras mucho tiempo sin red no tiene sentido avisar de cosas de hace horas
        private val MAX_LOOKBACK = Duration.ofHours(1)

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<EventsWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
