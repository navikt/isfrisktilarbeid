package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer
import java.util.*

/**
 * Kombinasjonen av dialogmelding type, kodeverk og kode bestemmer anvendelsen.
 * Dialogmelding type: `DIALOG_NOTAT`, kodeverk: 8127, kode: 2
 * Andvendelsen her er: Friskmelding til arbeidsformidling
 *
 * (Se pdf for "Veiledning til anvendelse av dialogmelding for 2-veis
 * kommunikasjon mellom NAV og samhandlere i helsesektoren."
 */
data class BehandlerMeldingRecord private constructor(
    private val behandlerRef: UUID,
    private val personIdent: String,
    private val dialogmeldingUuid: String,
    private val dialogmeldingRefParent: String?,
    private val dialogmeldingRefConversation: String,
    private val dialogmeldingType: String,
    private val dialogmeldingKodeverk: String,
    private val dialogmeldingKode: Int,
    private val dialogmeldingTekst: String?,
    private val dialogmeldingVedlegg: ByteArray,
) {
    constructor(
        behandlerRef: UUID,
        personident: String,
        dialogmeldingTekst: String,
        dialogmeldingVedlegg: ByteArray,
    ) : this(
        behandlerRef = behandlerRef,
        personIdent = personident,
        dialogmeldingUuid = UUID.randomUUID().toString(),
        dialogmeldingRefParent = null,
        dialogmeldingRefConversation = UUID.randomUUID().toString(),
        dialogmeldingType = "DIALOG_NOTAT",
        dialogmeldingKodeverk = "8127",
        dialogmeldingKode = 2,
        dialogmeldingTekst = dialogmeldingTekst,
        dialogmeldingVedlegg = dialogmeldingVedlegg,
    )
}

class BehandlerMeldingRecordSerializer : Serializer<BehandlerMeldingRecord> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: BehandlerMeldingRecord?): ByteArray =
        mapper.writeValueAsBytes(data)
}
