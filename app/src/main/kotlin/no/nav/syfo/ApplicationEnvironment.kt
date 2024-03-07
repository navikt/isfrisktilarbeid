package no.nav.syfo

import no.nav.syfo.infrastructure.ClientEnvironment
import no.nav.syfo.infrastructure.ClientsEnvironment
import no.nav.syfo.infrastructure.clients.azuread.AzureEnvironment

data class Environment(
    val azure: AzureEnvironment =
        AzureEnvironment(
            appClientId = getEnvVar("AZURE_APP_CLIENT_ID"),
            appClientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
            appWellKnownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
            openidConfigTokenEndpoint = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")
        ),
    val electorPath: String = getEnvVar("ELECTOR_PATH"),
    val clients: ClientsEnvironment =
        ClientsEnvironment(
            istilgangskontroll = ClientEnvironment(
                baseUrl = getEnvVar("ISTILGANGSKONTROLL_URL"),
                clientId = getEnvVar("ISTILGANGSKONTROLL_CLIENT_ID")
            ),
            dokarkiv = ClientEnvironment(
                baseUrl = getEnvVar("DOKARKIV_URL"),
                clientId = getEnvVar("DOKARKIV_CLIENT_ID"),
            ),
            pdl = ClientEnvironment(
                baseUrl = getEnvVar("PDL_URL"),
                clientId = getEnvVar("PDL_CLIENT_ID"),
            ),
        ),
    val publishForhandsvarselEnabled: Boolean = getEnvVar("TOGGLE_PUBLISH_FORHANDSVARSEL").toBoolean()
)

fun getEnvVar(
    varName: String,
    defaultValue: String? = null
) = System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun isLocal() = getEnvVar("KTOR_ENV", "local") == "local"
