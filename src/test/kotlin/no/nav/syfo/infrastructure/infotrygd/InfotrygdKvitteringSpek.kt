package no.nav.syfo.infrastructure.infotrygd

import io.ktor.server.testing.*
import io.mockk.*
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.application.VedtakService
import no.nav.syfo.application.vedtak
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getVedtakInfotrygdFeilmelding
import no.nav.syfo.infrastructure.database.getVedtakInfotrygdKvittering
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.kafka.*
import no.nav.syfo.infrastructure.kafka.VedtakProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselHendelseProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.EsyfovarselHendelse
import no.nav.syfo.infrastructure.mq.InfotrygdKvitteringMQConsumer
import no.nav.syfo.infrastructure.mq.InfotrygdMQSender
import no.nav.syfo.infrastructure.pdf.PdfService
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import javax.jms.*

val externalMockEnvironment = ExternalMockEnvironment.instance
val database = externalMockEnvironment.database

class InfotrygdKvitteringSpek : Spek({
    describe(InfotrygdKvitteringSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()

            val vedtakRepository = VedtakRepository(database)
            val messageConsumer = mockk<MessageConsumer>(relaxed = true)
            val incomingMessage = mockk<TextMessage>(relaxed = true)
            val journalforingService = JournalforingService(
                dokarkivClient = externalMockEnvironment.dokarkivClient,
                pdlClient = externalMockEnvironment.pdlClient,
                dialogmeldingBehandlerClient = externalMockEnvironment.dialogmeldingBehandlerClient
            )

            val infotrygdKvitteringMQConsumer = InfotrygdKvitteringMQConsumer(
                applicationState = externalMockEnvironment.applicationState,
                inputconsumer = messageConsumer,
                vedtakRepository = vedtakRepository,
            )
            val mockEsyfoVarselKafkaProducer = mockk<KafkaProducer<String, EsyfovarselHendelse>>()
            val esyfovarselHendelseProducer = EsyfovarselHendelseProducer(mockEsyfoVarselKafkaProducer)
            val mockVedtakFattetKafkaProducer = mockk<KafkaProducer<String, VedtakFattetRecord>>()
            val vedtakFattetProducer = VedtakFattetProducer(mockVedtakFattetKafkaProducer)
            val vedtakProducer = VedtakProducer(
                esyfovarselHendelseProducer = esyfovarselHendelseProducer,
                vedtakFattetProducer = vedtakFattetProducer,
            )

            val vedtakService = VedtakService(
                vedtakRepository = vedtakRepository,
                pdfService = PdfService(
                    pdfGenClient = externalMockEnvironment.pdfgenClient,
                    pdlClient = externalMockEnvironment.pdlClient,
                ),
                journalforingService = journalforingService,
                infotrygdService = InfotrygdService(
                    pdlClient = externalMockEnvironment.pdlClient,
                    mqSender = mockk<InfotrygdMQSender>(relaxed = true),
                ),
                vedtakProducer = vedtakProducer,
            )

            describe("Prosesserer innkommet kvittering") {

                beforeEachTest {
                    database.dropData()
                    clearAllMocks()
                }
                it("Prosesserer innkommet kvittering (ok)") {
                    val createdVedtak = vedtakRepository.createVedtak(
                        vedtak = vedtak,
                        vedtakPdf = UserConstants.PDF_VEDTAK,
                    )

                    val kvittering = "xxxxxxxxxxxxxxxxxxxMODIA1111113052024150000${UserConstants.ARBEIDSTAKER_PERSONIDENT.value}Jxxxxxxxx"

                    every { incomingMessage.text } returns (kvittering)

                    infotrygdKvitteringMQConsumer.processKvitteringMessage(incomingMessage)

                    database.getVedtakInfotrygdKvittering(createdVedtak.uuid) shouldBe true
                    database.getVedtakInfotrygdFeilmelding(createdVedtak.uuid) shouldBe null
                }
                it("Prosesserer innkommet kvittering (med feilkode)") {
                    val createdVedtak = vedtakRepository.createVedtak(
                        vedtak = vedtak,
                        vedtakPdf = UserConstants.PDF_VEDTAK,
                    )

                    val kvittering = "xxxxxxxxxxxxxxxxxxxMODIA1111113052024150000${UserConstants.ARBEIDSTAKER_PERSONIDENT.value}NFeilkode"

                    every { incomingMessage.text } returns (kvittering)

                    infotrygdKvitteringMQConsumer.processKvitteringMessage(incomingMessage)

                    database.getVedtakInfotrygdKvittering(createdVedtak.uuid) shouldBe false
                    database.getVedtakInfotrygdFeilmelding(createdVedtak.uuid) shouldBeEqualTo "Feilkode"
                }
            }
        }
    }
})
