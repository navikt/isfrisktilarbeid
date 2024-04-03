package no.nav.syfo.application

import no.nav.syfo.domain.Vedtak

interface IPdfService {
    suspend fun createVedtakPdf(
        vedtak: Vedtak,
        callId: String,
    ): ByteArray
}
