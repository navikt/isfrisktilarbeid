package no.nav.syfo.infrastructure

data class ClientsEnvironment(
    val istilgangskontroll: ClientEnvironment,
    val dokarkiv: ClientEnvironment,
    val pdl: ClientEnvironment,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)
