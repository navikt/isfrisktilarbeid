package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.clients.pdl.dto.*

suspend fun MockRequestHandleScope.pdlMockResponse(request: HttpRequestData): HttpResponseData {
    val pdlRequest = request.receiveBody<PdlHentPersonRequest>()
    return when (Personident(pdlRequest.variables.ident)) {
        UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME -> respond(generatePdlPersonResponse(pdlPersonNavn = null))
        UserConstants.ARBEIDSTAKER_PERSONIDENT_NAME_WITH_DASH -> respond(
            generatePdlPersonResponse(
                PdlPersonNavn(
                    fornavn = UserConstants.PERSON_FORNAVN_DASH,
                    mellomnavn = UserConstants.PERSON_MELLOMNAVN,
                    etternavn = UserConstants.PERSON_ETTERNAVN,
                )
            )
        )

        UserConstants.ARBEIDSTAKER_PERSONIDENT_PDL_FAILS -> respond(generatePdlPersonResponse(errors = generatePdlError()))
        else -> respond(generatePdlPersonResponse(generatePdlPersonNavn()))
    }
}

fun generatePdlPersonResponse(pdlPersonNavn: PdlPersonNavn? = null, errors: List<PdlError>? = null) = PdlPersonResponse(
    errors = errors,
    data = generatePdlHentPerson(pdlPersonNavn)
)

fun generatePdlPersonNavn(): PdlPersonNavn = PdlPersonNavn(
    fornavn = UserConstants.PERSON_FORNAVN,
    mellomnavn = UserConstants.PERSON_MELLOMNAVN,
    etternavn = UserConstants.PERSON_ETTERNAVN,
)

fun generatePdlHentPerson(
    pdlPersonNavn: PdlPersonNavn?,
): PdlHentPerson = PdlHentPerson(
    hentPerson = PdlPerson(
        navn = if (pdlPersonNavn != null) listOf(pdlPersonNavn) else emptyList(),
    )
)

fun generatePdlError() = listOf(
    PdlError(
        message = "Error in PDL",
        locations = emptyList(),
        path = null,
        extensions = PdlErrorExtension(
            code = null,
            classification = "",
        )
    )
)
