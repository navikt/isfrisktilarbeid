package no.nav.syfo.infrastructure.clients.dokarkiv.dto

enum class BrevkodeType(
    val value: String,
) {
    VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING("OPPF_VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING"),
    FRISKMELDING_TIL_ARBEIDSFORMIDLING_BEHANDLER("OPPF_FRISKMELDING_TIL_ARBEIDSFORMIDLING_BEHANDLER"),
}

data class Dokument private constructor(
    val brevkode: String,
    val dokumentKategori: String? = null,
    val dokumentvarianter: List<Dokumentvariant>,
    val tittel: String? = null,
) {
    companion object {
        fun create(
            brevkode: BrevkodeType,
            dokumentvarianter: List<Dokumentvariant>,
            tittel: String? = null,
        ) = Dokument(
            brevkode = brevkode.value,
            dokumentvarianter = dokumentvarianter,
            tittel = tittel,
        )
    }
}
