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
    val infotrygdStatus: InfotrygdStatus,
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
                veilederident = veilederident,
                status = Status.FATTET,
            )
        ),
        infotrygdStatus = InfotrygdStatus.IKKE_SENDT,
    )

    fun journalfor(journalpostId: JournalpostId): Vedtak = this.copy(journalpostId = journalpostId)

    fun addVedtakstatus(vedtakStatus: VedtakStatus): Vedtak = this.copy(
        statusListe = this.statusListe.toMutableList().also {
            it.add(vedtakStatus)
        }
    )

    fun isFerdigbehandlet(): Boolean = statusListe.any { it.status == Status.FERDIG_BEHANDLET }

    fun getFattetStatus(): VedtakStatus = statusListe.first { it.status == Status.FATTET }

    fun getFerdigbehandletStatus(): VedtakStatus? = statusListe.firstOrNull { it.status == Status.FERDIG_BEHANDLET }

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
            infotrygdStatus: InfotrygdStatus,
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
            infotrygdStatus = infotrygdStatus,
        )
    }
}
