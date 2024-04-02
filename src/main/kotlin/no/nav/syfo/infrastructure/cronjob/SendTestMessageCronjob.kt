package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime

class SendTestMessageCronjob(
    private val infotrygdService: InfotrygdService,
    private val testIdent: Personident,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 365 * 24 * 60

    override suspend fun run() {
        try {
            infotrygdService.sendMessageToInfotrygd(
                personident = testIdent,
                veilederident = "Z994248",
                navKontor = "0219",
                now = LocalDateTime.now(),
                datoFra = LocalDate.now().plusDays(1),
                datoTil = LocalDate.now().plusDays(31),
            )
            log.info("Completed SendTestMessageCronjob")
        } catch (exc: Exception) {
            log.error("Got exception when sending test message", exc)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(SendTestMessageCronjob::class.java)
    }
}
