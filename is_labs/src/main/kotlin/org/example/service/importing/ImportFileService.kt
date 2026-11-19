package org.example.service.importing

import org.example.dto.importing.request.EntityImportFileRequest
import org.example.dto.importing.response.ImportHistoryResponse
import org.example.dto.storage.response.StoredFileInfoResponse
import org.example.importing.parser.HoconImportParser
import org.example.service.storage.StorageService
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Сервис-орекстратор для импорта от имени пользователя
 */
@Service
class ImportFileService(
    private val parser: HoconImportParser,
    private val historyService: ImportHistoryService,
    private val transactionService: ImportTransactionService,
    private val storageService: StorageService,
) {
    fun importFile(
        file: MultipartFile,
        userId: UUID,
    ): ImportHistoryResponse {
        val historyId = historyService.start(userId) // CREATED
        var storedFileInfo: StoredFileInfoResponse? = null

        return try {
            // S3 stage
            storedFileInfo = storageService.upload(historyId, file)
            historyService.attachFile(historyId, storedFileInfo) // FILE_STORED

            val content = file.inputStream
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }

            val request = parser.parse(
                content,
                EntityImportFileRequest::class.java,
            )

            historyService.markPrepared(historyId) // PREPARED

            transactionService.importEntities(request, historyId) // SUCCESS

        } catch (exception: Exception) {
            if (storedFileInfo != null) {
                try {
                    storageService.delete(storedFileInfo.key)
                    historyService.markFailed(historyId)
                } catch (compensationException: Exception) {
                    // если не удалось удалить - s3 упало прям вот во время вау
                    exception.addSuppressed(compensationException)
                    // historyService.markRollbackRequired(historyId)
                    // TODO: сделать если не лень
                }
            } else {
                historyService.markFailed(historyId)
            }

            historyService.markFailed(historyId)
            throw exception
        }
    }
}