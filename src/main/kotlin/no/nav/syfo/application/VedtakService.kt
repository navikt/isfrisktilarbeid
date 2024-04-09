package no.nav.syfo.application

import no.nav.syfo.domain.BehandlerMelding
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
    private val esyfovarselHendelseProducer: IEsyfovarselHendelseProducer,
) {
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
        val behandlerMelding = BehandlerMelding(
            behandlerRef = behandlerRef,
            document = behandlerDocument,
        )
        val vedtakPdf = pdfService.createVedtakPdf(vedtak = vedtak, callId = callId)
        val behandlerMeldingPdf =
            pdfService.createBehandlerMeldingPdf(
                behandlerMelding = behandlerMelding,
                behandlerNavn = behandlerNavn,
                callId = callId
            )
        val (createdVedtak, createdBehandlerMelding) = vedtakRepository.createVedtak(
            vedtak = vedtak,
            vedtakPdf = vedtakPdf,
            behandlerMelding = behandlerMelding,
            behandlerMeldingPdf = behandlerMeldingPdf,
        )

        // TODO: Publiserer behandlermelding på kafka til isdialogmelding

        return createdVedtak
    }

    fun sendVedtakToInfotrygd(): List<Result<Vedtak>> {
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
            val result = esyfovarselHendelseProducer.sendVedtakVarsel(vedtak)
            result.map {
                val publishedVedtakVarsel = vedtak.publishVarsel()
                vedtakRepository.update(publishedVedtakVarsel)
                publishedVedtakVarsel
            }
        }
    }
}
