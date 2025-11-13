package no.nav.syfo.api.endpoints

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.*
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.UserConstants.PDF_VEDTAK
import no.nav.syfo.api.*
import no.nav.syfo.api.model.VedtakRequestDTO
import no.nav.syfo.api.model.VedtakResponseDTO
import no.nav.syfo.api.model.VilkarResponseDTO
import no.nav.syfo.application.IVedtakProducer
import no.nav.syfo.application.VedtakService
import no.nav.syfo.domain.InfotrygdStatus
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getVedtakPdf
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.mq.InfotrygdMQSender
import no.nav.syfo.infrastructure.gosysoppgave.GosysOppgaveService
import no.nav.syfo.infrastructure.pdf.PdfService
import no.nav.syfo.testAppState
import no.nav.syfo.util.configure
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import java.time.LocalDate
import java.util.*

class VedtakEndpointsTest {
    private val urlVedtak = "$apiBasePath/$vedtakPath"
    private val urlVilkar = "$apiBasePath/$vilkarPath"

    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val database = externalMockEnvironment.database

    private val validToken = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT,
    )
    private val validTokenOther = generateJWT(
        audience = externalMockEnvironment.environment.azure.appClientId,
        issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
        navIdent = UserConstants.VEILEDER_IDENT_OTHER,
    )
    private val personident = UserConstants.ARBEIDSTAKER_PERSONIDENT

    private val begrunnelse = "Dette er en begrunnelse for vedtak 8-5"
    private val vedtakDocument = generateDocumentComponent(
        fritekst = begrunnelse,
        header = "Vedtak"
    )
    private val vedtakFom = LocalDate.now()
    private val vedtakTom = LocalDate.now().plusWeeks(12)
    private val vedtakRequestDTO = VedtakRequestDTO(
        document = vedtakDocument,
        begrunnelse = begrunnelse,
        fom = vedtakFom,
        tom = vedtakTom,
    )
    private val vedtakRepository = VedtakRepository(database)
    private val vedtakService = VedtakService(
        pdfService = PdfService(externalMockEnvironment.pdfgenClient, externalMockEnvironment.pdlClient),
        vedtakRepository = vedtakRepository,
        journalforingService = mockk<JournalforingService>(relaxed = true),
        gosysOppgaveService = mockk<GosysOppgaveService>(relaxed = true),
        infotrygdService = mockk<InfotrygdService>(relaxed = true),
        vedtakProducer = mockk<IVedtakProducer>(relaxed = true),
    )
    private val infotrygdMQSender = mockk<InfotrygdMQSender>(relaxed = true)

    private fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
        application {
            testApiModule(
                externalMockEnvironment = externalMockEnvironment,
                infotrygdMQSender = infotrygdMQSender,
            )
        }
        return createClient { install(ContentNegotiation) { jackson { configure() } } }
    }

    private fun createVedtak(requestDTO: VedtakRequestDTO) = runBlocking {
        vedtakService.createVedtak(
            personident = personident,
            veilederident = UserConstants.VEILEDER_IDENT,
            begrunnelse = requestDTO.begrunnelse,
            document = requestDTO.document,
            fom = requestDTO.fom,
            tom = requestDTO.tom,
            callId = UUID.randomUUID().toString(),
        )
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun mockInfotrygdKvitteringReceived(kvitteringOk: Boolean) {
        GlobalScope.launch {
            try {
                withTimeout(1000) {
                    while (testAppState().ready) {
                        val vedtak = vedtakRepository.getVedtak(personident = UserConstants.ARBEIDSTAKER_PERSONIDENT).firstOrNull()
                        if (vedtak != null) {
                            vedtakRepository.setInfotrygdKvitteringReceived(
                                vedtak = vedtak,
                                ok = kvitteringOk,
                                feilmelding = null,
                            )
                            break
                        } else {
                            delay(100)
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
            }
        }
    }

    @BeforeEach
    fun setup() {
        clearAllMocks()
        justRun { infotrygdMQSender.sendToMQ(any(), any()) }
        database.dropData()
    }

    @Nested
    @DisplayName("Get vedtak")
    inner class GetVedtak {
        @Test
        fun `Successfully gets empty list of vedtak`() = testApplication {
            val client = setupApiAndClient()
            val response = client.get(urlVedtak) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, personident.value)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(0, response.body<List<VedtakResponseDTO>>().size)
        }

        @Test
        fun `Successfully gets list of single vedtak`() = testApplication {
            createVedtak(vedtakRequestDTO)
            val client = setupApiAndClient()
            val response = client.get(urlVedtak) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, personident.value)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val vedtakResponse = response.body<List<VedtakResponseDTO>>()
            assertEquals(1, vedtakResponse.size)
            assertEquals(begrunnelse, vedtakResponse[0].begrunnelse)
            assertEquals(vedtakDocument, vedtakResponse[0].document)
            assertEquals(vedtakFom, vedtakResponse[0].fom)
            assertEquals(vedtakTom, vedtakResponse[0].tom)
            assertEquals(personident.value, vedtakResponse[0].personident)
            assertEquals(UserConstants.VEILEDER_IDENT, vedtakResponse[0].veilederident)
            assertEquals(InfotrygdStatus.IKKE_SENDT.name, vedtakResponse[0].infotrygdStatus)
            assertFalse(vedtakResponse[0].isJournalfort)
            assertFalse(vedtakResponse[0].hasGosysOppgave)
            val vedtakPdf = database.getVedtakPdf(vedtakUuid = vedtakResponse[0].uuid)?.pdf!!
            assertEquals(PDF_VEDTAK.size, vedtakPdf.size)
            assertEquals(PDF_VEDTAK[0], vedtakPdf[0])
            assertEquals(PDF_VEDTAK[1], vedtakPdf[1])
            val vedtak = vedtakRepository.getVedtak(vedtakResponse[0].uuid)
            assertEquals(vedtak.uuid, vedtakResponse[0].uuid)
        }

        @Test
        fun `Successfully gets list of multiple vedtak`() = testApplication {
            createVedtak(vedtakRequestDTO.copy(fom = vedtakRequestDTO.fom.minusYears(1), tom = vedtakRequestDTO.tom.minusYears(1)))
            createVedtak(vedtakRequestDTO)
            val client = setupApiAndClient()
            val response = client.get(urlVedtak) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, personident.value)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val vedtakResponse = response.body<List<VedtakResponseDTO>>()
            assertEquals(2, vedtakResponse.size)
            assertTrue(vedtakResponse[0].createdAt.isAfter(vedtakResponse[1].createdAt))
            assertFalse(vedtakResponse[0].isJournalfort)
            assertFalse(vedtakResponse[0].hasGosysOppgave)
            assertFalse(vedtakResponse[1].isJournalfort)
            assertFalse(vedtakResponse[1].hasGosysOppgave)
        }
    }

    @Nested
    @DisplayName("Vilkar")
    inner class Vilkar {
        @Test
        fun `Vilkar when person is not arbeidssoker`() = testApplication {
            val client = setupApiAndClient()
            val response = client.get(urlVilkar) {
                contentType(ContentType.Application.Json)
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT_UTLAND.value)
                setBody(vedtakRequestDTO)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertFalse(response.body<VilkarResponseDTO>().isArbeidssoker)
        }

        @Test
        fun `Vilkar when person is arbeidssoker`() = testApplication {
            val client = setupApiAndClient()
            val response = client.get(urlVilkar) {
                contentType(ContentType.Application.Json)
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                setBody(vedtakRequestDTO)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.body<VilkarResponseDTO>().isArbeidssoker)
        }
    }

    @Nested
    @DisplayName("Post vedtak")
    inner class PostVedtak {
        @Test
        fun `Creates vedtak`() = testApplication {
            val client = setupApiAndClient()
            val response = client.post(urlVedtak) {
                contentType(ContentType.Application.Json)
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, personident.value)
                setBody(vedtakRequestDTO)
            }
            assertEquals(HttpStatusCode.Created, response.status)
            val vedtakResponse = response.body<VedtakResponseDTO>()
            assertEquals(begrunnelse, vedtakResponse.begrunnelse)
            assertEquals(vedtakDocument, vedtakResponse.document)
            assertEquals(vedtakFom, vedtakResponse.fom)
            assertEquals(vedtakTom, vedtakResponse.tom)
            assertEquals(personident.value, vedtakResponse.personident)
            assertEquals(UserConstants.VEILEDER_IDENT, vedtakResponse.veilederident)
            assertTrue(vedtakResponse.isJournalfort)
            assertTrue(vedtakResponse.hasGosysOppgave)
            val vedtakPdf = database.getVedtakPdf(vedtakUuid = vedtakResponse.uuid)?.pdf!!
            assertEquals(PDF_VEDTAK.size, vedtakPdf.size)
            assertEquals(PDF_VEDTAK[0], vedtakPdf[0])
            assertEquals(PDF_VEDTAK[1], vedtakPdf[1])
            val vedtak = vedtakRepository.getVedtak(vedtakResponse.uuid)
            assertEquals(vedtak.uuid, vedtakResponse.uuid)
        }
    }

    @Test
    fun `Creates vedtak back in time`() = testApplication {
        val client = setupApiAndClient()
        val vedtakRequestDTOInvalidTom = VedtakRequestDTO(
            document = vedtakDocument,
            begrunnelse = begrunnelse,
            fom = LocalDate.now().minusDays(90),
            tom = LocalDate.now().minusDays(10),
        )
        val response = client.post(urlVedtak) {
            contentType(ContentType.Application.Json)
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, personident.value)
            setBody(vedtakRequestDTOInvalidTom)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val responseBody = response.body<VedtakResponseDTO>()
        assertTrue(responseBody.isJournalfort)
        assertTrue(responseBody.hasGosysOppgave)
    }

    @Test
    fun `Error when tom-date is before fom-date`() = testApplication {
        val client = setupApiAndClient()
        val vedtakRequestDTOInvalidTom = VedtakRequestDTO(
            document = vedtakDocument,
            begrunnelse = begrunnelse,
            fom = LocalDate.now().plusDays(10),
            tom = LocalDate.now().plusDays(1),
        )
        val response = client.post(urlVedtak) {
            contentType(ContentType.Application.Json)
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, personident.value)
            setBody(vedtakRequestDTOInvalidTom)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `Error when person is not arbeidssoker`() = testApplication {
        val client = setupApiAndClient()
        val response = client.post(urlVedtak) {
            contentType(ContentType.Application.Json)
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT_UTLAND.value)
            setBody(vedtakRequestDTO)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `Creates vedtak and publish to infotrygd success`() = testApplication {
        val client = setupApiAndClient()
        val response = client.post(urlVedtak) {
            contentType(ContentType.Application.Json)
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, personident.value)
            setBody(vedtakRequestDTO)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        verify(exactly = 1) { infotrygdMQSender.sendToMQ(any(), any()) }
        val vedtakResponse = response.body<VedtakResponseDTO>()
        assertEquals(InfotrygdStatus.KVITTERING_MANGLER.name, vedtakResponse.infotrygdStatus)
        assertTrue(vedtakResponse.isJournalfort)
        assertTrue(vedtakResponse.hasGosysOppgave)
    }

    @Test
    fun `Creates vedtak and publish to infotrygd fails`() = testApplication {
        every { infotrygdMQSender.sendToMQ(any(), any()) } throws Exception("Error sending to infotrygd")
        val client = setupApiAndClient()
        val response = client.post(urlVedtak) {
            contentType(ContentType.Application.Json)
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, personident.value)
            setBody(vedtakRequestDTO)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        verify(exactly = 1) { infotrygdMQSender.sendToMQ(any(), any()) }
        val vedtakResponse = response.body<VedtakResponseDTO>()
        assertEquals(InfotrygdStatus.IKKE_SENDT.name, vedtakResponse.infotrygdStatus)
        assertTrue(vedtakResponse.isJournalfort)
        assertTrue(vedtakResponse.hasGosysOppgave)
    }

    @Test
    fun `Creates vedtak and publish to infotrygd success with kvittering OK`() = testApplication {
        mockInfotrygdKvitteringReceived(kvitteringOk = true)
        val client = setupApiAndClient()
        val response = client.post(urlVedtak) {
            contentType(ContentType.Application.Json)
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, personident.value)
            setBody(vedtakRequestDTO)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        verify(exactly = 1) { infotrygdMQSender.sendToMQ(any(), any()) }
        val vedtakResponse = response.body<VedtakResponseDTO>()
        assertEquals(InfotrygdStatus.KVITTERING_OK.name, vedtakResponse.infotrygdStatus)
        assertTrue(vedtakResponse.isJournalfort)
        assertTrue(vedtakResponse.hasGosysOppgave)
    }

    @Test
    fun `Creates vedtak and publish to infotrygd success with kvittering not OK`() = testApplication {
        mockInfotrygdKvitteringReceived(kvitteringOk = false)
        val client = setupApiAndClient()
        val response = client.post(urlVedtak) {
            contentType(ContentType.Application.Json)
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, personident.value)
            setBody(vedtakRequestDTO)
        }
        assertEquals(HttpStatusCode.Created, response.status)
        verify(exactly = 1) { infotrygdMQSender.sendToMQ(any(), any()) }
        val vedtakResponse = response.body<VedtakResponseDTO>()
        assertEquals(InfotrygdStatus.KVITTERING_FEIL.name, vedtakResponse.infotrygdStatus)
        assertTrue(vedtakResponse.isJournalfort)
        assertTrue(vedtakResponse.hasGosysOppgave)
    }

    @Test
    fun `Throws error when document is empty`() = testApplication {
        val vedtakWithoutDocument = vedtakRequestDTO.copy(document = emptyList())
        val client = setupApiAndClient()
        val response = client.post(urlVedtak) {
            contentType(ContentType.Application.Json)
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, personident.value)
            setBody(vedtakWithoutDocument)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `Throws error when begrunnelse is empty`() = testApplication {
        val vedtakWithoutBegrunnelse = vedtakRequestDTO.copy(begrunnelse = "")
        val client = setupApiAndClient()
        val response = client.post(urlVedtak) {
            contentType(ContentType.Application.Json)
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, personident.value)
            setBody(vedtakWithoutBegrunnelse)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `Returns status Conflict when vedtak not ferdigbehandlet exists`() = testApplication {
        createVedtak(vedtakRequestDTO)
        val client = setupApiAndClient()
        val response = client.post(urlVedtak) {
            contentType(ContentType.Application.Json)
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, personident.value)
            setBody(vedtakRequestDTO)
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `Returns status Conflict when overlapping vedtak exists`() = testApplication {
        val (vedtak, _) = createVedtak(vedtakRequestDTO)
        vedtakService.ferdigbehandleVedtak(vedtak, UserConstants.VEILEDER_IDENT)
        val newRequest = vedtakRequestDTO.copy(fom = vedtakRequestDTO.fom.plusDays(1), tom = vedtakRequestDTO.tom.plusDays(1))
        val client = setupApiAndClient()
        val response = client.post(urlVedtak) {
            contentType(ContentType.Application.Json)
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, personident.value)
            setBody(newRequest)
        }
        assertEquals(HttpStatusCode.Conflict, response.status)
    }

    @Test
    fun `Does not return status Conflict when non-overlapping vedtak exists`() = testApplication {
        val (vedtak, _) = createVedtak(vedtakRequestDTO)
        vedtakService.ferdigbehandleVedtak(vedtak, UserConstants.VEILEDER_IDENT)
        val newRequest = vedtakRequestDTO.copy(fom = vedtakRequestDTO.tom.plusDays(1), tom = vedtakRequestDTO.tom.plusDays(10))
        val client = setupApiAndClient()
        val response = client.post(urlVedtak) {
            contentType(ContentType.Application.Json)
            bearerAuth(validToken)
            header(NAV_PERSONIDENT_HEADER, personident.value)
            setBody(newRequest)
        }
        assertEquals(HttpStatusCode.Created, response.status)
    }

    @Nested
    @DisplayName("Ferdigbehandle vedtak")
    inner class FerdigbehandleVedtak {
        @Test
        fun `Sets vedtak ferdigbehandlet and updates veileder`() = testApplication {
            val (vedtak, _) = createVedtak(vedtakRequestDTO)
            val client = setupApiAndClient()
            val response = client.put("$urlVedtak/${vedtak.uuid}/ferdigbehandling") {
                bearerAuth(validTokenOther)
                header(NAV_PERSONIDENT_HEADER, personident.value)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            val vedtakResponse = response.body<VedtakResponseDTO>()
            assertEquals(vedtak.uuid, vedtakResponse.uuid)
            assertNotNull(vedtakResponse.ferdigbehandletAt)
            assertEquals(UserConstants.VEILEDER_IDENT_OTHER, vedtakResponse.ferdigbehandletBy)
            assertFalse(vedtakResponse.isJournalfort)
            assertFalse(vedtakResponse.hasGosysOppgave)
            val vedtakPersisted = vedtakRepository.getVedtak(vedtak.uuid)
            assertTrue(vedtakPersisted.isFerdigbehandlet())
            assertEquals(UserConstants.VEILEDER_IDENT_OTHER, vedtakPersisted.getFerdigbehandletStatus()!!.veilederident)
        }

        @Test
        fun `Throws error when ferdigstiller unknown vedtak`() = testApplication {
            val client = setupApiAndClient()
            val response = client.put("$urlVedtak/${UUID.randomUUID()}/ferdigbehandling") {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, personident.value)
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

        @Test
        fun `Throws error when ferdigbehandler already ferdigbehandlet vedtak`() = testApplication {
            val (vedtak, _) = createVedtak(vedtakRequestDTO)
            vedtakService.ferdigbehandleVedtak(vedtak, UserConstants.VEILEDER_IDENT)
            val client = setupApiAndClient()
            val response = client.put("$urlVedtak/${vedtak.uuid}/ferdigbehandling") {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, personident.value)
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
    }

    @Nested
    @DisplayName("Unhappy paths")
    inner class UnhappyPaths {
        @Test
        fun `Returns status Unauthorized if no token is supplied`() = testApplication {
            val client = setupApiAndClient()
            assertEquals(HttpStatusCode.Unauthorized, client.get(urlVedtak).status)
            assertEquals(HttpStatusCode.Unauthorized, client.post(urlVedtak).status)
        }

        @Test
        fun `Returns status Forbidden if denied access to person`() = testApplication {
            val client = setupApiAndClient()
            val getResponse = client.get(urlVedtak) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS.value)
            }.status
            assertEquals(HttpStatusCode.Forbidden, getResponse)
            val postResponse = client.post(
                urlVedtak
            ) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS.value)
            }.status
            assertEquals(HttpStatusCode.Forbidden, postResponse)
        }

        @Test
        fun `Returns status BadRequest if no NAV_PERSONIDENT_HEADER is supplied`() = testApplication {
            val client = setupApiAndClient()
            assertEquals(HttpStatusCode.BadRequest, client.get(urlVedtak) { bearerAuth(validToken) }.status)
            assertEquals(HttpStatusCode.BadRequest, client.post(urlVedtak) { bearerAuth(validToken) }.status)
        }

        @Test
        fun `Returns status BadRequest if NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied`() = testApplication {
            val invalidPersonident = UserConstants.ARBEIDSTAKER_PERSONIDENT.value.drop(1)
            val client = setupApiAndClient()
            val getReponse = client.get(urlVedtak) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, invalidPersonident)
            }.status
            assertEquals(HttpStatusCode.BadRequest, getReponse)
            val postResponse = client.post(urlVedtak) {
                bearerAuth(validToken)
                header(NAV_PERSONIDENT_HEADER, invalidPersonident)
            }.status
            assertEquals(HttpStatusCode.BadRequest, postResponse)
        }
    }
}
