package no.nav.syfo.domain

import no.nav.syfo.infrastructure.journalforing.JournalforingService.Companion.DEFAULT_FAILED_JP_ID
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
    val gosysOppgaveId: GosysOppgaveId?,
    val gosysOppgaveAt: OffsetDateTime?,
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
        gosysOppgaveId = null,
        gosysOppgaveAt = null,
    )

    fun journalfor(journalpostId: JournalpostId): Vedtak = this.copy(journalpostId = journalpostId)

    fun isJournalfort(): Boolean = journalpostId != null && journalpostId.value != DEFAULT_FAILED_JP_ID.toString()

    fun setGosysOppgaveId(gosysOppgaveId: GosysOppgaveId): Vedtak = this.copy(gosysOppgaveId = gosysOppgaveId, gosysOppgaveAt = nowUTC())

    fun sendTilInfotrygd(): Vedtak = this.copy(infotrygdStatus = InfotrygdStatus.KVITTERING_MANGLER)

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
            gosysOppgaveId: GosysOppgaveId?,
            gosysOppgaveAt: OffsetDateTime?,
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
            gosysOppgaveId = gosysOppgaveId,
            gosysOppgaveAt = gosysOppgaveAt,
            statusListe = vedtakStatus,
            infotrygdStatus = infotrygdStatus,
        )
    }
}
