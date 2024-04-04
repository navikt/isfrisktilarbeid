package no.nav.syfo.infrastructure.journalforing

import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.domain.*

class JournalforingService() : IJournalforingService {

    override suspend fun journalfor(vedtak: Vedtak, pdf: ByteArray): JournalpostId {
        TODO("Not yet implemented")
    }
}
