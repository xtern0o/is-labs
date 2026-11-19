package org.example.service.cache

import jakarta.persistence.EntityManagerFactory
import org.hibernate.SessionFactory
import org.springframework.stereotype.Service

@Service
class JpaL2CacheStatsService(
    entityManagerFactory: EntityManagerFactory
) {
    private val stats = entityManagerFactory
        .unwrap(SessionFactory::class.java)
        .statistics

    fun setEnabled(enabled: Boolean) {
        if (enabled) {
            stats.clear()
        }

        stats.isStatisticsEnabled = enabled
    }

    fun getStatistics(): Map<String, Any> =
        mapOf(
            "enabled" to stats.isStatisticsEnabled,
            "hits" to stats.secondLevelCacheHitCount,
            "misses" to stats.secondLevelCacheMissCount,
            "puts" to stats.secondLevelCachePutCount,
        )
}