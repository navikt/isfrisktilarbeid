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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class JournalforingServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val dokarkivMock = mockk<DokarkivClient>(relaxed = true)
    private val journalforingService = JournalforingService(
        dokarkivClient = dokarkivMock,
        pdlClient = externalMockEnvironment.pdlClient,
        isJournalforingRetryEnabled = externalMockEnvironment.environment.isJournalforingRetryEnabled,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
        coEvery { dokarkivMock.journalfor(any()) } returns dokarkivResponse
    }

    @Test
    fun `sender forventet journalpost til dokarkiv`() = runBlocking {
        val vedtak = generateVedtak()
        val journalpostId = journalforingService.journalfor(
            vedtak = vedtak,
            pdf = UserConstants.PDF_VEDTAK,
        ).getOrThrow()

        assertEquals(mockedJournalpostId, journalpostId)

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

    @Test
    fun `feiler når kall til pdl feiler`() = runBlocking {
        val failingVedtak = generateVedtak(personident = UserConstants.ARBEIDSTAKER_PERSONIDENT_PDL_FAILS)

        val result = journalforingService.journalfor(
            vedtak = failingVedtak,
            pdf = UserConstants.PDF_VEDTAK,
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { dokarkivMock.journalfor(any()) }
    }
}
