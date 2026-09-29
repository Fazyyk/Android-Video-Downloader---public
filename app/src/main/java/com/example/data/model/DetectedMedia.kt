package com.example.data.model

data class DetectedMedia(
    val url: String,
    val title: String,
    val mimeType: String,
    val extension: String,
    val estimatedSize: String = "Direct Stream",
    val mediaType: MediaType = MediaType.VIDEO,
    val quality: String = "HD",
    val detectedAt: Long = System.currentTimeMillis(),
    val sourcePageTitle: String = ""
) {
    val isStream: Boolean get() = extension.equals("m3u8", ignoreCase = true) ||
            extension.equals("mpd", ignoreCase = true) ||
            url.contains("videoplayback")
    val displayExtension: String get() = extension.uppercase().ifBlank { "MEDIA" }
}

enum class MediaType {
    VIDEO, AUDIO, IMAGE, OTHER
}
