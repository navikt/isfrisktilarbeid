package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class Vedtak private constructor(
    val uuid: UUID,
    val personident: Personident,
    val createdAt: OffsetDateTime,
    val begrunnelse: String,
    val document: List<DocumentComponent>,
    val fom: LocalDate,
    val tom: LocalDate,
    val journalpostId: JournalpostId?,
    val statusListe: List<VedtakStatus>,
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
        createdAt = nowUTC(),
        begrunnelse = begrunnelse,
        document = document,
        fom = fom,
        tom = tom,
        journalpostId = null,
        statusListe = listOf(
            VedtakStatus(
                uuid = UUID.randomUUID(),
                createdAt = nowUTC(),
                veilederident = veilederident,
                status = Status.FATTET,
            )
        ),
    )

    fun journalfor(journalpostId: JournalpostId): Vedtak = this.copy(journalpostId = journalpostId)

    fun ferdigbehandle(ferdigbehandletVeilederident: String): Vedtak = this.copy(
        statusListe = this.statusListe.toMutableList().also {
            it.add(
                VedtakStatus(
                    uuid = UUID.randomUUID(),
                    createdAt = nowUTC(),
                    veilederident = ferdigbehandletVeilederident,
                    status = Status.FERDIG_BEHANDLET,
                )
            )
        }
    )

    companion object {

        fun createFromDatabase(
            uuid: UUID,
            personident: Personident,
            createdAt: OffsetDateTime,
            begrunnelse: String,
            document: List<DocumentComponent>,
            fom: LocalDate,
            tom: LocalDate,
            journalpostId: JournalpostId?,
            vedtakStatus: List<VedtakStatus>,
        ) = Vedtak(
            uuid = uuid,
            personident = personident,
            createdAt = createdAt,
            begrunnelse = begrunnelse,
            document = document,
            fom = fom,
            tom = tom,
            journalpostId = journalpostId,
            statusListe = vedtakStatus,
        )
    }
}
