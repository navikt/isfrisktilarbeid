package no.nav.syfo.infrastructure.clients.pdl

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.pdl.dto.Adressebeskyttelse
import no.nav.syfo.infrastructure.clients.pdl.dto.Gradering
import no.nav.syfo.infrastructure.clients.pdl.dto.PdlPerson
import no.nav.syfo.infrastructure.clients.pdl.dto.PdlPersonNavn
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*

class PdlClientTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val pdlClient = PdlClient(
        azureAdClient = externalMockEnvironment.azureAdClient,
        pdlEnvironment = externalMockEnvironment.environment.clients.pdl,
        httpClient = externalMockEnvironment.mockHttpClient,
    )

    @Nested
    @DisplayName("Happy cases")
    inner class HappyCase {
        @Test
        fun `returns person from pdl`() {
            runBlocking {
                val person = pdlClient.getPerson(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                assertEquals(1, person.navn.size)
                assertEquals(UserConstants.PERSON_FORNAVN, person.navn[0].fornavn)
            }
        }

        @Test
        fun `returns fullname from person`() {
            val pdlPerson = PdlPerson(
                navn = listOf(
                    PdlPersonNavn(
                        fornavn = UserConstants.PERSON_FORNAVN,
                        mellomnavn = UserConstants.PERSON_MELLOMNAVN,
                        etternavn = UserConstants.PERSON_ETTERNAVN,
                    )
                ),
                adressebeskyttelse = listOf(Adressebeskyttelse(Gradering.UGRADERT))
            )
            val person = runBlocking { pdlClient.getPerson(UserConstants.ARBEIDSTAKER_PERSONIDENT) }
            assertEquals(pdlPerson.fullName, person.fullName)
        }

        @Test
        fun `returns full name when person has name with dashes`() {
            val fullname = runBlocking { pdlClient.getPerson(UserConstants.ARBEIDSTAKER_PERSONIDENT_NAME_WITH_DASH).fullName }
            assertEquals(UserConstants.PERSON_FULLNAME_DASH, fullname)
        }

        @Test
        fun `returns geografisk tilknytning`() {
            val geografiskTilknytning = runBlocking { pdlClient.geografiskTilknytning(UserConstants.ARBEIDSTAKER_PERSONIDENT) }
            assertEquals(UserConstants.KOMMUNE, geografiskTilknytning.kommune)
        }
    }

    @Nested
    @DisplayName("Unhappy cases")
    inner class UnhappyCase {
        @AfterEach
        fun clearMocks() {
            clearAllMocks()
        }

        @Test
        fun `throws exception when person is missing name`() {
            runBlocking {
                assertThrows<RuntimeException> {
                    pdlClient.getPerson(UserConstants.ARBEIDSTAKER_PERSONIDENT_NO_NAME)
                }
            }
        }

        @Test
        fun `throws exception when pdl has error`() {
            runBlocking {
                assertThrows<RuntimeException> {
                    pdlClient.getPerson(UserConstants.ARBEIDSTAKER_PERSONIDENT_PDL_FAILS)
                }
            }
        }

        @Test
        fun `throws exception when AzureAdClient has error`() {
            runBlocking {
                val azureAdMock = mockk<AzureAdClient>(relaxed = true)
                val pdlClientMockedAzure = PdlClient(
                    azureAdClient = azureAdMock,
                    pdlEnvironment = externalMockEnvironment.environment.clients.pdl,
                )
                coEvery { azureAdMock.getSystemToken(any()) } returns null
                assertThrows<RuntimeException> {
                    pdlClientMockedAzure.getPerson(UserConstants.ARBEIDSTAKER_PERSONIDENT)
                }
            }
        }
    }
}
