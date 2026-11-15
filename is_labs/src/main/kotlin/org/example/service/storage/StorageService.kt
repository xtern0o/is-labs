package org.example.service.storage

import io.awspring.cloud.s3.ObjectMetadata
import io.awspring.cloud.s3.S3Template
import org.example.dto.storage.response.StoredFileInfoResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.services.s3.S3Client
import java.util.UUID

/**
 * Сервис для загрузки файлов *.hocon в S3-хранилище
 */
@Service
class StorageService(
    private val s3Template: S3Template,
    private val s3Client: S3Client,

    @param:Value("\${app.s3.imports-bucket}")
    private val bucket: String
) {
    fun upload(
        historyId: UUID,
        file: MultipartFile
    ): StoredFileInfoResponse {
        val filename = file.originalFilename ?: "import.hocon"
        val key = "history/$historyId/source.hocon"

        val metadata = ObjectMetadata.builder()
            .contentType(file.contentType ?: "application/octet-stream")
            .contentLength(file.size)
            .metadata("original-filename", filename)
            .build()

        file.inputStream.use { inputStream ->
            s3Template.upload(
                bucket, key, inputStream, metadata
            )
        }

        val storedObject = s3Client.headObject {
            it.bucket(bucket)
            it.key(key)
        }

        return StoredFileInfoResponse(
            bucket = bucket,
            key = key,
            s3Uri = "s3://$bucket/$key",
            originalFileName = filename,
            size = storedObject.contentLength(),
            contentType = storedObject.contentType(),
            eTag = storedObject.eTag(),
        )
    }

    fun download(key: String): ByteArray =
        s3Client.getObjectAsBytes {
            it.bucket(bucket)
            it.key(key)
        }.asByteArray()
}