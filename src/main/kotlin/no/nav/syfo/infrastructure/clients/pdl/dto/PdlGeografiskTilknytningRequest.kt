package no.nav.syfo.infrastructure.clients.pdl.dto

data class PdlGeografiskTilknytningRequest(
    val query: String,
    val variables: PdlGeografiskTilknytningRequestVariables,
)

data class PdlGeografiskTilknytningRequestVariables(
    val ident: String,
)
