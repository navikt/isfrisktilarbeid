package no.nav.syfo.infrastructure.clients.pdl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.micrometer.core.instrument.Counter
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.clients.ClientEnvironment
import no.nav.syfo.infrastructure.bearerHeader
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.pdl.dto.*
import no.nav.syfo.infrastructure.clients.httpClientDefault
import no.nav.syfo.infrastructure.metric.METRICS_NS
import no.nav.syfo.infrastructure.metric.METRICS_REGISTRY
import org.slf4j.LoggerFactory

class PdlClient(
    private val azureAdClient: AzureAdClient,
    private val pdlEnvironment: ClientEnvironment,
    private val httpClient: HttpClient = httpClientDefault(),
) {

    suspend fun getPerson(personident: Personident): PdlPerson {
        val token = azureAdClient.getSystemToken(pdlEnvironment.clientId)
            ?: throw RuntimeException("Failed to send request to PDL: No token was found")
        val request = PdlHentPersonRequest(getPdlQuery(PDL_QUERY_HENT_PERSON_PATH), PdlHentPersonRequestVariables(personident.value))

        val response: HttpResponse = httpClient.post(pdlEnvironment.baseUrl) {
            setBody(request)
            header(HttpHeaders.ContentType, "application/json")
            header(HttpHeaders.Authorization, bearerHeader(token.accessToken))
            header(BEHANDLINGSNUMMER_HEADER_KEY, BEHANDLINGSNUMMER_HEADER_VALUE)
        }

        val person = when (response.status) {
            HttpStatusCode.OK -> {
                val pdlPersonReponse = response.body<PdlPersonResponse>()
                if (!pdlPersonReponse.errors.isNullOrEmpty()) {
                    Metrics.COUNT_CALL_PDL_PERSON_FAIL.increment()
                    pdlPersonReponse.errors.forEach {
                        logger.error("Error while requesting person from PersonDataLosningen: ${it.errorMessage()}")
                    }
                    null
                } else {
                    Metrics.COUNT_CALL_PDL_PERSON_SUCCESS.increment()
                    pdlPersonReponse.data?.hentPerson
                }
            }

            else -> {
                Metrics.COUNT_CALL_PDL_PERSON_FAIL.increment()
                logger.error("Request with url: ${pdlEnvironment.baseUrl} failed with reponse code ${response.status.value}")
                null
            }
        }

        return person ?: throw RuntimeException("PDL did not return a person for given fnr")
    }

    suspend fun geografiskTilknytning(
        personident: Personident,
    ): GeografiskTilknytning {
        val systemToken = azureAdClient.getSystemToken(
            scopeClientId = pdlEnvironment.clientId,
        )?.accessToken
            ?: throw RuntimeException("Failed to request PDL: Failed to get system token from AzureAD")

        val query = getPdlQuery(PDL_QUERY_GEOGRAFISK_TILKNYTNING_PATH)
        val request = PdlGeografiskTilknytningRequest(
            query = query,
            variables = PdlGeografiskTilknytningRequestVariables(personident.value)
        )
        try {
            val pdlPersonResponse: PdlGeografiskTilknytningResponse = httpClient.post(pdlEnvironment.baseUrl) {
                header(HttpHeaders.Authorization, bearerHeader(systemToken))
                header(BEHANDLINGSNUMMER_HEADER_KEY, BEHANDLINGSNUMMER_HEADER_VALUE)
                header(GT_HEADER, GT_HEADER)
                contentType(ContentType.Application.Json)
                setBody(request)
            }.body()
            if (pdlPersonResponse.errors != null && pdlPersonResponse.errors.isNotEmpty()) {
                Metrics.COUNT_CALL_PDL_GT_FAIL.increment()
                pdlPersonResponse.errors.forEach {
                    logger.error("Error while requesting person from PersonDataLosningen: ${it.errorMessage()}")
                }
                throw RuntimeException("No Geografisk Tilknytning was found in response from PDL: Errors found in response")
            } else if (pdlPersonResponse.data == null) {
                Metrics.COUNT_CALL_PDL_GT_FAIL.increment()
                val errorMessage =
                    "No Geografisk Tilknytning was found in response from PDL: No data was found in response"
                logger.error("Error while requesting person from PersonDataLosningen: $errorMessage")
                throw throw RuntimeException(errorMessage)
            } else {
                Metrics.COUNT_CALL_PDL_GT_SUCCESS.increment()
                return pdlPersonResponse.data.hentGeografiskTilknytning?.geografiskTilknytning()
                    ?: throw GeografiskTilknytningNotFoundException()
            }
        } catch (e: ResponseException) {
            logger.error(
                "Error while requesting GeografiskTilknytning from PersonDataLosningen {}, {}",
                StructuredArguments.keyValue("statusCode", e.response.status.value.toString()),
                StructuredArguments.keyValue("message", e.message),
            )
            Metrics.COUNT_CALL_PDL_GT_FAIL.increment()
            throw e
        }
    }

    private fun getPdlQuery(queryFilePath: String): String =
        this::class.java.getResource(queryFilePath)!!
            .readText()
            .replace("[\n\r]", "")

    companion object {
        private const val PDL_QUERY_HENT_PERSON_PATH = "/pdl/hentPerson.graphql"
        private const val PDL_QUERY_GEOGRAFISK_TILKNYTNING_PATH = "/pdl/hentGeografiskTilknytning.graphql"

        const val GT_HEADER = "geografisktilknytning"

        // Se behandlingskatalog https://behandlingskatalog.intern.nav.no/
        // Behandling: Sykefraværsoppfølging: Vurdere behov for oppfølging og rett til sykepenger etter §§ 8-4 og 8-8
        private const val BEHANDLINGSNUMMER_HEADER_KEY = "behandlingsnummer"
        private const val BEHANDLINGSNUMMER_HEADER_VALUE = "B426"

        private val logger = LoggerFactory.getLogger(PdlClient::class.java)
    }
}

private class Metrics {
    companion object {
        const val CALL_PDL_PERSON_BASE = "${METRICS_NS}_call_pdl_person"
        const val CALL_PDL_PERSON_SUCCESS = "${CALL_PDL_PERSON_BASE}_success_count"
        const val CALL_PDL_PERSON_FAIL = "${CALL_PDL_PERSON_BASE}_fail_count"
        const val CALL_PDL_GT_SUCCESS = "${CALL_PDL_PERSON_BASE}_gt_success_count"
        const val CALL_PDL_GT_FAIL = "${CALL_PDL_PERSON_BASE}_gt_fail_count"

        val COUNT_CALL_PDL_PERSON_SUCCESS: Counter = Counter.builder(CALL_PDL_PERSON_SUCCESS)
            .description("Counts the number of successful calls to pdl - person")
            .register(METRICS_REGISTRY)
        val COUNT_CALL_PDL_PERSON_FAIL: Counter = Counter.builder(CALL_PDL_PERSON_FAIL)
            .description("Counts the number of failed calls to pdl - person")
            .register(METRICS_REGISTRY)
        val COUNT_CALL_PDL_GT_SUCCESS: Counter = Counter.builder(CALL_PDL_GT_SUCCESS)
            .description("Counts the number of successful calls to pdl - gt")
            .register(METRICS_REGISTRY)
        val COUNT_CALL_PDL_GT_FAIL: Counter = Counter.builder(CALL_PDL_GT_FAIL)
            .description("Counts the number of failed calls to pdl - gt")
            .register(METRICS_REGISTRY)
    }
}
