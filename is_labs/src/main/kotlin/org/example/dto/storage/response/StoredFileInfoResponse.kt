package org.example.dto.storage.response

data class  StoredFileInfoResponse(
    val bucket: String,
    val key: String,
    val s3Uri: String,
    val originalFileName: String,
    val size: Long,
    val contentType: String?,
    val eTag: String?,
)
