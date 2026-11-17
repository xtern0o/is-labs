package org.example.service.importing

import org.example.aspect.LogJpaL2CacheStats
import org.example.dto.importing.response.ImportFileInfo
import org.example.dto.importing.response.ImportHistoryResponse
import org.example.dto.storage.response.StoredFileInfoResponse
import org.example.entity.ImportHistory
import org.example.entity.enums.ImportStatus
import org.example.entity.toResponse
import org.example.repository.ImportHistoryRepository
import org.example.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Сервис для обработки текущего статуса импорта из файла
 */
@Service
class ImportHistoryService(
    private val historyRepository: ImportHistoryRepository,
    private val userRepository: UserRepository,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun start(userId: UUID): UUID {
        val user = userRepository.findById(userId).orElseThrow {
            NoSuchElementException("user $userId not found")
        }

        return requireNotNull(
            historyRepository.save(
                ImportHistory(user = user),
            ).id,
        )
    }

    /**
     * После загрузки в S3 прикрепляем мета информацию о файле
     * REQUIRES_NEW, чтобы метаданные зафиксировались независимо от основной транзакции импорта
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun attachFile(
        historyId: UUID,
        fileInfo: StoredFileInfoResponse
    ): ImportHistoryResponse {
        val history = historyRepository.findById(historyId).orElseThrow()

        return history.apply {
            fileName = fileInfo.originalFileName
            fileSize = fileInfo.size
            fileETag = fileInfo.eTag
            fileContentType = fileInfo.contentType
            fileObjectKey = fileInfo.key
            status = ImportStatus.FILE_STORED
        }.toResponse()
    }

    @LogJpaL2CacheStats
    @Transactional(readOnly = true)
    fun getFileInfo(
        historyId: UUID,
        requesterId: UUID,
        isAdmin: Boolean,
    ): ImportFileInfo {
        val history = historyRepository.findById(historyId).orElseThrow {
            NoSuchElementException("import history $historyId not found")
        }

        if (!isAdmin && history.user.id != requesterId) {
            throw IllegalStateException("access denied")
        }

        return ImportFileInfo(
            objectKey = requireNotNull(history.fileObjectKey) {
                "file is not attached to import history"
            },
            fileName = history.fileName ?: "import.hocon",
            contentType = history.fileContentType,
        )
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markFailed(historyId: UUID) {
        val history = historyRepository.findById(historyId).orElseThrow()
        history.status = ImportStatus.FAILED
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun markPrepared(historyId: UUID) {
        val history = historyRepository.findById(historyId).orElseThrow()
        history.status = ImportStatus.PREPARED
    }

    @LogJpaL2CacheStats
    @PreAuthorize("#userId.toString() == authentication.name or hasRole('ADMIN')")
    @Transactional(readOnly = true)
    fun getPageByUserId(
        userId: UUID,
        pageable: Pageable
    ): Page<ImportHistoryResponse> {
        val history = historyRepository.findByUserId(userId, pageable)

        return history.map { it.toResponse() }
    }

    @LogJpaL2CacheStats
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    fun getPage(
        pageable: Pageable
    ): Page<ImportHistoryResponse> {
        val history = historyRepository.findAll(pageable)

        return history.map { it.toResponse() }
    }
}