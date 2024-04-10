package no.nav.syfo.infrastructure.kafka.esyfovarsel

import no.nav.syfo.application.IEsyfovarselHendelseProducer
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.infrastructure.kafka.esyfovarsel.dto.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class EsyfovarselHendelseProducer(
    private val kafkaProducer: KafkaProducer<String, EsyfovarselHendelse>,
) : IEsyfovarselHendelseProducer {

    override fun sendVedtakVarsel(vedtak: Vedtak): Result<Vedtak> {
        if (vedtak.journalpostId == null)
            throw IllegalStateException("JournalpostId is null for vedtak ${vedtak.uuid}")

        val varselHendelse = ArbeidstakerHendelse(
            type = HendelseType.SM_VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING,
            arbeidstakerFnr = vedtak.personident.value,
            data = VarselData(
                journalpost = VarselDataJournalpost(
                    uuid = vedtak.uuid.toString(),
                    id = vedtak.journalpostId.value,
                ),
            ),
            orgnummer = null,
        )

        return try {
            kafkaProducer.send(
                ProducerRecord(
                    ESYFOVARSEL_TOPIC,
                    UUID.nameUUIDFromBytes(vedtak.personident.value.toByteArray()).toString(),
                    varselHendelse,
                )
            ).get()
            Result.success(vedtak)
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send hendelse vedtak (uuid: ${vedtak.uuid}) to esyfovarsel: ${e.message}")
            Result.failure(e)
        }
    }

    companion object {
        private const val ESYFOVARSEL_TOPIC = "team-esyfo.varselbus"
        private val log = LoggerFactory.getLogger(EsyfovarselHendelseProducer::class.java)
    }
}
