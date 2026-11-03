package no.nav.syfo.infrastructure.journalforing

import no.nav.syfo.common.journalforing.Brevkode

enum class BrevkodeType(override val value: String) : Brevkode {
    VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING("OPPF_VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING"),
}
