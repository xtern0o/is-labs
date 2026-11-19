package org.example.aspect

import jakarta.persistence.EntityManagerFactory
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.hibernate.SessionFactory
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Aspect
@Component
@Order(1) // чтобы логи считались в конце
class LogJpaL2CacheStatsAspect(
    entityManagerFactory: EntityManagerFactory,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val stats = entityManagerFactory
        .unwrap(SessionFactory::class.java)
        .statistics

    @Around("@annotation(LogJpaL2CacheStats)")
    fun logStats(joinPoint: ProceedingJoinPoint): Any? {
        if (!stats.isStatisticsEnabled) return joinPoint.proceed()

        val hitsBefore = stats.secondLevelCacheHitCount
        val missesBefore = stats.secondLevelCacheMissCount
        val putsBefore = stats.secondLevelCachePutCount

        return try {
            joinPoint.proceed()
        } finally {
            log.info(
                "L2JpaCacheStats @{}: hits={}, misses={}, puts={}",
                joinPoint.signature.toShortString(),
                stats.secondLevelCacheHitCount - hitsBefore,
                stats.secondLevelCacheMissCount - missesBefore,
                stats.secondLevelCachePutCount - putsBefore
            )
        }
    }
}