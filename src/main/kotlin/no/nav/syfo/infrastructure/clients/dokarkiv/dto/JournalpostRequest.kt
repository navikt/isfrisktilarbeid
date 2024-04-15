package no.nav.syfo.infrastructure.clients.dokarkiv.dto

const val JOURNALFORENDE_ENHET = 9999

enum class JournalpostType {
    UTGAAENDE,
    NOTAT,
}

enum class JournalpostTema(val value: String) {
    OPPFOLGING("OPP"),
}

enum class JournalpostKanal {
    HELSENETTET,
}

enum class OverstyrInnsynsregler {
    VISES_MASKINELT_GODKJENT,
}

data class JournalpostRequest(
    val avsenderMottaker: AvsenderMottaker,
    val tittel: String,
    val bruker: Bruker? = null,
    val dokumenter: List<Dokument>,
    val journalfoerendeEnhet: Int? = JOURNALFORENDE_ENHET,
    val journalpostType: String = JournalpostType.UTGAAENDE.name,
    val tema: String = JournalpostTema.OPPFOLGING.value,
    val kanal: String? = null,
    val sak: Sak = Sak(),
    val eksternReferanseId: String,
    val overstyrInnsynsregler: String? = null,
)
