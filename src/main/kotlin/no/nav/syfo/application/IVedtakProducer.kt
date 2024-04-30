package no.nav.syfo.application

import no.nav.syfo.domain.Vedtak

interface IVedtakProducer {
    fun sendVedtakStatus(vedtak: Vedtak): Result<Vedtak>

    fun sendVedtakVarsel(vedtak: Vedtak): Result<Vedtak>
}
