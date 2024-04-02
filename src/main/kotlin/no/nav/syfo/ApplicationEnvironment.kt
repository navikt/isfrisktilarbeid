package no.nav.syfo

import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.clients.ClientEnvironment
import no.nav.syfo.infrastructure.clients.ClientsEnvironment
import no.nav.syfo.infrastructure.clients.OpenClientEnvironment
import no.nav.syfo.infrastructure.clients.azuread.AzureEnvironment
import no.nav.syfo.infrastructure.database.DatabaseEnvironment
import no.nav.syfo.infrastructure.mq.MQEnvironment

const val NAIS_DATABASE_ENV_PREFIX = "NAIS_DATABASE_ISFRISKTILARBEID_ISFRISKTILARBEID_DB"

data class Environment(
    val database: DatabaseEnvironment = DatabaseEnvironment(
        host = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_HOST"),
        port = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PORT"),
        name = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_DATABASE"),
        username = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_USERNAME"),
        password = getEnvVar("${NAIS_DATABASE_ENV_PREFIX}_PASSWORD"),
    ),
    val azure: AzureEnvironment =
        AzureEnvironment(
            appClientId = getEnvVar("AZURE_APP_CLIENT_ID"),
            appClientSecret = getEnvVar("AZURE_APP_CLIENT_SECRET"),
            appWellKnownUrl = getEnvVar("AZURE_APP_WELL_KNOWN_URL"),
            openidConfigTokenEndpoint = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT")
        ),
    val mq: MQEnvironment =
        MQEnvironment(
            mqQueueManager = getEnvVar("MQGATEWAY_NAME"),
            mqHostname = getEnvVar("MQGATEWAY_HOSTNAME"),
            mqPort = getEnvVar("MQGATEWAY_PORT", "1413").toInt(),
            mqChannelName = getEnvVar("MQGATEWAY_CHANNEL_NAME"),
            mqQueueName = getEnvVar("MQ_QUEUE_NAME"),
            serviceuserUsername = getEnvVar("SERVICEUSER_USERNAME"),
            serviceuserPassword = getEnvVar("SERVICEUSER_PASSWORD"),
        ),
    val testident: Personident = Personident(getEnvVar("TEST_IDENT")),
    val electorPath: String = getEnvVar("ELECTOR_PATH"),
    val clients: ClientsEnvironment =
        ClientsEnvironment(
            istilgangskontroll = ClientEnvironment(
                baseUrl = getEnvVar("ISTILGANGSKONTROLL_URL"),
                clientId = getEnvVar("ISTILGANGSKONTROLL_CLIENT_ID")
            ),
            ispdfgen = OpenClientEnvironment(
                baseUrl = "http://ispdfgen"
            ),
            pdl = ClientEnvironment(
                baseUrl = getEnvVar("PDL_URL"),
                clientId = getEnvVar("PDL_CLIENT_ID"),
            ),
            dokarkiv = ClientEnvironment(
                baseUrl = getEnvVar("DOKARKIV_URL"),
                clientId = getEnvVar("DOKARKIV_CLIENT_ID"),
            ),
        ),
)

fun getEnvVar(
    varName: String,
    defaultValue: String? = null
) = System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun isLocal() = getEnvVar("KTOR_ENV", "local") == "local"
