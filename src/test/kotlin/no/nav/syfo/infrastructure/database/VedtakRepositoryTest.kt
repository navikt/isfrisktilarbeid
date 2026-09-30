package no.nav.syfo.infrastructure.database

import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.InfotrygdStatus
import no.nav.syfo.domain.Status
import no.nav.syfo.domain.VedtakStatus
import no.nav.syfo.generator.generateKorrigering
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.OffsetDateTime

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
        assertNull(persistedVedtak.korrigererVedtakUuid)
    }

    @Test
    fun `Successfully creates korrigering and ferdigbehandler korrigert vedtak`() {
        val createdVedtak = vedtakRepository.createVedtak(
            vedtak = vedtak,
            vedtakPdf = UserConstants.PDF_VEDTAK,
        )
        val korrigering = generateKorrigering(korrigertVedtak = createdVedtak)
        val ferdigbehandletStatus = VedtakStatus(
            veilederident = UserConstants.VEILEDER_IDENT,
            status = Status.FERDIG_BEHANDLET,
        )

        val createdKorrigering = vedtakRepository.createKorrigering(
            korrigering = korrigering,
            vedtakPdf = UserConstants.PDF_VEDTAK,
            ferdigbehandling = createdVedtak to ferdigbehandletStatus,
        )

        val persistedKorrigering = vedtakRepository.getVedtak(createdKorrigering.uuid)
        assertEquals(createdVedtak.uuid, persistedKorrigering.korrigererVedtakUuid)
        assertTrue(persistedKorrigering.isKorrigering())
        assertEquals(korrigering.begrunnelse, persistedKorrigering.begrunnelse)
        assertEquals(korrigering.document, persistedKorrigering.document)
        assertEquals(korrigering.fom, persistedKorrigering.fom)
        assertEquals(korrigering.tom, persistedKorrigering.tom)
        assertFalse(persistedKorrigering.isFerdigbehandlet())

        val persistedKorrigertVedtak = vedtakRepository.getVedtak(createdVedtak.uuid)
        assertTrue(persistedKorrigertVedtak.isFerdigbehandlet())
    }

    @Test
    fun `Leaves korrigert vedtak untouched when ferdigbehandling is null`() {
        val createdVedtak = vedtakRepository.createVedtak(
            vedtak = vedtak,
            vedtakPdf = UserConstants.PDF_VEDTAK,
        )
        vedtakRepository.createKorrigering(
            korrigering = generateKorrigering(korrigertVedtak = createdVedtak),
            vedtakPdf = UserConstants.PDF_VEDTAK,
            ferdigbehandling = null,
        )

        assertFalse(vedtakRepository.getVedtak(createdVedtak.uuid).isFerdigbehandlet())
    }

    @Test
    fun `Korrigering is not included in unpublished infotrygd`() {
        val createdVedtak = vedtakRepository.createVedtak(
            vedtak = vedtak,
            vedtakPdf = UserConstants.PDF_VEDTAK,
        )
        val createdKorrigering = vedtakRepository.createKorrigering(
            korrigering = generateKorrigering(korrigertVedtak = createdVedtak),
            vedtakPdf = UserConstants.PDF_VEDTAK,
            ferdigbehandling = null,
        )
        database.setVedtakCreatedAt(OffsetDateTime.now().minusMinutes(5), createdVedtak.uuid)
        database.setVedtakCreatedAt(OffsetDateTime.now().minusMinutes(5), createdKorrigering.uuid)

        val unpublished = vedtakRepository.getUnpublishedInfotrygd()

        assertEquals(1, unpublished.size)
        assertEquals(createdVedtak.uuid, unpublished.single().uuid)
    }
}
