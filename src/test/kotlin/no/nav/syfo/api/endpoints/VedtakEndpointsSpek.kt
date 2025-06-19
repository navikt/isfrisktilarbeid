package no.nav.syfo.api.endpoints

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.testing.*
import io.mockk.*
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
import no.nav.syfo.infrastructure.oppgave.OppgaveService
import no.nav.syfo.infrastructure.pdf.PdfService
import no.nav.syfo.testAppState
import no.nav.syfo.util.configure
import org.amshove.kluent.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.*

object VedtakEndpointsSpek : Spek({

    val urlVedtak = "$apiBasePath/$vedtakPath"
    val urlVilkar = "$apiBasePath/$vilkarPath"

    describe("VedtakEndpoints") {
        val externalMockEnvironment = ExternalMockEnvironment.instance
        val database = externalMockEnvironment.database

        val validToken = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT,
        )
        val validTokenOther = generateJWT(
            audience = externalMockEnvironment.environment.azure.appClientId,
            issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
            navIdent = UserConstants.VEILEDER_IDENT_OTHER,
        )
        val personident = UserConstants.ARBEIDSTAKER_PERSONIDENT

        val begrunnelse = "Dette er en begrunnelse for vedtak 8-5"
        val vedtakDocument = generateDocumentComponent(
            fritekst = begrunnelse,
            header = "Vedtak"
        )
        val vedtakFom = LocalDate.now()
        val vedtakTom = LocalDate.now().plusWeeks(12)
        val vedtakRequestDTO = VedtakRequestDTO(
            document = vedtakDocument,
            begrunnelse = begrunnelse,
            fom = vedtakFom,
            tom = vedtakTom,
        )
        val vedtakRepository = VedtakRepository(database)
        val vedtakService = VedtakService(
            pdfService = PdfService(externalMockEnvironment.pdfgenClient, externalMockEnvironment.pdlClient),
            vedtakRepository = vedtakRepository,
            journalforingService = mockk<JournalforingService>(relaxed = true),
            oppgaveService = mockk<OppgaveService>(relaxed = true),
            infotrygdService = mockk<InfotrygdService>(relaxed = true),
            vedtakProducer = mockk<IVedtakProducer>(relaxed = true),
        )
        val infotrygdMQSender = mockk<InfotrygdMQSender>(relaxed = true)

        fun ApplicationTestBuilder.setupApiAndClient(): HttpClient {
            application {
                testApiModule(
                    externalMockEnvironment = externalMockEnvironment,
                    infotrygdMQSender = infotrygdMQSender,
                )
            }
            val client = createClient {
                install(ContentNegotiation) {
                    jackson { configure() }
                }
            }

            return client
        }

        fun createVedtak(
            requestDTO: VedtakRequestDTO,
        ) = runBlocking {
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
        fun mockInfotrygdKvitteringReceived(kvitteringOk: Boolean) {
            GlobalScope.launch {
                try {
                    withTimeout(1000) {
                        while (testAppState().ready) {
                            val vedtak =
                                vedtakRepository.getVedtak(personident = UserConstants.ARBEIDSTAKER_PERSONIDENT)
                                    .firstOrNull()
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
                } catch (e: TimeoutCancellationException) {
                    // Ignored
                }
            }
        }

        beforeEachTest {
            clearAllMocks()
            justRun { infotrygdMQSender.sendToMQ(any(), any()) }
            database.dropData()
        }

        describe("GET vedtak") {
            it("Successfully gets empty list of vedtak") {
                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get(urlVedtak) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val responseDTOs = response.body<List<VedtakResponseDTO>>()
                    responseDTOs.size shouldBeEqualTo 0
                }
            }
            it("Successfully gets list of single vedtak") {
                createVedtak(vedtakRequestDTO)
                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get(urlVedtak) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val vedtakResponse = response.body<List<VedtakResponseDTO>>()
                    vedtakResponse.size shouldBeEqualTo 1

                    vedtakResponse[0].begrunnelse shouldBeEqualTo begrunnelse
                    vedtakResponse[0].document shouldBeEqualTo vedtakDocument
                    vedtakResponse[0].fom shouldBeEqualTo vedtakFom
                    vedtakResponse[0].tom shouldBeEqualTo vedtakTom
                    vedtakResponse[0].personident shouldBeEqualTo personident.value
                    vedtakResponse[0].veilederident shouldBeEqualTo UserConstants.VEILEDER_IDENT
                    vedtakResponse[0].infotrygdStatus shouldBeEqualTo InfotrygdStatus.IKKE_SENDT.name

                    val vedtakPdf = database.getVedtakPdf(vedtakUuid = vedtakResponse[0].uuid)?.pdf!!
                    vedtakPdf.size shouldBeEqualTo PDF_VEDTAK.size
                    vedtakPdf[0] shouldBeEqualTo PDF_VEDTAK[0]
                    vedtakPdf[1] shouldBeEqualTo PDF_VEDTAK[1]

                    val vedtak = vedtakRepository.getVedtak(vedtakResponse[0].uuid)
                    vedtak.uuid shouldBeEqualTo vedtakResponse[0].uuid
                }
            }
            it("Successfully gets list of multiple vedtak") {
                createVedtak(
                    vedtakRequestDTO.copy(
                        fom = vedtakRequestDTO.fom.minusYears(1),
                        tom = vedtakRequestDTO.tom.minusYears(1),
                    )
                )
                createVedtak(vedtakRequestDTO)

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.get(urlVedtak) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.OK

                    val vedtakResponse = response.body<List<VedtakResponseDTO>>()
                    vedtakResponse.size shouldBeEqualTo 2
                    vedtakResponse[0].createdAt shouldBeAfter vedtakResponse[1].createdAt
                }
            }
        }

        describe("Vilkar") {
            it("Vilkar when person is not arbeidssoker") {
                testApplication {
                    val client = setupApiAndClient()

                    val response = client.get(urlVilkar) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT_UTLAND.value)
                        setBody(vedtakRequestDTO)
                    }
                    response.status shouldBeEqualTo HttpStatusCode.OK
                    val vilkarResponse = response.body<VilkarResponseDTO>()
                    vilkarResponse.isArbeidssoker shouldBeEqualTo false
                }
            }
            it("Vilkar when person is arbeidssoker") {
                testApplication {
                    val client = setupApiAndClient()

                    val response = client.get(urlVilkar) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                        setBody(vedtakRequestDTO)
                    }
                    response.status shouldBeEqualTo HttpStatusCode.OK
                    val vilkarResponse = response.body<VilkarResponseDTO>()
                    vilkarResponse.isArbeidssoker shouldBeEqualTo true
                }
            }
        }

        describe("POST vedtak") {
            it("Creates vedtak") {
                testApplication {
                    val client = setupApiAndClient()
                    val response = client.post(urlVedtak) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                        setBody(vedtakRequestDTO)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.Created

                    val vedtakResponse = response.body<VedtakResponseDTO>()

                    vedtakResponse.begrunnelse shouldBeEqualTo begrunnelse
                    vedtakResponse.document shouldBeEqualTo vedtakDocument
                    vedtakResponse.fom shouldBeEqualTo vedtakFom
                    vedtakResponse.tom shouldBeEqualTo vedtakTom
                    vedtakResponse.personident shouldBeEqualTo personident.value
                    vedtakResponse.veilederident shouldBeEqualTo UserConstants.VEILEDER_IDENT

                    val vedtakPdf = database.getVedtakPdf(vedtakUuid = vedtakResponse.uuid)?.pdf!!
                    vedtakPdf.size shouldBeEqualTo PDF_VEDTAK.size
                    vedtakPdf[0] shouldBeEqualTo PDF_VEDTAK[0]
                    vedtakPdf[1] shouldBeEqualTo PDF_VEDTAK[1]

                    val vedtak = vedtakRepository.getVedtak(vedtakResponse.uuid)
                    vedtak.uuid shouldBeEqualTo vedtakResponse.uuid
                }
            }

            it("Creates vedtak back in time") {
                testApplication {
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

                    response.status shouldBeEqualTo HttpStatusCode.Created
                }
            }

            it("Error when tom-date is before fom-date") {
                testApplication {
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
                    response.status shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }
            it("Error when person is not arbeidssoker") {
                testApplication {
                    val client = setupApiAndClient()

                    val response = client.post(urlVedtak) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT_UTLAND.value)
                        setBody(vedtakRequestDTO)
                    }
                    response.status shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }
            it("Creates vedtak and publish to infotrygd success") {
                testApplication {
                    val client = setupApiAndClient()
                    val response = client.post(urlVedtak) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                        setBody(vedtakRequestDTO)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.Created

                    verify(exactly = 1) { infotrygdMQSender.sendToMQ(any(), any()) }

                    val vedtakResponse = response.body<VedtakResponseDTO>()
                    vedtakResponse.infotrygdStatus shouldBeEqualTo InfotrygdStatus.KVITTERING_MANGLER.name
                }
            }

            it("Creates vedtak and publish to infotrygd fails") {
                every { infotrygdMQSender.sendToMQ(any(), any()) } throws Exception("Error sending to infotrygd")

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.post(urlVedtak) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                        setBody(vedtakRequestDTO)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.Created

                    verify(exactly = 1) { infotrygdMQSender.sendToMQ(any(), any()) }

                    val vedtakResponse = response.body<VedtakResponseDTO>()
                    vedtakResponse.infotrygdStatus shouldBeEqualTo InfotrygdStatus.IKKE_SENDT.name
                }
            }

            it("Creates vedtak and publish to infotrygd success with kvittering OK") {
                mockInfotrygdKvitteringReceived(kvitteringOk = true)

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.post(urlVedtak) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                        setBody(vedtakRequestDTO)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.Created

                    verify(exactly = 1) { infotrygdMQSender.sendToMQ(any(), any()) }

                    val vedtakResponse = response.body<VedtakResponseDTO>()
                    vedtakResponse.infotrygdStatus shouldBeEqualTo InfotrygdStatus.KVITTERING_OK.name
                }
            }

            it("Creates vedtak and publish to infotrygd success with kvittering not OK") {
                mockInfotrygdKvitteringReceived(kvitteringOk = false)

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.post(urlVedtak) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                        setBody(vedtakRequestDTO)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.Created

                    verify(exactly = 1) { infotrygdMQSender.sendToMQ(any(), any()) }

                    val vedtakResponse = response.body<VedtakResponseDTO>()
                    vedtakResponse.infotrygdStatus shouldBeEqualTo InfotrygdStatus.KVITTERING_FEIL.name
                }
            }

            it("Throws error when document is empty") {
                val vedtakWithoutDocument = vedtakRequestDTO.copy(document = emptyList())

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.post(urlVedtak) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                        setBody(vedtakWithoutDocument)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }
            it("Throws error when begrunnelse is empty") {
                val vedtakWithoutBegrunnelse = vedtakRequestDTO.copy(begrunnelse = "")

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.post(urlVedtak) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                        setBody(vedtakWithoutBegrunnelse)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }
            it("Returns status Conflict when vedtak not ferdigbehandlet exists") {
                createVedtak(vedtakRequestDTO)

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.post(urlVedtak) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                        setBody(vedtakRequestDTO)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.Conflict
                }
            }
            it("Returns status Conflict when overlapping vedtak exists") {
                val vedtak = createVedtak(vedtakRequestDTO)
                vedtakService.ferdigbehandleVedtak(vedtak, UserConstants.VEILEDER_IDENT)

                val newRequest = vedtakRequestDTO.copy(
                    fom = vedtakRequestDTO.fom.plusDays(1),
                    tom = vedtakRequestDTO.tom.plusDays(1),
                )

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.post(urlVedtak) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                        setBody(newRequest)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.Conflict
                }
            }
            it("Does not return status Conflict when non-overlapping vedtak exists") {
                val vedtak = createVedtak(vedtakRequestDTO)
                vedtakService.ferdigbehandleVedtak(vedtak, UserConstants.VEILEDER_IDENT)

                val newRequest = vedtakRequestDTO.copy(
                    fom = vedtakRequestDTO.tom.plusDays(1),
                    tom = vedtakRequestDTO.tom.plusDays(10),
                )

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.post(urlVedtak) {
                        contentType(ContentType.Application.Json)
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                        setBody(newRequest)
                    }

                    response.status shouldBeEqualTo HttpStatusCode.Created
                }
            }
        }

        describe("PUT vedtak ferdigbehandlet") {
            it("Sets vedtak ferdigbehandlet and updates veileder") {
                val vedtak = createVedtak(vedtakRequestDTO)

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.put("$urlVedtak/${vedtak.uuid}/ferdigbehandling") {
                        bearerAuth(validTokenOther)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                    }
                    response.status shouldBeEqualTo HttpStatusCode.OK
                    val vedtakResponse = response.body<VedtakResponseDTO>()
                    vedtakResponse.uuid shouldBeEqualTo vedtak.uuid
                    vedtakResponse.ferdigbehandletAt shouldNotBe null
                    vedtakResponse.ferdigbehandletBy shouldBeEqualTo UserConstants.VEILEDER_IDENT_OTHER

                    val vedtakPersisted = vedtakRepository.getVedtak(vedtak.uuid)

                    vedtakPersisted.isFerdigbehandlet() shouldBe true
                    vedtakPersisted.getFerdigbehandletStatus()!!.veilederident shouldBeEqualTo UserConstants.VEILEDER_IDENT_OTHER
                }
            }
            it("Throws error when ferdigstiller unknown vedtak") {
                testApplication {
                    val client = setupApiAndClient()
                    val response = client.put("$urlVedtak/${UUID.randomUUID()}/ferdigbehandling") {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                    }
                    response.status shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }
            it("Throws error when ferdigbehandler already ferdigbehandlet vedtak") {
                val vedtak = createVedtak(vedtakRequestDTO)
                vedtakService.ferdigbehandleVedtak(vedtak, UserConstants.VEILEDER_IDENT)

                testApplication {
                    val client = setupApiAndClient()
                    val response = client.put("$urlVedtak/${vedtak.uuid}/ferdigbehandling") {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, personident.value)
                    }
                    response.status shouldBeEqualTo HttpStatusCode.BadRequest
                }
            }
        }

        describe("Unhappy paths") {
            it("Returns status Unauthorized if no token is supplied") {
                testApplication {
                    val client = setupApiAndClient()
                    client.get(urlVedtak).apply {
                        status shouldBeEqualTo HttpStatusCode.Unauthorized
                    }

                    client.post(urlVedtak).apply {
                        status shouldBeEqualTo HttpStatusCode.Unauthorized
                    }
                }
            }
            it("Returns status Forbidden if denied access to person") {
                testApplication {
                    val client = setupApiAndClient()
                    client.get(urlVedtak) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS.value)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.Forbidden
                    }

                    client.post(urlVedtak) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, UserConstants.ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS.value)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.Forbidden
                    }
                }
            }
            it("Returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                testApplication {
                    val client = setupApiAndClient()
                    client.get(urlVedtak) {
                        bearerAuth(validToken)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.BadRequest
                    }

                    client.post(urlVedtak) {
                        bearerAuth(validToken)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
            }
            it("Returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                val invalidPersonident = UserConstants.ARBEIDSTAKER_PERSONIDENT.value.drop(1)
                testApplication {
                    val client = setupApiAndClient()
                    client.get(urlVedtak) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, invalidPersonident)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.BadRequest
                    }

                    client.post(urlVedtak) {
                        bearerAuth(validToken)
                        header(NAV_PERSONIDENT_HEADER, invalidPersonident)
                    }.apply {
                        status shouldBeEqualTo HttpStatusCode.BadRequest
                    }
                }
            }
        }
    }
})
