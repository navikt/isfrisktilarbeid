package no.nav.syfo.infrastructure.clients.arbeidssokeroppslag

data class ArbeidssokerperiodeResponse(
    val startet: MetadataResponse,
    val avsluttet: MetadataResponse? = null
)

data class MetadataResponse(
    val tidspunkt: java.time.Instant,
)
