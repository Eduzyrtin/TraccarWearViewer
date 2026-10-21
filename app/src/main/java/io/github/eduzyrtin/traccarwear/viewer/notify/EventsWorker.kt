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
import io.github.eduzyrtin.traccarwear.viewer.data.NotificationMode
import io.github.eduzyrtin.traccarwear.viewer.data.ViewerRepository
import io.github.eduzyrtin.traccarwear.viewer.data.ViewerStore
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Avisos propios del reloj, según el modo elegido en Ajustes: siempre, o solo cuando el teléfono
 * no está cerca (con él cerca ya avisa la Traccar Manager oficial del móvil). Consulta los eventos del servidor de los tipos que el usuario
 * tiene configurados como notificación y descarta los de los dispositivos o tipos que se han desactivado
 * en el reloj. No necesita configurar nada en el servidor ni Firebase.
 */
class EventsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val mode = ViewerStore.notificationMode(context).first()
        if (mode == NotificationMode.OFF) return Result.success()
        val api = ViewerRepository.api(context) ?: return Result.success()
        val now = Instant.now()
        val lastCheck = ViewerStore.lastEventCheck(context)
        // En modo "lejos del móvil", con el teléfono cerca ya avisa la Traccar Manager oficial
        if (lastCheck == null || (mode == NotificationMode.AWAY_FROM_PHONE && PhoneProximity.isNearby(context))) {
            ViewerStore.setLastEventCheck(context, now.toEpochMilli())
            return Result.success()
        }
        // Con un intervalo largo no se pierden los eventos de entre medias, pero tampoco se avisa de cosas muy antiguas
        val lookback = maxOf(MIN_LOOKBACK, Duration.ofMinutes(2 * ViewerStore.refreshInterval(context).first().minutes))
        val from = maxOf(Instant.ofEpochMilli(lastCheck), now.minus(lookback))
        return try {
            val types = api.notificationRules().map { it.type }.toSet()
            if (types.isNotEmpty()) {
                val filter = ViewerStore.alertFilter(context).first()
                val devices = api.devices()
                val watched = devices.filterNot { filter.isDeviceMuted(it.id) }
                val events = if (watched.isEmpty()) {
                    emptyList()
                } else {
                    api.events(watched.map { it.id }, types, from, now).filter { filter.allows(it.deviceId, it.type) }
                }
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
        private val MIN_LOOKBACK = Duration.ofHours(1)
        // Android no deja repetir un trabajo periódico en menos de 15 minutos
        private const val MIN_PERIOD_MINUTES = 15L

        /** Programa la búsqueda con el intervalo elegido en Ajustes (nunca menos de 15 minutos). */
        suspend fun schedule(context: Context) {
            val minutes = ViewerStore.refreshInterval(context).first().minutes
            val request = PeriodicWorkRequestBuilder<EventsWorker>(maxOf(minutes, MIN_PERIOD_MINUTES), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
