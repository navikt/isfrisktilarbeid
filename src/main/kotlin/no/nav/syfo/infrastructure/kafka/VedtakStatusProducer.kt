package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.domain.Status
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.domain.VedtakStatus
import no.nav.syfo.domain.asProducerRecordKey
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class VedtakStatusRecord(
    val uuid: UUID,
    val personident: String,
    val begrunnelse: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val status: Status,
    val statusAt: OffsetDateTime,
    val statusBy: String,
)

class VedtakStatusProducer(private val producer: KafkaProducer<String, VedtakStatusRecord>) {

    fun send(
        vedtak: Vedtak,
        vedtakStatus: VedtakStatus,
    ): Result<Vedtak> =
        try {
            producer.send(
                ProducerRecord(
                    TOPIC,
                    vedtak.personident.asProducerRecordKey(),
                    VedtakStatusRecord(
                        uuid = vedtak.uuid,
                        personident = vedtak.personident.value,
                        begrunnelse = vedtak.begrunnelse,
                        fom = vedtak.fom,
                        tom = vedtak.tom,
                        status = vedtakStatus.status,
                        statusAt = vedtakStatus.createdAt,
                        statusBy = vedtakStatus.veilederident,
                    )
                )
            ).get()
            Result.success(vedtak)
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to publish fattet vedtak: ${e.message}")
            Result.failure(e)
        }

    companion object {
        private const val TOPIC = "teamsykefravr.isfrisktilarbeid-vedtak-status"
        private val log = LoggerFactory.getLogger(VedtakStatusProducer::class.java)
    }
}

class VedtakStatusRecordSerializer : Serializer<VedtakStatusRecord> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: VedtakStatusRecord?): ByteArray =
        mapper.writeValueAsBytes(data)
}
