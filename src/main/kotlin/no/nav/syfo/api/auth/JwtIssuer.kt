package no.nav.syfo.api.auth

import no.nav.syfo.common.auth.WellKnown

data class JwtIssuer(
    val acceptedAudienceList: List<String>,
    val jwtIssuerType: JwtIssuerType,
    val wellKnown: WellKnown
)

enum class JwtIssuerType {
    INTERNAL_AZUREAD
}
