package com.videotools.videomerger

data class VideoItem(
    val path: String,
    val name: String,
    val width: Int,
    val height: Int,
    val frames: Long,
    val durationMs: Long,
    val codec: String
) {
    fun formattedMetadata(): String {
        val parts = mutableListOf<String>()
        if (width > 0 && height > 0) {
            parts.add("${width}×${height}")
        }
        if (durationMs > 0) {
            parts.add(VideoUtils.formatDuration(durationMs))
        }
        if (frames > 0) {
            parts.add("$frames frames")
        }
        if (codec.isNotBlank()) {
            parts.add(codec)
        }
        return if (parts.isEmpty()) path else parts.joinToString(" · ")
    }
}
