package no.nav.syfo.api.model

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.Vedtak
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class VedtakResponseDTO private constructor(
    val uuid: UUID,
    val createdAt: LocalDateTime,
    val personident: String,
    val veilederident: String,
    val begrunnelse: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val document: List<DocumentComponent>,
    val ferdigbehandletAt: LocalDateTime?,
    val ferdigbehandletBy: String?,
    val infotrygdStatus: String,
    val journalfort: Boolean = false,
    val gosysOppgave: Boolean = false,
) {
    companion object {
        fun createFromVedtak(vedtak: Vedtak): VedtakResponseDTO =
            VedtakResponseDTO(
                uuid = vedtak.uuid,
                createdAt = vedtak.createdAt.toLocalDateTime(),
                personident = vedtak.personident.value,
                begrunnelse = vedtak.begrunnelse,
                fom = vedtak.fom,
                tom = vedtak.tom,
                document = vedtak.document,
                veilederident = vedtak.getFattetStatus().veilederident,
                ferdigbehandletAt = vedtak.getFerdigbehandletStatus()?.createdAt?.toLocalDateTime(),
                ferdigbehandletBy = vedtak.getFerdigbehandletStatus()?.veilederident,
                infotrygdStatus = vedtak.infotrygdStatus.name,
                journalfort = vedtak.journalpostId != null,
                gosysOppgave = vedtak.gosysOppgaveId != null,
            )
    }
}
