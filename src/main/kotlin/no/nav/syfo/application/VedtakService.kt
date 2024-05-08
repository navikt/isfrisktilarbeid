package no.nav.syfo.application

import no.nav.syfo.domain.*
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
    ): Vedtak {
        val vedtakStatus = VedtakStatus(
            veilederident = veilederident,
            status = Status.FERDIG_BEHANDLET,
        )
        return vedtak.addVedtakstatus(vedtakStatus).also {
            vedtakRepository.addVedtakStatus(it, vedtakStatus)
        }
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
                vedtakRepository.setJournalpostId(journalfortVedtak)

                journalfortVedtak
            }
        }
    }

    fun publishVedtakVarsel(): List<Result<Vedtak>> {
        val unpublishedVedtakVarsler = vedtakRepository.getUnpublishedVedtakVarsler()
        return unpublishedVedtakVarsler.map { vedtak ->
            val result = vedtakProducer.sendVedtakVarsel(vedtak)
            result.map {
                vedtakRepository.setVedtakVarselPublished(it)
                it
            }
        }
    }

    fun publishUnpublishedVedtak(): List<Result<Vedtak>> {
        val unpublished = vedtakRepository.getUnpublishedVedtak()
        return unpublished.map { vedtak ->
            val producerResult = vedtakProducer.sendFattetVedtak(vedtak)
            producerResult.map {
                vedtakRepository.setVedtakStatusPublished(vedtak.getFattetStatus())
                it
            }
        }
    }
}
