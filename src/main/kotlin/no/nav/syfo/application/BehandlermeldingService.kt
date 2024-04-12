package no.nav.syfo.application

import no.nav.syfo.domain.Behandlermelding

class BehandlermeldingService(private val behandlermeldingRepository: IBehandlermeldingRepository, private val behandlermeldingProducer: IBehandlermeldingProducer) {
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
}
