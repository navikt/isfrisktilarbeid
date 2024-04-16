package no.nav.syfo.infrastructure.clients.behandler

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.infrastructure.NAV_CALL_ID_HEADER
import no.nav.syfo.infrastructure.bearerHeader
import no.nav.syfo.infrastructure.clients.ClientEnvironment
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.httpClientDefault
import org.slf4j.LoggerFactory
import java.util.UUID

class DialogmeldingBehandlerClient(
    private val azureAdClient: AzureAdClient,
    private val clientEnvironment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault()
) {
    private val behandlerUrl = "${clientEnvironment.baseUrl}$BEHANDLER_SYSTEM_PATH"

    suspend fun getBehandler(behandlerRef: UUID): BehandlerDTO? {
        val callId = UUID.randomUUID().toString()
        val token = azureAdClient.getSystemToken(clientEnvironment.clientId)?.accessToken ?: throw RuntimeException("Failed to get behandler: No token was found")

        return try {
            val response: HttpResponse = httpClient.get("$behandlerUrl/$behandlerRef") {
                header(HttpHeaders.Authorization, bearerHeader(token))
                header(NAV_CALL_ID_HEADER, callId)
                accept(ContentType.Application.Json)
            }
            response.body<BehandlerDTO>()
        } catch (responseException: ResponseException) {
            log.error(
                "Error while requesting behandler from isdialogmelding with {}, {}",
                StructuredArguments.keyValue("statusCode", responseException.response.status.value.toString()),
                StructuredArguments.keyValue("callId", callId)
            )
            null
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)

        const val BEHANDLER_SYSTEM_PATH = "/api/system/v1/behandlere"
    }
}
