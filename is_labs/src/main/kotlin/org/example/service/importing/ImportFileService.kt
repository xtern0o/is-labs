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
        val historyId = historyService.start(userId)

        return try {

            val storedFileInfo = storageService.upload(historyId, file)
            historyService.attachFile(historyId, storedFileInfo)

            val content = file.inputStream
                .bufferedReader(StandardCharsets.UTF_8)
                .use { it.readText() }

            val request = parser.parse(
                content,
                EntityImportFileRequest::class.java,
            )
            transactionService.importEntities(request, historyId)

        } catch (exception: Exception) {
            historyService.markFailed(historyId)
            throw exception
        }
    }
}