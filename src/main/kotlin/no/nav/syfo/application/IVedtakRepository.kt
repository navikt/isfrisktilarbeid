package no.nav.syfo.application

import no.nav.syfo.domain.BehandlerMelding
import no.nav.syfo.domain.Vedtak

interface IVedtakRepository {
    fun createVedtak(
        vedtak: Vedtak,
        vedtakPdf: ByteArray,
        behandlerMelding: BehandlerMelding,
        behandlerMeldingPdf: ByteArray,
    ): Pair<Vedtak, BehandlerMelding>

    fun getNotJournalforteVedtak(): List<Pair<Vedtak, ByteArray>>

    fun update(vedtak: Vedtak)
}
