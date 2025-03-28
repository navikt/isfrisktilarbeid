package no.nav.syfo.infrastructure.clients.arbeidssokeroppslag

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.NAV_CALL_ID_HEADER
import no.nav.syfo.infrastructure.bearerHeader
import no.nav.syfo.infrastructure.clients.ClientEnvironment
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.httpClientDefault
import org.slf4j.LoggerFactory
import java.time.Instant

class ArbeidssokeroppslagClient(
    private val azureAdClient: AzureAdClient,
    private val clientEnvironment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault(),
) {
    private val arbeidssokerperioderUrl = "${clientEnvironment.baseUrl}$ARBEIDSSOKERPERIODER_PATH"

    suspend fun isArbeidssoker(
        callId: String,
        personIdent: Personident,
        token: String
    ): Boolean {
        val onBehalfOfToken =
            azureAdClient.getOnBehalfOfToken(
                scopeClientId = clientEnvironment.clientId,
                token = token
            )?.accessToken ?: throw RuntimeException("Failed to request access to arbeidssokerregisteret: Failed to get OBO token")

        return try {
            val response = httpClient.post(arbeidssokerperioderUrl) {
                header(HttpHeaders.Authorization, bearerHeader(onBehalfOfToken))
                header(NAV_CALL_ID_HEADER, callId)
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(ArbeidssokerperiodeRequest(personIdent.value))
            }
            val arbeidssokerperioder = response.body<List<ArbeidssokerperiodeResponse>>()
            val nyeste = arbeidssokerperioder.firstOrNull()
            nyeste != null && nyeste.startet.tidspunkt.isBefore(Instant.now()) && (nyeste.avsluttet == null || nyeste.avsluttet.tidspunkt.isAfter(Instant.now()))
        } catch (e: ResponseException) {
            if (e.response.status != HttpStatusCode.Forbidden) {
                handleUnexpectedResponseException(e.response, callId)
            }
            false
        }
    }

    private fun handleUnexpectedResponseException(
        response: HttpResponse,
        callId: String
    ) {
        log.error(
            "Error while requesting access to person from arbeidssokerregisteret with {}, {}",
            StructuredArguments.keyValue("statusCode", response.status.value.toString()),
            StructuredArguments.keyValue("callId", callId)
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(ArbeidssokeroppslagClient::class.java)

        const val ARBEIDSSOKERPERIODER_PATH = "/api/v1/veileder/arbeidssoekerperioder"
    }
}
