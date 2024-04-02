package no.nav.syfo.infrastructure.mq

import jakarta.xml.bind.JAXBContext
import jakarta.xml.bind.JAXBException
import jakarta.xml.bind.Marshaller
import no.aetat.arena.arenainfotrygdskjema.Infotrygd
import java.io.StringWriter
import javax.xml.stream.XMLStreamReader
import javax.xml.transform.stream.StreamResult

object JAXB {
    private var INFOTRYGD_CONTEXT: JAXBContext? = null

    fun marshallInfotrygd(element: Any?): String {
        return try {
            val writer = StringWriter()
            val marshaller = INFOTRYGD_CONTEXT!!.createMarshaller()
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true)
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8")
            marshaller.marshal(element, StreamResult(writer))
            writer.toString()
        } catch (e: JAXBException) {
            throw RuntimeException(e)
        }
    }

    inline fun <reified T> unmarshallObject(xmlStreamReader: XMLStreamReader): T =
        JAXBContext.newInstance(T::class.java).createUnmarshaller().unmarshal(xmlStreamReader) as T

    init {
        try {
            INFOTRYGD_CONTEXT = JAXBContext.newInstance(
                Infotrygd::class.java,
            )
        } catch (e: JAXBException) {
            throw RuntimeException(e)
        }
    }
}
