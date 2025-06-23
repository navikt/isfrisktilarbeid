package no.nav.syfo.application

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.InfotrygdStatus
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.Status
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getVedtakStatusPublishedAt
import no.nav.syfo.infrastructure.database.getVedtakVarselPublishedAt
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.kafka.VedtakStatusProducer
import no.nav.syfo.infrastructure.kafka.VedtakStatusRecord
import no.nav.syfo.infrastructure.kafka.VedtakProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselHendelseProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.ArbeidstakerHendelse
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.EsyfovarselHendelse
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.HendelseType
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.VarselData
import no.nav.syfo.infrastructure.mock.mockedJournalpostId
import no.nav.syfo.infrastructure.mq.InfotrygdMQSender
import no.nav.syfo.infrastructure.gosysoppgave.GosysOppgaveService
import no.nav.syfo.infrastructure.pdf.PdfService
import org.amshove.kluent.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.concurrent.Future

val vedtak = generateVedtak()
val journalpostId = JournalpostId("123")

class VedtakServiceSpek : Spek({
    describe(VedtakService::class.java.simpleName) {

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val vedtakRepository = externalMockEnvironment.vedtakRepository

        val journalforingService = JournalforingService(
            dokarkivClient = externalMockEnvironment.dokarkivClient,
            pdlClient = externalMockEnvironment.pdlClient,
            isJournalforingRetryEnabled = externalMockEnvironment.environment.isJournalforingRetryEnabled,
        )
        val gosysOppgaveService = GosysOppgaveService(
            gosysOppgaveClient = externalMockEnvironment.gosysOppgaveClient,
        )

        val mockEsyfoVarselKafkaProducer = mockk<KafkaProducer<String, EsyfovarselHendelse>>()
        val esyfovarselHendelseProducer = EsyfovarselHendelseProducer(mockEsyfoVarselKafkaProducer)
        val mockVedtakStatusKafkaProducer = mockk<KafkaProducer<String, VedtakStatusRecord>>()
        val infotrygdMQSender = mockk<InfotrygdMQSender>(relaxed = true)
        val vedtakStatusProducer = VedtakStatusProducer(mockVedtakStatusKafkaProducer)
        val vedtakProducer = VedtakProducer(
            esyfovarselHendelseProducer = esyfovarselHendelseProducer,
            vedtakStatusProducer = vedtakStatusProducer,
        )

        val vedtakService = VedtakService(
            vedtakRepository = vedtakRepository,
            pdfService = PdfService(
                pdfGenClient = externalMockEnvironment.pdfgenClient,
                pdlClient = externalMockEnvironment.pdlClient,
            ),
            journalforingService = journalforingService,
            oppgaveService = gosysOppgaveService,
            infotrygdService = InfotrygdService(
                pdlClient = externalMockEnvironment.pdlClient,
                mqSender = infotrygdMQSender,
            ),
            vedtakProducer = vedtakProducer,
        )

        beforeEachTest {
            clearAllMocks()
            coEvery { mockEsyfoVarselKafkaProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
            coEvery { mockVedtakStatusKafkaProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
        }

        afterEachTest {
            database.dropData()
        }

        describe("ferdigbehandler vedtak") {
            it("successfully ferdigbehandler a vedtak") {
                val createdVedtak = vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                )

                val persistedVedtak = vedtakRepository.getVedtak(createdVedtak.uuid)
                persistedVedtak.isFerdigbehandlet() shouldBe false

                vedtakService.ferdigbehandleVedtak(persistedVedtak, UserConstants.VEILEDER_IDENT_OTHER)

                val persistedFerdigbehandletVedtak = vedtakRepository.getVedtak(createdVedtak.uuid)
                persistedFerdigbehandletVedtak.isFerdigbehandlet() shouldBe true
                persistedFerdigbehandletVedtak.getFerdigbehandletStatus()!!.veilederident shouldBeEqualTo UserConstants.VEILEDER_IDENT_OTHER
            }
        }

        describe("journalforVedtak") {
            it("journalfører vedtak som ikke er journalført") {
                vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                )

                val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }

                val (success, failed) = journalforteVedtak.partition { it.isSuccess }

                failed.shouldBeEmpty()
                success.size shouldBeEqualTo 1

                val journalfortVedtak = success.first().getOrThrow()
                journalfortVedtak.journalpostId shouldBeEqualTo mockedJournalpostId

                val persistedVedtak = vedtakRepository.getVedtak(journalfortVedtak.uuid)
                persistedVedtak.journalpostId!!.value shouldBeEqualTo mockedJournalpostId.value
            }

            it("journalfører ikke når ingen vedtak") {
                val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }

                journalforteVedtak.shouldBeEmpty()
            }

            it("journalfører ikke når vedtak allerede er journalført") {
                vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                )
                val journalfortVedtak = vedtak.journalfor(mockedJournalpostId)
                vedtakRepository.setJournalpostId(journalfortVedtak)

                val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }

                journalforteVedtak.shouldBeEmpty()
            }

            it("journalføring feiler mot dokarkiv") {
                val failingVedtak = generateVedtak().copy(uuid = UserConstants.FAILING_EKSTERN_REFERANSE_UUID)
                vedtakRepository.createVedtak(
                    vedtak = failingVedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
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
                )
                vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
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
                )
                vedtakRepository.setJournalpostId(unpublishedVedtakVarsel.copy(journalpostId = journalpostId))
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
                database.getVedtakVarselPublishedAt(publishedVedtakVarsel.uuid).shouldNotBeNull()

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
                )

                val (success, failed) = vedtakService.publishVedtakVarsel().partition { it.isSuccess }

                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0
            }

            it("Publishes nothing when varsel for vedtak already published") {
                val unpublishedVedtakVarsel = createUnpublishedVedtakVarsel()
                vedtakRepository.setVedtakVarselPublished(unpublishedVedtakVarsel)

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

        describe("Publish unpublished vedtakstatus") {

            it("publishes unpublished vedtak to kafka") {
                val unpublishedVedtak = vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                )

                val (success, failed) = vedtakService.publishUnpublishedVedtakStatus().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 1

                val publishedVedtak = success.first().getOrThrow()
                publishedVedtak.uuid.shouldBeEqualTo(unpublishedVedtak.uuid)
                database.getVedtakStatusPublishedAt(publishedVedtak.getFattetStatus().uuid) shouldNotBe null

                vedtakRepository.getUnpublishedVedtakStatus().shouldBeEmpty()

                val producerRecordSlot = slot<ProducerRecord<String, VedtakStatusRecord>>()
                verify(exactly = 1) { mockVedtakStatusKafkaProducer.send(capture(producerRecordSlot)) }

                val record = producerRecordSlot.captured.value()
                record.uuid shouldBeEqualTo publishedVedtak.uuid
                record.personident shouldBeEqualTo publishedVedtak.personident.value
                record.fom shouldBeEqualTo publishedVedtak.fom
                record.tom shouldBeEqualTo publishedVedtak.tom
                record.status shouldBe Status.FATTET
                record.statusBy shouldBeEqualTo publishedVedtak.getFattetStatus().veilederident
            }
            it("publishes unpublished ferdigbehandlet vedtak to kafka") {
                val unpublishedVedtak = vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                )
                val (success, _) = vedtakService.publishUnpublishedVedtakStatus().partition { it.isSuccess }
                val publishedVedtak = success.first().getOrThrow()
                database.getVedtakStatusPublishedAt(publishedVedtak.getFattetStatus().uuid) shouldNotBe null

                vedtakService.ferdigbehandleVedtak(publishedVedtak, UserConstants.VEILEDER_IDENT)

                clearAllMocks()
                coEvery { mockVedtakStatusKafkaProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)

                val (successFerdigbehandlet, failedFerdigbehandlet) = vedtakService.publishUnpublishedVedtakStatus().partition { it.isSuccess }
                failedFerdigbehandlet.size shouldBeEqualTo 0
                successFerdigbehandlet.size shouldBeEqualTo 1

                val publishedFerdigbehandletVedtak = successFerdigbehandlet.first().getOrThrow()
                publishedFerdigbehandletVedtak.uuid.shouldBeEqualTo(unpublishedVedtak.uuid)
                database.getVedtakStatusPublishedAt(publishedFerdigbehandletVedtak.getFerdigbehandletStatus()!!.uuid) shouldNotBe null

                vedtakRepository.getUnpublishedVedtakStatus().shouldBeEmpty()

                val producerRecordSlot = slot<ProducerRecord<String, VedtakStatusRecord>>()
                verify(exactly = 1) { mockVedtakStatusKafkaProducer.send(capture(producerRecordSlot)) }

                val record = producerRecordSlot.captured.value()
                record.uuid shouldBeEqualTo publishedFerdigbehandletVedtak.uuid
                record.personident shouldBeEqualTo publishedFerdigbehandletVedtak.personident.value
                record.status shouldBe Status.FERDIG_BEHANDLET
                record.statusBy shouldBeEqualTo publishedFerdigbehandletVedtak.getFerdigbehandletStatus()!!.veilederident
                record.statusAt shouldNotBe null
            }

            it("publishes nothing when no unpublished varsel") {
                val (success, failed) = vedtakService.publishUnpublishedVedtakStatus().partition { it.isSuccess }
                failed.size shouldBeEqualTo 0
                success.size shouldBeEqualTo 0

                verify(exactly = 0) { mockVedtakStatusKafkaProducer.send(any()) }
            }

            it("fails publishing when kafka-producer fails") {
                vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                )

                every { mockVedtakStatusKafkaProducer.send(any()) } throws Exception("Error producing to kafka")

                val (success, failed) = vedtakService.publishUnpublishedVedtakStatus().partition { it.isSuccess }
                failed.size shouldBeEqualTo 1
                success.size shouldBeEqualTo 0

                verify(exactly = 1) { mockVedtakStatusKafkaProducer.send(any()) }

                vedtakRepository.getUnpublishedVedtakStatus().shouldNotBeEmpty()
            }
        }

        describe("sendVedtakToInfotrygd") {
            it("sends vedtak to infotrygd, updates vedtak and returns result with updated vedtak") {
                vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                )
                justRun { infotrygdMQSender.sendToMQ(any(), any()) }

                val result = runBlocking { vedtakService.sendVedtakToInfotrygd(vedtak = vedtak) }

                verify(exactly = 1) { infotrygdMQSender.sendToMQ(any(), any()) }
                result.isSuccess.shouldBeTrue()
                val publishedVedtak = result.getOrThrow()
                publishedVedtak.infotrygdStatus shouldBeEqualTo InfotrygdStatus.KVITTERING_MANGLER

                val persistedVedtak = vedtakRepository.getVedtak(publishedVedtak.uuid)
                persistedVedtak.infotrygdStatus shouldBeEqualTo InfotrygdStatus.KVITTERING_MANGLER
            }
            it("returns failure when sending vedtak to infotrygd fails") {
                vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                )
                every { infotrygdMQSender.sendToMQ(any(), any()) } throws Exception("Error sending to infotrygd")

                val result = runBlocking { vedtakService.sendVedtakToInfotrygd(vedtak = vedtak) }

                verify(exactly = 1) { infotrygdMQSender.sendToMQ(any(), any()) }
                result.isFailure.shouldBeTrue()
            }
        }

        describe("createOppgaveForVedtakWithNoOppgave") {
            it("creates oppgave for vedtak without oppgaveId") {
                val vedtakUtenOppgave = vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                )
                vedtakRepository.setJournalpostId(vedtakUtenOppgave.journalfor(journalpostId = journalpostId))

                val result = runBlocking { vedtakService.createGosysOppgaveForVedtakUtenOppgave() }

                val (success, failed) = result.partition { it.isSuccess }
                failed.shouldBeEmpty()
                success.size shouldBeEqualTo 1

                val oppdatertVedtak = vedtakRepository.getVedtak(vedtakUtenOppgave.uuid)
                oppdatertVedtak.gosysOppgaveId.shouldNotBeNull()
                oppdatertVedtak.gosysOppgaveAt.shouldNotBeNull()
            }

            it("returns empty when no vedtak without oppgaveId") {
                val result = runBlocking { vedtakService.createGosysOppgaveForVedtakUtenOppgave() }
                result.shouldBeEmpty()
            }
        }
    }
})
