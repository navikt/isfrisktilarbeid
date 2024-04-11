package no.nav.syfo.infrastructure.database

import io.ktor.server.testing.*
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateBehandlermelding
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class VedtakRepositorySpek : Spek({

    val vedtak = generateVedtak()
    val behandlerMelding = generateBehandlermelding()

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
                    behandlerMelding = behandlerMelding,
                    behandlerMeldingPdf = UserConstants.PDF_BEHANDLER_MELDING,
                )

                val pVedtak = database.getVedtak(createdVedtak.uuid)
                val pBehandlermelding = database.getBehandlerMelding(createdBehandlermelding.uuid)

                vedtak.uuid shouldBeEqualTo pVedtak?.uuid
                vedtak.personident shouldBeEqualTo pVedtak?.personident
                vedtak.veilederident shouldBeEqualTo pVedtak?.veilederident
                vedtak.begrunnelse shouldBeEqualTo pVedtak?.begrunnelse
                vedtak.document shouldBeEqualTo pVedtak?.document
                vedtak.fom shouldBeEqualTo pVedtak?.fom
                vedtak.tom shouldBeEqualTo pVedtak?.tom
                vedtak.journalpostId shouldBeEqualTo pVedtak?.journalpostId

                behandlerMelding.uuid shouldBeEqualTo pBehandlermelding?.uuid
                behandlerMelding.behandlerRef shouldBeEqualTo pBehandlermelding?.behandlerRef
                behandlerMelding.document shouldBeEqualTo pBehandlermelding?.document
                behandlerMelding.journalpostId shouldBeEqualTo pBehandlermelding?.journalpostId

                pBehandlermelding?.vedtakId shouldBeEqualTo pVedtak?.id
            }
        }
    }
})
