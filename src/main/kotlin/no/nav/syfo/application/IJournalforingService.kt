package no.nav.syfo.application

import no.nav.syfo.domain.Behandlermelding
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Vedtak

interface IJournalforingService {
    suspend fun journalfor(
        vedtak: Vedtak,
        pdf: ByteArray,
    ): Result<JournalpostId>

    suspend fun journalfor(
        personident: Personident,
        behandlermelding: Behandlermelding,
        pdf: ByteArray
    ): Result<JournalpostId>
}
