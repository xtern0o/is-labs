package org.example.controller

import org.example.service.cache.JpaL2CacheStatsService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/l2-stats")
class JpaL2CacheStatsController(
    private val statsService: JpaL2CacheStatsService
) {
    @PutMapping
    fun setEnabled(
        @RequestParam enabled: Boolean
    ): Map<String, Any> {
        statsService.setEnabled(enabled)
        return statsService.getStatistics()
    }

    @GetMapping
    fun getStatistics(): Map<String, Any> =
        statsService.getStatistics()
}