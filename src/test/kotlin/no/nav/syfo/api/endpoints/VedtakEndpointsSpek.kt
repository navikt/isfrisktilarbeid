package no.nav.syfo.api.endpoints

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.http.*
import io.ktor.server.testing.*
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.UserConstants.PDF_VEDTAK
import no.nav.syfo.api.*
import no.nav.syfo.api.model.VedtakRequestDTO
import no.nav.syfo.api.model.VedtakResponseDTO
import no.nav.syfo.application.VedtakService
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.bearerHeader
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getVedtak
import no.nav.syfo.infrastructure.database.getVedtakPdf
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import no.nav.syfo.infrastructure.infotrygd.InfotrygdService
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.kafka.esyfovarsel.EsyfovarselHendelseProducer
import no.nav.syfo.infrastructure.pdf.PdfService
import no.nav.syfo.util.configuredJacksonMapper
import org.amshove.kluent.shouldBeAfter
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.util.*

object VedtakEndpointsSpek : Spek({

    val objectMapper: ObjectMapper = configuredJacksonMapper()
    val urlVedtak = "$apiBasePath/$vedtakPath"

    describe("VedtakEndpoints") {
        with(TestApplicationEngine()) {
            start()

            val externalMockEnvironment = ExternalMockEnvironment.instance
            val database = externalMockEnvironment.database

            val validToken = generateJWT(
                audience = externalMockEnvironment.environment.azure.appClientId,
                issuer = externalMockEnvironment.wellKnownInternalAzureAD.issuer,
                navIdent = UserConstants.VEILEDER_IDENT,
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
                behandlerRef = UUID.randomUUID(),
                behandlerNavn = "Beate Behandler",
                behandlerDocument = generateDocumentComponent("Til orientering", header = "Informasjon om vedtak"),
            )
            val vedtakService = VedtakService(
                pdfService = PdfService(externalMockEnvironment.pdfgenClient, externalMockEnvironment.pdlClient),
                vedtakRepository = VedtakRepository(database),
                journalforingService = mockk<JournalforingService>(relaxed = true),
                infotrygdService = mockk<InfotrygdService>(relaxed = true),
                esyfovarselHendelseProducer = mockk<EsyfovarselHendelseProducer>(relaxed = true),
            )
            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
            )

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
                    behandlerRef = requestDTO.behandlerRef,
                    behandlerNavn = requestDTO.behandlerNavn,
                    behandlerDocument = requestDTO.behandlerDocument,
                )
            }

            beforeEachTest {
                database.dropData()
            }

            describe("POST vedtak") {
                describe("Happy path") {
                    it("Successfully gets empty list of vedtak") {
                        with(
                            handleRequest(HttpMethod.Get, urlVedtak) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personident.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val responseDTOs = objectMapper.readValue<List<VedtakResponseDTO>>(response.content!!)
                            responseDTOs.size shouldBeEqualTo 0
                        }
                    }

                    it("Successfully gets list of single vedtak") {
                        createVedtak(vedtakRequestDTO)
                        with(
                            handleRequest(HttpMethod.Get, urlVedtak) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personident.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val vedtakResponse = objectMapper.readValue<List<VedtakResponseDTO>>(response.content!!)
                            vedtakResponse.size shouldBeEqualTo 1

                            vedtakResponse[0].begrunnelse shouldBeEqualTo begrunnelse
                            vedtakResponse[0].document shouldBeEqualTo vedtakDocument
                            vedtakResponse[0].fom shouldBeEqualTo vedtakFom
                            vedtakResponse[0].tom shouldBeEqualTo vedtakTom
                            vedtakResponse[0].personident shouldBeEqualTo personident.value
                            vedtakResponse[0].veilederident shouldBeEqualTo UserConstants.VEILEDER_IDENT

                            val vedtakPdf = database.getVedtakPdf(vedtakUuid = vedtakResponse[0].uuid)?.pdf!!
                            vedtakPdf.size shouldBeEqualTo PDF_VEDTAK.size
                            vedtakPdf[0] shouldBeEqualTo PDF_VEDTAK[0]
                            vedtakPdf[1] shouldBeEqualTo PDF_VEDTAK[1]

                            val pVedtak = database.getVedtak(vedtakUuid = vedtakResponse[0].uuid)!!
                            pVedtak.uuid shouldBeEqualTo vedtakResponse[0].uuid
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

                        with(
                            handleRequest(HttpMethod.Get, urlVedtak) {
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personident.value)
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.OK

                            val vedtakResponse = objectMapper.readValue<List<VedtakResponseDTO>>(response.content!!)
                            vedtakResponse.size shouldBeEqualTo 2
                            vedtakResponse[0].createdAt shouldBeAfter vedtakResponse[1].createdAt
                        }
                    }

                    it("Creates vedtak") {
                        with(
                            handleRequest(HttpMethod.Post, urlVedtak) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personident.value)
                                setBody(objectMapper.writeValueAsString(vedtakRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created

                            val vedtakResponse = objectMapper.readValue(response.content, VedtakResponseDTO::class.java)

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

                            val pVedtak = database.getVedtak(vedtakUuid = vedtakResponse.uuid)!!
                            pVedtak.uuid shouldBeEqualTo vedtakResponse.uuid
                        }
                    }
                }

                describe("Unhappy path") {
                    it("Throws error when document is empty") {
                        val vedtakWithoutDocument = vedtakRequestDTO.copy(document = emptyList())
                        with(
                            handleRequest(HttpMethod.Post, urlVedtak) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personident.value)
                                setBody(objectMapper.writeValueAsString(vedtakWithoutDocument))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("Throws error when begrunnelse is empty") {
                        val vedtakWithoutBegrunnelse = vedtakRequestDTO.copy(begrunnelse = "")
                        with(
                            handleRequest(HttpMethod.Post, urlVedtak) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personident.value)
                                setBody(objectMapper.writeValueAsString(vedtakWithoutBegrunnelse))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("Returns status Unauthorized if no token is supplied") {
                        testMissingToken(urlVedtak, HttpMethod.Get)
                        testMissingToken(urlVedtak, HttpMethod.Post)
                    }
                    it("Returns status Forbidden if denied access to person") {
                        testDeniedPersonAccess(urlVedtak, validToken, HttpMethod.Get)
                        testDeniedPersonAccess(urlVedtak, validToken, HttpMethod.Post)
                    }
                    it("Returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        testMissingPersonIdent(urlVedtak, validToken, HttpMethod.Get)
                        testMissingPersonIdent(urlVedtak, validToken, HttpMethod.Post)
                    }
                    it("Returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        testInvalidPersonIdent(urlVedtak, validToken, HttpMethod.Get)
                        testInvalidPersonIdent(urlVedtak, validToken, HttpMethod.Post)
                    }
                }
            }
        }
    }
})
