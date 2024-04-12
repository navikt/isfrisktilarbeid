package no.nav.syfo.application

import no.nav.syfo.domain.Behandlermelding

class BehandlermeldingService(
    private val behandlermeldingRepository: IBehandlermeldingRepository,
    private val behandlermeldingProducer: IBehandlermeldingProducer,
    private val journalforingService: IJournalforingService,
) {

    fun publishBehandlermeldinger(): List<Result<Behandlermelding>> {
        val unpublished = behandlermeldingRepository.getUnpublishedBehandlermeldinger()

        return unpublished.map { (personident, behandlermelding, behandlermeldingPdf) ->
            val producerResult = behandlermeldingProducer.send(personident, behandlermelding, behandlermeldingPdf)
            producerResult.map {
                val publishedBehandlermelding = it.publish()
                behandlermeldingRepository.update(publishedBehandlermelding)
                publishedBehandlermelding
            }
        }
    }

    suspend fun journalforBehandlermeldinger(): List<Result<Behandlermelding>> {
        val notJournalforteBehandlermeldinger = behandlermeldingRepository.getNotJournalforteBehandlermeldinger()

        return notJournalforteBehandlermeldinger.map { (personident, behandlermelding, pdf) ->
            journalforingService.journalfor(
                personident = personident,
                behandlermelding = behandlermelding,
                pdf = pdf,
            ).map {
                val journalfortBehandlermelding = behandlermelding.journalfor(journalpostId = it)
                behandlermeldingRepository.update(journalfortBehandlermelding)

                journalfortBehandlermelding
            }
        }
    }
}
