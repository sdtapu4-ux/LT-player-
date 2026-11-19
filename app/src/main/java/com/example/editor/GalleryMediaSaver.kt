package com.example.editor

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

data class SavedGalleryVideo(
    val contentUri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val relativeFolder: String = GalleryMediaSaver.EDITED_RELATIVE_PATH
)

object GalleryMediaSaver {

    const val EDITED_RELATIVE_PATH = "Movies/LT PLAYER/Edited Videos"

    fun sanitizeOutputFileName(baseTitle: String, suffix: String = ""): String {
        val cleaned = baseTitle
            .substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
            .ifBlank { "LT_Edit" }
            .take(40)
        val stamp = System.currentTimeMillis() % 100_000L
        val suffixPart = if (suffix.isBlank()) "" else "_$suffix"
        return "${cleaned}_LT${suffixPart}_$stamp.mp4"
    }

    fun saveVideoToGallery(
        context: Context,
        sourceTempFile: File,
        displayName: String
    ): SavedGalleryVideo {
        require(sourceTempFile.exists() && sourceTempFile.length() > 0L) {
            "Exported video output is empty or missing."
        }

        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = "${Environment.DIRECTORY_MOVIES}/LT PLAYER/Edited Videos"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, relativePath)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, values)
                ?: error("Could not create Gallery entry in MediaStore.")

            return try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    sourceTempFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                } ?: error("Failed to open MediaStore output stream.")

                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)

                SavedGalleryVideo(
                    contentUri = itemUri,
                    displayName = displayName,
                    sizeBytes = sourceTempFile.length(),
                    relativeFolder = relativePath
                )
            } catch (t: Throwable) {
                runCatching { resolver.delete(itemUri, null, null) }
                throw t
            }
        } else {
            @Suppress("DEPRECATION")
            val publicMovies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val targetDir = File(publicMovies, "LT PLAYER/Edited Videos").apply { mkdirs() }
            val destFile = if (targetDir.canWrite()) {
                File(targetDir, displayName)
            } else {
                val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "LT PLAYER/Edited Videos").apply { mkdirs() }
                File(appDir, displayName)
            }
            sourceTempFile.copyTo(destFile, overwrite = true)

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.TITLE, displayName.substringBeforeLast('.'))
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                @Suppress("DEPRECATION")
                put(MediaStore.Video.Media.DATA, destFile.absolutePath)
                put(MediaStore.Video.Media.SIZE, destFile.length())
            }
            val insertedUri = runCatching {
                resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            }.getOrNull() ?: Uri.fromFile(destFile)

            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("video/mp4"),
                null
            )

            return SavedGalleryVideo(
                contentUri = insertedUri,
                displayName = displayName,
                sizeBytes = destFile.length(),
                relativeFolder = "Movies/LT PLAYER/Edited Videos"
            )
        }
    }
}
