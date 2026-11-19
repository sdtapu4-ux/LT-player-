package com.example.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import com.example.data.database.DownloadDao
import com.example.data.database.DownloadEntity
import com.example.data.model.DownloadMediaCategory
import com.example.data.model.DownloadRecord
import com.example.data.model.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections

object DownloadLiveRegistry {
    private val _stats = MutableStateFlow<Map<Long, Pair<Long, Long>>>(emptyMap())
    val stats: StateFlow<Map<Long, Pair<Long, Long>>> = _stats.asStateFlow()

    private val pausedIds = Collections.synchronizedSet(mutableSetOf<Long>())
    private val cancelledIds = Collections.synchronizedSet(mutableSetOf<Long>())

    fun updateStats(id: Long, speedBytesPerSec: Long, etaSeconds: Long) {
        val mutable = _stats.value.toMutableMap()
        mutable[id] = speedBytesPerSec to etaSeconds
        _stats.value = mutable
    }

    fun clearStats(id: Long) {
        if (_stats.value.containsKey(id)) {
            val mutable = _stats.value.toMutableMap()
            mutable.remove(id)
            _stats.value = mutable
        }
    }

    fun markPaused(id: Long) {
        pausedIds.add(id)
    }

    fun markCancelled(id: Long) {
        cancelledIds.add(id)
    }

    fun clearFlags(id: Long) {
        pausedIds.remove(id)
        cancelledIds.remove(id)
    }

    fun isPaused(id: Long): Boolean = pausedIds.contains(id)
    fun isCancelled(id: Long): Boolean = cancelledIds.contains(id)
}

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val blockedExtensions = setOf("m3u8", "mpd", "ism", "wvm")

    val downloadsFlow: Flow<List<DownloadRecord>> = combine(
        downloadDao.observeAll(),
        DownloadLiveRegistry.stats
    ) { entities, statsMap ->
        entities.map { entity ->
            val live = statsMap[entity.id]
            val category = runCatching { DownloadMediaCategory.valueOf(entity.mediaCategory) }
                .getOrElse { DownloadMediaCategory.classify(entity.title, entity.mimeType) }
            val status = runCatching { DownloadStatus.valueOf(entity.status) }
                .getOrDefault(DownloadStatus.PENDING)
            DownloadRecord(
                id = entity.id,
                systemDownloadId = entity.systemDownloadId,
                title = entity.title,
                url = entity.url,
                destinationUri = entity.destinationUri,
                localPath = entity.localPath,
                bytesDownloaded = entity.bytesDownloaded,
                totalBytes = entity.totalBytes,
                speedBytesPerSec = if (status == DownloadStatus.RUNNING) (live?.first ?: 0L) else 0L,
                etaSeconds = if (status == DownloadStatus.RUNNING) (live?.second ?: 0L) else 0L,
                status = status,
                errorReason = entity.errorReason,
                createdAt = entity.createdAt,
                mimeType = entity.mimeType,
                mediaCategory = category,
                qualityLabel = entity.qualityLabel,
                supportsRange = entity.supportsRange,
                safTreeUri = entity.safTreeUri
            )
        }
    }

    fun validateAuthorizedUrl(rawUrl: String): Result<Uri> {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Enter a valid HTTPS media URL."))
        }
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
            ?: return Result.failure(IllegalArgumentException("Malformed URL."))
        val scheme = uri.scheme?.lowercase()
        if (scheme != "https") {
            return Result.failure(
                IllegalArgumentException("Only secure HTTPS direct media links are supported.")
            )
        }
        val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (ext in blockedExtensions) {
            return Result.failure(
                IllegalArgumentException("Protected or adaptive streaming manifests (.$ext) cannot be downloaded.")
            )
        }
        if (!URLUtil.isHttpsUrl(trimmed)) {
            return Result.failure(IllegalArgumentException("Invalid HTTPS URL format."))
        }
        return Result.success(uri)
    }

    suspend fun enqueueDownload(
        rawUrl: String,
        customTitle: String?,
        mimeType: String? = null,
        qualityLabel: String? = null,
        knownTotalBytes: Long = 0L,
        usePublicDownloadsFolder: Boolean = true,
        safTreeUri: String? = null
    ): Result<Long> = withContext(Dispatchers.IO) {
        val uriResult = validateAuthorizedUrl(rawUrl)
        val uri = uriResult.getOrElse { return@withContext Result.failure(it) }

        val guessedName = URLUtil.guessFileName(uri.toString(), null, mimeType)
            .ifBlank { "lt_media_${System.currentTimeMillis()}.mp4" }
        val fileName = customTitle?.takeIf { it.isNotBlank() }?.let { safe ->
            val ext = guessedName.substringAfterLast('.', "")
            if (safe.contains('.') || ext.isBlank()) safe else "$safe.$ext"
        } ?: guessedName

        val resolvedMime = mimeType?.takeIf { it.isNotBlank() } ?: guessMimeFromName(fileName)
        val category = DownloadMediaCategory.classify(fileName, resolvedMime)
        val localPathLabel = when {
            !safTreeUri.isNullOrBlank() -> "SAF Folder/$fileName"
            usePublicDownloadsFolder -> "Downloads/LTPlayer/$fileName"
            else -> "App Storage/$fileName"
        }

        return@withContext try {
            val entityId = downloadDao.insert(
                DownloadEntity(
                    systemDownloadId = System.currentTimeMillis(),
                    title = fileName,
                    url = uri.toString(),
                    destinationUri = null,
                    localPath = localPathLabel,
                    bytesDownloaded = 0L,
                    totalBytes = knownTotalBytes.coerceAtLeast(0L),
                    status = DownloadStatus.PENDING.name,
                    errorReason = null,
                    createdAt = System.currentTimeMillis(),
                    mimeType = resolvedMime,
                    mediaCategory = category.name,
                    qualityLabel = qualityLabel,
                    supportsRange = true,
                    safTreeUri = safTreeUri
                )
            )
            DownloadLiveRegistry.clearFlags(entityId)
            MediaDownloadService.enqueue(context, entityId)
            Result.success(entityId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun startDownload(
        rawUrl: String,
        customTitle: String?,
        usePublicDownloadsFolder: Boolean
    ): Result<Long> = enqueueDownload(
        rawUrl = rawUrl,
        customTitle = customTitle,
        usePublicDownloadsFolder = usePublicDownloadsFolder
    )

    suspend fun pauseDownload(id: Long) = withContext(Dispatchers.IO) {
        DownloadLiveRegistry.markPaused(id)
        MediaDownloadService.pause(context, id)
        downloadDao.getById(id)?.let { entity ->
            downloadDao.update(entity.copy(status = DownloadStatus.PAUSED.name))
        }
        DownloadLiveRegistry.clearStats(id)
    }

    suspend fun resumeDownload(id: Long): Result<Long> = withContext(Dispatchers.IO) {
        val entity = downloadDao.getById(id)
            ?: return@withContext Result.failure(IllegalArgumentException("Download entry not found."))
        DownloadLiveRegistry.clearFlags(id)
        downloadDao.update(
            entity.copy(
                status = DownloadStatus.PENDING.name,
                errorReason = null
            )
        )
        MediaDownloadService.resume(context, id)
        Result.success(id)
    }

    suspend fun cancelDownload(record: DownloadRecord) = withContext(Dispatchers.IO) {
        DownloadLiveRegistry.markCancelled(record.id)
        MediaDownloadService.cancel(context, record.id)
        MediaDownloadService.partFileFor(context, record.id).delete()
        DownloadLiveRegistry.clearStats(record.id)
        downloadDao.deleteById(record.id)
    }

    suspend fun retryDownload(record: DownloadRecord): Result<Long> = withContext(Dispatchers.IO) {
        MediaDownloadService.partFileFor(context, record.id).delete()
        DownloadLiveRegistry.clearFlags(record.id)
        DownloadLiveRegistry.clearStats(record.id)
        val entity = downloadDao.getById(record.id)
        if (entity != null) {
            downloadDao.update(
                entity.copy(
                    bytesDownloaded = 0L,
                    status = DownloadStatus.PENDING.name,
                    errorReason = null
                )
            )
            MediaDownloadService.enqueue(context, entity.id)
            Result.success(entity.id)
        } else {
            enqueueDownload(
                rawUrl = record.url,
                customTitle = record.title,
                mimeType = record.mimeType,
                qualityLabel = record.qualityLabel,
                knownTotalBytes = record.totalBytes,
                usePublicDownloadsFolder = record.localPath?.startsWith("Downloads/") == true,
                safTreeUri = record.safTreeUri
            )
        }
    }

    suspend fun deleteDownloadedFile(record: DownloadRecord): Boolean = withContext(Dispatchers.IO) {
        MediaDownloadService.partFileFor(context, record.id).delete()
        val deletedUri = record.destinationUri?.let { dest ->
            runCatching {
                val uri = Uri.parse(dest)
                if (uri.scheme == "file") {
                    File(uri.path.orEmpty()).delete()
                } else {
                    context.contentResolver.delete(uri, null, null) > 0
                }
            }.getOrDefault(false)
        } ?: true
        DownloadLiveRegistry.clearStats(record.id)
        downloadDao.deleteById(record.id)
        deletedUri
    }

    suspend fun clearDownloadHistory() = withContext(Dispatchers.IO) {
        downloadDao.clearCompletedAndFailed()
    }

    suspend fun recoverInterruptedDownloads() = withContext(Dispatchers.IO) {
        val active = downloadDao.getActiveDownloads()
        for (entity in active) {
            if (entity.status == DownloadStatus.RUNNING.name) {
                val partialLen = MediaDownloadService.partFileFor(context, entity.id).length()
                downloadDao.update(
                    entity.copy(
                        bytesDownloaded = partialLen.coerceAtLeast(entity.bytesDownloaded),
                        status = DownloadStatus.PAUSED.name
                    )
                )
            }
        }
    }

    fun buildOpenIntent(record: DownloadRecord): Intent? {
        val uri = resolveShareableUri(record) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, record.mimeType.ifBlank { "*/*" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildShareIntent(record: DownloadRecord): Intent? {
        val uri = resolveShareableUri(record) ?: return null
        return Intent(Intent.ACTION_SEND).apply {
            type = record.mimeType.ifBlank { "*/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, record.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.let { Intent.createChooser(it, "Share ${record.title}") }.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun resolveShareableUri(record: DownloadRecord): Uri? {
        val raw = record.destinationUri ?: return null
        val parsed = Uri.parse(raw)
        if (parsed.scheme == "file") {
            val file = File(parsed.path.orEmpty())
            if (file.exists()) {
                return runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }.getOrDefault(parsed)
            }
        }
        return parsed
    }

    private fun guessMimeFromName(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "3gp" -> "video/3gpp"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }
}
