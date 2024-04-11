package no.nav.syfo.application

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.generator.generateBehandlermelding
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getVedtak
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselHendelseProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.ArbeidstakerHendelse
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.EsyfovarselHendelse
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.HendelseType
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.VarselData
import no.nav.syfo.infrastructure.mock.mockedJournalpostId
import no.nav.syfo.infrastructure.mq.InfotrygdMQSender
import no.nav.syfo.infrastructure.pdf.PdfService
import no.nav.syfo.util.nowUTC
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.Future

val vedtak = generateVedtak()
val behandlermelding = generateBehandlermelding()
val otherBehandlermelding = generateBehandlermelding()
val journalpostId = JournalpostId("123")

class VedtakServiceSpek : Spek({
    describe(VedtakService::class.java.simpleName) {

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val vedtakRepository = externalMockEnvironment.vedtakRepository

        val journalforingService = JournalforingService(
            dokarkivClient = externalMockEnvironment.dokarkivClient,
            pdlClient = externalMockEnvironment.pdlClient,
        )

        val mockEsyfoVarselKafkaProducer = mockk<KafkaProducer<String, EsyfovarselHendelse>>()
        val esyfovarselHendelseProducer = EsyfovarselHendelseProducer(
            kafkaProducer = mockEsyfoVarselKafkaProducer,
        )
        val vedtakService = VedtakService(
            vedtakRepository = vedtakRepository,
            pdfService = PdfService(
                pdfGenClient = externalMockEnvironment.pdfgenClient,
                pdlClient = externalMockEnvironment.pdlClient,
            ),
            journalforingService = journalforingService,
            infotrygdService = InfotrygdService(
                mqSender = mockk<InfotrygdMQSender>(relaxed = true),
            ),
            esyfovarselHendelseProducer = esyfovarselHendelseProducer,
        )

        beforeEachTest {
            clearAllMocks()
            coEvery { mockEsyfoVarselKafkaProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }

        afterEachTest {
            database.dropData()
        }

        describe("journalforVedtak") {
            it("journalfører vedtak som ikke er journalført") {
                vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                    behandlerMelding = behandlermelding,
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
                    behandlerMelding = behandlermelding,
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
                    behandlerMelding = behandlermelding,
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
                    behandlerMelding = behandlermelding,
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
                    behandlerMelding = behandlermelding,
                    behandlerMeldingPdf = UserConstants.PDF_BEHANDLER_MELDING,
                )
                vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                    behandlerMelding = otherBehandlermelding,
                    behandlerMeldingPdf = UserConstants.PDF_BEHANDLER_MELDING,
                )

                val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }

                val (success, failed) = journalforteVedtak.partition { it.isSuccess }

                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 1
            }
        }

        describe("Publish varsel for vedtak to esyfovarsel") {
            fun createUnpublishedVedtakVarsel(): Vedtak {
                val unpublishedVedtakVarsel = vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                    behandlerMelding = behandlermelding,
                    behandlerMeldingPdf = UserConstants.PDF_BEHANDLER_MELDING,
                ).first
                vedtakRepository.update(unpublishedVedtakVarsel.copy(journalpostId = journalpostId))
                return unpublishedVedtakVarsel
            }

            it("Publishes varsel for vedtak") {
                val unpublishedVedtakVarsel = createUnpublishedVedtakVarsel()

                val (success, failed) = vedtakService.publishVedtakVarsel().partition { it.isSuccess }

                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 1

                val producerRecordSlot = slot<ProducerRecord<String, EsyfovarselHendelse>>()
                verify(exactly = 1) { mockEsyfoVarselKafkaProducer.send(capture(producerRecordSlot)) }

                val publishedVedtakVarsel = success.first().getOrThrow()
                publishedVedtakVarsel.uuid.shouldBeEqualTo(unpublishedVedtakVarsel.uuid)
                publishedVedtakVarsel.varselPublishedAt.shouldNotBeNull()

                val esyfovarselHendelse = producerRecordSlot.captured.value() as ArbeidstakerHendelse
                esyfovarselHendelse.type shouldBeEqualTo HendelseType.SM_VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING
                esyfovarselHendelse.arbeidstakerFnr shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value
                val varselData = esyfovarselHendelse.data as VarselData
                varselData.journalpost?.uuid shouldBeEqualTo publishedVedtakVarsel.uuid.toString()
                varselData.journalpost?.id!! shouldBeEqualTo journalpostId.value
            }

            it("Publishes nothing when no vedtak") {
                val (success, failed) = vedtakService.publishVedtakVarsel().partition { it.isSuccess }

                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0
            }

            it("Publishes nothing when no journalfort vedtak") {
                vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                    behandlerMelding = behandlermelding,
                    behandlerMeldingPdf = UserConstants.PDF_BEHANDLER_MELDING,
                )

                val (success, failed) = vedtakService.publishVedtakVarsel().partition { it.isSuccess }

                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0
            }

            it("Publishes nothing when varsel for vedtak already published") {
                val publishedVedtakVarsel = createUnpublishedVedtakVarsel().copy(varselPublishedAt = nowUTC())
                vedtakRepository.update(publishedVedtakVarsel)

                val (success, failed) = vedtakService.publishVedtakVarsel().partition { it.isSuccess }

                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0
            }

            it("Fails publishing when kafka-producer fails") {
                val unpublishedVedtak = createUnpublishedVedtakVarsel()
                every { mockEsyfoVarselKafkaProducer.send(any()) } throws Exception("Error producing to kafka")

                val (success, failed) = vedtakService.publishVedtakVarsel().partition { it.isSuccess }
                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 0

                verify(exactly = 1) { mockEsyfoVarselKafkaProducer.send(any()) }

                val vedtak = vedtakRepository.getUnpublishedVedtakVarsler().first()
                vedtak.uuid shouldBeEqualTo unpublishedVedtak.uuid
            }
        }
    }
})
