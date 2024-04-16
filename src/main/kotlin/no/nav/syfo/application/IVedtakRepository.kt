package no.nav.syfo.application

import no.nav.syfo.domain.Behandlermelding
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Vedtak

interface IVedtakRepository {
    fun createVedtak(
        vedtak: Vedtak,
        vedtakPdf: ByteArray,
        behandlermelding: Behandlermelding,
        behandlermeldingPdf: ByteArray,
    ): Pair<Vedtak, Behandlermelding>

    fun getVedtak(personident: Personident): List<Vedtak>

    fun getUnpublishedInfotrygd(): List<Vedtak>

    fun setVedtakPublishedInfotrygd(vedtak: Vedtak)

    fun getNotJournalforteVedtak(): List<Pair<Vedtak, ByteArray>>

    fun update(vedtak: Vedtak)

    fun getUnpublishedVedtakVarsler(): List<Vedtak>
}
