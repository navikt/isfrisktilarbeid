package no.nav.syfo.application

import no.nav.syfo.domain.GosysOppgaveId
import no.nav.syfo.domain.Vedtak

interface IOppgaveService {
    suspend fun createGosysOppgave(
        vedtak: Vedtak,
    ): Result<GosysOppgaveId>
}
