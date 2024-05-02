package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.domain.Status
import no.nav.syfo.domain.VedtakStatus
import java.time.OffsetDateTime
import java.util.*

data class PVedtakStatus(
    val id: Int,
    val uuid: UUID,
    val vedtak_id: Int,
    val createdAt: OffsetDateTime,
    val veilederident: String,
    val status: String,
) {
    fun toVedtakStatus() = VedtakStatus(
        uuid = uuid,
        createdAt = createdAt,
        veilederident = veilederident,
        status = Status.valueOf(status),
    )
}
