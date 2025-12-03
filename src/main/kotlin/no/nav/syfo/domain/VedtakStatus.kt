package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

/**
 * VedtakStatus representerer en status for et vedtak for § 8-5 friskmelding til arbeidsformidling.
 *
 * Det finnes to typer statuser:
 * - `FATTET` - Når selve vedtaket er fattet av en veileder. Det kan likevel være videre prosesser etter at man fatter et vedtak som gjør at man som veileder ønsker å fortsette å se vedtaket som en aktiv oppgave, f.eks. overgang til arbeidsrettet oppfølging, koordinering med ny veileder eller videre oppfølging av sykmeldt.
 * - `FERDIG_BEHANDLET` - Når et vedtak er ferdigbehandlet av veileder. Betyr at veileder er ferdig med behandlingen av vedtaket. Vedtaket vil da ikke lenger være en aktiv oppgave for veileder.
 *
 */
data class VedtakStatus private constructor(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val veilederident: String,
    val status: Status,
) {
    constructor(
        veilederident: String,
        status: Status,
    ) : this(
        uuid = UUID.randomUUID(),
        createdAt = nowUTC(),
        veilederident = veilederident,
        status = status,
    )

    companion object {

        fun createFromDatabase(
            uuid: UUID,
            createdAt: OffsetDateTime,
            veilederident: String,
            status: Status,
        ) = VedtakStatus(
            uuid = uuid,
            createdAt = createdAt,
            veilederident = veilederident,
            status = status,
        )
    }
}

enum class Status {
    FATTET,
    FERDIG_BEHANDLET,
}
