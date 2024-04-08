package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import org.slf4j.LoggerFactory
import java.time.LocalDate

class SendTestMessageCronjob(
    private val infotrygdService: InfotrygdService,
    private val testIdent: Personident,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 365 * 24 * 60

    override suspend fun run() {
        try {
            infotrygdService.sendMessageToInfotrygd(
                Vedtak(
                    personident = testIdent,
                    veilederident = "Z994248",
                    "",
                    emptyList(),
                    fom = LocalDate.now().plusDays(1),
                    tom = LocalDate.now().plusDays(31),
                )
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
