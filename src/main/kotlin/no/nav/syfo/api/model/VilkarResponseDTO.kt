package no.nav.syfo.api.model

import java.time.Instant

data class VilkarResponseDTO(
    val isArbeidssoker: Boolean,
    val arbeidssokerFom: Instant?,
)
