package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.infrastructure.clients.oppgave.OppgaveRequest
import no.nav.syfo.infrastructure.clients.oppgave.OppgaveResponse

const val oppgaveId = "456"
val oppgaveResponse = OppgaveResponse(
    id = oppgaveId
)

suspend fun MockRequestHandleScope.oppgaveMockResponse(request: HttpRequestData): HttpResponseData {
    val oppgaveRequest = request.receiveBody<OppgaveRequest>()
    return respond(oppgaveResponse)
}
