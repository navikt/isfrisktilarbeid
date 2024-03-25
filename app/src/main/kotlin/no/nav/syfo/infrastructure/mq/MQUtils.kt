package no.nav.syfo.application.mq

import com.ibm.mq.constants.CMQC.MQENC_NATIVE
import com.ibm.msg.client.jms.JmsConstants
import com.ibm.msg.client.jms.JmsFactoryFactory
import com.ibm.msg.client.wmq.common.CommonConstants
import no.nav.syfo.Environment

private const val UTF_8_WITH_PUA = 1208

fun connectionFactory(env: Environment): javax.jms.ConnectionFactory =
    JmsFactoryFactory.getInstance(CommonConstants.WMQ_PROVIDER).createConnectionFactory().apply {
        setIntProperty(CommonConstants.WMQ_CONNECTION_MODE, CommonConstants.WMQ_CM_CLIENT)
        setStringProperty(CommonConstants.WMQ_QUEUE_MANAGER, env.mq.mqQueueManager)
        setStringProperty(CommonConstants.WMQ_HOST_NAME, env.mq.mqHostname)
        setStringProperty(CommonConstants.WMQ_APPLICATIONNAME, env.mq.mqApplicationName)
        setIntProperty(CommonConstants.WMQ_PORT, env.mq.mqPort)
        setStringProperty(CommonConstants.WMQ_CHANNEL, env.mq.mqChannelName)
        setIntProperty(CommonConstants.WMQ_CCSID, UTF_8_WITH_PUA)
        setStringProperty(CommonConstants.WMQ_SSL_CIPHER_SUITE, "*TLS13ORHIGHER")
        setIntProperty(JmsConstants.JMS_IBM_ENCODING, MQENC_NATIVE)
        setIntProperty(JmsConstants.JMS_IBM_CHARACTER_SET, UTF_8_WITH_PUA)
        setBooleanProperty(CommonConstants.USER_AUTHENTICATION_MQCSP, true)
        setStringProperty(CommonConstants.USERID, env.mq.serviceuserUsername)
        setStringProperty(CommonConstants.PASSWORD, env.mq.serviceuserPassword)
    }
