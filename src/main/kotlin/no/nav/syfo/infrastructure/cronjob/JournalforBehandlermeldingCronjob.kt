package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.application.BehandlermeldingService

class JournalforBehandlermeldingCronjob(private val behandlermeldingService: BehandlermeldingService) : Cronjob {

    override val initialDelayMinutes: Long = 4
    override val intervalDelayMinutes: Long = 10

    override suspend fun run() = behandlermeldingService.journalforBehandlermeldinger()
}
