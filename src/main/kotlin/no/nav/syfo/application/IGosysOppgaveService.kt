package no.nav.syfo.application

import no.nav.syfo.domain.GosysOppgaveId
import no.nav.syfo.domain.Vedtak

interface IGosysOppgaveService {
    suspend fun createGosysOppgave(
        vedtak: Vedtak,
    ): Result<GosysOppgaveId>
}
