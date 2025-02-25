package no.nav.syfo.infrastructure.kafka.identhendelse

import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateIdenthendelse
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEmpty
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

private val aktivIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT
private val inaktivIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT_NAME_WITH_DASH
private val annenInaktivIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME

class IdenthendelseServiceSpek : Spek({
    describe(IdenthendelseService::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val vedtakRepository = VedtakRepository(database = database)
        val identhendelseService = IdenthendelseService(
            vedtakRepository = vedtakRepository,
        )

        beforeEachTest {
            database.dropData()
        }

        val vedtakMedInaktivIdent = generateVedtak(personident = inaktivIdent)
        val vedtakMedAnnenInaktivIdent = generateVedtak(personident = annenInaktivIdent)

        it("Flytter vedtak fra inaktiv ident til ny ident når person får ny ident") {
            vedtakRepository.createVedtak(
                vedtak = vedtakMedInaktivIdent,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )

            val identhendelse = generateIdenthendelse(
                aktivIdent = aktivIdent,
                inaktiveIdenter = listOf(inaktivIdent)
            )
            identhendelseService.handle(identhendelse)

            vedtakRepository.getVedtak(personident = inaktivIdent).shouldBeEmpty()
            vedtakRepository.getVedtak(personident = aktivIdent).shouldNotBeEmpty()
        }

        it("Flytter vedtak fra inaktive identer når person for ny ident") {
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

            vedtakRepository.getVedtak(personident = inaktivIdent).shouldBeEmpty()
            vedtakRepository.getVedtak(personident = annenInaktivIdent).shouldBeEmpty()
            vedtakRepository.getVedtak(personident = aktivIdent).size shouldBeEqualTo 2
        }

        it("Oppdaterer ingenting når person får ny ident og uten vedtak på inaktiv ident") {
            val identhendelse = generateIdenthendelse(
                aktivIdent = aktivIdent,
                inaktiveIdenter = listOf(inaktivIdent)
            )
            identhendelseService.handle(identhendelse)

            vedtakRepository.getVedtak(personident = inaktivIdent).shouldBeEmpty()
            vedtakRepository.getVedtak(personident = aktivIdent).shouldBeEmpty()
        }

        it("Oppdaterer ingenting når person får ny ident uten inaktive identer") {
            vedtakRepository.createVedtak(
                vedtak = vedtakMedInaktivIdent,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )

            val identhendelse = generateIdenthendelse(
                aktivIdent = aktivIdent,
                inaktiveIdenter = emptyList()
            )
            identhendelseService.handle(identhendelse)

            vedtakRepository.getVedtak(personident = inaktivIdent).shouldNotBeEmpty()
            vedtakRepository.getVedtak(personident = aktivIdent).shouldBeEmpty()
        }

        it("Oppdaterer ingenting når person mangler aktiv ident") {
            vedtakRepository.createVedtak(
                vedtak = vedtakMedInaktivIdent,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )

            val identhendelse = generateIdenthendelse(
                aktivIdent = null,
                inaktiveIdenter = listOf(inaktivIdent)
            )
            identhendelseService.handle(identhendelse)

            vedtakRepository.getVedtak(personident = inaktivIdent).shouldNotBeEmpty()
            vedtakRepository.getVedtak(personident = aktivIdent).shouldBeEmpty()
        }
    }
})
