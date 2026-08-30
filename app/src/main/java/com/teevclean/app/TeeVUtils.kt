package com.teevclean.app

import java.util.Locale

/** Formats a byte count into a human-readable string (e.g., "1.2 GB"). */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(Locale.US, "%.1f %s", value, units[index])
}

/** Formats a duration in milliseconds to a human-readable string (e.g., "2 days, 3 hours"). */
fun formatDuration(milliseconds: Long): String {
    val totalHours = milliseconds.coerceAtLeast(0L) / 3_600_000
    val days = totalHours / 24
    val hours = totalHours % 24
    return if (days > 0) "$days days, $hours hours" else "$hours hours"
}
