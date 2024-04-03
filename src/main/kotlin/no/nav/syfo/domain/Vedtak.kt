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
    )
}
