package no.nav.syfo.application

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.*
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.database.*
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.kafka.*
import no.nav.syfo.infrastructure.kafka.esyfovarsel.*
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.*
import no.nav.syfo.infrastructure.mock.mockedJournalpostId
import no.nav.syfo.infrastructure.mq.InfotrygdMQSender
import no.nav.syfo.infrastructure.gosysoppgave.GosysOppgaveService
import no.nav.syfo.infrastructure.pdf.PdfService
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.time.OffsetDateTime
import java.util.concurrent.Future

class VedtakServiceTest {
    private val vedtak = generateVedtak()
    private val journalpostId = JournalpostId("123")

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val vedtakRepository = externalMockEnvironment.vedtakRepository

    private val journalforingService = JournalforingService(
        dokarkivClient = externalMockEnvironment.dokarkivClient,
        pdlClient = externalMockEnvironment.pdlClient,
        isJournalforingRetryEnabled = externalMockEnvironment.environment.isJournalforingRetryEnabled,
    )
    private val gosysOppgaveService = GosysOppgaveService(
        gosysOppgaveClient = externalMockEnvironment.gosysOppgaveClient,
    )

    private val mockEsyfoVarselKafkaProducer = mockk<KafkaProducer<String, EsyfovarselHendelse>>()
    private val esyfovarselHendelseProducer = EsyfovarselHendelseProducer(mockEsyfoVarselKafkaProducer)
    private val mockVedtakStatusKafkaProducer = mockk<KafkaProducer<String, VedtakStatusRecord>>()
    private val infotrygdMQSender = mockk<InfotrygdMQSender>(relaxed = true)
    private val vedtakStatusProducer = VedtakStatusProducer(mockVedtakStatusKafkaProducer)
    private val vedtakProducer = VedtakProducer(
        esyfovarselHendelseProducer = esyfovarselHendelseProducer,
        vedtakStatusProducer = vedtakStatusProducer,
    )

    private val vedtakService = VedtakService(
        vedtakRepository = vedtakRepository,
        pdfService = PdfService(
            pdfGenClient = externalMockEnvironment.pdfgenClient,
            pdlClient = externalMockEnvironment.pdlClient,
        ),
        journalforingService = journalforingService,
        gosysOppgaveService = gosysOppgaveService,
        infotrygdService = InfotrygdService(
            pdlClient = externalMockEnvironment.pdlClient,
            mqSender = infotrygdMQSender,
        ),
        vedtakProducer = vedtakProducer,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
        coEvery { mockEsyfoVarselKafkaProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
        coEvery { mockVedtakStatusKafkaProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
    }

    @AfterEach
    fun cleanup() = database.dropData()

    @Nested
    @DisplayName("Ferdigbehandle vedtak")
    inner class FerdigbehandlerVedtak {
        @Test
        fun `successfully ferdigbehandler a vedtak`() {
            val createdVedtak = vedtakRepository.createVedtak(
                vedtak = vedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            val persistedVedtak = vedtakRepository.getVedtak(createdVedtak.uuid)
            assertFalse(persistedVedtak.isFerdigbehandlet())

            vedtakService.ferdigbehandleVedtak(persistedVedtak, UserConstants.VEILEDER_IDENT_OTHER)

            val persistedFerdigbehandletVedtak = vedtakRepository.getVedtak(createdVedtak.uuid)
            assertTrue(persistedFerdigbehandletVedtak.isFerdigbehandlet())
            assertEquals(UserConstants.VEILEDER_IDENT_OTHER, persistedFerdigbehandletVedtak.getFerdigbehandletStatus()!!.veilederident)
        }
    }

    @Nested
    @DisplayName("Journalfor vedtak")
    inner class JournalforVedtak {
        @Test
        fun `journalfoerer vedtak som ikke er journalfoert`() {
            vedtakRepository.createVedtak(
                vedtak = vedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            database.setVedtakCreatedAt(OffsetDateTime.now().minusMinutes(1), vedtak.uuid)

            val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }
            val (success, failed) = journalforteVedtak.partition { it.isSuccess }

            assertTrue(failed.isEmpty())
            assertEquals(1, success.size)

            val journalfortVedtak = success.first().getOrThrow()
            assertEquals(mockedJournalpostId, journalfortVedtak.journalpostId)

            val persistedVedtak = vedtakRepository.getVedtak(journalfortVedtak.uuid)
            assertEquals(mockedJournalpostId.value, persistedVedtak.journalpostId!!.value)
        }

        @Test
        fun `journalfoerer ikke naar ingen vedtak`() {
            val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }
            assertTrue(journalforteVedtak.isEmpty())
        }

        @Test
        fun `journalfoerer ikke naar vedtak allerede er journalfoert`() {
            vedtakRepository.createVedtak(
                vedtak = vedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            val journalfortVedtak = vedtak.journalfor(mockedJournalpostId)
            vedtakRepository.setJournalpostId(journalfortVedtak)

            val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }
            assertTrue(journalforteVedtak.isEmpty())
        }

        @Test
        fun `journalfoering feiler mot dokarkiv`() {
            val failingVedtak = generateVedtak().copy(uuid = UserConstants.FAILING_EKSTERN_REFERANSE_UUID)
            vedtakRepository.createVedtak(
                vedtak = failingVedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            database.setVedtakCreatedAt(OffsetDateTime.now().minusMinutes(1), failingVedtak.uuid)

            val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }
            val (success, failed) = journalforteVedtak.partition { it.isSuccess }
            assertEquals(1, failed.size)
            assertTrue(success.isEmpty())
        }

        @Test
        fun `journalfoering feiler mot pdl`() {
            val failingVedtak = generateVedtak(personident = UserConstants.ARBEIDSTAKER_PERSONIDENT_PDL_FAILS)
            vedtakRepository.createVedtak(
                vedtak = failingVedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            database.setVedtakCreatedAt(OffsetDateTime.now().minusMinutes(1), failingVedtak.uuid)

            val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }
            val (success, failed) = journalforteVedtak.partition { it.isSuccess }
            assertEquals(1, failed.size)
            assertTrue(success.isEmpty())
        }

        @Test
        fun `journalfoerer et vedtak selv om annet vedtak feiler`() {
            val failingVedtak = generateVedtak(personident = UserConstants.ARBEIDSTAKER_PERSONIDENT_PDL_FAILS)
            vedtakRepository.createVedtak(
                vedtak = failingVedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            vedtakRepository.createVedtak(
                vedtak = vedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            database.setVedtakCreatedAt(OffsetDateTime.now().minusMinutes(1), failingVedtak.uuid)
            database.setVedtakCreatedAt(OffsetDateTime.now().minusMinutes(1), vedtak.uuid)

            val journalforteVedtak = runBlocking { vedtakService.journalforVedtak() }
            val (success, failed) = journalforteVedtak.partition { it.isSuccess }
            assertEquals(1, failed.size)
            assertEquals(1, success.size)
        }
    }

    @Nested
    @DisplayName("Publish varsel for vedtak to esyfovarsel")
    inner class PublishVarselForVedtakToEsyfovarsel {
        private fun createUnpublishedVedtakVarsel(): Vedtak {
            val unpublishedVedtakVarsel = vedtakRepository.createVedtak(
                vedtak = vedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            vedtakRepository.setJournalpostId(unpublishedVedtakVarsel.copy(journalpostId = journalpostId))
            return unpublishedVedtakVarsel
        }

        @Test
        fun `Publishes varsel for vedtak`() {
            val unpublishedVedtakVarsel = createUnpublishedVedtakVarsel()
            val (success, failed) = vedtakService.publishVedtakVarsel().partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(1, success.size)

            val producerRecordSlot = slot<ProducerRecord<String, EsyfovarselHendelse>>()
            verify(exactly = 1) { mockEsyfoVarselKafkaProducer.send(capture(producerRecordSlot)) }

            val publishedVedtakVarsel = success.first().getOrThrow()
            assertEquals(unpublishedVedtakVarsel.uuid, publishedVedtakVarsel.uuid)
            assertNotNull(database.getVedtakVarselPublishedAt(publishedVedtakVarsel.uuid))

            val esyfovarselHendelse = producerRecordSlot.captured.value() as ArbeidstakerHendelse
            assertEquals(HendelseType.SM_VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING, esyfovarselHendelse.type)
            assertEquals(UserConstants.ARBEIDSTAKER_PERSONIDENT.value, esyfovarselHendelse.arbeidstakerFnr)
            val varselData = esyfovarselHendelse.data as VarselData
            assertEquals(publishedVedtakVarsel.uuid.toString(), varselData.journalpost?.uuid)
            assertEquals(journalpostId.value, varselData.journalpost?.id)
        }

        @Test
        fun `Publishes nothing when no vedtak`() {
            val (success, failed) = vedtakService.publishVedtakVarsel().partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(0, success.size)
        }

        @Test
        fun `Publishes nothing when no journalfort vedtak`() {
            vedtakRepository.createVedtak(
                vedtak = vedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            val (success, failed) = vedtakService.publishVedtakVarsel().partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(0, success.size)
        }

        @Test
        fun `Publishes nothing when varsel for vedtak already published`() {
            val unpublishedVedtakVarsel = createUnpublishedVedtakVarsel()
            vedtakRepository.setVedtakVarselPublished(unpublishedVedtakVarsel)
            val (success, failed) = vedtakService.publishVedtakVarsel().partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(0, success.size)
        }

        @Test
        fun `Fails publishing when kafka-producer fails`() {
            val unpublishedVedtak = createUnpublishedVedtakVarsel()
            every { mockEsyfoVarselKafkaProducer.send(any()) } throws Exception("Error producing to kafka")

            val (success, failed) = vedtakService.publishVedtakVarsel().partition { it.isSuccess }
            assertEquals(1, failed.size)
            assertEquals(0, success.size)

            verify(exactly = 1) { mockEsyfoVarselKafkaProducer.send(any()) }

            val vedtak = vedtakRepository.getUnpublishedVedtakVarsler().first()
            assertEquals(unpublishedVedtak.uuid, vedtak.uuid)
        }
    }

    @Nested
    @DisplayName("Publish unpublished vedtak status to kafka")
    inner class PublishUnpublishedVedtakstatus {
        @Test
        fun `publishes unpublished vedtak to kafka`() {
            val unpublishedVedtak = vedtakRepository.createVedtak(
                vedtak = vedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            val (success, failed) = vedtakService.publishUnpublishedVedtakStatus().partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(1, success.size)

            val publishedVedtak = success.first().getOrThrow()
            assertEquals(unpublishedVedtak.uuid, publishedVedtak.uuid)
            assertNotNull(database.getVedtakStatusPublishedAt(publishedVedtak.getFattetStatus().uuid))

            assertTrue(vedtakRepository.getUnpublishedVedtakStatus().isEmpty())

            val producerRecordSlot = slot<ProducerRecord<String, VedtakStatusRecord>>()
            verify(exactly = 1) { mockVedtakStatusKafkaProducer.send(capture(producerRecordSlot)) }

            val record = producerRecordSlot.captured.value()
            assertEquals(publishedVedtak.uuid, record.uuid)
            assertEquals(publishedVedtak.personident.value, record.personident)
            assertEquals(publishedVedtak.fom, record.fom)
            assertEquals(publishedVedtak.tom, record.tom)
            assertEquals(Status.FATTET, record.status)
            assertEquals(publishedVedtak.getFattetStatus().veilederident, record.statusBy)
        }

        @Test
        fun `publishes unpublished ferdigbehandlet vedtak to kafka`() {
            val unpublishedVedtak = vedtakRepository.createVedtak(
                vedtak = vedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            val (success, _) = vedtakService.publishUnpublishedVedtakStatus().partition { it.isSuccess }
            val publishedVedtak = success.first().getOrThrow()
            assertNotNull(database.getVedtakStatusPublishedAt(publishedVedtak.getFattetStatus().uuid))

            vedtakService.ferdigbehandleVedtak(publishedVedtak, UserConstants.VEILEDER_IDENT)

            clearAllMocks()
            coEvery { mockVedtakStatusKafkaProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)

            val (successFerdigbehandlet, failedFerdigbehandlet) = vedtakService.publishUnpublishedVedtakStatus().partition { it.isSuccess }
            assertEquals(0, failedFerdigbehandlet.size)
            assertEquals(1, successFerdigbehandlet.size)

            val publishedFerdigbehandletVedtak = successFerdigbehandlet.first().getOrThrow()
            assertEquals(unpublishedVedtak.uuid, publishedFerdigbehandletVedtak.uuid)
            assertNotNull(database.getVedtakStatusPublishedAt(publishedFerdigbehandletVedtak.getFerdigbehandletStatus()!!.uuid))

            assertTrue(vedtakRepository.getUnpublishedVedtakStatus().isEmpty())

            val producerRecordSlot = slot<ProducerRecord<String, VedtakStatusRecord>>()
            verify(exactly = 1) { mockVedtakStatusKafkaProducer.send(capture(producerRecordSlot)) }

            val record = producerRecordSlot.captured.value()
            assertEquals(publishedFerdigbehandletVedtak.uuid, record.uuid)
            assertEquals(publishedFerdigbehandletVedtak.personident.value, record.personident)
            assertEquals(Status.FERDIG_BEHANDLET, record.status)
            assertEquals(publishedFerdigbehandletVedtak.getFerdigbehandletStatus()!!.veilederident, record.statusBy)
            assertNotNull(record.statusAt)
        }

        @Test
        fun `publishes nothing when no unpublished varsel`() {
            val (success, failed) = vedtakService.publishUnpublishedVedtakStatus().partition { it.isSuccess }
            assertEquals(0, failed.size)
            assertEquals(0, success.size)
            verify(exactly = 0) { mockVedtakStatusKafkaProducer.send(any()) }
        }

        @Test
        fun `fails publishing when kafka-producer fails`() {
            vedtakRepository.createVedtak(
                vedtak = vedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            every { mockVedtakStatusKafkaProducer.send(any()) } throws Exception("Error producing to kafka")

            val (success, failed) = vedtakService.publishUnpublishedVedtakStatus().partition { it.isSuccess }
            assertEquals(1, failed.size)
            assertEquals(0, success.size)
            verify(exactly = 1) { mockVedtakStatusKafkaProducer.send(any()) }
            assertFalse(vedtakRepository.getUnpublishedVedtakStatus().isEmpty())
        }
    }

    @Nested
    @DisplayName("Send vedtak to infotrygd")
    inner class SendVedtakToInfotrygd {
        @Test
        fun `sends vedtak to infotrygd, updates vedtak and returns result with updated vedtak`() {
            vedtakRepository.createVedtak(
                vedtak = vedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            justRun { infotrygdMQSender.sendToMQ(any(), any()) }

            val result = runBlocking { vedtakService.sendVedtakToInfotrygd(vedtak = vedtak) }
            verify(exactly = 1) { infotrygdMQSender.sendToMQ(any(), any()) }
            assertTrue(result.isSuccess)
            val publishedVedtak = result.getOrThrow()
            assertEquals(InfotrygdStatus.KVITTERING_MANGLER, publishedVedtak.infotrygdStatus)

            val persistedVedtak = vedtakRepository.getVedtak(publishedVedtak.uuid)
            assertEquals(InfotrygdStatus.KVITTERING_MANGLER, persistedVedtak.infotrygdStatus)
        }

        @Test
        fun `returns failure when sending vedtak to infotrygd fails`() {
            vedtakRepository.createVedtak(
                vedtak = vedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            every { infotrygdMQSender.sendToMQ(any(), any()) } throws Exception("Error sending to infotrygd")

            val result = runBlocking { vedtakService.sendVedtakToInfotrygd(vedtak = vedtak) }
            verify(exactly = 1) { infotrygdMQSender.sendToMQ(any(), any()) }
            assertTrue(result.isFailure)
        }
    }

    @Nested
    @DisplayName("Create gosys oppgave for vedtak without oppgaveId")
    inner class CreateOppgaveForVedtakWithNoOppgave {
        @Test
        fun `creates oppgave for vedtak without oppgaveId`() {
            val vedtakUtenOppgave = vedtakRepository.createVedtak(
                vedtak = vedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            vedtakRepository.setJournalpostId(vedtakUtenOppgave.journalfor(journalpostId = journalpostId))
            database.setVedtakCreatedAt(OffsetDateTime.now().minusMinutes(1), vedtakUtenOppgave.uuid)

            val result = runBlocking { vedtakService.createGosysOppgaveForVedtakUtenOppgave() }
            val (success, failed) = result.partition { it.isSuccess }
            assertTrue(failed.isEmpty())
            assertEquals(1, success.size)

            val oppdatertVedtak = vedtakRepository.getVedtak(vedtakUtenOppgave.uuid)
            assertNotNull(oppdatertVedtak.gosysOppgaveId)
            assertNotNull(oppdatertVedtak.gosysOppgaveAt)
        }

        @Test
        fun `returns empty when vedtak without oppgaveId is not journalfort`() {
            vedtakRepository.createVedtak(
                vedtak = vedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )
            val result = runBlocking { vedtakService.createGosysOppgaveForVedtakUtenOppgave() }
            assertTrue(result.isEmpty())
        }

        @Test
        fun `returns empty when no vedtak without oppgaveId`() {
            val result = runBlocking { vedtakService.createGosysOppgaveForVedtakUtenOppgave() }
            assertTrue(result.isEmpty())
        }
    }
}
