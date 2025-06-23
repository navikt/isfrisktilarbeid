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
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.mq.InfotrygdMQSender
import no.nav.syfo.infrastructure.gosysoppgave.GosysOppgaveService
import no.nav.syfo.infrastructure.pdf.PdfService
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime

class PublishMQCronjobSpek : Spek({

    describe(PublishMQCronjobSpek::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val mqSenderMock = mockk<InfotrygdMQSender>(relaxed = true)
        val vedtakRepository = VedtakRepository(database)
        val vedtakService = VedtakService(
            pdfService = PdfService(externalMockEnvironment.pdfgenClient, externalMockEnvironment.pdlClient),
            vedtakRepository = vedtakRepository,
            journalforingService = mockk<JournalforingService>(relaxed = true),
            oppgaveService = mockk<GosysOppgaveService>(relaxed = true),
            infotrygdService = InfotrygdService(externalMockEnvironment.pdlClient, mqSenderMock),
            vedtakProducer = mockk<IVedtakProducer>(relaxed = true),
        )
        val publishMQCronjob = PublishMQCronjob(vedtakService)

        beforeEachTest {
            database.dropData()
            clearAllMocks()
            justRun { mqSenderMock.sendToMQ(any(), any()) }
        }

        describe("Cronjob sender lagrede vedtak") {
            val fom = LocalDate.now()
            val tom = LocalDate.now().plusDays(30)

            it("Sender ikke vedtak lagret nå") {
                runBlocking {
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
                }

                verify(exactly = 0) { mqSenderMock.sendToMQ(any(), any()) }
            }
            it("Sender vedtak lagret for 1 minutt siden") {
                val vedtak = runBlocking {
                    vedtakService.createVedtak(
                        personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        veilederident = UserConstants.VEILEDER_IDENT,
                        begrunnelse = "begrunnelse",
                        document = generateDocumentComponent("begrunnelse"),
                        fom = fom,
                        tom = tom,
                        callId = "callId",
                    )
                }
                database.setVedtakCreatedAt(OffsetDateTime.now().minusMinutes(1), vedtak.uuid)

                vedtak.infotrygdStatus shouldBeEqualTo InfotrygdStatus.IKKE_SENDT
                database.getPublishedInfotrygdAt(vedtak.uuid) shouldBe null

                runBlocking {
                    publishMQCronjob.run()
                }

                database.getPublishedInfotrygdAt(vedtak.uuid) shouldNotBe null

                val payloadSlot = slot<String>()
                verify(exactly = 1) { mqSenderMock.sendToMQ(capture(payloadSlot), any()) }

                // vedtak should not be sent again when already published
                clearMocks(mqSenderMock)
                runBlocking {
                    publishMQCronjob.run()
                }
                verify(exactly = 0) { mqSenderMock.sendToMQ(any(), any()) }

                val publishedVedtak = vedtakService.getVedtak(personident = UserConstants.ARBEIDSTAKER_PERSONIDENT).first()
                publishedVedtak.infotrygdStatus shouldBeEqualTo InfotrygdStatus.KVITTERING_MANGLER
            }
        }
    }
})
