package no.nav.syfo.infrastructure.pdf

import no.nav.syfo.application.IPdfService
import no.nav.syfo.domain.Behandlermelding
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.infrastructure.clients.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.clients.pdfgen.PdfModel.*
import no.nav.syfo.infrastructure.clients.pdl.PdlClient

class PdfService(
    private val pdfGenClient: PdfGenClient,
    private val pdlClient: PdlClient,
) : IPdfService {

    override suspend fun createVedtakPdf(
        vedtak: Vedtak,
        callId: String,
    ): ByteArray {
        val personNavn = pdlClient.getPerson(vedtak.personident).fullName
        val vedtakPdfModel = VedtakPdfModel(
            mottakerFodselsnummer = vedtak.personident,
            mottakerNavn = personNavn,
            documentComponents = vedtak.document,
        )

        return pdfGenClient.createVedtakPdf(
            callId = callId,
            payload = vedtakPdfModel,
        )
    }

    override suspend fun createBehandlermeldingPdf(
        behandlerMelding: Behandlermelding,
        behandlerNavn: String,
        callId: String
    ): ByteArray {
        val behandlerMeldingPdfModel = BehandlermeldingPdfModel(
            mottakerNavn = behandlerNavn,
            documentComponents = behandlerMelding.document,
        )
        return pdfGenClient.createBehandlerPdf(
            callId = callId,
            payload = behandlerMeldingPdfModel,
        )
    }
}
