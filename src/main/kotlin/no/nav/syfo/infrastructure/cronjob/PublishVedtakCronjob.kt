package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.application.VedtakService

class PublishVedtakCronjob(
    private val vedtakService: VedtakService,
) : Cronjob {
    override val initialDelayMinutes: Long = 3
    override val intervalDelayMinutes: Long = 1

    override suspend fun run() = vedtakService.publishUnpublishedVedtakStatus()
}
