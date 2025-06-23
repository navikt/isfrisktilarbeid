package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.application.IVedtakRepository
import no.nav.syfo.application.VedtakService
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.arbeidssokeroppslag.ArbeidssokeroppslagClient
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.clients.gosysoppgave.GosysOppgaveClient
import no.nav.syfo.infrastructure.clients.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.clients.pdl.PdlClient
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.clients.wellknown.getWellKnown
import no.nav.syfo.infrastructure.cronjob.launchCronjobs
import no.nav.syfo.infrastructure.database.applicationDatabase
import no.nav.syfo.infrastructure.database.databaseModule
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.kafka.*
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselHendelseProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.KafkaEsyfovarselHendelseSerializer
import no.nav.syfo.infrastructure.kafka.identhendelse.IdenthendelseConsumer
import no.nav.syfo.infrastructure.kafka.identhendelse.IdenthendelseService
import no.nav.syfo.infrastructure.kafka.identhendelse.launchIdenthendelseConsumer
import no.nav.syfo.infrastructure.mq.InfotrygdKvitteringMQConsumer
import no.nav.syfo.infrastructure.mq.InfotrygdMQSender
import no.nav.syfo.infrastructure.mq.connectionFactory
import no.nav.syfo.infrastructure.mq.consumerForQueue
import no.nav.syfo.infrastructure.gosysoppgave.GosysGosysOppgaveService
import no.nav.syfo.infrastructure.pdf.PdfService
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import javax.jms.Session

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val environment = Environment()
    val logger = LoggerFactory.getLogger("ktor.application")

    val wellKnownInternalAzureAD = getWellKnown(
        wellKnownUrl = environment.azure.appWellKnownUrl
    )
    val azureAdClient = AzureAdClient(
        azureEnvironment = environment.azure
    )
    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        pdlEnvironment = environment.clients.pdl,
    )
    val dokarkivClient = DokarkivClient(
        azureAdClient = azureAdClient,
        dokarkivEnvironment = environment.clients.dokarkiv,
    )
    val gosysOppgaveClient = GosysOppgaveClient(
        azureAdClient = azureAdClient,
        environment = environment.clients.oppgave,
    )

    val pdfGenClient = PdfGenClient(
        pdfGenBaseUrl = environment.clients.ispdfgen.baseUrl,
    )
    val arbeidssokeroppslagClient = ArbeidssokeroppslagClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.arbeidssokeroppslag,
    )
    val veilederTilgangskontrollClient =
        VeilederTilgangskontrollClient(
            azureAdClient = azureAdClient,
            clientEnvironment = environment.clients.istilgangskontroll
        )

    val pdfService = PdfService(pdfGenClient = pdfGenClient, pdlClient = pdlClient)
    val infotrygdService = InfotrygdService(
        pdlClient = pdlClient,
        mqSender = InfotrygdMQSender(environment.mq),
    )
    val vedtakProducer = VedtakProducer(
        esyfovarselHendelseProducer = EsyfovarselHendelseProducer(
            kafkaProducer = KafkaProducer(
                kafkaAivenProducerConfig<KafkaEsyfovarselHendelseSerializer>(kafkaEnvironment = environment.kafka)
            )
        ),
        vedtakStatusProducer = VedtakStatusProducer(
            producer = KafkaProducer(
                kafkaAivenProducerConfig<VedtakStatusRecordSerializer>(kafkaEnvironment = environment.kafka)
            )
        ),
    )
    val journalforingService = JournalforingService(
        dokarkivClient = dokarkivClient,
        pdlClient = pdlClient,
        isJournalforingRetryEnabled = environment.isJournalforingRetryEnabled,
    )

    val gosysOppgaveService = GosysGosysOppgaveService(
        gosysOppgaveClient = gosysOppgaveClient,
    )

    lateinit var vedtakRepository: IVedtakRepository
    lateinit var vedtakService: VedtakService

    val applicationEngineEnvironment =
        applicationEnvironment {
            log = logger
            config = HoconApplicationConfig(ConfigFactory.load())
        }
    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment,
        configure = {
            connector {
                port = applicationPort
            }
            connectionGroupSize = 8
            workerGroupSize = 8
            callGroupSize = 16
        },
        module = {
            databaseModule(
                databaseEnvironment = environment.database,
            )
            vedtakRepository = VedtakRepository(database = applicationDatabase)
            vedtakService = VedtakService(
                pdfService = pdfService,
                vedtakRepository = vedtakRepository,
                infotrygdService = infotrygdService,
                journalforingService = journalforingService,
                gosysOppgaveService = gosysOppgaveService,
                vedtakProducer = vedtakProducer,
            )
            apiModule(
                applicationState = applicationState,
                environment = environment,
                wellKnownInternalAzureAD = wellKnownInternalAzureAD,
                database = applicationDatabase,
                veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                vedtakService = vedtakService,
                arbeidssokeroppslagClient = arbeidssokeroppslagClient,
            )
            monitor.subscribe(ApplicationStarted) {
                applicationState.ready = true
                logger.info("Application is ready, running Java VM ${Runtime.version()}")

                launchCronjobs(
                    applicationState = applicationState,
                    environment = environment,
                    vedtakService = vedtakService,
                )
                launchBackgroundTask(
                    applicationState = applicationState,
                ) {
                    connectionFactory(environment.mq).createConnection(
                        environment.mq.serviceuserUsername,
                        environment.mq.serviceuserPassword,
                    ).use { mqConnection ->
                        mqConnection.start()
                        val session = mqConnection.createSession(false, Session.CLIENT_ACKNOWLEDGE)
                        val blockingApplicationRunner = InfotrygdKvitteringMQConsumer(
                            applicationState = applicationState,
                            inputconsumer = session.consumerForQueue(environment.mq.mqQueueNameKvittering),
                            vedtakRepository = vedtakRepository,
                        )
                        blockingApplicationRunner.run()
                    }
                }
                launchIdenthendelseConsumer(
                    applicationState = applicationState,
                    kafkaEnvironment = environment.kafka,
                    identhendelseConsumer = IdenthendelseConsumer(
                        identhendelseService = IdenthendelseService(
                            vedtakRepository = vedtakRepository,
                        )
                    )
                )
            }
        }
    )

    Runtime.getRuntime().addShutdownHook(
        Thread { server.stop(10, 10, TimeUnit.SECONDS) }
    )

    server.start(wait = true)
}
