package no.nav.syfo.application

import no.nav.syfo.domain.Behandlermelding
import no.nav.syfo.domain.Personident

interface IBehandlermeldingRepository {
    fun getUnpublishedBehandlermeldinger(): List<Triple<Personident, Behandlermelding, ByteArray>>
    fun update(behandlermelding: Behandlermelding)
}
