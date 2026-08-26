package no.nav.syfo.infrastructure.clients.arbeidssokeroppslag

import java.time.Instant

data class ArbeidssokerperiodeResponse(
    val startet: MetadataResponse,
    val avsluttet: MetadataResponse? = null
) {
    val isArbeidssoker: Boolean
        get() {
            val now = Instant.now()
            return startet.tidspunkt.isBefore(now) &&
                (avsluttet == null || avsluttet.tidspunkt.isAfter(now))
        }
}

data class MetadataResponse(
    val tidspunkt: Instant,
)
