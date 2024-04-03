package no.nav.syfo.application

import no.nav.syfo.domain.Vedtak

interface IVedtakRepository {
    fun createVedtak(
        vedtak: Vedtak,
        pdf: ByteArray,
    ): Vedtak

    fun getUnpublishedMQVedtak(): List<Vedtak>

    fun setVedtakPublishedMQ(vedtak: Vedtak)

    fun getNotJournalforteVedtak(): List<Pair<Vedtak, ByteArray>>

    fun update(vedtak: Vedtak)
}
