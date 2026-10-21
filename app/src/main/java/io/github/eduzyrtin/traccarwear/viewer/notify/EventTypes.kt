package io.github.eduzyrtin.traccarwear.viewer.notify

import androidx.annotation.StringRes
import io.github.eduzyrtin.traccarwear.viewer.R

/** Tipos de evento de Traccar, en el orden en que los ofrece su web al crear una notificación. */
enum class EventType(val id: String, @StringRes val label: Int) {
    COMMAND_RESULT("commandResult", R.string.event_type_command_result),
    DEVICE_ONLINE("deviceOnline", R.string.event_type_device_online),
    DEVICE_UNKNOWN("deviceUnknown", R.string.event_type_device_unknown),
    DEVICE_OFFLINE("deviceOffline", R.string.event_type_device_offline),
    DEVICE_INACTIVE("deviceInactive", R.string.event_type_device_inactive),
    QUEUED_COMMAND_SENT("queuedCommandSent", R.string.event_type_queued_command_sent),
    DEVICE_MOVING("deviceMoving", R.string.event_type_device_moving),
    DEVICE_STOPPED("deviceStopped", R.string.event_type_device_stopped),
    DEVICE_OVERSPEED("deviceOverspeed", R.string.event_type_device_overspeed),
    DEVICE_FUEL_DROP("deviceFuelDrop", R.string.event_type_device_fuel_drop),
    DEVICE_FUEL_INCREASE("deviceFuelIncrease", R.string.event_type_device_fuel_increase),
    GEOFENCE_ENTER("geofenceEnter", R.string.event_type_geofence_enter),
    GEOFENCE_EXIT("geofenceExit", R.string.event_type_geofence_exit),
    ALARM("alarm", R.string.event_type_alarm),
    IGNITION_ON("ignitionOn", R.string.event_type_ignition_on),
    IGNITION_OFF("ignitionOff", R.string.event_type_ignition_off),
    MAINTENANCE("maintenance", R.string.event_type_maintenance),
    TEXT_MESSAGE("textMessage", R.string.event_type_text_message),
    DRIVER_CHANGED("driverChanged", R.string.event_type_driver_changed),
    MEDIA("media", R.string.event_type_media);

    companion object {
        // Una notificación de este tipo en Traccar vale para cualquier evento
        const val ALL_EVENTS = "allEvents"

        fun fromId(id: String): EventType? = entries.find { it.id == id }

        /** Los tipos que el usuario tiene en sus notificaciones del servidor; con "todos los eventos", todos. */
        fun available(ruleTypes: Set<String>?): List<EventType> =
            if (ruleTypes == null || ALL_EVENTS in ruleTypes) entries else entries.filter { it.id in ruleTypes }
    }
}
