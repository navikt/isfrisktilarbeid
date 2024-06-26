package no.nav.syfo.infrastructure.mq

import com.ibm.jms.JMSBytesMessage
import kotlinx.coroutines.delay
import no.nav.syfo.ApplicationState
import no.nav.syfo.application.IVedtakRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.UUID
import javax.jms.Message
import javax.jms.MessageConsumer

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.infrastructure.mq")
val EBCDIC = Charset.forName("Cp1047") // encoding som brukes av Infotrygd (z/OS)

class InfotrygdKvitteringMQConsumer(
    val applicationState: ApplicationState,
    val inputconsumer: MessageConsumer,
    val vedtakRepository: IVedtakRepository,
) {

    suspend fun run() {
        try {
            while (applicationState.ready) {
                val message = inputconsumer.receiveNoWait()
                if (message == null) {
                    delay(100)
                    continue
                }
                processKvitteringMessage(message)
            }
        } catch (exc: Exception) {
            log.error("InfotrygdKvitteringMQConsumer failed, restarting application", exc)
        } finally {
            applicationState.alive = false
        }
    }

    fun processKvitteringMessage(message: Message) {
        val inputMessageBody = when (message) {
            is JMSBytesMessage -> message.getBody(ByteArray::class.java)
            else -> {
                log.warn("InfotrygdKvitteringMQConsumer message ignored, incoming message needs to be a bytes message")
                null
            }
        }

        if (inputMessageBody != null) {
            val inputMessageText = inputMessageBody.toString(EBCDIC)
            val correlationId = message.jmsCorrelationIDAsBytes.toUUID()
            log.info("Kvittering mottatt fra Infotrygd med correlationId: $correlationId")

            storeKvittering(
                kvittering = inputMessageText,
                correlationId = correlationId,
            )
        }
        message.acknowledge()
    }

    private fun storeKvittering(
        kvittering: String,
        correlationId: UUID,
    ) {
        /*
        https://confluence.adeo.no/display/INFOTRYGD/IT30_MA+-+Meldinger+mellom+INFOTRYGD+OG+ARENA#IT30_MAMeldingermellomINFOTRYGDOGARENA-K278M890%E2%80%93Kvitteringsmelding

        10 :TAG:-COPY-ID                  PIC X(8).
        10 :TAG:-AKSJON                   PIC X(11).
        10 :TAG:-KILDE                    PIC X(5).
        10 :TAG:-MLEN                     PIC 9(5).
        10 :TAG:-DATO                     PIC X(8).
        10 :TAG:-KLOKKE                   PIC X(6).
        10 :TAG:-FNR                      PIC X(11).
        10 :TAG:-STATUS-OK                PIC X(1).
        10 :TAG:-FEILKODE                 PIC X(8).
        10 :TAG:-FEILMELDING              PIC X(100).
         */

        val vedtak = vedtakRepository.getVedtak(correlationId)
        if (kvittering.length >= 55) {
            val personident = kvittering.substring(43, 54)
            val feilkode = kvittering[54]
            val ok = feilkode == 'J'
            var feilmelding: String? = null
            if (!ok) {
                feilmelding = kvittering.substring(55)
            }
            if (vedtak == null || vedtak.personident.value != personident) {
                log.error("Kvittering received from Infotrygd, but no vedtak found for correlationId $correlationId")
            } else {
                vedtakRepository.setInfotrygdKvitteringReceived(
                    vedtak = vedtak,
                    ok = ok,
                    feilmelding = feilmelding,
                )
            }
        } else {
            log.error("Invalid kvittering received from Infotrygd: $kvittering")
        }
    }

    private fun ByteArray.toUUID() = ByteBuffer.wrap(this).let {
        it.getLong() // skip first 8 bytes
        UUID(it.getLong(), it.getLong())
    }
}
