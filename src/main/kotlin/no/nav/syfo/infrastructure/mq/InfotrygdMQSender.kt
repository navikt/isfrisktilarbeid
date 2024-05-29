package no.nav.syfo.infrastructure.mq

import com.ibm.mq.jms.MQDestination
import com.ibm.msg.client.wmq.common.CommonConstants
import io.micrometer.core.instrument.Counter
import no.nav.syfo.infrastructure.metric.METRICS_NS
import no.nav.syfo.infrastructure.metric.METRICS_REGISTRY
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.UUID
import javax.jms.JMSContext

private val log: Logger = LoggerFactory.getLogger("no.nav.syfo.infrastructure.mq")

class InfotrygdMQSender(
    val env: MQEnvironment,
) {

    private val jmsContext: JMSContext? = if (env.mqHostname.startsWith("mpls02"))
        null
    else
        connectionFactory(env).createContext()

    protected fun finalize() {
        try {
            jmsContext!!.close()
        } catch (exc: Exception) {
            log.warn("Got exception when closing MQ-connection", exc)
        }
    }

    fun sendToMQ(
        payload: String,
        correlationId: UUID,
    ) {
        jmsContext!!.createContext(JMSContext.AUTO_ACKNOWLEDGE).use { context ->
            val destination = context.createQueue("queue:///${env.mqQueueName}")
            val kvitteringQueue = context.createQueue("queue:///${env.mqQueueNameKvittering}")
            (destination as MQDestination).targetClient = CommonConstants.WMQ_TARGET_DEST_MQ
            (destination as MQDestination).messageBodyStyle = CommonConstants.WMQ_MESSAGE_BODY_MQ
            val message = context.createTextMessage(payload)
            message.jmsCorrelationIDAsBytes = correlationId.asBytes()
            message.jmsReplyTo = kvitteringQueue
            context.createProducer().send(destination, message)
            log.info("Sent message to MQ, msgId: ${message.jmsMessageID}, correlationId: ${message.jmsCorrelationID}")
        }
        Metrics.COUNT_MQ_PRODUCER_MESSAGE_SENT.increment()
    }
}

fun UUID.asBytes() = ByteBuffer.wrap(ByteArray(24)).also {
    it.putLong(0) // padding
    it.putLong(mostSignificantBits)
    it.putLong(leastSignificantBits)
}.array()

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
