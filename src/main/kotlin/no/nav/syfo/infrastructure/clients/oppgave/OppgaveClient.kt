package no.nav.syfo.infrastructure.clients.oppgave

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.infrastructure.clients.ClientEnvironment
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.bearerHeader
import no.nav.syfo.infrastructure.clients.httpClientDefault
import org.slf4j.LoggerFactory
import java.util.UUID

class OppgaveClient(
    private val azureAdClient: AzureAdClient,
    private val environment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault(),
) {
    private val oppgaveUrl: String = "${environment.baseUrl}$OPPGAVE_PATH"

    suspend fun createOppgave(
        request: OppgaveRequest,
        correlationId: UUID,
    ): OppgaveResponse {
        val token = azureAdClient.getSystemToken(environment.clientId)?.accessToken
            ?: throw RuntimeException("Failed to create Gosys-oppgave: No token was found")
        return try {
            val response: HttpResponse = httpClient.post(oppgaveUrl) {
                header(HttpHeaders.Authorization, bearerHeader(token))
                header("X-Correlation-ID", correlationId.toString())
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            response.body<OppgaveResponse>()
        } catch (e: ClientRequestException) {
            handleUnexpectedResponseException(e.response, e.message)
            throw e
        } catch (e: ServerResponseException) {
            handleUnexpectedResponseException(e.response, e.message)
            throw e
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        message: String?,
    ) {
        log.error(
            "Error while creating gosys-oppgave: {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            StructuredArguments.keyValue("message", message),
        )
    }

    companion object {
        private const val OPPGAVE_PATH = "/api/v1/oppgaver"
        private val log = LoggerFactory.getLogger(OppgaveClient::class.java)
    }
}
