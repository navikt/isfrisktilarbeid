package no.nav.syfo.api

import io.ktor.server.application.*
import io.mockk.mockk
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.application.IBehandlerMeldingProducer
import no.nav.syfo.application.VedtakService
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.kafka.BehandlerMeldingProducer
import no.nav.syfo.infrastructure.kafka.BehandlerMeldingRecord
import no.nav.syfo.infrastructure.mq.MQSender
import no.nav.syfo.infrastructure.pdf.PdfService
import org.apache.kafka.clients.producer.KafkaProducer

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
) {
    val database = externalMockEnvironment.database
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = externalMockEnvironment.azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.istilgangskontroll,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val pdfService = PdfService(
        pdfGenClient = externalMockEnvironment.pdfgenClient,
        pdlClient = externalMockEnvironment.pdlClient,
    )
    val journalforingService = JournalforingService(
        dokarkivClient = externalMockEnvironment.dokarkivClient,
        pdlClient = externalMockEnvironment.pdlClient,
    )
    val mockBehandlerMeldingProducer = mockk<KafkaProducer<String, BehandlerMeldingRecord>>(relaxed = true)
    val behandlerMeldingProducer = BehandlerMeldingProducer(produder = mockBehandlerMeldingProducer)
    val vedtakService = VedtakService(
        vedtakRepository = VedtakRepository(database = database),
        pdfService = pdfService,
        journalforingService = journalforingService,
        infotrygdService = InfotrygdService(
            mqQueueName = externalMockEnvironment.environment.mq.mqQueueName,
            mqSender = mockk<MQSender>(relaxed = true),
        ),
        behandlerMeldingProducer = behandlerMeldingProducer,
    )

    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        database = database,
        veilederTilgangskontrollClient = veilederTilgangskontrollClient,
        vedtakService = vedtakService,
    )
}
