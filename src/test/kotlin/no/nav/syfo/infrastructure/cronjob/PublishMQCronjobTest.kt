package no.nav.syfo.infrastructure.cronjob

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.application.IVedtakProducer
import no.nav.syfo.application.VedtakService
import no.nav.syfo.domain.InfotrygdStatus
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getPublishedInfotrygdAt
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import no.nav.syfo.infrastructure.database.setVedtakCreatedAt
import no.nav.syfo.infrastructure.gosysoppgave.GosysOppgaveService
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.mq.InfotrygdMQSender
import no.nav.syfo.infrastructure.pdf.PdfService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.OffsetDateTime

class PublishMQCronjobTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val mqSenderMock = mockk<InfotrygdMQSender>(relaxed = true)
    private val vedtakRepository = VedtakRepository(database)
    private val vedtakService = VedtakService(
        pdfService = PdfService(externalMockEnvironment.pdfgenClient, externalMockEnvironment.pdlClient),
        vedtakRepository = vedtakRepository,
        journalforingService = mockk<JournalforingService>(relaxed = true),
        gosysOppgaveService = mockk<GosysOppgaveService>(relaxed = true),
        infotrygdService = InfotrygdService(externalMockEnvironment.pdlClient, mqSenderMock),
        vedtakProducer = mockk<IVedtakProducer>(relaxed = true),
    )
    private val publishMQCronjob = PublishMQCronjob(vedtakService)

    @BeforeEach
    fun setup() {
        database.dropData()
        clearAllMocks()
        justRun { mqSenderMock.sendToMQ(any(), any()) }
    }

    private val fom = LocalDate.now()
    private val tom = LocalDate.now().plusDays(30)

    @Test
    fun `Sender ikke vedtak lagret naa`() = runBlocking {
        vedtakService.createVedtak(
            personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            veilederident = UserConstants.VEILEDER_IDENT,
            begrunnelse = "begrunnelse",
            document = generateDocumentComponent("begrunnelse"),
            fom = fom,
            tom = tom,
            callId = "callId",
        )
        publishMQCronjob.run()
        verify(exactly = 0) { mqSenderMock.sendToMQ(any(), any()) }
    }

    @Test
    fun `Sender vedtak lagret for 1 minutt siden`() = runBlocking {
        val (vedtak, _) = vedtakService.createVedtak(
            personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            veilederident = UserConstants.VEILEDER_IDENT,
            begrunnelse = "begrunnelse",
            document = generateDocumentComponent("begrunnelse"),
            fom = fom,
            tom = tom,
            callId = "callId",
        )
        database.setVedtakCreatedAt(OffsetDateTime.now().minusMinutes(1), vedtak.uuid)

        assertEquals(InfotrygdStatus.IKKE_SENDT, vedtak.infotrygdStatus)
        assertNull(database.getPublishedInfotrygdAt(vedtak.uuid))

        publishMQCronjob.run()

        assertNotNull(database.getPublishedInfotrygdAt(vedtak.uuid))

        val payloadSlot = slot<String>()
        verify(exactly = 1) { mqSenderMock.sendToMQ(capture(payloadSlot), any()) }

        clearAllMocks()
        publishMQCronjob.run()
        verify(exactly = 0) { mqSenderMock.sendToMQ(any(), any()) }

        val publishedVedtak = vedtakService.getVedtak(personident = UserConstants.ARBEIDSTAKER_PERSONIDENT).first()
        assertEquals(InfotrygdStatus.KVITTERING_MANGLER, publishedVedtak.infotrygdStatus)
    }
}
