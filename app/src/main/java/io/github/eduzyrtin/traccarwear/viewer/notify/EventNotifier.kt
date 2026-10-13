package io.github.eduzyrtin.traccarwear.viewer.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import io.github.eduzyrtin.traccarwear.viewer.MainActivity
import io.github.eduzyrtin.traccarwear.viewer.R
import io.github.eduzyrtin.traccarwear.viewer.api.Event
import io.github.eduzyrtin.traccarwear.viewer.ui.parseTime
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object EventNotifier {
    private const val CHANNEL_ID = "events"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_events),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission") // comprobado con areNotificationsEnabled
    fun show(context: Context, events: List<Event>, deviceNames: Map<Long, String>, geofenceNames: Map<Long, String>) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        events.forEach { event ->
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(deviceNames[event.deviceId] ?: context.getString(R.string.unknown_device))
                .setContentText(describe(context, event, geofenceNames))
                .setWhen(parseTime(event.eventTime) ?: System.currentTimeMillis())
                .setShowWhen(true)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            manager.notify(event.id.toInt(), notification)
        }
    }

    private fun describe(context: Context, event: Event, geofenceNames: Map<Long, String>): String {
        val geofence = geofenceNames[event.geofenceId] ?: context.getString(R.string.event_geofence_fallback)
        return when (event.type) {
            "deviceOnline" -> context.getString(R.string.event_device_online)
            "deviceOffline" -> context.getString(R.string.event_device_offline)
            "deviceUnknown" -> context.getString(R.string.event_device_unknown)
            "deviceMoving" -> context.getString(R.string.event_device_moving)
            "deviceStopped" -> context.getString(R.string.event_device_stopped)
            "deviceOverspeed" -> context.getString(R.string.event_device_overspeed)
            "deviceFuelDrop" -> context.getString(R.string.event_fuel_drop)
            "deviceFuelIncrease" -> context.getString(R.string.event_fuel_increase)
            "geofenceEnter" -> context.getString(R.string.event_geofence_enter, geofence)
            "geofenceExit" -> context.getString(R.string.event_geofence_exit, geofence)
            "ignitionOn" -> context.getString(R.string.event_ignition_on)
            "ignitionOff" -> context.getString(R.string.event_ignition_off)
            "maintenance" -> context.getString(R.string.event_maintenance)
            "textMessage" -> context.getString(R.string.event_text_message)
            "driverChanged" -> context.getString(R.string.event_driver_changed)
            "alarm" -> context.getString(
                R.string.event_alarm,
                (event.attributes["alarm"] as? JsonPrimitive)?.contentOrNull.orEmpty(),
            )
            else -> event.type
        }
    }
}
