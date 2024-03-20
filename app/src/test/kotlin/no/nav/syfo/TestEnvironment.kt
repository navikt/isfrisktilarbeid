package no.nav.syfo

import no.nav.syfo.infrastructure.ClientEnvironment
import no.nav.syfo.infrastructure.ClientsEnvironment
import no.nav.syfo.infrastructure.clients.azuread.AzureEnvironment
import no.nav.syfo.infrastructure.database.DatabaseEnvironment
import no.nav.syfo.infrastructure.mq.MQEnvironment

fun testEnvironment() = Environment(
    database = DatabaseEnvironment(
        host = "localhost",
        port = "5432",
        name = "isaktivitetskrav_dev",
        username = "username",
        password = "password",
    ),
    azure = AzureEnvironment(
        appClientId = "isarbeidsuforhet-client-id",
        appClientSecret = "isarbeidsuforhet-secret",
        appWellKnownUrl = "wellknown",
        openidConfigTokenEndpoint = "azureOpenIdTokenEndpoint",
    ),
    clients = ClientsEnvironment(
        istilgangskontroll = ClientEnvironment(
            baseUrl = "isTilgangskontrollUrl",
            clientId = "dev-gcp.teamsykefravr.istilgangskontroll",
        ),
        pdl = ClientEnvironment(
            baseUrl = "pdlUrl",
            clientId = "pdlClientId",
        ),
    ),
    mq = MQEnvironment(
        mqQueueManager = "mqQueueManager",
        mqHostname = "mqHostname",
        mqPort = 1414,
        mqChannelName = "mqChannelName",
        mqQueueName = "mqQueueName",
        serviceuserUsername = "serviceuser",
        serviceuserPassword = "servicepw",
    ),
    electorPath = "electorPath",
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
