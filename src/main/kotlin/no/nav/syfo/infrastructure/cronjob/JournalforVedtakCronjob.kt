package no.nav.syfo.infrastructure.cronjob

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.VedtakService
import org.slf4j.LoggerFactory

class JournalforVedtakCronjob(
    private val vedtakService: VedtakService,
) : Cronjob {

    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() {
        val (success, failed) = vedtakService.journalforVedtak().partition { it.isSuccess }
        failed.forEach {
            log.error("Exception caught while journalforing vedtak", it.exceptionOrNull())
        }
        if (failed.size + success.size > 0) {
            log.info(
                "Completed journalforing vedtak with result: {}, {}",
                StructuredArguments.keyValue("failed", failed.size),
                StructuredArguments.keyValue("updated", success.size),
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(JournalforVedtakCronjob::class.java)
    }
}
