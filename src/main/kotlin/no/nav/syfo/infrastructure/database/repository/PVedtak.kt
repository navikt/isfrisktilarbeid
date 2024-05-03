package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Vedtak
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class PVedtak(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val personident: Personident,
    val fom: LocalDate,
    val tom: LocalDate,
    val begrunnelse: String,
    val document: List<DocumentComponent>,
    val journalpostId: String?,
    val pdfId: Int,
    val publishedInfotrygdAt: OffsetDateTime?,
    val varselPublishedAt: OffsetDateTime?,
) {
    fun toVedtak(statusListe: List<PVedtakStatus>): Vedtak = Vedtak.createFromDatabase(
        uuid = uuid,
        personident = personident,
        createdAt = createdAt,
        begrunnelse = begrunnelse,
        document = document,
        fom = fom,
        tom = tom,
        journalpostId = journalpostId?.let { JournalpostId(it) },
        vedtakStatus = statusListe.map {
            it.toVedtakStatus()
        },
    )
}
