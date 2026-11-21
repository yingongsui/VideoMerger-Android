package com.videotools.videomerger

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFprobeKit
import java.io.File
import java.util.Locale
import kotlin.math.roundToLong

object VideoUtils {

    fun resolvePath(context: Context, uri: Uri): String? {
        if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }

        if (DocumentsContract.isDocumentUri(context, uri)) {
            val docId = DocumentsContract.getDocumentId(uri)
            when (uri.authority) {
                "com.android.externalstorage.documents" -> {
                    val split = docId.split(":")
                    if (split.size >= 2) {
                        val type = split[0]
                        val relativePath = split.drop(1).joinToString(":")
                        return if ("primary".equals(type, ignoreCase = true)) {
                            "${Environment.getExternalStorageDirectory()}/$relativePath"
                        } else {
                            "/storage/$type/$relativePath"
                        }
                    }
                }

                "com.android.providers.media.documents" -> {
                    val split = docId.split(":")
                    if (split.size == 2) {
                        val contentUri = when (split[0]) {
                            "video" -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            "audio" -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                            "image" -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            else -> null
                        }
                        if (contentUri != null) {
                            return queryDataColumn(context, contentUri, "_id=?", arrayOf(split[1]))
                        }
                    }
                }

                "com.android.providers.downloads.documents" -> {
                    if (docId.startsWith("raw:")) {
                        return docId.removePrefix("raw:")
                    }
                    val id = docId.toLongOrNull()
                    if (id != null) {
                        val contentUri = ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"),
                            id
                        )
                        return queryDataColumn(context, contentUri, null, null)
                    }
                }
            }
        }

        if ("content".equals(uri.scheme, ignoreCase = true)) {
            return queryDataColumn(context, uri, null, null)
        }

        return null
    }

    private fun queryDataColumn(
        context: Context,
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (index >= 0) cursor.getString(index) else null
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    fun probeVideo(path: String): VideoItem {
        val file = File(path)
        val session = FFprobeKit.getMediaInformation(path)
        val mediaInfo = session.mediaInformation

        val durationSec = mediaInfo?.duration?.toDoubleOrNull() ?: 0.0
        val durationMs = (durationSec * 1000.0).roundToLong()

        val videoStream = mediaInfo?.streams?.firstOrNull {
            "video".equals(it.type, ignoreCase = true)
        }

        val width = videoStream?.width?.toInt() ?: 0
        val height = videoStream?.height?.toInt() ?: 0
        val codec = videoStream?.codec.orEmpty()

        val rawFrames = videoStream?.getStringProperty("nb_frames")?.toLongOrNull() ?: 0L
        val frames = if (rawFrames > 0L) {
            rawFrames
        } else {
            estimateFrames(videoStream?.averageFrameRate, durationSec)
        }

        return VideoItem(
            path = path,
            name = file.name,
            width = width,
            height = height,
            frames = frames,
            durationMs = durationMs,
            codec = codec
        )
    }

    private fun estimateFrames(avgFrameRate: String?, durationSec: Double): Long {
        if (avgFrameRate.isNullOrBlank() || durationSec <= 0.0) return 0L
        val parts = avgFrameRate.split("/")
        val fps = when (parts.size) {
            2 -> {
                val num = parts[0].toDoubleOrNull() ?: 0.0
                val den = parts[1].toDoubleOrNull() ?: 0.0
                if (den > 0.0) num / den else 0.0
            }
            1 -> parts[0].toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
        return if (fps > 0.0) (fps * durationSec).roundToLong() else 0L
    }

    fun suggestOutputPath(firstFilePath: String): String {
        val file = File(firstFilePath)
        val parent = file.parent ?: ""
        val ext = file.extension.ifBlank { "mp4" }
        val baseName = file.nameWithoutExtension.replace(Regex("[_\\-]?\\d+$"), "").ifBlank {
            file.nameWithoutExtension
        }
        val outputName = "${baseName}_merged.$ext"
        return if (parent.isNotEmpty()) "$parent/$outputName" else outputName
    }

    fun escapeForConcat(path: String): String {
        return path.replace("'", "'\\''")
    }

    fun formatDuration(durationMs: Long): String {
        val totalSeconds = (durationMs / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}
