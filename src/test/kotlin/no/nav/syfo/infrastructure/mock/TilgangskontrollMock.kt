package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.UserConstants.ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER

fun MockRequestHandleScope.tilgangskontrollResponse(request: HttpRequestData): HttpResponseData {
    val erGodkjent = request.headers[NAV_PERSONIDENT_HEADER] != ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS.value
    return respond(
        content = """{"erGodkjent":$erGodkjent,"fullTilgang":$erGodkjent}""",
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )
}
