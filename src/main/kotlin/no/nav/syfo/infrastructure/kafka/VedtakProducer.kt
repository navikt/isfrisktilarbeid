package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.application.IVedtakProducer
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselHendelseProducer

class VedtakProducer(
    private val esyfovarselHendelseProducer: EsyfovarselHendelseProducer,
    private val vedtakFattetProducer: VedtakFattetProducer,
) : IVedtakProducer {
    override fun sendFattetVedtak(vedtak: Vedtak): Result<Vedtak> = vedtakFattetProducer.send(vedtak)

    override fun sendVedtakVarsel(vedtak: Vedtak): Result<Vedtak> = esyfovarselHendelseProducer.sendVedtakVarsel(vedtak)
}
