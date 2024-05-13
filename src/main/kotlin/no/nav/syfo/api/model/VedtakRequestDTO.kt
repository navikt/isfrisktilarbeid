package no.nav.syfo.api.model

import no.nav.syfo.domain.DocumentComponent
import java.time.LocalDate
import java.util.*

data class VedtakRequestDTO(
    val begrunnelse: String,
    val document: List<DocumentComponent>,
    val fom: LocalDate,
    val tom: LocalDate,
)
