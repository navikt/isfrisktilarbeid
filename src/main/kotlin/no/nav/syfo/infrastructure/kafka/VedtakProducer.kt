package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.application.IVedtakProducer
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselHendelseProducer

class VedtakProducer(
    private val esyfovarselHendelseProducer: EsyfovarselHendelseProducer,
    private val vedtakStatusProducer: VedtakStatusProducer,
) : IVedtakProducer {
    override fun sendVedtakStatus(vedtak: Vedtak): Result<Vedtak> = vedtakStatusProducer.send(vedtak)

    override fun sendVedtakVarsel(vedtak: Vedtak): Result<Vedtak> = esyfovarselHendelseProducer.sendVedtakVarsel(vedtak)
}
