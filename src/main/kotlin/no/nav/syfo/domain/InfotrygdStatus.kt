package no.nav.syfo.domain

import java.time.OffsetDateTime

enum class InfotrygdStatus {
    IKKE_SENDT,
    SKAL_IKKE_SENDES,
    KVITTERING_MANGLER,
    KVITTERING_OK,
    KVITTERING_FEIL;

    companion object {
        fun create(
            publishedInfotrygdAt: OffsetDateTime?,
            infotrygdOk: Boolean?,
            isKorrigering: Boolean = false,
        ): InfotrygdStatus =
            when {
                isKorrigering -> SKAL_IKKE_SENDES
                publishedInfotrygdAt == null -> IKKE_SENDT
                infotrygdOk == null -> KVITTERING_MANGLER
                infotrygdOk -> KVITTERING_OK
                else -> KVITTERING_FEIL
            }
    }
}
