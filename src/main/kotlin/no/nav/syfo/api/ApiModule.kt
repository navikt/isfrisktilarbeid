package no.nav.syfo.api

import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig
import kotlinx.coroutines.Dispatchers
import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.api.auth.JwtIssuer
import no.nav.syfo.api.auth.JwtIssuerType
import no.nav.syfo.api.auth.installJwtAuthentication
import no.nav.syfo.api.endpoints.metricEndpoints
import no.nav.syfo.api.endpoints.podEndpoints
import no.nav.syfo.api.endpoints.registerVedtakEndpoints
import no.nav.syfo.application.VedtakService
import no.nav.syfo.infrastructure.clients.arbeidssokeroppslag.ArbeidssokeroppslagClient
import no.nav.syfo.common.tilgangskontroll.TilgangDeniedException
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.util.NAV_CALL_ID_HEADER
import no.nav.syfo.common.util.consumerClientId
import no.nav.syfo.infrastructure.clients.wellknown.WellKnown
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.metric.METRICS_REGISTRY
import no.nav.syfo.util.configure
import java.time.Duration
import java.util.*

fun Application.apiModule(
    applicationState: ApplicationState,
    environment: Environment,
    wellKnownInternalAzureAD: WellKnown,
    database: DatabaseInterface,
    tilgangskontrollClient: TilgangskontrollClient,
    vedtakService: VedtakService,
    arbeidssokeroppslagClient: ArbeidssokeroppslagClient,
) {
    installMetrics()
    installCallId()
    installContentNegotiation()
    installStatusPages()
    installJwtAuthentication(
        jwtIssuerList =
        listOf(
            JwtIssuer(
                acceptedAudienceList = listOf(environment.azure.appClientId),
                jwtIssuerType = JwtIssuerType.INTERNAL_AZUREAD,
                wellKnown = wellKnownInternalAzureAD
            )
        )
    )

    routing {
        podEndpoints(applicationState = applicationState, database = database)
        metricEndpoints()
        authenticate(JwtIssuerType.INTERNAL_AZUREAD.name) {
            registerVedtakEndpoints(
                tilgangskontrollClient = tilgangskontrollClient,
                vedtakService = vedtakService,
                arbeidssokeroppslagClient = arbeidssokeroppslagClient,
                dispatcher = Dispatchers.IO.limitedParallelism(20),
            )
        }
    }
}

fun Application.installContentNegotiation() {
    install(ContentNegotiation) {
        jackson { configure() }
    }
}

fun Application.installMetrics() {
    install(MicrometerMetrics) {
        registry = METRICS_REGISTRY
        distributionStatisticConfig =
            DistributionStatisticConfig.Builder()
                .percentilesHistogram(true)
                .maximumExpectedValue(Duration.ofSeconds(20).toNanos().toDouble())
                .build()
    }
}

fun generateCallId(): String = UUID.randomUUID().toString()

fun Application.installCallId() {
    install(CallId) {
        retrieve { it.request.headers[NAV_CALL_ID_HEADER] }
        generate { generateCallId() }
        verify { callId: String -> callId.isNotEmpty() }
        header(NAV_CALL_ID_HEADER)
    }
}

fun Application.installStatusPages() {
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            val callId = call.callId
            val consumerClientId = call.consumerClientId
            val logExceptionMessage = "Caught exception: ${cause.message}"
            val log = call.application.log

            when (cause) {
                is TilgangDeniedException -> {
                    log.atWarn()
                        .setCause(cause)
                        .setMessage(logExceptionMessage)
                        .addKeyValue("callId", callId)
                        .addKeyValue("consumerClientId", consumerClientId)
                        .log()
                }

                else -> {
                    log.atError()
                        .setCause(cause)
                        .setMessage(logExceptionMessage)
                        .addKeyValue("callId", callId)
                        .addKeyValue("consumerClientId", consumerClientId)
                        .log()
                }
            }

            var isUnexpectedException = false

            val responseStatus: HttpStatusCode =
                when (cause) {
                    is ResponseException -> {
                        cause.response.status
                    }

                    is IllegalArgumentException -> {
                        HttpStatusCode.BadRequest
                    }

                    is TilgangDeniedException -> {
                        HttpStatusCode.Forbidden
                    }

                    else -> {
                        isUnexpectedException = true
                        HttpStatusCode.InternalServerError
                    }
                }
            val message =
                if (isUnexpectedException) {
                    "The server reported an unexpected error and cannot complete the request."
                } else {
                    cause.message ?: "Unknown error"
                }
            call.respond(responseStatus, message)
        }
    }
}
