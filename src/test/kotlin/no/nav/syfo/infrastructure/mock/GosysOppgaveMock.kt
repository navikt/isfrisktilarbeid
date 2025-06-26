package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.infrastructure.clients.gosysoppgave.OppgaveRequest
import no.nav.syfo.infrastructure.clients.gosysoppgave.OppgaveResponse

const val gosysOppgaveId = "456"
val oppgaveResponse = OppgaveResponse(
    id = gosysOppgaveId
)

suspend fun MockRequestHandleScope.oppgaveMockResponse(request: HttpRequestData): HttpResponseData {
    val oppgaveRequest = request.receiveBody<OppgaveRequest>()
    return respond(oppgaveResponse)
}
