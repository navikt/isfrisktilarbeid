package no.nav.syfo.infrastructure.database.repository

import java.time.OffsetDateTime
import java.util.*

data class PPdf(
    val id: Int,
    val uuid: UUID,
    val createdAt: OffsetDateTime,
    val pdf: ByteArray,
)
