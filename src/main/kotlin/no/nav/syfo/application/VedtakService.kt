package no.nav.syfo.application

import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import java.time.LocalDate
import java.util.*

class VedtakService(
    private val pdfService: IPdfService,
    private val vedtakRepository: IVedtakRepository,
    private val journalforingService: IJournalforingService,
    private val oppgaveService: IOppgaveService,
    private val infotrygdService: InfotrygdService,
    private val vedtakProducer: IVedtakProducer,
) {
    fun getVedtak(personident: Personident) =
        vedtakRepository.getVedtak(personident)

    fun getVedtak(uuid: UUID): Vedtak = vedtakRepository.getVedtak(uuid = uuid)

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
            vedtakPdf = vedtakPdf,
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

    suspend fun sendUnpublishedVedtakToInfotrygd(): List<Result<Vedtak>> {
        val unpublished = vedtakRepository.getUnpublishedInfotrygd()
        return unpublished.map { vedtak ->
            sendVedtakToInfotrygd(vedtak)
        }
    }

    internal suspend fun sendVedtakToInfotrygd(vedtak: Vedtak): Result<Vedtak> = runCatching {
        infotrygdService.sendMessageToInfotrygd(vedtak)
        vedtakRepository.setVedtakPublishedInfotrygd(vedtak)
        vedtak.sendTilInfotrygd()
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

    suspend fun createOppgaveForVedtakWithNoOppgave(): List<Result<Vedtak>> =
        vedtakRepository.getNotOppgaveVedtak().map { vedtak ->
            oppgaveService.createOppgave(
                vedtak = vedtak,
            ).map { oppgaveId ->
                vedtak.oppgave(oppgaveId = oppgaveId).also { updatedVedtak ->
                    vedtakRepository.setOppgaveId(updatedVedtak)
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

    fun publishUnpublishedVedtakStatus(): List<Result<Vedtak>> {
        val unpublished = vedtakRepository.getUnpublishedVedtakStatus()
        return unpublished.map { (vedtak, vedtakStatus) ->
            val producerResult = vedtakProducer.sendVedtakStatus(vedtak, vedtakStatus)
            producerResult.map {
                vedtakRepository.setVedtakStatusPublished(vedtakStatus)
                it
            }
        }
    }
}
