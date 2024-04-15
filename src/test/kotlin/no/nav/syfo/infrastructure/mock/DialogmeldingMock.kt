package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.UserConstants
import no.nav.syfo.infrastructure.clients.behandler.BehandlerDTO
import java.util.*

val behandlerResponse = BehandlerDTO(
    type = "LEGE",
    behandlerRef = UserConstants.BEHANDLER_REF.toString(),
    fnr = UserConstants.BEHANDLER_FNR.value,
    fornavn = "Beate",
    etternavn = "Behandler",
    orgnummer = "123456789",
    kontor = "Kontor",
    adresse = "Adresse",
    postnummer = "1234",
    poststed = "Poststed",
    telefon = "12345678",
    mellomnavn = null,
)

fun MockRequestHandleScope.dialogmeldingBehandlerClientMockResponse(request: HttpRequestData): HttpResponseData {
    val behandlerRefParam = request.url.encodedPath.split("/").last()
    return when (UUID.fromString(behandlerRefParam)) {
        UserConstants.BEHANDLER_REF -> respond(behandlerResponse)
        else -> respondError(HttpStatusCode.NotFound)
    }
}
