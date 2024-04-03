package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.application.IVedtakRepository
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.infrastructure.database.DatabaseInterface

class VedtakRepository(private val database: DatabaseInterface) : IVedtakRepository {
    override fun createVedtak(vedtak: Vedtak, pdf: ByteArray): Vedtak {
        TODO("Not yet implemented")
    }
}
