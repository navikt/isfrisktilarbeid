package no.nav.syfo.infrastructure.clients.dokarkiv.dto

enum class BrevkodeType(
    val value: String,
) {
    VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING("OPPF_VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING"),
    BEHANDLERMELDING_FRISKMELDING_TIL_ARBEIDSFORMIDLING("OPPF_BEHANDLERMELDING_FRISKMELDING_TIL_ARBEIDSFORMIDLING"),
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
