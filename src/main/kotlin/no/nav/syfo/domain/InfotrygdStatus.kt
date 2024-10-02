package no.nav.syfo.domain

import java.time.OffsetDateTime

enum class InfotrygdStatus {
    IKKE_SENDT,
    KVITTERING_MANGLER,
    KVITTERING_OK,
    KVITTERING_FEIL;

    companion object {
        fun create(publishedInfotrygdAt: OffsetDateTime?, infotrygdOk: Boolean?): InfotrygdStatus =
            when (publishedInfotrygdAt) {
                null -> {
                    IKKE_SENDT
                }

                else -> when (infotrygdOk) {
                    null -> KVITTERING_MANGLER
                    true -> KVITTERING_OK
                    false -> KVITTERING_FEIL
                }
            }
    }
}
