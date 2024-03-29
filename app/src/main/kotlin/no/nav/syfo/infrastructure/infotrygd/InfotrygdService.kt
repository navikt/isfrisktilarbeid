package no.nav.syfo.infrastructure.infotrygd

import no.aetat.arena.arenainfotrygdskjema.ObjectFactory
import no.nav.syfo.application.mq.MQSender
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.infrastructure.mq.JAXB
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.xml.datatype.DatatypeFactory

class InfotrygdService(
    val mqQueueName: String,
    val mqSender: MQSender,
) {

    fun sendMessageToInfotrygd(
        personident: PersonIdent,
        veilederident: String,
        navKontor: String,
        now: LocalDateTime,
        datoFra: LocalDate,
        datoTil: LocalDate,
    ) {
        val objectFactory = ObjectFactory()
        val dataTypeFactory = DatatypeFactory.newInstance()
        val infotrygdHeader = objectFactory.createHeader()
        infotrygdHeader.copyId = "K278M810"
        infotrygdHeader.aksjon = "SENDMELDING"
        infotrygdHeader.kilde = "MODIA"
        infotrygdHeader.brukerId = veilederident
        infotrygdHeader.dato = dataTypeFactory.newXMLGregorianCalendar(
            now.toLocalDate().toString()
        )
        infotrygdHeader.klokke = timeFormatter.format(now)
        infotrygdHeader.navKontor = navKontor
        infotrygdHeader.fnr = personident.value
        infotrygdHeader.meldKode = "O"
        val infotygdHeaderMeldingsdata = objectFactory.createHeaderMeldingsdata()
        infotygdHeaderMeldingsdata.antall = BigInteger.ONE
        infotygdHeaderMeldingsdata.copyId = "K278M830"
        val headerTekstlinjer = objectFactory.createHeaderTekstlinjer()
        headerTekstlinjer.antall = BigInteger.ZERO
        headerTekstlinjer.copyId = "K278M840"

        val meldingsspesFelt = objectFactory.createMeldingsspesFelt()
        meldingsspesFelt.meldVersjon = BigInteger.ONE
        meldingsspesFelt.meldId = "MA-TSP-1"
        val meldingdataMATSP1 = objectFactory.createMeldingsdataMATSP1()
        meldingdataMATSP1.aktType = "FA"
        meldingdataMATSP1.datoFra = dataTypeFactory.newXMLGregorianCalendar(
            datoFra.toString()
        )
        meldingdataMATSP1.datoTil = dataTypeFactory.newXMLGregorianCalendar(
            datoTil.toString()
        )
        val meldingdata = objectFactory.createMeldingsdata()
        meldingdata.matsP1 = meldingdataMATSP1
        meldingsspesFelt.meldingsdata = meldingdata

        val infotrygdMessage = objectFactory.createInfotrygd()
        infotrygdMessage.headerMeldingsdata = infotygdHeaderMeldingsdata
        infotrygdMessage.header = infotrygdHeader
        infotrygdMessage.headerTekstlinjer = headerTekstlinjer
        infotrygdMessage.meldingsspesFelt = meldingsspesFelt

        val payload = JAXB.marshallInfotrygd(infotrygdMessage)

        mqSender.sendToMQ(
            queueName = mqQueueName,
            payload = payload,
        )
    }

    companion object {
        val timeFormatter = DateTimeFormatter.ofPattern("HHmmss")
    }
}
