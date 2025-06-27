package no.nav.syfo.infrastructure.clients.gosysoppgave

import java.time.LocalDate

data class OppgaveRequest(
    var personident: String,
    val journalpostId: String,
    var tema: String,
    val behandlingstema: String,
    val oppgavetype: String,
    var tildeltEnhetsnr: String,
    val beskrivelse: String,
    val prioritet: String,
    val aktivDato: LocalDate = LocalDate.now(),
    val fristFerdigstillelse: LocalDate = LocalDate.now(),
)
