package no.nav.syfo.infrastructure.infotrygd

import com.ibm.jms.JMSBytesMessage
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.InfotrygdStatus
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getVedtakInfotrygdFeilmelding
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import no.nav.syfo.infrastructure.mq.EBCDIC
import no.nav.syfo.infrastructure.mq.InfotrygdKvitteringMQConsumer
import no.nav.syfo.infrastructure.mq.asBytes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import javax.jms.MessageConsumer

class InfotrygdKvitteringTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val vedtakRepository = VedtakRepository(database)
    private val messageConsumer = mockk<MessageConsumer>(relaxed = true)
    private val incomingMessage = mockk<JMSBytesMessage>(relaxed = true)
    private val infotrygdKvitteringMQConsumer = InfotrygdKvitteringMQConsumer(
        applicationState = externalMockEnvironment.applicationState,
        inputconsumer = messageConsumer,
        vedtakRepository = vedtakRepository,
    )

    @BeforeEach
    fun setup() {
        database.dropData()
        clearAllMocks()
    }

    @Test
    fun `Prosesserer innkommet kvittering ok`() {
        val domainVedtak = generateVedtak()
        val createdVedtak = vedtakRepository.createVedtak(
            vedtak = domainVedtak,
            vedtakPdf = UserConstants.PDF_VEDTAK,
        )
        vedtakRepository.setVedtakPublishedInfotrygd(vedtak = domainVedtak)
        val correlationId = domainVedtak.uuid.asBytes()
        val kvittering =
            "xxxxxxxxxxxxxxxxxxxMODIA1111113052024150000${UserConstants.ARBEIDSTAKER_PERSONIDENT.value}Jxxxxxxxx".toByteArray(EBCDIC)
        every { incomingMessage.getBody(ByteArray::class.java) } returns kvittering
        every { incomingMessage.jmsCorrelationIDAsBytes } returns correlationId
        infotrygdKvitteringMQConsumer.processKvitteringMessage(incomingMessage)
        val vedtakWithKvittering = vedtakRepository.getVedtak(uuid = domainVedtak.uuid)
        assertEquals(InfotrygdStatus.KVITTERING_OK, vedtakWithKvittering.infotrygdStatus)
        assertNull(database.getVedtakInfotrygdFeilmelding(createdVedtak.uuid))
    }

    @Test
    fun `Prosesserer innkommet kvittering med feilkode`() {
        val domainVedtak = generateVedtak()
        val createdVedtak = vedtakRepository.createVedtak(
            vedtak = domainVedtak,
            vedtakPdf = UserConstants.PDF_VEDTAK,
        )
        vedtakRepository.setVedtakPublishedInfotrygd(vedtak = domainVedtak)
        val correlationId = domainVedtak.uuid.asBytes()
        val kvittering =
            "xxxxxxxxxxxxxxxxxxxMODIA1111113052024150000${UserConstants.ARBEIDSTAKER_PERSONIDENT.value}NFeilkode".toByteArray(EBCDIC)
        every { incomingMessage.getBody(ByteArray::class.java) } returns kvittering
        every { incomingMessage.jmsCorrelationIDAsBytes } returns correlationId
        infotrygdKvitteringMQConsumer.processKvitteringMessage(incomingMessage)
        val vedtakWithKvittering = vedtakRepository.getVedtak(uuid = domainVedtak.uuid)
        assertEquals(InfotrygdStatus.KVITTERING_FEIL, vedtakWithKvittering.infotrygdStatus)
        assertNotNull(database.getVedtakInfotrygdFeilmelding(createdVedtak.uuid))
        assertEquals("Feilkode", database.getVedtakInfotrygdFeilmelding(createdVedtak.uuid))
    }
}
