package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.application.IVedtakFattetProducer
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Vedtak
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

data class VedtakFattetRecord(
    val uuid: UUID,
    val personident: Personident,
    val veilederident: String,
    val createdAt: OffsetDateTime,
    val begrunnelse: String,
    val fom: LocalDate,
    val tom: LocalDate,
)

class VedtakFattetProducer(
    private val producer: KafkaProducer<String, VedtakFattetRecord>,
) : IVedtakFattetProducer {

    override fun send(
        vedtak: Vedtak,
    ): Result<Vedtak> =
        try {
            producer.send(
                ProducerRecord(
                    TOPIC,
                    UUID.randomUUID().toString(),
                    VedtakFattetRecord(
                        uuid = vedtak.uuid,
                        personident = vedtak.personident,
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
