package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

data class Behandlermelding private constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val behandlerRef: UUID,
    val document: List<DocumentComponent>,
    val journalpostId: JournalpostId?,
    val publishedAt: OffsetDateTime?,
) {
    constructor(
        behandlerRef: UUID,
        document: List<DocumentComponent>,
    ) : this(
        uuid = UUID.randomUUID(),
        createdAt = nowUTC(),
        behandlerRef = behandlerRef,
        document = document,
        journalpostId = null,
        publishedAt = null,
    )

    fun journalfor(journalpostId: JournalpostId): Behandlermelding = this.copy(journalpostId = journalpostId)

    fun publish(): Behandlermelding = this.copy(publishedAt = nowUTC())

    companion object {

        fun fromDatabase(
            uuid: UUID,
            createdAt: OffsetDateTime,
            behandlerRef: UUID,
            document: List<DocumentComponent>,
            journalpostId: JournalpostId?,
            publishedAt: OffsetDateTime?,
        ) =
            Behandlermelding(
                uuid = uuid,
                createdAt = createdAt,
                behandlerRef = behandlerRef,
                document = document,
                journalpostId = journalpostId,
                publishedAt = publishedAt,
            )
    }
}
