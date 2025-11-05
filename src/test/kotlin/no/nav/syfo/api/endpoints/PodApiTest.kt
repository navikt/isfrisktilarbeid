package no.nav.syfo.api.endpoints

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.TestDatabaseNotResponding
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PodApiTest {
    private val database = ExternalMockEnvironment.instance.database
    private val databaseNotResponding = TestDatabaseNotResponding()

    private fun ApplicationTestBuilder.setupPodApi(database: DatabaseInterface, applicationState: ApplicationState) {
        application {
            routing {
                podEndpoints(
                    applicationState = applicationState,
                    database = database,
                )
            }
        }
    }

    @Nested
    @DisplayName("Liveness and readiness checks")
    inner class SuccessfulLivenessAndReadiness {
        @Test
        fun `Returns ok on is_alive`() = testApplication {
            setupPodApi(database = database, applicationState = ApplicationState(alive = true, ready = true))
            val response = client.get("/internal/is_alive")
            assertTrue(response.status.isSuccess())
            assertNotNull(response.bodyAsText())
        }

        @Test
        fun `Returns ok on is_ready`() = testApplication {
            setupPodApi(database = database, applicationState = ApplicationState(alive = true, ready = true))
            val response = client.get("/internal/is_ready")
            assertTrue(response.status.isSuccess())
            assertNotNull(response.bodyAsText())
        }
    }

    @Nested
    @DisplayName("Unsuccessful liveness and readiness checks")
    inner class UnsuccessfulLivenessAndReadiness {
        @Test
        fun `Returns internal server error when liveness check fails`() = testApplication {
            setupPodApi(database = database, applicationState = ApplicationState(alive = false, ready = false))
            val response = client.get("/internal/is_alive")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertNotNull(response.bodyAsText())
        }

        @Test
        fun `Returns internal server error when readiness check fails`() = testApplication {
            setupPodApi(database = database, applicationState = ApplicationState(alive = false, ready = false))
            val response = client.get("/internal/is_ready")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertNotNull(response.bodyAsText())
        }
    }

    @Nested
    @DisplayName("Successful liveness and unsuccessful readiness when database not working")
    inner class DatabaseNotWorking {
        @Test
        fun `Returns ok on is_alive even when DB down`() = testApplication {
            setupPodApi(database = databaseNotResponding, applicationState = ApplicationState(alive = true, ready = true))
            val response = client.get("/internal/is_alive")
            assertTrue(response.status.isSuccess())
            assertNotNull(response.bodyAsText())
        }

        @Test
        fun `Returns internal server error when readiness check fails when DB down`() = testApplication {
            setupPodApi(database = databaseNotResponding, applicationState = ApplicationState(alive = true, ready = true))
            val response = client.get("/internal/is_ready")
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertNotNull(response.bodyAsText())
        }
    }
}
