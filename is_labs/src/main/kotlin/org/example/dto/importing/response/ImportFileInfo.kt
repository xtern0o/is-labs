package org.example.dto.importing.response

data class ImportFileInfo(
    val objectKey: String,
    val fileName: String,
    val contentType: String?,
)
