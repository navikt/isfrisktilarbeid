package no.nav.syfo.infrastructure.clients.behandler

data class BehandlerDTO(
    val type: String?,
    val behandlerRef: String,
    val fnr: String?,
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val orgnummer: String?,
    val kontor: String?,
    val adresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val telefon: String?,
)

fun BehandlerDTO.fullName(): String = listOf(fornavn, mellomnavn, etternavn)
    .filterNot { it.isNullOrBlank() }
    .joinToString(" ")
