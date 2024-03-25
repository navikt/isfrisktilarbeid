package no.nav.syfo.application.mq

import io.micrometer.core.instrument.Counter
import no.nav.syfo.Environment
import no.nav.syfo.infrastructure.metric.METRICS_NS
import no.nav.syfo.infrastructure.metric.METRICS_REGISTRY
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.jms.JMSContext

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.application.mq")

class MQSender(private val env: Environment) {

    private val jmsContext: JMSContext = connectionFactory(env).createContext()

    protected fun finalize() {
        try {
            jmsContext.close()
        } catch (exc: Exception) {
            log.warn("Got exception when closing MQ-connection", exc)
        }
    }

    fun sendMessageToInfotrygd(payload: String) {
        jmsContext.createContext(JMSContext.AUTO_ACKNOWLEDGE).use { context ->
            val destination = context.createQueue("queue:///${env.mq.mqQueueName}")
            val message = context.createTextMessage(payload)
            context.createProducer().send(destination, message)
        }
        Metrics.COUNT_MQ_PRODUCER_MESSAGE_SENT.increment()
    }
}

private class Metrics {
    companion object {
        const val MQ_PRODUCER_BASE = "${METRICS_NS}_mq_producer"
        const val MQ_PRODUCER_MESSAGE_SENT = "${MQ_PRODUCER_BASE}_sent"

        val COUNT_MQ_PRODUCER_MESSAGE_SENT: Counter =
            Counter.builder(MQ_PRODUCER_MESSAGE_SENT)
                .description("Counts the number of messages sent to Infotrygd via MQ")
                .register(METRICS_REGISTRY)
    }
}