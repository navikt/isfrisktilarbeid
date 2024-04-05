package no.nav.syfo.application

import no.nav.syfo.domain.BehandlerMelding
import no.nav.syfo.domain.Vedtak

interface IPdfService {
    suspend fun createVedtakPdf(
        vedtak: Vedtak,
        callId: String,
    ): ByteArray

    suspend fun createBehandlerMeldingPdf(
        behandlerMelding: BehandlerMelding,
        behandlerNavn: String,
        callId: String,
    ): ByteArray
}
