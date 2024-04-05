package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

data class BehandlerMelding private constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val behandlerRef: UUID,
    val document: List<DocumentComponent>,
    val journalpostId: JournalpostId?,
) {
    constructor(
        behandlerRef: UUID,
        document: List<DocumentComponent>,
    ) : this(
        uuid = behandlerRef,
        createdAt = nowUTC(),
        behandlerRef = behandlerRef,
        document = document,
        journalpostId = null,
    )

    fun journalfor(journalpostId: JournalpostId): BehandlerMelding = this.copy(journalpostId = journalpostId)

    companion object {

        fun fromDatabase(
            uuid: UUID,
            createdAt: OffsetDateTime,
            behandlerRef: UUID,
            document: List<DocumentComponent>,
            journalpostId: JournalpostId?,
        ) =
            BehandlerMelding(
                uuid = uuid,
                createdAt = createdAt,
                behandlerRef = behandlerRef,
                document = document,
                journalpostId = journalpostId,
            )
    }
}
