package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.application.IBehandlerMeldingProducer
import no.nav.syfo.domain.BehandlerMelding
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.serialize
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import java.util.*

class BehandlerMeldingProducer(private val produder: KafkaProducer<String, BehandlerMeldingRecord>) :
    IBehandlerMeldingProducer {

    override fun send(
        personident: Personident,
        behandlermelding: BehandlerMelding,
        behandlermeldingPdf: ByteArray
    ): Result<BehandlerMelding> =
        try {
            produder.send(
                ProducerRecord(
                    TOPIC,
                    UUID.randomUUID().toString(),
                    BehandlerMeldingRecord(
                        behandlerRef = behandlermelding.behandlerRef,
                        personident = personident.value,
                        dialogmeldingTekst = behandlermelding.document.serialize(),
                        dialogmeldingVedlegg = behandlermeldingPdf
                    )
                )
            ).get()
            Result.success(behandlermelding)
        } catch (e: Exception) {
            log.error("Exception was thrown when attempting to send behandlermelding: ${e.message}")
            Result.failure(e)
        }

    companion object {
        private const val TOPIC = "teamsykefravr.isdialogmelding-behandler-dialogmelding-bestilling"
        private val log = LoggerFactory.getLogger(BehandlerMeldingProducer::class.java)
    }
}
