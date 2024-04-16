package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.clients.pdl.dto.*

suspend fun MockRequestHandleScope.pdlMockResponse(request: HttpRequestData): HttpResponseData {
    val pdlRequest = request.receiveBody<PdlHentPersonRequest>()
    return if (pdlRequest.query.contains("hentPerson")) {
        when (Personident(pdlRequest.variables.ident)) {
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
            UserConstants.ARBEIDSTAKER_PERSONIDENT_GRADERT -> respond(
                generatePdlPersonResponse(generatePdlPersonNavn(), null, listOf(Gradering.STRENGT_FORTROLIG))
            )
            else -> respond(generatePdlPersonResponse(generatePdlPersonNavn()))
        }
    } else if (pdlRequest.query.contains("hentGeografiskTilknytning")) {
        respond(generateGeografisktilknytingResponse())
    } else throw RuntimeException()
}

fun generatePdlPersonResponse(
    pdlPersonNavn: PdlPersonNavn? = null,
    errors: List<PdlError>? = null,
    gradert: List<Gradering> = emptyList(),
) = PdlPersonResponse(
    errors = errors,
    data = generatePdlHentPerson(pdlPersonNavn, gradert),
)

fun generateGeografisktilknytingResponse() = PdlGeografiskTilknytningResponse(
    data = PdlHentGeografiskTilknytning(
        hentGeografiskTilknytning = PdlGeografiskTilknytning(
            gtType = PdlGeografiskTilknytningType.KOMMUNE.name,
            gtKommune = UserConstants.KOMMUNE,
            gtBydel = null,
            gtLand = null,
        )
    ),
    errors = emptyList(),
)

fun generatePdlPersonNavn(): PdlPersonNavn = PdlPersonNavn(
    fornavn = UserConstants.PERSON_FORNAVN,
    mellomnavn = UserConstants.PERSON_MELLOMNAVN,
    etternavn = UserConstants.PERSON_ETTERNAVN,
)

fun generatePdlHentPerson(
    pdlPersonNavn: PdlPersonNavn?,
    gradering: List<Gradering> = emptyList(),
): PdlHentPerson = PdlHentPerson(
    hentPerson = PdlPerson(
        navn = if (pdlPersonNavn != null) listOf(pdlPersonNavn) else emptyList(),
        adressebeskyttelse = gradering.map { Adressebeskyttelse(it) },
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
