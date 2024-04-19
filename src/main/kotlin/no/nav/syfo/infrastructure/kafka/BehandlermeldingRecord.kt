package no.nav.syfo.infrastructure.kafka

import no.nav.syfo.util.configuredJacksonMapper
import org.apache.kafka.common.serialization.Serializer
import java.util.*

/**
 * Kombinasjonen av dialogmelding type, kodeverk og kode bestemmer anvendelsen.
 * Dialogmelding type: `DIALOG_NOTAT`, kodeverk: HENVENDELSE (8127), kode: 2
 * Andvendelsen her er: Friskmelding til arbeidsformidling
 *
 * (Se pdf for "Veiledning til anvendelse av dialogmelding for 2-veis
 * kommunikasjon mellom NAV og samhandlere i helsesektoren."
 */
data class BehandlermeldingRecord private constructor(
    val behandlerRef: UUID,
    val personIdent: String,
    val dialogmeldingUuid: String,
    val dialogmeldingRefParent: String?,
    val dialogmeldingRefConversation: String,
    val dialogmeldingType: String,
    val dialogmeldingKodeverk: String,
    val dialogmeldingKode: Int,
    val dialogmeldingTekst: String,
    val dialogmeldingVedlegg: ByteArray,
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
        dialogmeldingKodeverk = "HENVENDELSE",
        dialogmeldingKode = 2,
        dialogmeldingTekst = dialogmeldingTekst,
        dialogmeldingVedlegg = dialogmeldingVedlegg,
    )
}

class BehandlermeldingRecordSerializer : Serializer<BehandlermeldingRecord> {
    private val mapper = configuredJacksonMapper()
    override fun serialize(topic: String?, data: BehandlermeldingRecord?): ByteArray =
        mapper.writeValueAsBytes(data)
}
