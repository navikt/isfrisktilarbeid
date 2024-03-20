package no.nav.syfo.application.mq

import no.nav.syfo.Environment
import no.nav.syfo.infrastructure.mq.COUNT_MQ_PRODUCER_MESSAGE_SENT
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
        COUNT_MQ_PRODUCER_MESSAGE_SENT.increment()
    }
}
