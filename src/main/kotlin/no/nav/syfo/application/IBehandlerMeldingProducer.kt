package no.nav.syfo.application

import no.nav.syfo.domain.BehandlerMelding
import no.nav.syfo.domain.Personident

interface IBehandlerMeldingProducer {
    fun send(
        personident: Personident,
        behandlermelding: BehandlerMelding,
        behandlermeldingPdf: ByteArray,
    ): Result<BehandlerMelding>
}
