package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.application.VedtakService
import no.nav.syfo.infrastructure.clients.leaderelection.LeaderPodClient
import no.nav.syfo.launchBackgroundTask

fun launchCronjobs(
    applicationState: ApplicationState,
    environment: Environment,
    vedtakService: VedtakService,
) {
    val leaderPodClient = LeaderPodClient(
        electorPath = environment.electorPath
    )
    val cronjobRunner = CronjobRunner(
        applicationState = applicationState,
        leaderPodClient = leaderPodClient,
    )
    val cronjobs = mutableListOf<Cronjob>()

    val publishMQCronjob = PublishMQCronjob(vedtakService)
    cronjobs.add(publishMQCronjob)

    val journalforVedtakCronjob = JournalforVedtakCronjob(vedtakService = vedtakService)
    cronjobs.add(journalforVedtakCronjob)

    val gosysOppgaveCronjob = GosysOppgaveCronjob(vedtakService = vedtakService)
    cronjobs.add(gosysOppgaveCronjob)

    val publishVedtakVarselCronjob = PublishVedtakVarselCronjob(vedtakService = vedtakService)
    cronjobs.add(publishVedtakVarselCronjob)

    val publishVedtakStatusCronjob = PublishVedtakStatusCronjob(vedtakService)
    cronjobs.add(publishVedtakStatusCronjob)

    cronjobs.forEach {
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = it)
        }
    }
}
