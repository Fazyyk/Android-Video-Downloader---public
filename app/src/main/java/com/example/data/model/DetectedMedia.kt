package com.example.data.model

data class DetectedMedia(
    val url: String,
    val title: String,
    val mimeType: String,
    val extension: String,
    val estimatedSize: String = "Direct Stream",
    val mediaType: MediaType = MediaType.VIDEO,
    val detectedAt: Long = System.currentTimeMillis()
)

enum class MediaType {
    VIDEO, AUDIO, IMAGE, OTHER
}
