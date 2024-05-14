package no.nav.syfo.infrastructure.mq

data class MQEnvironment(
    val mqQueueManager: String,
    val mqHostname: String,
    val mqPort: Int,
    val mqChannelName: String,
    val mqQueueName: String,
    val mqQueueNameKvittering: String,
    val mqApplicationName: String = "isfrisktilarbeid",
    val serviceuserUsername: String,
    val serviceuserPassword: String,
)
