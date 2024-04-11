package no.nav.syfo.application

import no.nav.syfo.domain.Behandlermelding
import no.nav.syfo.domain.Personident

interface IBehandlermeldingProducer {
    fun send(
        personident: Personident,
        behandlermelding: Behandlermelding,
        behandlermeldingPdf: ByteArray,
    ): Result<Behandlermelding>
}
