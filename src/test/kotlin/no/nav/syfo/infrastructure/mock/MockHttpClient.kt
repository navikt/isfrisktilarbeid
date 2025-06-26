package no.nav.syfo.infrastructure.mock

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import no.nav.syfo.Environment
import no.nav.syfo.infrastructure.clients.commonConfig

fun mockHttpClient(environment: Environment) = HttpClient(MockEngine) {
    commonConfig()
    engine {
        addHandler { request ->
            val requestUrl = request.url.encodedPath
            when {
                requestUrl == "/${environment.azure.openidConfigTokenEndpoint}" -> azureAdMockResponse()
                requestUrl.startsWith("/${environment.clients.istilgangskontroll.baseUrl}") -> tilgangskontrollResponse(
                    request
                )
                requestUrl.startsWith("/${environment.clients.ispdfgen.baseUrl}") -> pdfGenMockResponse(
                    request
                )

                requestUrl.startsWith("/${environment.clients.pdl.baseUrl}") -> pdlMockResponse(request)
                requestUrl.startsWith("/${environment.clients.dokarkiv.baseUrl}") -> dokarkivMockResponse(request)
                requestUrl.startsWith("/${environment.clients.arbeidssokeroppslag.baseUrl}") -> arbeidssokeroppslagMockResponse(request)
                requestUrl.startsWith("/${environment.clients.gosysoppgave.baseUrl}") -> oppgaveMockResponse(request)
                else -> error("Unhandled ${request.url.encodedPath}")
            }
        }
    }
}
