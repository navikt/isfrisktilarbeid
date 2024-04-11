package no.nav.syfo.application

import no.nav.syfo.domain.Behandlermelding
import no.nav.syfo.domain.Vedtak

interface IVedtakRepository {
    fun createVedtak(
        vedtak: Vedtak,
        vedtakPdf: ByteArray,
        behandlerMelding: Behandlermelding,
        behandlerMeldingPdf: ByteArray,
    ): Pair<Vedtak, Behandlermelding>

    fun getUnpublishedInfotrygd(): List<Vedtak>

    fun setVedtakPublishedInfotrygd(vedtak: Vedtak)

    fun getNotJournalforteVedtak(): List<Pair<Vedtak, ByteArray>>

    fun update(vedtak: Vedtak)

    fun getUnpublishedVedtakVarsler(): List<Vedtak>
}
