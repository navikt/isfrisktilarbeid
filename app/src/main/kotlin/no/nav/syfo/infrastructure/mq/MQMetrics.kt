package no.nav.syfo.infrastructure.mq

import io.micrometer.core.instrument.Counter
import no.nav.syfo.infrastructure.metric.METRICS_NS
import no.nav.syfo.infrastructure.metric.METRICS_REGISTRY

const val MQ_PRODUCER_BASE = "${METRICS_NS}_mq_producer"
const val MQ_PRODUCER_MESSAGE_SENT = "${MQ_PRODUCER_BASE}_sent"

val COUNT_MQ_PRODUCER_MESSAGE_SENT: Counter =
    Counter.builder(MQ_PRODUCER_MESSAGE_SENT)
        .description("Counts the number of messages sent to Infotrygd via MQ")
        .register(METRICS_REGISTRY)
