package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.domain.*
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
    val gosysOppgaveId: String?,
    val gosysOppgaveAt: OffsetDateTime?,
    val pdfId: Int,
    val publishedInfotrygdAt: OffsetDateTime?,
    val varselPublishedAt: OffsetDateTime?,
    val infotrygdOk: Boolean?,
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
        gosysOppgaveId = gosysOppgaveId?.let { GosysOppgaveId(it) },
        gosysOppgaveAt = gosysOppgaveAt,
        vedtakStatus = statusListe.map {
            it.toVedtakStatus()
        },
        infotrygdStatus = InfotrygdStatus.create(publishedInfotrygdAt, infotrygdOk),
    )
}
