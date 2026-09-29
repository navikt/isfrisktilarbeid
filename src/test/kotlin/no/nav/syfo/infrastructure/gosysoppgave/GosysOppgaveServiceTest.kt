package no.nav.syfo.infrastructure.gosysoppgave

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.generator.generateKorrigering
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.clients.gosysoppgave.GosysOppgaveClient
import no.nav.syfo.infrastructure.clients.gosysoppgave.OppgaveResponse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDate

class GosysOppgaveServiceTest {
    private val gosysOppgaveClientMock = mockk<GosysOppgaveClient>(relaxed = true)
    private val gosysOppgaveService = GosysOppgaveService(
        gosysOppgaveClient = gosysOppgaveClientMock,
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `returns OppgaveId on success`() = runBlocking {
        val vedtak = generateVedtak().copy(journalpostId = JournalpostId("1234567890"))
        val expectedOppgaveId = "123456"
        coEvery { gosysOppgaveClientMock.createOppgave(any(), any()) } returns OppgaveResponse(id = expectedOppgaveId)
        val result = gosysOppgaveService.createGosysOppgave(vedtak)
        assertEquals(expectedOppgaveId, result.getOrThrow().value)
        coVerify(exactly = 1) {
            gosysOppgaveClientMock.createOppgave(
                request = match {
                    it.personident == vedtak.personident.value &&
                        it.journalpostId == vedtak.journalpostId!!.value &&
                        it.fristFerdigstillelse == LocalDate.now()
                },
                correlationId = vedtak.uuid,
            )
        }
    }

    @Test
    fun `uses korrigering beskrivelse for korrigering`() = runBlocking {
        val korrigering = generateKorrigering().copy(journalpostId = JournalpostId("1234567890"))
        coEvery { gosysOppgaveClientMock.createOppgave(any(), any()) } returns OppgaveResponse(id = "123456")

        gosysOppgaveService.createGosysOppgave(korrigering).getOrThrow()

        coVerify(exactly = 1) {
            gosysOppgaveClientMock.createOppgave(
                request = match { it.beskrivelse.startsWith("Korrigert vedtak: innvilget i perioden ") },
                correlationId = korrigering.uuid,
            )
        }
    }

    @Test
    fun `returns failure when OppgaveClient throws exception`() {
        runBlocking {
            val vedtak = generateVedtak()
            coEvery { gosysOppgaveClientMock.createOppgave(any(), any()) } throws RuntimeException("Oppgave error")
            val result = gosysOppgaveService.createGosysOppgave(vedtak)
            assertTrue(result.isFailure)
        }
    }
}
