package com.example.data.scanner

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.example.data.model.AudioItem
import com.example.data.model.MediaFolder
import com.example.data.model.StorageBreakdown
import com.example.data.model.SubtitleTrackOption
import com.example.data.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaScanner(private val context: Context) {

    suspend fun scanVideos(safTreeUris: List<String> = emptyList()): List<VideoItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<VideoItem>()
        val seenKeys = HashSet<String>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.RELATIVE_PATH
        } else {
            MediaStore.Video.Media.DATA
        }

        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            pathColumn
        )

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val wCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val modCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val pathCol = cursor.getColumnIndexOrThrow(pathColumn)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol) ?: "Video_$id"
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                        ?: displayName.substringBeforeLast('.')
                    val durationMs = cursor.getLong(durCol).coerceAtLeast(0L)
                    val sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val width = cursor.getInt(wCol).coerceAtLeast(0)
                    val height = cursor.getInt(hCol).coerceAtLeast(0)
                    val mimeType = cursor.getString(mimeCol) ?: "video/mp4"
                    val dateAdded = cursor.getLong(addedCol)
                    val dateMod = cursor.getLong(modCol)
                    val rawPath = cursor.getString(pathCol).orEmpty()

                    val folderPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        rawPath.trimEnd('/').ifBlank { "Internal Storage" }
                    } else {
                        File(rawPath).parent ?: "Internal Storage"
                    }
                    val folderName = folderPath.substringAfterLast('/').ifBlank { "Root" }
                    val ext = displayName.substringAfterLast('.', "").lowercase()
                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val (compat, note) = CodecInspector.quickContainerCheck(ext, mimeType)

                    val item = VideoItem(
                        id = id,
                        uriString = uri.toString(),
                        title = title,
                        displayName = displayName,
                        durationMs = durationMs,
                        sizeBytes = sizeBytes,
                        width = width,
                        height = height,
                        mimeType = mimeType,
                        containerExtension = ext,
                        folderName = folderName,
                        folderPath = folderPath,
                        dateAddedSec = dateAdded,
                        dateModifiedSec = dateMod,
                        isCompatible = compat,
                        compatibilityNote = note
                    )
                    seenKeys.add(uri.toString())
                    result.add(item)
                }
            }
        }

        // Also scan user-selected SAF folders (e.g., SD card / custom directories)
        safTreeUris.forEach { treeUriStr ->
            runCatching {
                val treeUri = Uri.parse(treeUriStr)
                scanSafDirectoryVideos(treeUri, result, seenKeys)
            }
        }

        result
    }

    private fun scanSafDirectoryVideos(
        treeUri: Uri,
        out: MutableList<VideoItem>,
        seenKeys: MutableSet<String>
    ) {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val folderName = docId.substringAfterLast(':').substringAfterLast('/').ifBlank { "External Folder" }
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: continue
                val mime = cursor.getString(mimeIdx) ?: ""
                val ext = name.substringAfterLast('.', "").lowercase()
                val isVideo = mime.startsWith("video/") || ext in CodecInspector.supportedVideoContainers
                if (!isVideo) continue
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                if (!seenKeys.add(docUri.toString())) continue

                val size = cursor.getLong(sizeIdx).coerceAtLeast(0L)
                val modSec = cursor.getLong(modIdx) / 1000L
                val (compat, note) = CodecInspector.quickContainerCheck(ext, mime)
                out.add(
                    VideoItem(
                        id = docUri.toString().hashCode().toLong(),
                        uriString = docUri.toString(),
                        title = name.substringBeforeLast('.'),
                        displayName = name,
                        durationMs = 0L,
                        sizeBytes = size,
                        width = 0,
                        height = 0,
                        mimeType = mime.ifBlank { "video/$ext" },
                        containerExtension = ext,
                        folderName = folderName,
                        folderPath = "SAF/$folderName",
                        dateAddedSec = modSec,
                        dateModifiedSec = modSec,
                        isCompatible = compat,
                        compatibilityNote = note
                    )
                )
            }
        }
    }

    suspend fun scanAudio(): List<AudioItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<AudioItem>()
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.RELATIVE_PATH
        } else {
            MediaStore.Audio.Media.DATA
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED,
            pathColumn
        )

        runCatching {
            context.contentResolver.query(
                collection,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 5000",
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val pathCol = cursor.getColumnIndexOrThrow(pathColumn)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol) ?: "Audio_$id"
                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() }
                        ?: displayName.substringBeforeLast('.')
                    val artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "Unknown Artist"
                    val album = cursor.getString(albumCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                        ?: "Unknown Album"
                    val albumId = cursor.getLong(albumIdCol)
                    val durationMs = cursor.getLong(durCol).coerceAtLeast(0L)
                    val sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L)
                    val mimeType = cursor.getString(mimeCol) ?: "audio/mpeg"
                    val added = cursor.getLong(addedCol)
                    val rawPath = cursor.getString(pathCol).orEmpty()
                    val folderPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        rawPath.trimEnd('/').ifBlank { "Music" }
                    } else {
                        File(rawPath).parent ?: "Music"
                    }
                    val folderName = folderPath.substringAfterLast('/').ifBlank { "Music" }
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                    val artUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    val isCompat = CodecInspector.isDecoderAvailable(mimeType)
                    result.add(
                        AudioItem(
                            id = id,
                            uriString = uri.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            albumId = albumId,
                            genre = guessGenreFromPath(folderName, title),
                            durationMs = durationMs,
                            sizeBytes = sizeBytes,
                            mimeType = mimeType,
                            folderName = folderName,
                            folderPath = folderPath,
                            dateAddedSec = added,
                            albumArtUri = artUri,
                            isCompatible = isCompat,
                            compatibilityNote = if (isCompat) null else "Codec $mimeType may not be decoded on this device"
                        )
                    )
                }
            }
        }
        result
    }

    suspend fun resolveSingleVideoFromUri(uri: Uri): VideoItem? = withContext(Dispatchers.IO) {
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Video"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = c.getString(nameIdx) ?: displayName
                    if (sizeIdx >= 0) size = c.getLong(sizeIdx)
                }
            }
        }
        val retriever = MediaMetadataRetriever()
        var duration = 0L
        var width = 0
        var height = 0
        runCatching {
            retriever.setDataSource(context, uri)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        }
        runCatching { retriever.release() }

        val ext = displayName.substringAfterLast('.', "").lowercase()
        val mime = context.contentResolver.getType(uri) ?: "video/mp4"
        val (compat, note) = CodecInspector.quickContainerCheck(ext, mime)
        VideoItem(
            id = uri.toString().hashCode().toLong(),
            uriString = uri.toString(),
            title = displayName.substringBeforeLast('.'),
            displayName = displayName,
            durationMs = duration,
            sizeBytes = size,
            width = width,
            height = height,
            mimeType = mime,
            containerExtension = ext,
            folderName = "Opened Files",
            folderPath = "Picked via SAF",
            dateAddedSec = System.currentTimeMillis() / 1000L,
            dateModifiedSec = System.currentTimeMillis() / 1000L,
            isCompatible = compat,
            compatibilityNote = note
        )
    }

    suspend fun discoverSubtitlesForVideo(video: VideoItem): List<SubtitleTrackOption> = withContext(Dispatchers.IO) {
        val baseTitle = video.displayName.substringBeforeLast('.').lowercase()
        val found = mutableListOf<SubtitleTrackOption>()
        runCatching {
            val filesUri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME
            )
            context.contentResolver.query(
                filesUri,
                projection,
                "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?",
                arrayOf("%.srt", "%.vtt", "%.ass", "%.ssa"),
                null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val subBase = name.substringBeforeLast('.').lowercase()
                    if (subBase == baseTitle || subBase.startsWith(baseTitle)) {
                        val subUri = ContentUris.withAppendedId(filesUri, id)
                        found.add(
                            SubtitleTrackOption(
                                id = "ext_$id",
                                label = name,
                                language = null,
                                isEmbedded = false,
                                uriString = subUri.toString()
                            )
                        )
                    }
                }
            }
        }
        found
    }

    suspend fun extractFrameForAi(uriString: String): Bitmap? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        return@withContext try {
            retriever.setDataSource(context, Uri.parse(uriString))
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val targetUs = if (durationMs > 4000L) (durationMs / 3L) * 1000L else 1_000_000L
            val raw = retriever.getFrameAtTime(targetUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            raw?.let { bmp ->
                val maxDim = 960
                if (bmp.width > maxDim || bmp.height > maxDim) {
                    val scale = maxDim.toFloat() / maxOf(bmp.width, bmp.height).toFloat()
                    Bitmap.createScaledBitmap(
                        bmp,
                        (bmp.width * scale).toInt().coerceAtLeast(1),
                        (bmp.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else bmp
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun buildFolders(videos: List<VideoItem>, audio: List<AudioItem>, safTrees: List<String>): List<MediaFolder> {
        val map = linkedMapOf<String, MutableList<VideoItem>>()
        videos.forEach { map.getOrPut(it.folderPath) { mutableListOf() }.add(it) }
        val audioMap = audio.groupBy { it.folderPath }
        val allPaths = (map.keys + audioMap.keys).distinct()
        return allPaths.map { path ->
            val vList = map[path].orEmpty()
            val aList = audioMap[path].orEmpty()
            val name = vList.firstOrNull()?.folderName ?: aList.firstOrNull()?.folderName ?: path.substringAfterLast('/')
            MediaFolder(
                name = name.ifBlank { "Internal Storage" },
                path = path,
                videoCount = vList.size,
                audioCount = aList.size,
                totalSizeBytes = vList.sumOf { it.sizeBytes } + aList.sumOf { it.sizeBytes },
                isSafTree = path.startsWith("SAF/") || safTrees.any { it == path }
            )
        }.sortedByDescending { it.videoCount + it.audioCount }
    }

    fun computeStorageBreakdown(
        videos: List<VideoItem>,
        audio: List<AudioItem>,
        downloadBytes: Long
    ): StorageBreakdown {
        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes
        val available = stat.availableBytes
        val used = (total - available).coerceAtLeast(0L)
        val videoBytes = videos.sumOf { it.sizeBytes }
        val audioBytes = audio.sumOf { it.sizeBytes }
        val cacheBytes = dirSize(context.cacheDir)
        return StorageBreakdown(
            totalDeviceBytes = total,
            availableDeviceBytes = available,
            usedDeviceBytes = used,
            videoBytes = videoBytes,
            audioBytes = audioBytes,
            downloadBytes = downloadBytes,
            appCacheBytes = cacheBytes
        )
    }

    fun clearAppThumbnailCache(): Long {
        val before = dirSize(context.cacheDir)
        context.cacheDir.listFiles()?.forEach { child ->
            if (child.name.contains("image_cache", ignoreCase = true) ||
                child.name.contains("coil", ignoreCase = true) ||
                child.name.endsWith(".tmp")
            ) {
                child.deleteRecursively()
            }
        }
        return (before - dirSize(context.cacheDir)).coerceAtLeast(0L)
    }

    private fun dirSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { dirSize(it) } ?: 0L
    }

    private fun guessGenreFromPath(folder: String, title: String): String {
        val text = "$folder $title".lowercase()
        return when {
            "rock" in text -> "Rock"
            "jazz" in text -> "Jazz"
            "lofi" in text || "chill" in text -> "Lo-Fi & Chill"
            "podcast" in text -> "Podcast"
            "classic" in text -> "Classical"
            "edm" in text || "electronic" in text -> "Electronic"
            "ringtone" in text -> "Ringtones"
            else -> "Local Audio"
        }
    }
}
