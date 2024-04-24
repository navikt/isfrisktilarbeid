package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.domain.Vedtak
import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.Serializer
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class VedtakFattetRecord(
    val uuid: UUID,
    val personident: String,
    val veilederident: String,
    val createdAt: OffsetDateTime,
    val begrunnelse: String,
    val fom: LocalDate,
    val tom: LocalDate,
)

class VedtakFattetProducer(private val producer: KafkaProducer<String, VedtakFattetRecord>) {

    fun send(vedtak: Vedtak): Result<Vedtak> =
        try {
            producer.send(
                ProducerRecord(
                    TOPIC,
                    UUID.randomUUID().toString(),
                    VedtakFattetRecord(
                        uuid = vedtak.uuid,
                        personident = vedtak.personident.value,
                        veilederident = vedtak.veilederident,
                        createdAt = vedtak.createdAt,
                        begrunnelse = vedtak.begrunnelse,
                        fom = vedtak.fom,
                        tom = vedtak.tom,
                    )
                )
            ).get()
            Result.success(vedtak)
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to publish fattet vedtak: ${e.message}")
            Result.failure(e)
        }

    companion object {
        private const val TOPIC = "teamsykefravr.isfrisktilarbeid-vedtak-fattet"
        private val log = LoggerFactory.getLogger(VedtakFattetProducer::class.java)
    }
}

class VedtakFattetRecordSerializer : Serializer<VedtakFattetRecord> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: VedtakFattetRecord?): ByteArray =
        mapper.writeValueAsBytes(data)
}
