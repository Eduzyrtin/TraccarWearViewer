package io.github.eduzyrtin.traccarwear.viewer.ui

import android.text.format.DateUtils
import androidx.annotation.StringRes
import io.github.eduzyrtin.traccarwear.viewer.R
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

// Traccar envía las fechas como "2026-10-13T08:12:34.000+00:00"
fun parseTime(value: String?): Long? = value?.let {
    try {
        OffsetDateTime.parse(it).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}

fun timeAgo(time: Long?): CharSequence = time?.let {
    DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE)
} ?: "—"

@StringRes
fun statusLabel(status: String?): Int = when (status) {
    "online" -> R.string.status_online
    "offline" -> R.string.status_offline
    else -> R.string.status_unknown
}

fun knotsToKmh(knots: Double): Int = (knots * 1.852).roundToInt()
