package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

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
