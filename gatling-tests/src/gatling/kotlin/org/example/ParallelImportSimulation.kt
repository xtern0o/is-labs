package org.example

import io.gatling.javaapi.core.CoreDsl.StringBody
import io.gatling.javaapi.core.CoreDsl.atOnceUsers
import io.gatling.javaapi.core.CoreDsl.csv
import io.gatling.javaapi.core.CoreDsl.global
import io.gatling.javaapi.core.CoreDsl.jsonPath
import io.gatling.javaapi.core.CoreDsl.scenario
import io.gatling.javaapi.core.Simulation
import io.gatling.javaapi.http.HttpDsl.http
import io.gatling.javaapi.http.HttpDsl.status
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class ParallelImportSimulation : Simulation() {

    private val successCount = AtomicInteger()
    private val conflictCount = AtomicInteger()
    private val unexpectedCount = AtomicInteger()

    /**
     * username,password,file
     */
    private val users = csv("users.csv").queue()

    private val httpProtocol = http
        .baseUrl("http://localhost:55080")
        .acceptHeader("application/json")

    private val loginRequest = http("LoginRequest")
        .post("/api/auth/login")
        .body(
            StringBody(
                """
                    {
                        "name": "#{username}",
                        "password": "#{password}"
                    }
                """.trimIndent()
            )
        )
        .asJson()
        .check(
            status().`is`(200),
            jsonPath("$.accessToken").saveAs("accessToken"),
        )

    private val importRequest = http("Concurrent import")
        .post("/api/imports")
        .header("Authorization", "Bearer #{accessToken}")
        .formUpload("file", "#{file}")
        .check(
            status().`in`(200, 409).saveAs("importStatus")
        )

    private val concurrentImportScenario = scenario("Concurrent Import Scenario")
        .feed(users)            // каждый юзер идет по своей csv строчку
        .exec(loginRequest)
        .rendezVous(2)                // первый ждет второго (синк таймер из jmeter)
        .exec(importRequest)

        .exec { session ->
            val responseCode = session.getIntegerWrapper("importStatus")

            when (responseCode) {
                200 -> {
                    successCount.incrementAndGet()
                    session
                }

                409 -> {
                    conflictCount.incrementAndGet()
                    session
                }

                else -> {
                    unexpectedCount.incrementAndGet()
                    session.markAsFailed()
                }
            }
        }

    init {
        setUp(
            concurrentImportScenario.injectOpen(atOnceUsers(2))
        )
            .protocols(httpProtocol)
            .maxDuration(Duration.ofSeconds(30)) // защита от вечного rendezVous
            .assertions(
                global().failedRequests().count().`is`(0)   // без ошибок все нормас
            )

    }

    override fun after() {
        val successes = successCount.get()
        val conflicts = conflictCount.get()
        val wtfs = unexpectedCount.get()

        println(
            "Concurrent Import Results: " +
            "successes=$successes, " +
            "conflicts=$conflicts, " +
            "unexpected=$wtfs"
        )

        check(
            successes == 1 && conflicts == 1 && wtfs == 0
        ) {
            "expected: 1x200 + 1x409, got: " +
                    "200=$successes, " +
                    "409=$conflicts, " +
                    "unexpected=$wtfs"
        }
    }

}
