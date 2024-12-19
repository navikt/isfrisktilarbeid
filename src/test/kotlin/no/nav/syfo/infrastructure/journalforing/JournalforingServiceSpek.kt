package no.nav.syfo.infrastructure.journalforing

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateJournalpostRequest
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.BrevkodeType
import no.nav.syfo.infrastructure.mock.dokarkivResponse
import no.nav.syfo.infrastructure.mock.mockedJournalpostId
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class JournalforingServiceSpek : Spek({
    describe(JournalforingService::class.java.simpleName) {

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val dokarkivMock = mockk<DokarkivClient>(relaxed = true)
        val journalforingService = JournalforingService(
            dokarkivClient = dokarkivMock,
            pdlClient = externalMockEnvironment.pdlClient,
            isJournalforingRetryEnabled = externalMockEnvironment.environment.isJournalforingRetryEnabled,
        )

        beforeEachTest {
            clearAllMocks()
            coEvery { dokarkivMock.journalfor(any()) } returns dokarkivResponse
        }

        describe("journalfor vedtak") {
            it("sender forventet journalpost til dokarkiv") {
                val vedtak = generateVedtak()
                val journalpostId = runBlocking {
                    journalforingService.journalfor(
                        vedtak = vedtak,
                        pdf = UserConstants.PDF_VEDTAK,
                    )
                }.getOrThrow()

                journalpostId shouldBeEqualTo mockedJournalpostId

                coVerify(exactly = 1) {
                    dokarkivMock.journalfor(
                        journalpostRequest = generateJournalpostRequest(
                            tittel = "Vedtak om friskmelding til arbeidsformidling",
                            brevkodeType = BrevkodeType.VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING,
                            pdf = UserConstants.PDF_VEDTAK,
                            eksternReferanse = vedtak.uuid,
                            mottakerPersonident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                            mottakerNavn = UserConstants.PERSON_FULLNAME,
                            brukerPersonident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        )
                    )
                }
            }

            it("feiler når kall til pdl feiler") {
                val failingVedtak = generateVedtak(personident = UserConstants.ARBEIDSTAKER_PERSONIDENT_PDL_FAILS)

                val result = runBlocking {
                    journalforingService.journalfor(
                        vedtak = failingVedtak,
                        pdf = UserConstants.PDF_VEDTAK,
                    )
                }

                result.isFailure shouldBeEqualTo true

                coVerify(exactly = 0) { dokarkivMock.journalfor(any()) }
            }
        }
    }
})
