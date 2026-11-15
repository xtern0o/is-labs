package org.example.entity

import jakarta.persistence.Cacheable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import org.example.dto.importing.response.ImportHistoryResponse
import org.example.entity.enums.ImportStatus
import org.hibernate.annotations.Cache
import org.hibernate.annotations.CacheConcurrencyStrategy
import java.time.Instant
import java.util.UUID

@Entity
@Cacheable
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
class ImportHistory(
    @field:Id
    @field:GeneratedValue(strategy = GenerationType.UUID)
    @field:Column(nullable = false, updatable = false)
    var id: UUID? = null,

    @field:Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @field:ManyToOne(fetch = FetchType.EAGER, optional = false)
    @field:JoinColumn(
        name = "user_id",
        nullable = false,
        updatable = false,
        foreignKey = ForeignKey(name = "fk_import_history_user"),
    )
    var user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: ImportStatus = ImportStatus.STARTED,

    @Column
    var locationsCount: Int? = null,

    @Column
    var moviesCount: Int? = null,

    @Column
    var coordinatesCount: Int? = null,

    @Column
    var personsCount: Int? = null,

    // s3 related fields

    @Column(name = "file_object_key")
    var fileObjectKey: String? = null,

    @Column(name = "file_name")
    var fileName: String? = null,

    @Column(name = "file_content_type")
    var fileContentType: String? = null,

    @Column(name = "file_size")
    var fileSize: Long? = null,

    @Column(name = "file_etag")
    var fileETag: String? = null,

    )

fun ImportHistory.toResponse() =
    ImportHistoryResponse(
        id = requireNotNull(id),
        createdAt = requireNotNull(createdAt),
        status = status,
        userId = requireNotNull(user.id),
        username = user.name,
        moviesCount = moviesCount,
        personsCount = personsCount,
        coordinatesCount = coordinatesCount,
        locationsCount = locationsCount,
        fileObjectKey = fileObjectKey,
        fileName = fileName,
        fileContentType = fileContentType,
        fileSize = fileSize,
    )