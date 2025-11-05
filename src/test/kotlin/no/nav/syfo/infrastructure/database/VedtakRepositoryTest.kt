package no.nav.syfo.infrastructure.database

import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.InfotrygdStatus
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class VedtakRepositoryTest {
    private val vedtak = generateVedtak()
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val vedtakRepository = VedtakRepository(database = database)

    @AfterEach
    fun cleanup() {
        database.dropData()
    }

    @Test
    fun `Successfully creates vedtak`() {
        val createdVedtak = vedtakRepository.createVedtak(
            vedtak = vedtak,
            vedtakPdf = UserConstants.PDF_VEDTAK,
        )

        val persistedVedtak = vedtakRepository.getVedtak(createdVedtak.uuid)

        assertEquals(vedtak.uuid, persistedVedtak.uuid)
        assertEquals(vedtak.personident, persistedVedtak.personident)
        assertEquals(vedtak.getFattetStatus().veilederident, persistedVedtak.getFattetStatus().veilederident)
        assertNull(persistedVedtak.getFerdigbehandletStatus())
        assertEquals(vedtak.begrunnelse, persistedVedtak.begrunnelse)
        assertEquals(vedtak.document, persistedVedtak.document)
        assertEquals(vedtak.fom, persistedVedtak.fom)
        assertEquals(vedtak.tom, persistedVedtak.tom)
        assertEquals(vedtak.journalpostId, persistedVedtak.journalpostId)
        assertEquals(InfotrygdStatus.IKKE_SENDT, persistedVedtak.infotrygdStatus)
    }
}
