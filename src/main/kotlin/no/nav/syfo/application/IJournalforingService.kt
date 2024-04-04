package no.nav.syfo.application

import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.Vedtak

interface IJournalforingService {
    suspend fun journalfor(
        vedtak: Vedtak,
        pdf: ByteArray,
    ): JournalpostId
}
