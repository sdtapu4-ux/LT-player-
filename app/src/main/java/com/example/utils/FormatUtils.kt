package com.example.utils

import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

object FormatUtils {

    fun formatDuration(durationMs: Long): String {
        if (durationMs <= 0L) return "00:00"
        val totalSeconds = durationMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
        val value = bytes / 1024.0.pow(digitGroups.toDouble())
        return if (digitGroups >= 2) {
            String.format(Locale.US, "%.1f %s", value, units[digitGroups])
        } else {
            String.format(Locale.US, "%.0f %s", value, units[digitGroups])
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return "0 KB/s"
        return "${formatFileSize(bytesPerSec)}/s"
    }

    fun formatEta(seconds: Long): String {
        if (seconds <= 0L) return "--"
        val mins = seconds / 60L
        val secs = seconds % 60L
        return if (mins > 0L) "${mins}m ${secs}s" else "${secs}s"
    }
}
