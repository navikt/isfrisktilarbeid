package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.application.VedtakService

class PublishMQCronjob(
    private val vedtakService: VedtakService,
) : Cronjob {
    override val initialDelayMinutes: Long = 2
    override val intervalDelayMinutes: Long = 1

    override suspend fun run() = vedtakService.sendVedtakToInfotrygd()
}
