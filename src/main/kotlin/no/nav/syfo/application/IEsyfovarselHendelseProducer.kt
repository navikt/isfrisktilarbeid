package no.nav.syfo.application

import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Vedtak

interface IEsyfovarselHendelseProducer {
    fun sendVedtakVarsel(
        personident: Personident,
        vedtak: Vedtak
    ): Result<Vedtak>
}
