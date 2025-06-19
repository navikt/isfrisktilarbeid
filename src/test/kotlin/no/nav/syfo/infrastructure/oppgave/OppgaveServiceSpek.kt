package no.nav.syfo.infrastructure.oppgave

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.clients.oppgave.OppgaveClient
import no.nav.syfo.infrastructure.clients.oppgave.OppgaveResponse
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class OppgaveServiceSpek : Spek({
    describe(OppgaveService::class.java.simpleName) {
        val oppgaveClientMock = mockk<OppgaveClient>(relaxed = true)
        val oppgaveService = OppgaveService(
            oppgaveClient = oppgaveClientMock,
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
                coEvery { oppgaveClientMock.createOppgave(any(), any()) } returns
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
                    oppgaveService.createOppgave(vedtak)
                }

                result.getOrThrow().value shouldBeEqualTo expectedOppgaveId

                coVerify(exactly = 1) {
                    oppgaveClientMock.createOppgave(
                        request = match {
                            it.personident == vedtak.personident.value &&
                                it.journalpostId == vedtak.journalpostId!!.value
                        },
                        correlationId = vedtak.uuid,
                    )
                }
            }

            it("returns failure when OppgaveClient throws") {
                val vedtak = generateVedtak()
                coEvery { oppgaveClientMock.createOppgave(any(), any()) } throws RuntimeException("Oppgave error")

                val result = runBlocking {
                    oppgaveService.createOppgave(vedtak)
                }

                result.isFailure shouldBeEqualTo true
            }
        }
    }
})
