package io.github.eduzyrtin.traccarwear.viewer.data

enum class IntervalUnit(val minutes: Long) { MINUTES(1), HOURS(60), DAYS(24 * 60) }

/** Cada cuánto se refresca el tile y se buscan eventos: cualquier número de minutos, horas o días. */
data class RefreshInterval(val value: Long, val unit: IntervalUnit) {

    val minutes: Long get() = value * unit.minutes

    /** Mismo valor en otra unidad (conserva el número, como al cambiar la unidad en una hoja de cálculo). */
    fun withUnit(newUnit: IntervalUnit): RefreshInterval = of(value, newUnit)

    companion object {
        val DEFAULT = RefreshInterval(15, IntervalUnit.MINUTES)
        private const val MAX_MINUTES = 365L * 24 * 60

        fun of(value: Long, unit: IntervalUnit): RefreshInterval =
            RefreshInterval(value.coerceIn(1, MAX_MINUTES / unit.minutes), unit)
    }
}
