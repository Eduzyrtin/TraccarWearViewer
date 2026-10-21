package io.github.eduzyrtin.traccarwear.viewer.notify

/**
 * Qué avisos propios del reloj se dan para cada dispositivo. Por defecto llegan todos (los de las
 * notificaciones del servidor); aquí solo se guardan las exclusiones: un dispositivo entero o
 * algunos tipos de evento.
 */
data class AlertFilter(private val disabled: Map<Long, Set<String>> = emptyMap()) {

    fun isDeviceMuted(deviceId: Long): Boolean = MUTED in disabled[deviceId].orEmpty()

    fun allows(deviceId: Long, type: String): Boolean {
        val off = disabled[deviceId] ?: return true
        return MUTED !in off && type !in off
    }

    fun disabledTypes(deviceId: Long): Set<String> = disabled[deviceId].orEmpty() - MUTED

    companion object {
        // Marca de "este dispositivo no avisa", guardada junto a los tipos desactivados
        const val MUTED = "*"
    }
}
