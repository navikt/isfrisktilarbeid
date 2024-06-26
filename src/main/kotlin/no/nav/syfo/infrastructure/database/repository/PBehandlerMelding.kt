package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.domain.Behandlermelding
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.JournalpostId
import java.time.OffsetDateTime
import java.util.*

data class PBehandlerMelding(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
    val behandlerRef: UUID,
    val document: List<DocumentComponent>,
    val journalpostId: JournalpostId?,
    val publishedAt: OffsetDateTime?,
    val vedtakId: Int,
    val pdfId: Int,
) {
    fun toBehandlermelding(): Behandlermelding = Behandlermelding.fromDatabase(
        uuid = uuid,
        createdAt = createdAt,
        behandlerRef = behandlerRef,
        document = document,
        journalpostId = journalpostId,
        publishedAt = publishedAt,
    )
}
