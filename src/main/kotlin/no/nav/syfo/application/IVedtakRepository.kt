package no.nav.syfo.application

import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.domain.VedtakStatus
import java.util.UUID

interface IVedtakRepository {
    fun createVedtak(
        vedtak: Vedtak,
        vedtakPdf: ByteArray,
    ): Vedtak

    fun getVedtak(personident: Personident): List<Vedtak>

    fun getVedtak(uuid: UUID): Vedtak

    fun getVedtak(id: Int): Vedtak?

    fun getVedtakId(uuid: UUID): Int

    fun getUnpublishedInfotrygd(): List<Vedtak>

    fun setVedtakPublishedInfotrygd(vedtak: Vedtak)

    fun getNotJournalforteVedtak(): List<Pair<Vedtak, ByteArray>>

    fun setJournalpostId(vedtak: Vedtak)

    fun getUnpublishedVedtakVarsler(): List<Vedtak>

    fun setVedtakVarselPublished(vedtak: Vedtak)

    fun getUnpublishedVedtakStatus(): List<Pair<Vedtak, VedtakStatus>>

    fun addVedtakStatus(vedtak: Vedtak, vedtakStatus: VedtakStatus)

    fun setVedtakStatusPublished(vedtakStatus: VedtakStatus)

    fun setInfotrygdKvitteringReceived(vedtak: Vedtak, ok: Boolean, feilmelding: String?)
}
