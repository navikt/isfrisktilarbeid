package no.nav.syfo.infrastructure.database

import io.ktor.server.testing.*
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateBehandlermelding
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class VedtakRepositorySpek : Spek({

    val vedtak = generateVedtak()
    val behandlerMelding = generateBehandlermelding(behandlerRef = UserConstants.BEHANDLER_REF)

    describe(VedtakRepository::class.java.simpleName) {
        with(TestApplicationEngine()) {
            start()
            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database
            val vedtakRepository = VedtakRepository(database = database)

            afterEachTest {
                database.dropData()
            }

            it("Successfully creates a vedtak and behandlermelding") {

                val (createdVedtak, createdBehandlermelding) = vedtakRepository.createVedtak(
                    vedtak = vedtak,
                    vedtakPdf = UserConstants.PDF_VEDTAK,
                    behandlermelding = behandlerMelding,
                    behandlermeldingPdf = UserConstants.PDF_BEHANDLER_MELDING,
                )

                val persistedVedtak = vedtakRepository.getVedtak(createdVedtak.uuid)
                val pBehandlermelding = database.getBehandlerMelding(createdBehandlermelding.uuid)

                vedtak.uuid shouldBeEqualTo persistedVedtak.uuid
                vedtak.personident shouldBeEqualTo persistedVedtak.personident
                vedtak.getFattetStatus().veilederident shouldBeEqualTo persistedVedtak.getFattetStatus().veilederident
                vedtak.getFerdigbehandletStatus() shouldBe null
                vedtak.begrunnelse shouldBeEqualTo persistedVedtak.begrunnelse
                vedtak.document shouldBeEqualTo persistedVedtak.document
                vedtak.fom shouldBeEqualTo persistedVedtak.fom
                vedtak.tom shouldBeEqualTo persistedVedtak.tom
                vedtak.journalpostId shouldBeEqualTo persistedVedtak.journalpostId

                behandlerMelding.uuid shouldBeEqualTo pBehandlermelding?.uuid
                behandlerMelding.behandlerRef shouldBeEqualTo pBehandlermelding?.behandlerRef
                behandlerMelding.document shouldBeEqualTo pBehandlermelding?.document
                behandlerMelding.journalpostId shouldBeEqualTo pBehandlermelding?.journalpostId
                behandlerMelding.publishedAt shouldBeEqualTo pBehandlermelding?.publishedAt
                pBehandlermelding?.vedtakId shouldBeEqualTo database.getVedtak(createdVedtak.uuid)!!.id
            }
        }
    }
})
