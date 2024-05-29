package no.nav.syfo.infrastructure.infotrygd

import no.nav.syfo.domain.Vedtak
import no.nav.syfo.infrastructure.clients.pdl.GeografiskTilknytningType
import no.nav.syfo.infrastructure.clients.pdl.PdlClient
import no.nav.syfo.infrastructure.clients.pdl.dto.gradering
import no.nav.syfo.infrastructure.clients.pdl.dto.isKode6
import no.nav.syfo.infrastructure.clients.pdl.dto.isKode7
import no.nav.syfo.infrastructure.mq.InfotrygdMQSender
import org.slf4j.LoggerFactory
import java.time.format.DateTimeFormatter

class InfotrygdService(
    val pdlClient: PdlClient,
    val mqSender: InfotrygdMQSender,
) {

    suspend fun sendMessageToInfotrygd(
        vedtak: Vedtak,
    ) {
        val infotrygdMessage = StringBuilder()
        // Format definert her: https://confluence.adeo.no/display/INFOTRYGD/IT30_MA+-+Meldinger+mellom+INFOTRYGD+OG+ARENA
        infotrygdMessage.append("K278M810")
        infotrygdMessage.append("SENDMELDING")
        infotrygdMessage.append("MODIA")
        infotrygdMessage.append(vedtak.getFattetStatus().veilederident.padEnd(8))
        infotrygdMessage.append("00000")
        infotrygdMessage.append(dateFormatter.format(vedtak.createdAt))
        infotrygdMessage.append(timeFormatter.format(vedtak.createdAt))
        infotrygdMessage.append(
            hentBostedskommune(vedtak)
                ?: throw RuntimeException("Cannot send to Infotrygd: bostedskommune missing for vedtak ${vedtak.uuid}")
        )
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
            correlationId = vedtak.uuid,
        )
    }

    private suspend fun hentBostedskommune(vedtak: Vedtak): String? =
        try {
            val gradering = pdlClient.getPerson(vedtak.personident).gradering()
            if (gradering.any { it.isKode6() || it.isKode7() }) {
                logger.error("Hent geografisk tilknytning feilet for vedtak ${vedtak.uuid}")
                null
            } else {
                val geografiskTilknytning = pdlClient.geografiskTilknytning(vedtak.personident)
                if (geografiskTilknytning.type == GeografiskTilknytningType.KOMMUNE) {
                    geografiskTilknytning.kommune
                } else if (geografiskTilknytning.type == GeografiskTilknytningType.BYDEL) {
                    geografiskTilknytning.bydel?.substring(0, 4)
                } else {
                    logger.error("Geografisk tilknytning er UTLAND/UDEFINERT for vedtak ${vedtak.uuid}")
                    null
                }
            }
        } catch (exc: Exception) {
            logger.error("Hent geografisk tilknytning feilet for vedtak ${vedtak.uuid}", exc)
            null
        }

    companion object {
        private val logger = LoggerFactory.getLogger(InfotrygdService::class.java)

        val dateFormatter = DateTimeFormatter.ofPattern("ddMMYYYY")
        val timeFormatter = DateTimeFormatter.ofPattern("HHmmss")
    }
}
