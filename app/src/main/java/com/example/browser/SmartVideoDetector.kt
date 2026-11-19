package com.example.browser

import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object SmartVideoDetector {

    private val blockedExtensions = setOf("m3u8", "mpd", "ism", "wvm", "f4m")
    private val blockedSchemes = setOf("blob", "data", "file", "content", "javascript")
    private val blockedManifestMimes = setOf(
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl",
        "application/dash+xml",
        "application/vnd.ms-sstr+xml"
    )
    private val mediaExtensions = setOf(
        "mp4", "mkv", "webm", "mov", "m4v", "3gp", "avi", "flv", "ts",
        "mp3", "aac", "flac", "wav", "ogg", "opus", "m4a",
        "pdf", "zip"
    )

    val quickDials = listOf(
        BrowserQuickDial("Google", "Web search & discovery", "https://www.google.com"),
        BrowserQuickDial("Internet Archive", "Open public-domain movies & audio", "https://archive.org/details/movies"),
        BrowserQuickDial("Wikimedia Commons", "Free media files & clips", "https://commons.wikimedia.org"),
        BrowserQuickDial("Pexels Videos", "Free stock HD/4K videos", "https://www.pexels.com/videos/"),
        BrowserQuickDial("Pixabay Videos", "Royalty-free clips & music", "https://pixabay.com/videos/"),
        BrowserQuickDial("Wikipedia", "Encyclopedia", "https://www.wikipedia.org")
    )

    val DOM_MEDIA_SCAN_JS = """
        (function() {
            var urls = [];
            function add(u) {
                if (u && typeof u === 'string' && u.indexOf('https://') === 0 && urls.indexOf(u) === -1) {
                    urls.push(u);
                }
            }
            var mediaEls = document.querySelectorAll('video, audio, source');
            for (var i = 0; i < mediaEls.length; i++) {
                add(mediaEls[i].currentSrc || mediaEls[i].src || mediaEls[i].getAttribute('src'));
            }
            var links = document.querySelectorAll('a[href]');
            for (var j = 0; j < links.length && urls.length < 40; j++) {
                var h = links[j].href;
                if (/\.(mp4|mkv|webm|mov|m4v|3gp|mp3|aac|flac|wav|ogg|opus|m4a|pdf)(\?|#|$)/i.test(h)) {
                    add(h);
                }
            }
            return JSON.stringify(urls);
        })();
    """.trimIndent()

    private val probeClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun resolveNavigationTarget(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return "https://www.google.com"
        if (trimmed == BrowserTab.HOME_URL) return BrowserTab.HOME_URL

        val lower = trimmed.lowercase()
        if (lower.startsWith("https://")) return trimmed
        if (lower.startsWith("http://")) return "https://${trimmed.substring(7)}"

        val looksLikeDomain = !trimmed.contains(' ') &&
            trimmed.substringBefore('/').contains('.') &&
            trimmed.substringBefore('/').substringAfterLast('.').length >= 2
        if (looksLikeDomain) {
            return "https://$trimmed"
        }
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        return "https://www.google.com/search?q=$encoded"
    }

    fun isBlockedOrDrmUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return true
        val lower = trimmed.lowercase()
        val scheme = lower.substringBefore(':', "")
        if (scheme in blockedSchemes) return true
        val cleanPath = lower.substringBefore('?').substringBefore('#')
        val ext = cleanPath.substringAfterLast('.', "")
        if (ext in blockedExtensions) return true
        if (lower.contains("widevine") || lower.contains("fairplay") || lower.contains("playready")) {
            return true
        }
        return false
    }

    fun isDirectDownloadableMediaUrl(rawUrl: String): Boolean {
        val trimmed = rawUrl.trim()
        if (!trimmed.lowercase().startsWith("https://")) return false
        if (isBlockedOrDrmUrl(trimmed)) return false
        val cleanPath = trimmed.lowercase().substringBefore('?').substringBefore('#')
        val ext = cleanPath.substringAfterLast('.', "")
        return ext in mediaExtensions
    }

    fun parseDomScanResult(rawJsResult: String?): List<String> {
        if (rawJsResult.isNullOrBlank() || rawJsResult == "null") return emptyList()
        return runCatching {
            val outer = if (rawJsResult.startsWith("\"") && rawJsResult.endsWith("\"")) {
                Json.parseToJsonElement(rawJsResult).jsonPrimitive.content
            } else {
                rawJsResult
            }
            Json.parseToJsonElement(outer).jsonArray.mapNotNull { el ->
                el.jsonPrimitive.content.takeIf { isDirectDownloadableMediaUrl(it) }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    fun inferMimeType(url: String, headerMime: String? = null): String {
        val cleanHeader = headerMime?.substringBefore(';')?.trim()?.lowercase()
        if (!cleanHeader.isNullOrBlank() && cleanHeader != "application/octet-stream" && cleanHeader != "text/html") {
            return cleanHeader
        }
        val ext = url.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            "ts" -> "video/mp2t"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }

    fun inferQualityLabel(url: String, mimeType: String): String? {
        val lower = url.lowercase()
        return when {
            "2160p" in lower || "4k" in lower -> "4K"
            "1440p" in lower || "2k" in lower -> "2K"
            "1080p" in lower || "fullhd" in lower -> "1080p"
            "720p" in lower -> "720p"
            "480p" in lower -> "480p"
            "360p" in lower -> "360p"
            "240p" in lower -> "240p"
            mimeType.startsWith("audio/") -> "Audio"
            else -> null
        }
    }

    suspend fun probeMediaUrl(
        url: String,
        fallbackTitle: String? = null
    ): DetectedWebMedia? = withContext(Dispatchers.IO) {
        if (!url.lowercase().startsWith("https://") || isBlockedOrDrmUrl(url)) {
            return@withContext null
        }
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return@withContext null
        val guessedName = URLUtil.guessFileName(uri.toString(), null, null)
            .ifBlank { fallbackTitle?.takeIf { it.isNotBlank() } ?: "media_file" }

        val probeResult = runCatching {
            val headReq = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "LTPLAYER-Browser/1.0 (Android)")
                .build()
            probeClient.newCall(headReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val mime = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase().orEmpty()
                    if (mime in blockedManifestMimes || mime.startsWith("text/html")) {
                        return@runCatching null
                    }
                    val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                    val range = resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                    Triple(mime, len, range)
                } else {
                    // Fallback: 1-byte ranged GET if HEAD disallowed
                    val rangeReq = Request.Builder()
                        .url(url)
                        .get()
                        .header("Range", "bytes=0-0")
                        .header("User-Agent", "LTPLAYER-Browser/1.0 (Android)")
                        .build()
                    probeClient.newCall(rangeReq).execute().use { r2 ->
                        if (!r2.isSuccessful) return@runCatching null
                        val mime = r2.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase().orEmpty()
                        if (mime in blockedManifestMimes || mime.startsWith("text/html")) {
                            return@runCatching null
                        }
                        val total = r2.header("Content-Range")?.substringAfterLast('/', "")?.toLongOrNull()
                            ?: r2.header("Content-Length")?.toLongOrNull()
                            ?: -1L
                        val supportsRange = r2.code == 206 || r2.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                        Triple(mime, total, supportsRange)
                    }
                }
            }
        }.getOrNull()

        val resolvedMime = inferMimeType(url, probeResult?.first)
        if (resolvedMime.startsWith("text/html") || resolvedMime in blockedManifestMimes) {
            return@withContext null
        }

        DetectedWebMedia(
            url = url,
            title = guessedName,
            mimeType = resolvedMime,
            sizeBytes = probeResult?.second ?: -1L,
            qualityLabel = inferQualityLabel(url, resolvedMime),
            supportsRange = probeResult?.third ?: true
        )
    }
}
