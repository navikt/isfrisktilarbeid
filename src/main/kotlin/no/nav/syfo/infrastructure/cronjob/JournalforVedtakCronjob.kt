package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.application.VedtakService

class JournalforVedtakCronjob(
    private val vedtakService: VedtakService,
) : Cronjob {

    override val initialDelayMinutes: Long = 3
    override val intervalDelayMinutes: Long = 2

    override suspend fun run(): List<Result<Any>> = vedtakService.journalforVedtak()
}
