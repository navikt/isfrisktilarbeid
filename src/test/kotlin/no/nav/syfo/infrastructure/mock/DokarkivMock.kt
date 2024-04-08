package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.JournalpostRequest
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.JournalpostResponse

const val journalpostId = 123
val mockedJournalpostId = JournalpostId(journalpostId.toString())
val dokarkivResponse = JournalpostResponse(
    journalpostId = journalpostId,
    journalpostferdigstilt = true,
    journalstatus = "status",
)

val dokarkivConflictResponse = JournalpostResponse(
    journalpostId = 2,
    journalpostferdigstilt = true,
    journalstatus = "conflict",
)

suspend fun MockRequestHandleScope.dokarkivMockResponse(request: HttpRequestData): HttpResponseData {
    val journalpostRequest = request.receiveBody<JournalpostRequest>()
    val eksternReferanseId = journalpostRequest.eksternReferanseId

    return when (eksternReferanseId) {
        UserConstants.EXISTING_EKSTERN_REFERANSE_UUID.toString() -> respond(dokarkivConflictResponse, HttpStatusCode.Conflict)
        UserConstants.FAILING_EKSTERN_REFERANSE_UUID.toString() -> respondError(HttpStatusCode.InternalServerError, "Journalføring failed")
        else -> respond(dokarkivResponse)
    }
}
