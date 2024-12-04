package no.nav.syfo.infrastructure.database

import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.InfotrygdStatus
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class VedtakRepositorySpek : Spek({

    val vedtak = generateVedtak()

    describe(VedtakRepository::class.java.simpleName) {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database
        val vedtakRepository = VedtakRepository(database = database)

        afterEachTest {
            database.dropData()
        }

        it("Successfully creates vedtak") {

            val createdVedtak = vedtakRepository.createVedtak(
                vedtak = vedtak,
                vedtakPdf = UserConstants.PDF_VEDTAK,
            )

            val persistedVedtak = vedtakRepository.getVedtak(createdVedtak.uuid)

            vedtak.uuid shouldBeEqualTo persistedVedtak.uuid
            vedtak.personident shouldBeEqualTo persistedVedtak.personident
            vedtak.getFattetStatus().veilederident shouldBeEqualTo persistedVedtak.getFattetStatus().veilederident
            vedtak.getFerdigbehandletStatus() shouldBe null
            vedtak.begrunnelse shouldBeEqualTo persistedVedtak.begrunnelse
            vedtak.document shouldBeEqualTo persistedVedtak.document
            vedtak.fom shouldBeEqualTo persistedVedtak.fom
            vedtak.tom shouldBeEqualTo persistedVedtak.tom
            vedtak.journalpostId shouldBeEqualTo persistedVedtak.journalpostId
            vedtak.infotrygdStatus shouldBeEqualTo InfotrygdStatus.IKKE_SENDT
        }
    }
})
