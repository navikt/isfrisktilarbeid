package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.UserConstants
import no.nav.syfo.infrastructure.clients.arbeidssokeroppslag.ArbeidssokerperiodeRequest
import no.nav.syfo.infrastructure.clients.arbeidssokeroppslag.ArbeidssokerperiodeResponse
import no.nav.syfo.infrastructure.clients.arbeidssokeroppslag.MetadataResponse

val arbeidssokeroppslagMockResponseOld = ArbeidssokerperiodeResponse(
    startet = MetadataResponse(
        tidspunkt = java.time.Instant.now().minusSeconds(60 * 60 * 24 * 365)
    ),
    avsluttet = MetadataResponse(
        tidspunkt = java.time.Instant.now().minusSeconds(60 * 60 * 24 * 300)
    ),
)

val arbeidssokeroppslagMockResponse = ArbeidssokerperiodeResponse(
    startet = MetadataResponse(
        tidspunkt = java.time.Instant.now().minusSeconds(60)
    ),
    avsluttet = null,
)

suspend fun MockRequestHandleScope.arbeidssokeroppslagMockResponse(request: HttpRequestData): HttpResponseData {
    val arbeidssokerperiodeRequest = request.receiveBody<ArbeidssokerperiodeRequest>()
    return respond(
        if (arbeidssokerperiodeRequest.identitetsnummer == UserConstants.ARBEIDSTAKER_PERSONIDENT_UTLAND.value) {
            listOf(arbeidssokeroppslagMockResponseOld)
        } else {
            listOf(arbeidssokeroppslagMockResponse)
        }
    )
}
