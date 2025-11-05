package no.nav.syfo.infrastructure.kafka.identhendelse

import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateIdenthendelse
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

private val aktivIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
private val inaktivIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT_NAME_WITH_DASH
private val annenInaktivIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME

class IdenthendelseServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database
    private val vedtakRepository = VedtakRepository(database = database)
    private val identhendelseService = IdenthendelseService(
        vedtakRepository = vedtakRepository,
    )

    private val vedtakMedInaktivIdent = generateVedtak(personident = inaktivIdent)
    private val vedtakMedAnnenInaktivIdent = generateVedtak(personident = annenInaktivIdent)

    @BeforeEach
    fun setup() {
        database.dropData()
    }

    @Test
    fun `Flytter vedtak fra inaktiv ident til ny ident naar person faar ny ident`() {
        vedtakRepository.createVedtak(
            vedtak = vedtakMedInaktivIdent,
            vedtakPdf = UserConstants.PDF_VEDTAK,
        )
        val identhendelse = generateIdenthendelse(
            aktivIdent = aktivIdent,
            inaktiveIdenter = listOf(inaktivIdent)
        )
        identhendelseService.handle(identhendelse)
        assertTrue(vedtakRepository.getVedtak(personident = inaktivIdent).isEmpty())
        assertTrue(vedtakRepository.getVedtak(personident = aktivIdent).isNotEmpty())
    }

    @Test
    fun `Flytter vedtak fra inaktive identer naar person faar ny ident`() {
        vedtakRepository.createVedtak(
            vedtak = vedtakMedInaktivIdent,
            vedtakPdf = UserConstants.PDF_VEDTAK,
        )
        vedtakRepository.createVedtak(
            vedtak = vedtakMedAnnenInaktivIdent,
            vedtakPdf = UserConstants.PDF_VEDTAK,
        )
        val identhendelse = generateIdenthendelse(
            aktivIdent = aktivIdent,
            inaktiveIdenter = listOf(inaktivIdent, annenInaktivIdent)
        )
        identhendelseService.handle(identhendelse)
        assertTrue(vedtakRepository.getVedtak(personident = inaktivIdent).isEmpty())
        assertTrue(vedtakRepository.getVedtak(personident = annenInaktivIdent).isEmpty())
        assertEquals(2, vedtakRepository.getVedtak(personident = aktivIdent).size)
    }

    @Test
    fun `Oppdaterer ingenting naar person faar ny ident og uten vedtak paa inaktiv ident`() {
        val identhendelse = generateIdenthendelse(
            aktivIdent = aktivIdent,
            inaktiveIdenter = listOf(inaktivIdent)
        )
        identhendelseService.handle(identhendelse)
        assertTrue(vedtakRepository.getVedtak(personident = inaktivIdent).isEmpty())
        assertTrue(vedtakRepository.getVedtak(personident = aktivIdent).isEmpty())
    }

    @Test
    fun `Oppdaterer ingenting naar person faar ny ident uten inaktive identer`() {
        vedtakRepository.createVedtak(
            vedtak = vedtakMedInaktivIdent,
            vedtakPdf = UserConstants.PDF_VEDTAK,
        )
        val identhendelse = generateIdenthendelse(
            aktivIdent = aktivIdent,
            inaktiveIdenter = emptyList()
        )
        identhendelseService.handle(identhendelse)
        assertTrue(vedtakRepository.getVedtak(personident = inaktivIdent).isNotEmpty())
        assertTrue(vedtakRepository.getVedtak(personident = aktivIdent).isEmpty())
    }

    @Test
    fun `Oppdaterer ingenting naar person mangler aktiv ident`() {
        vedtakRepository.createVedtak(
            vedtak = vedtakMedInaktivIdent,
            vedtakPdf = UserConstants.PDF_VEDTAK,
        )
        val identhendelse = generateIdenthendelse(
            aktivIdent = null,
            inaktiveIdenter = listOf(inaktivIdent)
        )
        identhendelseService.handle(identhendelse)
        assertTrue(vedtakRepository.getVedtak(personident = inaktivIdent).isNotEmpty())
        assertTrue(vedtakRepository.getVedtak(personident = aktivIdent).isEmpty())
    }
}
