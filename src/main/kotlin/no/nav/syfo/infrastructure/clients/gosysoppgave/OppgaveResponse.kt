package no.nav.syfo.infrastructure.clients.gosysoppgave

data class OppgaveResponse(
    val id: String,
    val beskrivelse: String? = null,
    val status: String? = null,
    val oppgavetype: String? = null,
    val tema: String? = null,
    val tildeltEnhetsnr: String? = null,
    val versjon: Int = 0,
)
