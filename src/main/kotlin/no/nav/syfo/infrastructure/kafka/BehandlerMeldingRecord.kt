package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer
import java.util.*

data class BehandlerMeldingRecord(
    val behandlerRef: UUID,
    val behandlerNavn: String,
)

class BehandlerMeldingRecordSerializer : Serializer<BehandlerMeldingRecord> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: BehandlerMeldingRecord?): ByteArray =
        mapper.writeValueAsBytes(data)
}
