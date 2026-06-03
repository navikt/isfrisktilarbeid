package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS
import no.nav.syfo.common.mock.MockTilgangResponse
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER

fun MockRequestHandleScope.tilgangskontrollResponse(request: HttpRequestData): HttpResponseData {
    return when (request.headers[NAV_PERSONIDENT_HEADER]) {
        ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS.value -> respond(
            MockTilgangResponse(
                erGodkjent = false,
                fullTilgang = false
            )
        )

        else -> respond(MockTilgangResponse(erGodkjent = true, fullTilgang = true))
    }
}
