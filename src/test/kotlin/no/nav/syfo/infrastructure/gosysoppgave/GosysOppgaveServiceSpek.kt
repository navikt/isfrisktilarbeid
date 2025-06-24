package no.nav.syfo.infrastructure.gosysoppgave

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.clients.gosysoppgave.GosysOppgaveClient
import no.nav.syfo.infrastructure.clients.gosysoppgave.OppgaveResponse
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class GosysOppgaveServiceSpek : Spek({
    describe(GosysOppgaveService::class.java.simpleName) {
        val gosysOppgaveClientMock = mockk<GosysOppgaveClient>(relaxed = true)
        val gosysOppgaveService = GosysOppgaveService(
            gosysOppgaveClient = gosysOppgaveClientMock,
        )

        beforeEachTest {
            clearAllMocks()
        }

        describe("createOppgave") {
            it("returns OppgaveId on success") {
                val vedtak = generateVedtak().copy(
                    journalpostId = JournalpostId("1234567890"),
                )
                val expectedOppgaveId = "123456"
                coEvery { gosysOppgaveClientMock.createOppgave(any(), any()) } returns
                    OppgaveResponse(
                        id = expectedOppgaveId,
                        beskrivelse = "Innvilget i perioden (${vedtak.fom} - ${vedtak.tom})",
                        status = "OPPRETTET",
                        oppgavetype = "VURD_HENV",
                        tema = "SYK",
                        tildeltEnhetsnr = "4488",
                        versjon = 1
                    )

                val result = runBlocking {
                    gosysOppgaveService.createGosysOppgave(vedtak)
                }

                result.getOrThrow().value shouldBeEqualTo expectedOppgaveId

                coVerify(exactly = 1) {
                    gosysOppgaveClientMock.createOppgave(
                        request = match {
                            it.personident == vedtak.personident.value &&
                                it.journalpostId == vedtak.journalpostId!!.value
                        },
                        correlationId = vedtak.uuid,
                    )
                }
            }

            it("returns failure when OppgaveClient throws exception") {
                val vedtak = generateVedtak()
                coEvery { gosysOppgaveClientMock.createOppgave(any(), any()) } throws RuntimeException("Oppgave error")

                val result = runBlocking {
                    gosysOppgaveService.createGosysOppgave(vedtak)
                }

                result.isFailure shouldBeEqualTo true
            }
        }
    }
})
