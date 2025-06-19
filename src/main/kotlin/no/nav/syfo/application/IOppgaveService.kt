package no.nav.syfo.application

import no.nav.syfo.domain.OppgaveId
import no.nav.syfo.domain.Vedtak

interface IOppgaveService {
    suspend fun createOppgave(
        vedtak: Vedtak,
    ): Result<OppgaveId>
}
