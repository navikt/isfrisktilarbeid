package no.nav.syfo.infrastructure.clients

import no.nav.syfo.common.util.ClientConfig
import no.nav.syfo.common.util.OpenClientConfig

data class ClientsEnvironment(
    val istilgangskontroll: ClientConfig,
    val pdl: ClientConfig,
    val ispdfgen: OpenClientConfig,
    val dokarkiv: ClientConfig,
    val gosysoppgave: ClientConfig,
    val arbeidssokeroppslag: ClientConfig,
)
