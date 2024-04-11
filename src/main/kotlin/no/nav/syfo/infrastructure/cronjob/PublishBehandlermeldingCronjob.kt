package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.application.BehandlermeldingService

class PublishBehandlermeldingCronjob(private val behandlermeldingService: BehandlermeldingService) : Cronjob {
    override val initialDelayMinutes: Long = 5
    override val intervalDelayMinutes: Long = 10

    override suspend fun run(): List<Result<Any>> = behandlermeldingService.publishBehandlermeldinger()
}
