package no.nav.syfo.infrastructure.clients

data class ClientsEnvironment(
    val istilgangskontroll: ClientEnvironment,
    val pdl: ClientEnvironment,
    val ispdfgen: OpenClientEnvironment,
    val dokarkiv: ClientEnvironment,
    val gosysoppgave: ClientEnvironment,
    val arbeidssokeroppslag: ClientEnvironment,
)

data class ClientEnvironment(
    val baseUrl: String,
    val clientId: String,
)

data class OpenClientEnvironment(
    val baseUrl: String,
)
