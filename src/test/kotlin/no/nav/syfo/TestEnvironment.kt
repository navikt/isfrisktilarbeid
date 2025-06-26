package no.nav.syfo

import no.nav.syfo.infrastructure.clients.ClientEnvironment
import no.nav.syfo.infrastructure.clients.ClientsEnvironment
import no.nav.syfo.infrastructure.clients.OpenClientEnvironment
import no.nav.syfo.infrastructure.clients.azuread.AzureEnvironment
import no.nav.syfo.infrastructure.database.DatabaseEnvironment
import no.nav.syfo.infrastructure.kafka.KafkaEnvironment
import no.nav.syfo.infrastructure.mq.MQEnvironment

fun testEnvironment() = Environment(
    database = DatabaseEnvironment(
        host = "localhost",
        port = "5432",
        name = "isfrisktilarbeid_dev",
        username = "username",
        password = "password",
    ),
    kafka = KafkaEnvironment(
        aivenBootstrapServers = "kafkaBootstrapServers",
        aivenCredstorePassword = "credstorepassord",
        aivenKeystoreLocation = "keystore",
        aivenSecurityProtocol = "SSL",
        aivenTruststoreLocation = "truststore",
        aivenSchemaRegistryUrl = "http://kafka-schema-registry.tpa.svc.nais.local:8081",
        aivenRegistryUser = "registryuser",
        aivenRegistryPassword = "registrypassword",
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
        dokarkiv = ClientEnvironment(
            baseUrl = "dokarkivUrl",
            clientId = "dokarkivClientId",
        ),
        gosysoppgave = ClientEnvironment(
            baseUrl = "oppgaveUrl",
            clientId = "oppgaveClientId",
        ),
        arbeidssokeroppslag = ClientEnvironment(
            baseUrl = "arbeidssokeroppslagUrl",
            clientId = "arbeidssokeroppslagClientId",
        )
    ),
    mq = MQEnvironment(
        mqQueueManager = "mqQueueManager",
        mqHostname = "mqHostname",
        mqPort = 1414,
        mqChannelName = "mqChannelName",
        mqQueueName = "mqQueueName",
        mqQueueNameKvittering = "mqQueueNameKvittering",
        serviceuserUsername = "serviceuser",
        serviceuserPassword = "servicepw",
    ),
    electorPath = "electorPath",
    isJournalforingRetryEnabled = true,
)

fun testAppState() = ApplicationState(
    alive = true,
    ready = true,
)
