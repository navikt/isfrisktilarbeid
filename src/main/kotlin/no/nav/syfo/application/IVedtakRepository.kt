package no.nav.syfo.application

import no.nav.syfo.domain.Vedtak

interface IVedtakRepository {
    fun createVedtak(
        vedtak: Vedtak,
        pdf: ByteArray,
    ): Vedtak

    fun getNotJournalforteVedtak(): List<Pair<Vedtak, ByteArray>>

    fun update(vedtak: Vedtak)
}
