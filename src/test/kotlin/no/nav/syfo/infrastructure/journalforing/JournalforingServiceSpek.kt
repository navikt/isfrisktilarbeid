package no.nav.syfo.infrastructure.journalforing

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateBehandlermelding
import no.nav.syfo.generator.generateJournalpostRequest
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.BrevkodeType
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.JournalpostKanal
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.OverstyrInnsynsregler
import no.nav.syfo.infrastructure.mock.dokarkivResponse
import no.nav.syfo.infrastructure.mock.mockedJournalpostId
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.*

class JournalforingServiceSpek : Spek({
    describe(JournalforingService::class.java.simpleName) {

        val externalMockEnvironment = ExternalMockEnvironment.instance
        val dokarkivMock = mockk<DokarkivClient>(relaxed = true)
        val journalforingService = JournalforingService(
            dokarkivClient = dokarkivMock,
            pdlClient = externalMockEnvironment.pdlClient,
            dialogmeldingBehandlerClient = externalMockEnvironment.dialogmeldingBehandlerClient,
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
        describe("journalfør behandlermelding") {
            it("sender forventet journalpost til dokarkiv") {
                val behandlermelding = generateBehandlermelding(behandlerRef = UserConstants.BEHANDLER_REF)
                val journalpostId = runBlocking {
                    journalforingService.journalfor(
                        personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        behandlermelding = behandlermelding,
                        pdf = UserConstants.PDF_BEHANDLER_MELDING,
                    )
                }.getOrThrow()

                journalpostId shouldBeEqualTo mockedJournalpostId

                coVerify(exactly = 1) {
                    dokarkivMock.journalfor(
                        journalpostRequest = generateJournalpostRequest(
                            tittel = "Melding til behandler om friskmelding til arbeidsformidling",
                            brevkodeType = BrevkodeType.BEHANDLERMELDING_FRISKMELDING_TIL_ARBEIDSFORMIDLING,
                            kanal = JournalpostKanal.HELSENETTET,
                            overstyrInnsynsregler = OverstyrInnsynsregler.VISES_MASKINELT_GODKJENT,
                            pdf = UserConstants.PDF_BEHANDLER_MELDING,
                            eksternReferanse = behandlermelding.uuid,
                            mottakerPersonident = UserConstants.BEHANDLER_FNR,
                            mottakerNavn = UserConstants.BEHANDLER_NAVN,
                            brukerPersonident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        )
                    )
                }
            }

            it("feiler når kall til isdialogmelding for å hente behandler feiler") {
                val failingBehandlermelding = generateBehandlermelding(behandlerRef = UUID.randomUUID())
                val result = runBlocking {
                    journalforingService.journalfor(
                        personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        behandlermelding = failingBehandlermelding,
                        pdf = UserConstants.PDF_BEHANDLER_MELDING,
                    )
                }

                result.isFailure shouldBeEqualTo true

                coVerify(exactly = 0) { dokarkivMock.journalfor(any()) }
            }
        }
    }
})
