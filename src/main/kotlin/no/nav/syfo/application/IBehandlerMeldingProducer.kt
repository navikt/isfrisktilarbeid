package no.nav.syfo.application

import no.nav.syfo.domain.BehandlerMelding

interface IBehandlerMeldingProducer {
    fun send(behandlermelding: BehandlerMelding, behandlerNavn: String): Result<BehandlerMelding>
}
