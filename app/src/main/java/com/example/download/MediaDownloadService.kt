package com.example.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.StatFs
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.MainActivity
import com.example.R
import com.example.data.database.DownloadEntity
import com.example.data.database.LTPlayerDatabase
import com.example.data.model.DownloadMediaCategory
import com.example.data.model.DownloadStatus
import com.example.utils.FormatUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MediaDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val concurrencySemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)
    private val activeJobs = ConcurrentHashMap<Long, Job>()

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val downloadDao by lazy {
        LTPlayerDatabase.getInstance(applicationContext).downloadDao()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_SYNC_QUEUE
        val targetId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L

        when (action) {
            ACTION_PAUSE -> {
                if (targetId > 0L) {
                    DownloadLiveRegistry.markPaused(targetId)
                    serviceScope.launch {
                        downloadDao.getById(targetId)?.let { entity ->
                            downloadDao.update(entity.copy(status = DownloadStatus.PAUSED.name))
                        }
                        refreshForegroundNotification()
                    }
                }
            }
            ACTION_CANCEL -> {
                if (targetId > 0L) {
                    DownloadLiveRegistry.markCancelled(targetId)
                    activeJobs.remove(targetId)?.cancel()
                    serviceScope.launch {
                        partFileFor(this@MediaDownloadService, targetId).delete()
                        downloadDao.deleteById(targetId)
                        DownloadLiveRegistry.clearStats(targetId)
                        refreshForegroundNotification()
                    }
                }
            }
            ACTION_ENQUEUE, ACTION_RESUME, ACTION_SYNC_QUEUE -> {
                promoteToForeground("Preparing download queue...", 0)
                if (targetId > 0L) {
                    DownloadLiveRegistry.clearFlags(targetId)
                }
                scheduleQueuedDownloads()
            }
        }
        return START_NOT_STICKY
    }

    private fun scheduleQueuedDownloads() {
        serviceScope.launch {
            val active = downloadDao.getActiveDownloads()
            val runnable = active.filter {
                it.status == DownloadStatus.PENDING.name || it.status == DownloadStatus.RUNNING.name
            }
            if (runnable.isEmpty() && activeJobs.isEmpty()) {
                stopForegroundSafely()
                stopSelf()
                return@launch
            }
            for (entity in runnable) {
                if (activeJobs.containsKey(entity.id)) continue
                if (DownloadLiveRegistry.isPaused(entity.id) || DownloadLiveRegistry.isCancelled(entity.id)) continue
                val job = serviceScope.launch {
                    concurrencySemaphore.withPermit {
                        executeDownload(entity.id)
                    }
                }
                activeJobs[entity.id] = job
            }
        }
    }

    private suspend fun executeDownload(downloadId: Long) {
        try {
            var entity = downloadDao.getById(downloadId) ?: return
            if (DownloadLiveRegistry.isPaused(downloadId) || DownloadLiveRegistry.isCancelled(downloadId)) {
                return
            }

            if (!isNetworkAvailable()) {
                downloadDao.update(
                    entity.copy(
                        status = DownloadStatus.FAILED.name,
                        errorReason = "No active internet connection. Tap Retry when online."
                    )
                )
                return
            }

            val partDir = File(filesDir, "downloads_part").apply { mkdirs() }
            val partFile = File(partDir, "$downloadId.part")
            var existingBytes = if (partFile.exists()) partFile.length() else 0L

            entity = entity.copy(
                status = DownloadStatus.RUNNING.name,
                bytesDownloaded = existingBytes,
                errorReason = null
            )
            downloadDao.update(entity)

            var response = performGet(entity.url, existingBytes)
            if (response.code == 416 && existingBytes > 0L) {
                response.close()
                partFile.delete()
                existingBytes = 0L
                response = performGet(entity.url, 0L)
            }

            response.use { resp ->
                if (!resp.isSuccessful) {
                    downloadDao.update(
                        entity.copy(
                            status = DownloadStatus.FAILED.name,
                            errorReason = "HTTP ${resp.code}: ${resp.message.ifBlank { "Download error" }}"
                        )
                    )
                    return
                }

                val contentType = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()
                if (contentType != null && contentType in BLOCKED_MANIFEST_MIME_TYPES) {
                    partFile.delete()
                    downloadDao.update(
                        entity.copy(
                            status = DownloadStatus.FAILED.name,
                            errorReason = "Protected/adaptive stream manifests cannot be downloaded."
                        )
                    )
                    return
                }

                val isPartial = resp.code == 206
                if (!isPartial && existingBytes > 0L) {
                    // Server ignored Range header; restart cleanly from byte 0
                    partFile.delete()
                    existingBytes = 0L
                }

                val acceptRangesHeader = resp.header("Accept-Ranges")?.lowercase().orEmpty()
                val supportsRange = isPartial || acceptRangesHeader.contains("bytes")
                val body = resp.body
                if (body == null) {
                    downloadDao.update(
                        entity.copy(
                            status = DownloadStatus.FAILED.name,
                            errorReason = "Empty response body from server."
                        )
                    )
                    return
                }

                val contentLength = body.contentLength().coerceAtLeast(-1L)
                val totalBytes = when {
                    isPartial -> parseTotalFromContentRange(resp.header("Content-Range"))
                        ?: if (contentLength >= 0L) existingBytes + contentLength else entity.totalBytes
                    contentLength >= 0L -> contentLength
                    else -> entity.totalBytes
                }.coerceAtLeast(0L)

                val freeSpace = availableInternalBytes()
                val needed = if (totalBytes > existingBytes) totalBytes - existingBytes else 0L
                if (needed > 0L && freeSpace < needed + MIN_SAFETY_FREE_BYTES) {
                    downloadDao.update(
                        entity.copy(
                            status = DownloadStatus.FAILED.name,
                            errorReason = "Insufficient storage space (${FormatUtils.formatFileSize(freeSpace)} free)."
                        )
                    )
                    return
                }

                val resolvedMime = resolveMimeType(entity.title, contentType ?: entity.mimeType)
                val resolvedCategory = DownloadMediaCategory.classify(entity.title, resolvedMime)
                val updatedMeta = entity.copy(
                    totalBytes = totalBytes,
                    bytesDownloaded = existingBytes,
                    supportsRange = supportsRange,
                    etag = resp.header("ETag") ?: entity.etag,
                    mimeType = resolvedMime,
                    mediaCategory = resolvedCategory.name
                )
                downloadDao.update(updatedMeta)
                entity = updatedMeta

                val stream = body.byteStream()
                val buffer = ByteArray(32 * 1024)
                var downloaded = existingBytes
                var windowBytes = 0L
                var windowStartMs = SystemClock.elapsedRealtime()
                var lastDbFlushMs = windowStartMs
                var smoothedSpeed = 0L

                FileOutputStream(partFile, isPartial).use { out ->
                    while (true) {
                        if (DownloadLiveRegistry.isCancelled(downloadId)) {
                            partFile.delete()
                            downloadDao.deleteById(downloadId)
                            return
                        }
                        if (DownloadLiveRegistry.isPaused(downloadId)) {
                            out.flush()
                            downloadDao.update(
                                entity.copy(
                                    bytesDownloaded = downloaded,
                                    totalBytes = totalBytes.coerceAtLeast(downloaded),
                                    status = DownloadStatus.PAUSED.name
                                )
                            )
                            DownloadLiveRegistry.clearStats(downloadId)
                            return
                        }

                        val read = stream.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        windowBytes += read

                        val nowMs = SystemClock.elapsedRealtime()
                        val windowElapsed = nowMs - windowStartMs
                        if (windowElapsed >= 500L) {
                            val instantSpeed = (windowBytes * 1000L) / windowElapsed.coerceAtLeast(1L)
                            smoothedSpeed = if (smoothedSpeed == 0L) instantSpeed else (smoothedSpeed * 3 + instantSpeed) / 4
                            val effectiveTotal = totalBytes.coerceAtLeast(downloaded)
                            val eta = if (smoothedSpeed > 0L && effectiveTotal > downloaded) {
                                (effectiveTotal - downloaded) / smoothedSpeed
                            } else 0L
                            DownloadLiveRegistry.updateStats(downloadId, smoothedSpeed, eta)
                            windowBytes = 0L
                            windowStartMs = nowMs
                        }

                        if (nowMs - lastDbFlushMs >= 750L) {
                            lastDbFlushMs = nowMs
                            val effectiveTotal = totalBytes.coerceAtLeast(downloaded)
                            downloadDao.update(
                                entity.copy(
                                    bytesDownloaded = downloaded,
                                    totalBytes = effectiveTotal,
                                    status = DownloadStatus.RUNNING.name
                                )
                            )
                            val pct = if (effectiveTotal > 0L) ((downloaded * 100L) / effectiveTotal).toInt() else 0
                            promoteToForeground("Downloading ${entity.title}", pct)
                        }
                    }
                    out.flush()
                }

                // Transfer finished cleanly — move .part to final destination
                val finalSize = partFile.length()
                val finalized = finalizeToDestination(entity, partFile)
                partFile.delete()
                DownloadLiveRegistry.clearStats(downloadId)

                downloadDao.update(
                    entity.copy(
                        bytesDownloaded = finalSize,
                        totalBytes = finalSize,
                        destinationUri = finalized.destinationUri,
                        localPath = finalized.displayPath,
                        mimeType = resolvedMime,
                        mediaCategory = resolvedCategory.name,
                        status = DownloadStatus.SUCCESSFUL.name,
                        errorReason = null
                    )
                )
                postCompletionNotification(entity.title, finalized.destinationUri, resolvedMime)
            }
        } catch (t: Throwable) {
            DownloadLiveRegistry.clearStats(downloadId)
            if (DownloadLiveRegistry.isCancelled(downloadId)) {
                partFileFor(this, downloadId).delete()
                downloadDao.deleteById(downloadId)
            } else if (DownloadLiveRegistry.isPaused(downloadId)) {
                downloadDao.getById(downloadId)?.let { cur ->
                    val partialLen = partFileFor(this, downloadId).length()
                    downloadDao.update(cur.copy(bytesDownloaded = partialLen, status = DownloadStatus.PAUSED.name))
                }
            } else {
                downloadDao.getById(downloadId)?.let { cur ->
                    val partialLen = partFileFor(this, downloadId).length()
                    downloadDao.update(
                        cur.copy(
                            bytesDownloaded = partialLen,
                            status = DownloadStatus.FAILED.name,
                            errorReason = t.localizedMessage ?: "Network transfer interrupted"
                        )
                    )
                }
            }
        } finally {
            activeJobs.remove(downloadId)
            refreshForegroundNotification()
        }
    }

    private fun performGet(url: String, startOffset: Long) =
        httpClient.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", "LTPLAYER-Downloader/1.0 (Android)")
                .apply {
                    if (startOffset > 0L) {
                        header("Range", "bytes=$startOffset-")
                    }
                }
                .build()
        ).execute()

    private data class FinalizedOutput(
        val destinationUri: String,
        val displayPath: String
    )

    private fun finalizeToDestination(entity: DownloadEntity, partFile: File): FinalizedOutput {
        val safeName = entity.title.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank {
            "lt_download_${entity.id}"
        }
        val mime = entity.mimeType.ifBlank { "application/octet-stream" }

        // 1. Custom SAF Folder destination
        val safTree = entity.safTreeUri
        if (!safTree.isNullOrBlank()) {
            val safResult = runCatching {
                val treeUri = Uri.parse(safTree)
                val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
                val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
                val createdDocUri = DocumentsContract.createDocument(contentResolver, parentDocUri, mime, safeName)
                    ?: error("SAF folder denied write access")
                contentResolver.openOutputStream(createdDocUri)?.use { out ->
                    FileInputStream(partFile).use { input ->
                        input.copyTo(out, 32 * 1024)
                    }
                } ?: error("Failed to open SAF output stream")
                FinalizedOutput(
                    destinationUri = createdDocUri.toString(),
                    displayPath = "SAF Folder/$safeName"
                )
            }
            if (safResult.isSuccess) return safResult.getOrThrow()
        }

        // 2. Public Downloads/LTPlayer folder
        val wantsPublic = entity.localPath?.startsWith("Downloads/") == true
        if (wantsPublic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/LTPlayer")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        FileInputStream(partFile).use { input ->
                            input.copyTo(out, 32 * 1024)
                        }
                    }
                    val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
                    contentResolver.update(uri, done, null, null)
                    return FinalizedOutput(uri.toString(), "Downloads/LTPlayer/$safeName")
                }
            } else {
                @Suppress("DEPRECATION")
                val publicDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "LTPlayer"
                ).apply { mkdirs() }
                val destFile = uniqueTargetFile(publicDir, safeName)
                FileInputStream(partFile).use { input ->
                    FileOutputStream(destFile).use { out -> input.copyTo(out, 32 * 1024) }
                }
                MediaScannerConnection.scanFile(this, arrayOf(destFile.absolutePath), arrayOf(mime), null)
                val uri = runCatching {
                    FileProvider.getUriForFile(this, "${packageName}.fileprovider", destFile)
                }.getOrElse { Uri.fromFile(destFile) }
                return FinalizedOutput(uri.toString(), "Downloads/LTPlayer/${destFile.name}")
            }
        }

        // 3. App-private Downloads storage fallback
        val appDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(filesDir, "downloads").apply { mkdirs() }
        appDir.mkdirs()
        val appTarget = uniqueTargetFile(appDir, safeName)
        FileInputStream(partFile).use { input ->
            FileOutputStream(appTarget).use { out -> input.copyTo(out, 32 * 1024) }
        }
        val fileUri = runCatching {
            FileProvider.getUriForFile(this, "${packageName}.fileprovider", appTarget)
        }.getOrElse { Uri.fromFile(appTarget) }
        return FinalizedOutput(fileUri.toString(), "App Storage/${appTarget.name}")
    }

    private fun uniqueTargetFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast('.')
        val ext = fileName.substringAfterLast('.', "")
        var idx = 1
        while (candidate.exists()) {
            val nextName = if (ext.isNotEmpty()) "${base}_$idx.$ext" else "${base}_$idx"
            candidate = File(dir, nextName)
            idx++
        }
        return candidate
    }

    private fun parseTotalFromContentRange(header: String?): Long? {
        // Format: bytes 0-99/12345
        val slash = header?.substringAfterLast('/', "")?.trim() ?: return null
        return slash.toLongOrNull()
    }

    private fun resolveMimeType(fileName: String, rawMime: String?): String {
        if (!rawMime.isNullOrBlank() && rawMime != "application/octet-stream") {
            return rawMime
        }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext.isNotEmpty()) {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        }
        return when (ext) {
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mp4", "m4v" -> "video/mp4"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            else -> "application/octet-stream"
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun availableInternalBytes(): Long {
        return runCatching {
            val stat = StatFs(filesDir.absolutePath)
            stat.availableBytes
        }.getOrDefault(Long.MAX_VALUE)
    }

    private suspend fun refreshForegroundNotification() {
        val remaining = downloadDao.getActiveDownloads().filter {
            it.status == DownloadStatus.RUNNING.name || it.status == DownloadStatus.PENDING.name
        }
        if (remaining.isEmpty() && activeJobs.isEmpty()) {
            stopForegroundSafely()
            stopSelf()
        } else if (remaining.isNotEmpty()) {
            val first = remaining.first()
            val pct = if (first.totalBytes > 0L) ((first.bytesDownloaded * 100L) / first.totalBytes).toInt() else 0
            promoteToForeground("${remaining.size} active download(s)", pct)
        }
    }

    private fun promoteToForeground(statusText: String, progressPercent: Int) {
        ensureChannel(this)
        val openPending = PendingIntent.getActivity(
            this,
            4510,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("LT PLAYER Download Manager")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openPending)
        if (progressPercent in 1..100) {
            builder.setProgress(100, progressPercent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0
        runCatching {
            ServiceCompat.startForeground(this, NOTIF_ID_FG, builder.build(), fgType)
        }
    }

    private fun stopForegroundSafely() {
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
    }

    private fun postCompletionNotification(title: String, uriString: String, mimeType: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = ensureChannel(this)
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(Uri.parse(uriString), mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val openPending = PendingIntent.getActivity(
            this,
            (System.currentTimeMillis() and 0x7FFF).toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_DOWNLOADS)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .build()
        runCatching {
            nm.notify(NOTIF_ID_COMPLETE_BASE + (title.hashCode() and 0xFF), notif)
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_DOWNLOADS = "lt_downloads"
        private const val NOTIF_ID_FG = 4501
        private const val NOTIF_ID_COMPLETE_BASE = 4600
        private const val MAX_CONCURRENT_DOWNLOADS = 2
        private const val MIN_SAFETY_FREE_BYTES = 10L * 1024L * 1024L

        const val ACTION_ENQUEUE = "com.example.download.ENQUEUE"
        const val ACTION_PAUSE = "com.example.download.PAUSE"
        const val ACTION_RESUME = "com.example.download.RESUME"
        const val ACTION_CANCEL = "com.example.download.CANCEL"
        const val ACTION_SYNC_QUEUE = "com.example.download.SYNC_QUEUE"
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"

        private val BLOCKED_MANIFEST_MIME_TYPES = setOf(
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
            "application/dash+xml",
            "application/vnd.ms-sstr+xml"
        )

        fun partFileFor(context: Context, id: Long): File =
            File(File(context.filesDir, "downloads_part"), "$id.part")

        fun ensureChannel(context: Context): NotificationManager {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_DOWNLOADS) == null) {
                val ch = NotificationChannel(
                    CHANNEL_DOWNLOADS,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Resumable background media and file download progress"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
            return nm
        }

        fun enqueue(context: Context, downloadId: Long) {
            dispatchAction(context, ACTION_ENQUEUE, downloadId, foreground = true)
        }

        fun pause(context: Context, downloadId: Long) {
            dispatchAction(context, ACTION_PAUSE, downloadId, foreground = false)
        }

        fun resume(context: Context, downloadId: Long) {
            dispatchAction(context, ACTION_RESUME, downloadId, foreground = true)
        }

        fun cancel(context: Context, downloadId: Long) {
            dispatchAction(context, ACTION_CANCEL, downloadId, foreground = false)
        }

        private fun dispatchAction(context: Context, action: String, downloadId: Long, foreground: Boolean) {
            val intent = Intent(context, MediaDownloadService::class.java).apply {
                this.action = action
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            runCatching {
                if (foreground) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    }
}
