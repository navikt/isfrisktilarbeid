package no.nav.syfo.domain

import java.time.OffsetDateTime
import java.util.*

data class VedtakStatus(
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val veilederident: String,
    val status: Status,
)

enum class Status {
    FATTET,
    FERDIG_BEHANDLET,
}
