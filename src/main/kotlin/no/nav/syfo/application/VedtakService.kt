package no.nav.syfo.application

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import java.time.LocalDate
import java.time.LocalDateTime

class VedtakService(
    private val pdfService: IPdfService,
    private val vedtakRepository: IVedtakRepository,
    private val journalforingService: IJournalforingService,
    private val infotrygdService: InfotrygdService,
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

    suspend fun journalforVedtak(): List<Result<Vedtak>> {
        val notJournalforteVedtak = vedtakRepository.getNotJournalforteVedtak()

        return notJournalforteVedtak.map { (vedtak, pdf) ->
            journalforingService.journalfor(
                vedtak = vedtak,
                pdf = pdf,
            ).map {
                val journalfortVedtak = vedtak.journalfor(journalpostId = it)
                vedtakRepository.update(journalfortVedtak)

                journalfortVedtak
            }
        }
    }

    fun publishMQUnpublishedVedtak(): List<Result<Vedtak>> {
        val unpublished = vedtakRepository.getUnpublishedMQVedtak()
        val result: MutableList<Result<Vedtak>> = mutableListOf()
        unpublished.forEach { vedtak ->
            try {
                infotrygdService.sendMessageToInfotrygd(
                    personident = vedtak.personident,
                    veilederident = vedtak.veilederident,
                    now = LocalDateTime.now(),
                    datoFra = vedtak.fom,
                    datoTil = vedtak.tom,
                    navKontor = "", // TODO: må diskuteres
                )
                vedtakRepository.setVedtakPublishedMQ(vedtak)
                result.add(Result.success(vedtak))
            } catch (exc: Exception) {
                result.add(Result.failure(exc))
            }
        }
        return result
    }
}
