package no.nav.syfo.infrastructure.oppgave

import no.nav.syfo.application.IOppgaveService
import no.nav.syfo.domain.OppgaveId
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.infrastructure.clients.oppgave.OppgaveClient
import no.nav.syfo.infrastructure.clients.oppgave.OppgaveRequest

class OppgaveService(
    val oppgaveClient: OppgaveClient,
) : IOppgaveService {

    override suspend fun createOppgave(vedtak: Vedtak): Result<OppgaveId> = runCatching {
        val response = oppgaveClient.createOppgave(
            request = OppgaveRequest(
                personident = vedtak.personident.value,
                journalpostId = vedtak.journalpostId!!.value,
                tema = "SYK",
                behandlingstema = "ab0352",
                oppgavetype = "VURD_HENV",
                tildeltEnhetsnr = "4488",
                beskrivelse = "Innvilget i perioden (${vedtak.fom} - ${vedtak.tom})",
                prioritet = "NORM",
            ),
            correlationId = vedtak.uuid,
        )
        OppgaveId(response.id)
    }
}
