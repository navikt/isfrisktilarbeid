package no.nav.syfo.infrastructure.cronjob

import no.nav.syfo.ApplicationState
import no.nav.syfo.Environment
import no.nav.syfo.infrastructure.clients.leaderelection.LeaderPodClient
import no.nav.syfo.application.VedtakService
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import no.nav.syfo.infrastructure.mq.MQSender
import no.nav.syfo.launchBackgroundTask
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(CronjobRunner::class.java)

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

    log.info("Creating MQSender")
    val mqSender = MQSender(environment.mq)

    log.info("Creating Cronjob")
    val sendTestMessageCronjob = SendTestMessageCronjob(
        infotrygdService = InfotrygdService(
            mqQueueName = environment.mq.mqQueueName,
            mqSender = mqSender,
        ),
        testIdent = environment.testident,
    )
    cronjobs.add(sendTestMessageCronjob)

    val publishMQCronjob = PublishMQCronjob(vedtakService)
    cronjobs.add(publishMQCronjob)

    val journalforVedtakCronjob = JournalforVedtakCronjob(vedtakService = vedtakService)
    cronjobs.add(journalforVedtakCronjob)

    cronjobs.forEach {
        log.info("Launching cronjob: $it")
        launchBackgroundTask(
            applicationState = applicationState,
        ) {
            cronjobRunner.start(cronjob = it)
        }
    }
}
