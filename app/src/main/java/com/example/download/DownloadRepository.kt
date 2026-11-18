package com.example.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import com.example.data.database.DownloadDao
import com.example.data.database.DownloadEntity
import com.example.data.model.DownloadRecord
import com.example.data.model.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DownloadRepository(
    private val context: Context,
    private val downloadDao: DownloadDao
) {
    private val downloadManager: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    private val blockedExtensions = setOf("m3u8", "mpd", "ism", "wvm")

    val downloadsFlow: Flow<List<DownloadRecord>> = downloadDao.observeAll().map { entities ->
        entities.map { entity ->
            DownloadRecord(
                id = entity.id,
                systemDownloadId = entity.systemDownloadId,
                title = entity.title,
                url = entity.url,
                destinationUri = entity.destinationUri,
                localPath = entity.localPath,
                bytesDownloaded = entity.bytesDownloaded,
                totalBytes = entity.totalBytes,
                speedBytesPerSec = 0L,
                etaSeconds = 0L,
                status = runCatching { DownloadStatus.valueOf(entity.status) }
                    .getOrDefault(DownloadStatus.PENDING),
                errorReason = entity.errorReason,
                createdAt = entity.createdAt
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

    suspend fun startDownload(
        rawUrl: String,
        customTitle: String?,
        usePublicDownloadsFolder: Boolean
    ): Result<Long> = withContext(Dispatchers.IO) {
        val uriResult = validateAuthorizedUrl(rawUrl)
        val uri = uriResult.getOrElse { return@withContext Result.failure(it) }

        val guessedName = URLUtil.guessFileName(uri.toString(), null, null)
            .ifBlank { "lt_media_${System.currentTimeMillis()}.mp4" }
        val fileName = customTitle?.takeIf { it.isNotBlank() }?.let { safe ->
            val ext = guessedName.substringAfterLast('.', "mp4")
            if (safe.contains('.')) safe else "$safe.$ext"
        } ?: guessedName

        return@withContext try {
            val request = DownloadManager.Request(uri).apply {
                setTitle(fileName)
                setDescription("LT PLAYER Download Manager")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                if (usePublicDownloadsFolder) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "LTPlayer/$fileName")
                } else {
                    setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                }
            }
            val sysId = downloadManager.enqueue(request)
            val entityId = downloadDao.insert(
                DownloadEntity(
                    systemDownloadId = sysId,
                    title = fileName,
                    url = uri.toString(),
                    destinationUri = null,
                    localPath = if (usePublicDownloadsFolder) "Downloads/LTPlayer/$fileName" else "App Downloads/$fileName",
                    bytesDownloaded = 0L,
                    totalBytes = 0L,
                    status = DownloadStatus.RUNNING.name,
                    errorReason = null,
                    createdAt = System.currentTimeMillis()
                )
            )
            Result.success(entityId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncProgressWithSystem(): Map<Long, Pair<Long, Long>> = withContext(Dispatchers.IO) {
        val active = downloadDao.getActiveDownloads()
        val speedSamples = mutableMapOf<Long, Pair<Long, Long>>()
        for (entity in active) {
            val query = DownloadManager.Query().setFilterById(entity.systemDownloadId)
            downloadManager.query(query)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val dlIdx = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIdx = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val uriIdx = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                    val sysStatus = if (statusIdx >= 0) cursor.getInt(statusIdx) else DownloadManager.STATUS_PENDING
                    val downloaded = if (dlIdx >= 0) cursor.getLong(dlIdx).coerceAtLeast(0L) else entity.bytesDownloaded
                    val total = if (totalIdx >= 0) cursor.getLong(totalIdx).coerceAtLeast(0L) else entity.totalBytes
                    val localUri = if (uriIdx >= 0) cursor.getString(uriIdx) else entity.destinationUri
                    val reason = if (reasonIdx >= 0) cursor.getInt(reasonIdx) else 0

                    val mappedStatus = when (sysStatus) {
                        DownloadManager.STATUS_PENDING -> DownloadStatus.PENDING
                        DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
                        DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
                        DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.SUCCESSFUL
                        DownloadManager.STATUS_FAILED -> DownloadStatus.FAILED
                        else -> DownloadStatus.PENDING
                    }
                    val delta = (downloaded - entity.bytesDownloaded).coerceAtLeast(0L)
                    speedSamples[entity.id] = delta to total

                    downloadDao.update(
                        entity.copy(
                            bytesDownloaded = downloaded,
                            totalBytes = total,
                            destinationUri = localUri,
                            status = mappedStatus.name,
                            errorReason = if (mappedStatus == DownloadStatus.FAILED) "Download error code $reason" else null
                        )
                    )
                }
            }
        }
        speedSamples
    }

    suspend fun cancelDownload(record: DownloadRecord) = withContext(Dispatchers.IO) {
        runCatching { downloadManager.remove(record.systemDownloadId) }
        downloadDao.deleteById(record.id)
    }

    suspend fun retryDownload(record: DownloadRecord): Result<Long> {
        cancelDownload(record)
        return startDownload(
            rawUrl = record.url,
            customTitle = record.title,
            usePublicDownloadsFolder = record.localPath?.startsWith("Downloads/") == true
        )
    }
}
