package no.nav.syfo.infrastructure.mq

import com.ibm.jms.JMSBytesMessage
import kotlinx.coroutines.delay
import no.nav.syfo.ApplicationState
import no.nav.syfo.application.IVedtakRepository
import no.nav.syfo.domain.Personident
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.jms.Message
import javax.jms.MessageConsumer

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.infrastructure.mq")

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
        val inputMessageText = when (message) {
            is JMSBytesMessage -> message.getBody(String::class.java)
            else -> {
                log.warn("InfotrygdKvitteringMQConsumer message ignored, incoming message needs to be a byte message or text message")
                null
            }
        }

        if (inputMessageText != null) {
            log.info("Kvittering fra Infotrygd: $inputMessageText")
            storeKvittering(inputMessageText)
        }
        message.acknowledge()
    }

    private fun storeKvittering(kvittering: String) {
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

        if (kvittering.length >= 55) {
            val personident = kvittering.substring(43, 54)
            val feilkode = kvittering[54]
            val ok = feilkode == 'J'
            var feilmelding: String? = null
            if (!ok) {
                feilmelding = kvittering.substring(55)
            }
            val vedtak = vedtakRepository.getVedtak(Personident(personident)).firstOrNull()
            if (vedtak == null) {
                log.warn("Kvittering received from Infotrygd, but no vedtak found: $kvittering")
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
}
