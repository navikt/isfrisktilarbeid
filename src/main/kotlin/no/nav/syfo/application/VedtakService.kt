package no.nav.syfo.application

import no.nav.syfo.domain.Behandlermelding
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import java.time.LocalDate
import java.util.*

class VedtakService(
    private val pdfService: IPdfService,
    private val vedtakRepository: IVedtakRepository,
    private val journalforingService: IJournalforingService,
    private val infotrygdService: InfotrygdService,
    private val vedtakProducer: IVedtakProducer,
) {
    fun getVedtak(personident: Personident) =
        vedtakRepository.getVedtak(personident)

    suspend fun createVedtak(
        personident: Personident,
        veilederident: String,
        begrunnelse: String,
        document: List<DocumentComponent>,
        fom: LocalDate,
        tom: LocalDate,
        callId: String,
        behandlerRef: UUID,
        behandlerNavn: String,
        behandlerDocument: List<DocumentComponent>,
    ): Vedtak {
        val vedtak = Vedtak(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            fom = fom,
            tom = tom,
        )
        val behandlerMelding = Behandlermelding(
            behandlerRef = behandlerRef,
            document = behandlerDocument,
        )
        val vedtakPdf = pdfService.createVedtakPdf(vedtak = vedtak, callId = callId)
        val behandlerMeldingPdf =
            pdfService.createBehandlermeldingPdf(
                behandlerMelding = behandlerMelding,
                behandlerNavn = behandlerNavn,
                callId = callId
            )
        val (createdVedtak, _) = vedtakRepository.createVedtak(
            vedtak = vedtak,
            vedtakPdf = vedtakPdf,
            behandlermelding = behandlerMelding,
            behandlermeldingPdf = behandlerMeldingPdf,
        )

        return createdVedtak
    }

    fun ferdigbehandleVedtak(
        vedtak: Vedtak,
        veilederident: String,
    ) = vedtak.ferdigbehandle(veilederident).also {
        vedtakRepository.update(it)
    }

    suspend fun sendVedtakToInfotrygd(): List<Result<Vedtak>> {
        val unpublished = vedtakRepository.getUnpublishedInfotrygd()
        return unpublished.map { vedtak ->
            runCatching {
                infotrygdService.sendMessageToInfotrygd(vedtak)
                vedtakRepository.setVedtakPublishedInfotrygd(vedtak)
                vedtak
            }
        }
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

    fun publishVedtakVarsel(): List<Result<Vedtak>> {
        val unpublishedVedtakVarsler = vedtakRepository.getUnpublishedVedtakVarsler()
        return unpublishedVedtakVarsler.map { vedtak ->
            val result = vedtakProducer.sendVedtakVarsel(vedtak)
            result.map {
                val publishedVedtakVarsel = vedtak.publishVarsel()
                vedtakRepository.update(publishedVedtakVarsel)
                publishedVedtakVarsel
            }
        }
    }

    fun publishUnpublishedVedtak(): List<Result<Vedtak>> {
        val unpublished = vedtakRepository.getUnpublishedVedtak()
        return unpublished.map { vedtak ->
            val producerResult = vedtakProducer.sendFattetVedtak(vedtak)
            producerResult.map {
                val publishedVedtak = it.setPublished()
                vedtakRepository.update(publishedVedtak)
                publishedVedtak
            }
        }
    }
}
