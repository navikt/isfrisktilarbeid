package no.nav.syfo.infrastructure.cronjob

import io.ktor.server.testing.*
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.application.VedtakService
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getVedtak
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.mq.MQSender
import no.nav.syfo.infrastructure.pdf.PdfService
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBe
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

class PublishMQCronjobSpek : Spek({

    describe(PublishMQCronjobSpek::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val environment = externalMockEnvironment.environment
            val mqSenderMock = mockk<MQSender>(relaxed = true)
            val vedtakService = VedtakService(
                pdfService = PdfService(externalMockEnvironment.pdfgenClient, externalMockEnvironment.pdlClient),
                vedtakRepository = VedtakRepository(database),
                journalforingService = mockk<JournalforingService>(relaxed = true),
                infotrygdService = InfotrygdService(environment.mq.mqQueueName, mqSenderMock)
            )

            val publishMQCronjob = PublishMQCronjob(vedtakService)

            beforeEachTest {
                database.dropData()
                clearAllMocks()
                justRun { mqSenderMock.sendToMQ(any(), any()) }
            }

            describe("Cronjob sender lagrede vedtak") {
                it("Sender lagret vedtak") {
                    val fom = LocalDate.now()
                    val tom = LocalDate.now().plusDays(30)
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
                    val lagretVedtakBefore = database.getVedtak(vedtak.uuid)
                    lagretVedtakBefore!!.publishedInfotrygdAt shouldBe null

                    runBlocking {
                        publishMQCronjob.run()
                    }

                    val lagretVedtakAfter = database.getVedtak(vedtak.uuid)
                    lagretVedtakAfter!!.publishedInfotrygdAt shouldNotBe null

                    val queueNameSlot = slot<String>()
                    val payloadSlot = slot<String>()
                    verify(exactly = 1) { mqSenderMock.sendToMQ(capture(queueNameSlot), capture(payloadSlot)) }

                    queueNameSlot.captured shouldBeEqualTo environment.mq.mqQueueName

                    // vedtak should not be sent again when already published
                    clearMocks(mqSenderMock)
                    runBlocking {
                        publishMQCronjob.run()
                    }
                    verify(exactly = 0) { mqSenderMock.sendToMQ(any(), any()) }
                }
            }
        }
    }
})
