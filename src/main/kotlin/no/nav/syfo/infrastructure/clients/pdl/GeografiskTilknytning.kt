package no.nav.syfo.infrastructure.clients.pdl

data class GeografiskTilknytning(
    val type: GeografiskTilknytningType,
    val kommune: String?,
    val bydel: String? = null,
    val land: String? = null,
)

enum class GeografiskTilknytningType {
    BYDEL,
    KOMMUNE,
    UTLAND,
    UDEFINERT
}
