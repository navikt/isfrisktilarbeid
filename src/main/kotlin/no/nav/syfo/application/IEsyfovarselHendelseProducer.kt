package no.nav.syfo.application

import no.nav.syfo.domain.Vedtak

interface IEsyfovarselHendelseProducer {
    fun sendVedtakVarsel(vedtak: Vedtak): Result<Vedtak>
}
