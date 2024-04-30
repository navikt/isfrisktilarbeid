package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class Vedtak private constructor(
    val uuid: UUID,
    val personident: Personident,
    val veilederident: String,
    val createdAt: OffsetDateTime,
    val begrunnelse: String,
    val document: List<DocumentComponent>,
    val fom: LocalDate,
    val tom: LocalDate,
    val journalpostId: JournalpostId?,
    val varselPublishedAt: OffsetDateTime?,
    val publishedAt: OffsetDateTime?,
    val ferdigbehandletAt: OffsetDateTime?,
    val ferdigbehandletBy: String?,
) {
    constructor(
        personident: Personident,
        veilederident: String,
        begrunnelse: String,
        document: List<DocumentComponent>,
        fom: LocalDate,
        tom: LocalDate,
    ) : this(
        uuid = UUID.randomUUID(),
        personident = personident,
        veilederident = veilederident,
        createdAt = nowUTC(),
        begrunnelse = begrunnelse,
        document = document,
        fom = fom,
        tom = tom,
        journalpostId = null,
        varselPublishedAt = null,
        publishedAt = null,
        ferdigbehandletAt = null,
        ferdigbehandletBy = null,
    )

    fun journalfor(journalpostId: JournalpostId): Vedtak = this.copy(journalpostId = journalpostId)

    fun publishVarsel(): Vedtak = this.copy(varselPublishedAt = nowUTC())

    fun setPublished(): Vedtak = this.copy(publishedAt = nowUTC())

    fun ferdigbehandle(veilderIdent: String): Vedtak = this.copy(
        ferdigbehandletAt = nowUTC(),
        ferdigbehandletBy = veilderIdent,
    )

    companion object {

        fun createFromDatabase(
            uuid: UUID,
            personident: Personident,
            veilederident: String,
            createdAt: OffsetDateTime,
            begrunnelse: String,
            document: List<DocumentComponent>,
            fom: LocalDate,
            tom: LocalDate,
            journalpostId: JournalpostId?,
            varselPublishedAt: OffsetDateTime?,
            publishedAt: OffsetDateTime?,
            ferdigbehandletAt: OffsetDateTime?,
            ferdigbehandletBy: String?,
        ) = Vedtak(
            uuid = uuid,
            personident = personident,
            veilederident = veilederident,
            createdAt = createdAt,
            begrunnelse = begrunnelse,
            document = document,
            fom = fom,
            tom = tom,
            journalpostId = journalpostId,
            varselPublishedAt = varselPublishedAt,
            publishedAt = publishedAt,
            ferdigbehandletAt = ferdigbehandletAt,
            ferdigbehandletBy = ferdigbehandletBy,
        )
    }
}
