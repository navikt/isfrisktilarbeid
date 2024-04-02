package no.nav.syfo

import no.nav.syfo.infrastructure.clients.ClientEnvironment
import no.nav.syfo.infrastructure.clients.ClientsEnvironment
import no.nav.syfo.infrastructure.clients.OpenClientEnvironment
import no.nav.syfo.infrastructure.clients.azuread.AzureEnvironment
import no.nav.syfo.infrastructure.database.DatabaseEnvironment
import no.nav.syfo.infrastructure.mq.MQEnvironment

fun testEnvironment() = Environment(
    database = DatabaseEnvironment(
        host = "localhost",
        port = "5432",
        name = "isfrisktilarbeid_dev",
        username = "username",
        password = "password",
    ),
    azure = AzureEnvironment(
        appClientId = "isfrisktilarbeid-client-id",
        appClientSecret = "isfrisktilarbeid-secret",
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
        ispdfgen = OpenClientEnvironment(
            baseUrl = "ispdfgenurl"
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
