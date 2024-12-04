package no.nav.syfo.infrastructure.infotrygd

import com.ibm.jms.JMSBytesMessage
import io.mockk.*
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.application.vedtak
import no.nav.syfo.domain.InfotrygdStatus
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getVedtakInfotrygdFeilmelding
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import no.nav.syfo.infrastructure.mq.EBCDIC
import no.nav.syfo.infrastructure.mq.InfotrygdKvitteringMQConsumer
import no.nav.syfo.infrastructure.mq.asBytes
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import javax.jms.*

val externalMockEnvironment = ExternalMockEnvironment.instance
val database = externalMockEnvironment.database

class InfotrygdKvitteringSpek : Spek({
    describe(InfotrygdKvitteringSpek::class.java.simpleName) {
        val vedtakRepository = VedtakRepository(database)
        val messageConsumer = mockk<MessageConsumer>(relaxed = true)
        val incomingMessage = mockk<JMSBytesMessage>(relaxed = true)

        val infotrygdKvitteringMQConsumer = InfotrygdKvitteringMQConsumer(
            applicationState = externalMockEnvironment.applicationState,
            inputconsumer = messageConsumer,
            vedtakRepository = vedtakRepository,
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
                vedtakRepository.setVedtakPublishedInfotrygd(vedtak = vedtak)

                val correlationId = vedtak.uuid.asBytes()

                val kvittering = "xxxxxxxxxxxxxxxxxxxMODIA1111113052024150000${UserConstants.ARBEIDSTAKER_PERSONIDENT.value}Jxxxxxxxx".toByteArray(EBCDIC)

                every { incomingMessage.getBody(ByteArray::class.java) } returns (kvittering)
                every { incomingMessage.jmsCorrelationIDAsBytes } returns (correlationId)

                infotrygdKvitteringMQConsumer.processKvitteringMessage(incomingMessage)

                val vedtakWithKvittering = vedtakRepository.getVedtak(uuid = vedtak.uuid)
                vedtakWithKvittering.infotrygdStatus shouldBeEqualTo InfotrygdStatus.KVITTERING_OK
                database.getVedtakInfotrygdFeilmelding(createdVedtak.uuid) shouldBe null
            }
            it("Prosesserer innkommet kvittering (med feilkode)") {
                val createdVedtak = vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                )
                vedtakRepository.setVedtakPublishedInfotrygd(vedtak = vedtak)

                val correlationId = vedtak.uuid.asBytes()

                val kvittering = "xxxxxxxxxxxxxxxxxxxMODIA1111113052024150000${UserConstants.ARBEIDSTAKER_PERSONIDENT.value}NFeilkode".toByteArray(EBCDIC)

                every { incomingMessage.getBody(ByteArray::class.java) } returns (kvittering)
                every { incomingMessage.jmsCorrelationIDAsBytes } returns (correlationId)

                infotrygdKvitteringMQConsumer.processKvitteringMessage(incomingMessage)

                val vedtakWithKvittering = vedtakRepository.getVedtak(uuid = vedtak.uuid)
                vedtakWithKvittering.infotrygdStatus shouldBeEqualTo InfotrygdStatus.KVITTERING_FEIL
                database.getVedtakInfotrygdFeilmelding(createdVedtak.uuid) shouldBeEqualTo "Feilkode"
            }
        }
    }
})
