package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.application.IVedtakProducer
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.domain.VedtakStatus

class VedtakProducer(
    private val vedtakStatusProducer: VedtakStatusProducer,
) : IVedtakProducer {
    override fun sendVedtakStatus(vedtak: Vedtak, vedtakStatus: VedtakStatus): Result<Vedtak> = vedtakStatusProducer.send(vedtak, vedtakStatus)
}
