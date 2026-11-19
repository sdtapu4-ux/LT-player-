package com.example.data.scanner

import android.content.Context
import android.net.Uri
import com.example.data.model.SubtitleCue
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.Charset

object SubtitleParser {

    fun loadFromUri(
        context: Context,
        uri: Uri,
        encoding: String = "UTF-8"
    ): List<SubtitleCue> {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return emptyList()
        val rawText = decodeBytes(rawBytes, encoding)
        if (rawText.isBlank()) return emptyList()
        val lowerName = uri.toString().lowercase()
        return parseAuto(rawText, lowerName)
    }

    fun decodeBytes(bytes: ByteArray, preferredEncoding: String = "UTF-8"): String {
        if (bytes.isEmpty()) return ""
        val detectedCharset = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte() -> Charsets.UTF_8
            else -> runCatching { Charset.forName(preferredEncoding) }.getOrDefault(Charsets.UTF_8)
        }
        return String(bytes, detectedCharset).removePrefix("\uFEFF")
    }

    fun parseAuto(rawText: String, fileNameOrUri: String = ""): List<SubtitleCue> {
        val clean = rawText.removePrefix("\uFEFF")
        val lower = fileNameOrUri.lowercase()
        return when {
            lower.endsWith(".vtt") || clean.trimStart().startsWith("WEBVTT") -> parseVtt(clean)
            lower.endsWith(".ass") || lower.endsWith(".ssa") || clean.contains("[Events]") -> parseAssSsa(clean)
            else -> parseSrt(clean)
        }
    }

    fun parseSrt(raw: String): List<SubtitleCue> {
        val normalized = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace("\r", "\n").trim()
        if (normalized.isEmpty()) return emptyList()
        val blocks = normalized.split(Regex("\n\\s*\n"))
        val cues = mutableListOf<SubtitleCue>()
        for (block in blocks) {
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            val timeLineIdx = lines.indexOfFirst { it.contains("-->") }
            if (timeLineIdx == -1) continue
            val times = lines[timeLineIdx].split("-->")
            if (times.size < 2) continue
            val startMs = parseTimestampMs(times[0].trim())
            val endMs = parseTimestampMs(times[1].trim().split(" ").first())
            val text = lines.drop(timeLineIdx + 1).joinToString("\n")
                .replace(Regex("<[^>]+>"), "")
                .trim()
            if (startMs >= 0L && endMs > startMs && text.isNotEmpty()) {
                cues.add(SubtitleCue(startMs, endMs, text))
            }
        }
        return cues.sortedBy { it.startMs }
    }

    fun parseVtt(raw: String): List<SubtitleCue> {
        val cleaned = raw.replace("\r\n", "\n")
            .replace("\r", "\n")
            .removePrefix("\uFEFF")
            .replace(Regex("^WEBVTT.*?\n\n", RegexOption.DOT_MATCHES_ALL), "")
        return parseSrt(cleaned)
    }

    fun parseAssSsa(raw: String): List<SubtitleCue> {
        val lines = raw.replace("\r\n", "\n").lines()
        val cues = mutableListOf<SubtitleCue>()
        var formatFields: List<String> = emptyList()
        var inEvents = false
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.equals("[Events]", ignoreCase = true)) {
                inEvents = true
                continue
            }
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                inEvents = false
                continue
            }
            if (!inEvents) continue
            if (trimmed.startsWith("Format:", ignoreCase = true)) {
                formatFields = trimmed.substringAfter(":").split(",").map { it.trim().lowercase() }
            } else if (trimmed.startsWith("Dialogue:", ignoreCase = true)) {
                val payload = trimmed.substringAfter(":").trimStart()
                val startIdx = formatFields.indexOf("start").takeIf { it >= 0 } ?: 1
                val endIdx = formatFields.indexOf("end").takeIf { it >= 0 } ?: 2
                val textIdx = formatFields.indexOf("text").takeIf { it >= 0 } ?: 9
                val parts = payload.split(",", limit = textIdx + 1)
                if (parts.size > textIdx) {
                    val startMs = parseAssTimestampMs(parts[startIdx].trim())
                    val endMs = parseAssTimestampMs(parts[endIdx].trim())
                    val text = parts[textIdx]
                        .replace("\\N", "\n")
                        .replace("\\n", "\n")
                        .replace(Regex("\\{[^}]*\\}"), "")
                        .trim()
                    if (startMs >= 0L && endMs > startMs && text.isNotEmpty()) {
                        cues.add(SubtitleCue(startMs, endMs, text))
                    }
                }
            }
        }
        return cues.sortedBy { it.startMs }
    }

    fun findCueAt(cues: List<SubtitleCue>, positionMs: Long, delayMs: Long): SubtitleCue? {
        if (cues.isEmpty()) return null
        val adjusted = positionMs - delayMs
        return cues.firstOrNull { adjusted in it.startMs..it.endMs }
    }

    private fun parseTimestampMs(raw: String): Long {
        val clean = raw.replace(",", ".").trim()
        val parts = clean.split(":")
        return try {
            when (parts.size) {
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val secParts = parts[2].split(".")
                    val s = secParts[0].toLong()
                    val ms = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
                    ((h * 3600) + (m * 60) + s) * 1000L + ms
                }
                2 -> {
                    val m = parts[0].toLong()
                    val secParts = parts[1].split(".")
                    val s = secParts[0].toLong()
                    val ms = secParts.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toLong() ?: 0L
                    ((m * 60) + s) * 1000L + ms
                }
                else -> -1L
            }
        } catch (_: Exception) {
            -1L
        }
    }

    private fun parseAssTimestampMs(raw: String): Long {
        val parts = raw.split(":")
        if (parts.size != 3) return -1L
        return try {
            val h = parts[0].toLong()
            val m = parts[1].toLong()
            val secParts = parts[2].split(".")
            val s = secParts[0].toLong()
            val cs = secParts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLong() ?: 0L
            ((h * 3600) + (m * 60) + s) * 1000L + cs * 10L
        } catch (_: Exception) {
            -1L
        }
    }
}
