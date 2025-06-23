package no.nav.syfo.infrastructure.gosysoppgave

import no.nav.syfo.application.IOppgaveService
import no.nav.syfo.domain.GosysOppgaveId
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.infrastructure.clients.gosysoppgave.GosysOppgaveClient
import no.nav.syfo.infrastructure.clients.gosysoppgave.OppgaveRequest
import java.time.format.DateTimeFormatter

class GosysOppgaveService(
    val gosysOppgaveClient: GosysOppgaveClient,
) : IOppgaveService {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    override suspend fun createGosysOppgave(vedtak: Vedtak): Result<GosysOppgaveId> = runCatching {
        val response = gosysOppgaveClient.createOppgave(
            request = OppgaveRequest(
                personident = vedtak.personident.value,
                journalpostId = vedtak.journalpostId!!.value,
                tema = TEMA_SYKEPENGER,
                behandlingstema = BEHANDLINGSTEMA_FRISK_TIL_ARBEID,
                oppgavetype = OPPGAVE_TYPE_VURDER_HENVENDELSE,
                tildeltEnhetsnr = NAY_TRONDHEIM,
                beskrivelse = "Innvilget i perioden ${vedtak.fom.format(dateFormatter)} - ${vedtak.tom.format(dateFormatter)}",
                prioritet = PRIORITET_NORMAL,
            ),
            correlationId = vedtak.uuid,
        )
        GosysOppgaveId(response.id)
    }

    companion object {
        const val TEMA_SYKEPENGER = "SYK"
        const val BEHANDLINGSTEMA_FRISK_TIL_ARBEID = "ab0352"
        const val OPPGAVE_TYPE_VURDER_HENVENDELSE = "VURD_HENV"
        const val NAY_TRONDHEIM = "4488"
        const val PRIORITET_NORMAL = "NORM"
    }
}
