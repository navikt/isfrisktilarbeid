package no.nav.syfo.application

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateBehandlerMelding
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getVedtak
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.kafka.BehandlerMeldingProducer
import no.nav.syfo.infrastructure.kafka.BehandlerMeldingRecord
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselHendelseProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.EsyfovarselHendelse
import no.nav.syfo.infrastructure.mock.mockedJournalpostId
import no.nav.syfo.infrastructure.mq.MQSender
import no.nav.syfo.infrastructure.pdf.PdfService
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.postgresql.core.Tuple
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import scala.Tuple3
import java.time.LocalDate
import java.util.concurrent.Future

val vedtak = generateVedtak()
val behandlerMelding = generateBehandlerMelding()
val otherBehandlerMelding = generateBehandlerMelding()

class VedtakServiceSpek : Spek({
    describe(VedtakService::class.java.simpleName) {

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val vedtakRepository = externalMockEnvironment.vedtakRepository

        val journalforingService = JournalforingService(
            dokarkivClient = externalMockEnvironment.dokarkivClient,
            pdlClient = externalMockEnvironment.pdlClient,
        )

        val mockBehandlerMeldingRecordProducer = mockk<KafkaProducer<String, BehandlerMeldingRecord>>()
        val behandlerMeldingProducer = BehandlerMeldingProducer(mockBehandlerMeldingRecordProducer)
        val mockProducer = mockk<KafkaProducer<String, EsyfovarselHendelse>>()
        val esyfovarselHendelseProducer = EsyfovarselHendelseProducer(
            kafkaProducer = mockProducer,
        )
        val vedtakService = VedtakService(
            vedtakRepository = vedtakRepository,
            pdfService = PdfService(
                pdfGenClient = externalMockEnvironment.pdfgenClient,
                pdlClient = externalMockEnvironment.pdlClient,
            ),
            journalforingService = journalforingService,
            infotrygdService = InfotrygdService(
                mqQueueName = externalMockEnvironment.environment.mq.mqQueueName,
                mqSender = mockk<MQSender>(relaxed = true),
            ),
            behandlerMeldingProducer = behandlerMeldingProducer,
            esyfovarselHendelseProducer = esyfovarselHendelseProducer,
        )

        beforeEachTest {
            clearAllMocks()
            coEvery { mockBehandlerMeldingRecordProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }

        afterEachTest {
            database.dropData()
        }

        describe("journalforVedtak") {
            it("journalfører vedtak som ikke er journalført") {
                vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                    behandlerMelding = behandlerMelding,
                    behandlerMeldingPdf = UserConstants.PDF_BEHANDLER_MELDING,
                )

                val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }

                val (success, failed) = journalforteVedtak.partition { it.isSuccess }

                failed.shouldBeEmpty()
                success.size shouldBeEqualTo 1

                val journalfortVedtak = success.first().getOrThrow()
                journalfortVedtak.journalpostId shouldBeEqualTo mockedJournalpostId

                val pVedtak = database.getVedtak(vedtakUuid = journalfortVedtak.uuid)
                pVedtak!!.updatedAt shouldBeGreaterThan pVedtak.createdAt
                pVedtak.journalpostId shouldBeEqualTo mockedJournalpostId.value
            }

            it("journalfører ikke når ingen vedtak") {
                val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }

                journalforteVedtak.shouldBeEmpty()
            }

            it("journalfører ikke når vedtak allerede er journalført") {
                vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                    behandlerMelding = behandlerMelding,
                    behandlerMeldingPdf = UserConstants.PDF_BEHANDLER_MELDING,
                )
                val journafortVedtak = vedtak.journalfor(mockedJournalpostId)
                vedtakRepository.update(journafortVedtak)

                val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }

                journalforteVedtak.shouldBeEmpty()
            }

            it("journalføring feiler mot dokarkiv") {
                val failingVedtak = generateVedtak().copy(uuid = UserConstants.FAILING_EKSTERN_REFERANSE_UUID)
                vedtakRepository.createVedtak(
                    vedtak = failingVedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                    behandlerMelding = behandlerMelding,
                    behandlerMeldingPdf = UserConstants.PDF_BEHANDLER_MELDING,
                )

                val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }

                val (success, failed) = journalforteVedtak.partition { it.isSuccess }

                failed.size shouldBeEqualTo 1
                success.shouldBeEmpty()
            }

            it("journalføring feiler mot pdl") {
                val failingVedtak = generateVedtak(personident = UserConstants.ARBEIDSTAKER_PERSONIDENT_PDL_FAILS)
                vedtakRepository.createVedtak(
                    vedtak = failingVedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                    behandlerMelding = behandlerMelding,
                    behandlerMeldingPdf = UserConstants.PDF_BEHANDLER_MELDING,
                )

                val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }

                val (success, failed) = journalforteVedtak.partition { it.isSuccess }

                failed.size shouldBeEqualTo 1
                success.shouldBeEmpty()
            }

            it("journalfører et vedtak selv om annet vedtak feiler") {
                val failingVedtak = generateVedtak(personident = UserConstants.ARBEIDSTAKER_PERSONIDENT_PDL_FAILS)
                vedtakRepository.createVedtak(
                    vedtak = failingVedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                    behandlerMelding = behandlerMelding,
                    behandlerMeldingPdf = UserConstants.PDF_BEHANDLER_MELDING,
                )
                vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                    behandlerMelding = otherBehandlerMelding,
                    behandlerMeldingPdf = UserConstants.PDF_BEHANDLER_MELDING,
                )

                val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }

                val (success, failed) = journalforteVedtak.partition { it.isSuccess }

                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 1
            }
        }
        describe("createVedtak") {
            it("Publiserer behandlermelding om vedtak på kafka til isdialogmelding pa riktig format") {
                val fom = LocalDate.now()
                val tom = LocalDate.now().plusDays(30)
                val behandlerRef = UserConstants.BEHANDLER_REF
                val createdVedtak = runBlocking {
                    vedtakService.createVedtak(
                        personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        veilederident = UserConstants.VEILEDER_IDENT,
                        begrunnelse = "En god begrunnelse",
                        document = generateDocumentComponent("Til orientering", header = "Informasjon om vedtak"),
                        fom = fom,
                        tom = tom,
                        callId = "callId",
                        behandlerRef = behandlerRef,
                        behandlerNavn = UserConstants.BEHANDLER_NAVN,
                        behandlerDocument = generateDocumentComponent("En melding til behandler"),
                    )
                }

                val producerRecordSlot = slot<ProducerRecord<String, BehandlerMeldingRecord>>()
                verify(exactly = 1) { mockBehandlerMeldingRecordProducer.send(capture(producerRecordSlot)) }

                val behandlermeldingRecord = producerRecordSlot.captured.value()
                behandlermeldingRecord.behandlerRef shouldBeEqualTo behandlerRef
                behandlermeldingRecord.personIdent shouldBeEqualTo createdVedtak.personident.value
                behandlermeldingRecord.dialogmeldingType shouldBeEqualTo "DIALOG_NOTAT"
                behandlermeldingRecord.dialogmeldingKodeverk shouldBeEqualTo "8127"
                behandlermeldingRecord.dialogmeldingKode shouldBeEqualTo 2
            }
        }
    }
})
