package no.nav.syfo.infrastructure.mq

import com.ibm.mq.constants.CMQC.MQENC_NATIVE
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.jms.JmsFactoryFactory
import com.ibm.msg.client.wmq.common.CommonConstants
import javax.jms.MessageConsumer
import javax.jms.Session

private const val UTF_8_WITH_PUA = 1208

fun connectionFactory(env: MQEnvironment): javax.jms.ConnectionFactory =
    JmsFactoryFactory.getInstance(CommonConstants.WMQ_PROVIDER).createConnectionFactory().apply {
        setIntProperty(CommonConstants.WMQ_CONNECTION_MODE, CommonConstants.WMQ_CM_CLIENT)
        setStringProperty(CommonConstants.WMQ_QUEUE_MANAGER, env.mqQueueManager)
        setStringProperty(CommonConstants.WMQ_HOST_NAME, env.mqHostname)
        setStringProperty(CommonConstants.WMQ_APPLICATIONNAME, env.mqApplicationName)
        setIntProperty(CommonConstants.WMQ_PORT, env.mqPort)
        setStringProperty(CommonConstants.WMQ_CHANNEL, env.mqChannelName)
        setIntProperty(CommonConstants.WMQ_CCSID, UTF_8_WITH_PUA)
        setIntProperty(JmsConstants.JMS_IBM_ENCODING, MQENC_NATIVE)
        setIntProperty(JmsConstants.JMS_IBM_CHARACTER_SET, UTF_8_WITH_PUA)
        setBooleanProperty(CommonConstants.USER_AUTHENTICATION_MQCSP, true)
        setStringProperty(CommonConstants.USERID, env.serviceuserUsername)
        setStringProperty(CommonConstants.PASSWORD, env.serviceuserPassword)
    }

fun Session.consumerForQueue(queueName: String): MessageConsumer = createConsumer(createQueue(queueName))
