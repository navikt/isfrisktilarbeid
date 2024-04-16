package no.nav.syfo.application

import no.nav.syfo.domain.Vedtak

interface IVedtakFattetProducer {
    fun send(vedtak: Vedtak): Result<Vedtak>
}
