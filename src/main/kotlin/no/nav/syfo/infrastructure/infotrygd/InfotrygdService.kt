package no.nav.syfo.infrastructure.infotrygd

import no.nav.syfo.domain.Vedtak
import no.nav.syfo.infrastructure.mq.InfotrygdMQSender
import java.time.format.DateTimeFormatter

class InfotrygdService(
    val mqSender: InfotrygdMQSender,
) {

    fun sendMessageToInfotrygd(
        vedtak: Vedtak,
    ) {
        val infotrygdMessage = StringBuilder()
        infotrygdMessage.append("K278M810")
        infotrygdMessage.append("SENDMELDING")
        infotrygdMessage.append("MODIA")
        infotrygdMessage.append(vedtak.veilederident.padEnd(8))
        infotrygdMessage.append("00000")
        infotrygdMessage.append(dateFormatter.format(vedtak.createdAt))
        infotrygdMessage.append(timeFormatter.format(vedtak.createdAt))
        infotrygdMessage.append("0315") // TODO: nav-kontor eller kommunenr
        infotrygdMessage.append(vedtak.personident.value)
        infotrygdMessage.append("".padEnd(4))
        infotrygdMessage.append("O".padEnd(2))
        infotrygdMessage.append("K278M83000001")
        infotrygdMessage.append("MA-TSP-1".padEnd(10))
        infotrygdMessage.append("001FA")
        infotrygdMessage.append(dateFormatter.format(vedtak.fom))
        infotrygdMessage.append(dateFormatter.format(vedtak.tom))
        infotrygdMessage.append("".padEnd(72))
        infotrygdMessage.append("K278M84000000")

        mqSender.sendToMQ(
            payload = infotrygdMessage.toString(),
        )
    }

    companion object {
        val dateFormatter = DateTimeFormatter.ofPattern("ddMMYYYY")
        val timeFormatter = DateTimeFormatter.ofPattern("HHmmss")
    }
}
