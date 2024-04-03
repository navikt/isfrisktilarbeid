package no.nav.syfo.application

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Vedtak
import java.time.LocalDate

class VedtakService(
    private val pdfService: IPdfService,
    private val vedtakRepository: IVedtakRepository,
) {
    suspend fun createVedtak(
        personident: Personident,
        veilederident: String,
        begrunnelse: String,
        document: List<DocumentComponent>,
        fom: LocalDate,
        tom: LocalDate,
        callId: String,
    ): Vedtak {
        val vedtak = Vedtak(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            fom = fom,
            tom = tom,
        )
        val vedtakPdf = pdfService.createVedtakPdf(vedtak = vedtak, callId = callId)
        val createdVedtak = vedtakRepository.createVedtak(
            vedtak = vedtak,
            pdf = vedtakPdf,
        )

        // TODO: Lage melding til behandler inkl pdf, lagre denne og produsere til isdialogmelding

        return createdVedtak
    }
}
