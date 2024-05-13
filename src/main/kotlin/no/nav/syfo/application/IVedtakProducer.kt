package no.nav.syfo.application

import no.nav.syfo.domain.Vedtak
import no.nav.syfo.domain.VedtakStatus

interface IVedtakProducer {
    fun sendVedtakStatus(vedtak: Vedtak, vedtakStatus: VedtakStatus): Result<Vedtak>

    fun sendVedtakVarsel(vedtak: Vedtak): Result<Vedtak>
}
