package no.nav.syfo.api.endpoints

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.*
import io.ktor.server.testing.*
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.api.*
import no.nav.syfo.api.model.VedtakRequestDTO
import no.nav.syfo.generator.generateDocumentComponent
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.bearerHeader
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.util.configuredJacksonMapper
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
            val personIdent = UserConstants.ARBEIDSTAKER_PERSONIDENT.value

            val begrunnelse = "Dette er en begrunnelse for vedtak 8-5"
            val vedtakDocument = generateDocumentComponent(
                fritekst = begrunnelse,
                header = "Vedtak"
            )
            val vedtakRequestDTO = VedtakRequestDTO(
                document = vedtakDocument,
                begrunnelse = begrunnelse,
                fom = LocalDate.now(),
                tom = LocalDate.now().plusWeeks(12),
                behandlerRef = UUID.randomUUID(),
                behandlerDocument = generateDocumentComponent("Til orientering", header = "Informasjon om vedtak"),
            )

            application.testApiModule(
                externalMockEnvironment = externalMockEnvironment,
            )

            beforeEachTest {
                database.dropData()
            }

            describe("POST vedtak") {
                describe("Happy path") {
                    it("Creates vedtak") {
                        with(
                            handleRequest(HttpMethod.Post, urlVedtak) {
                                addHeader(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                                addHeader(HttpHeaders.Authorization, bearerHeader(validToken))
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                                setBody(objectMapper.writeValueAsString(vedtakRequestDTO))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.Created
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
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
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
                                addHeader(NAV_PERSONIDENT_HEADER, personIdent)
                                setBody(objectMapper.writeValueAsString(vedtakWithoutBegrunnelse))
                            }
                        ) {
                            response.status() shouldBeEqualTo HttpStatusCode.BadRequest
                        }
                    }
                    it("Returns status Unauthorized if no token is supplied") {
                        testMissingToken(urlVedtak, HttpMethod.Post)
                    }
                    it("Returns status Forbidden if denied access to person") {
                        testDeniedPersonAccess(urlVedtak, validToken, HttpMethod.Post)
                    }
                    it("Returns status BadRequest if no $NAV_PERSONIDENT_HEADER is supplied") {
                        testMissingPersonIdent(urlVedtak, validToken, HttpMethod.Post)
                    }
                    it("Returns status BadRequest if $NAV_PERSONIDENT_HEADER with invalid PersonIdent is supplied") {
                        testInvalidPersonIdent(urlVedtak, validToken, HttpMethod.Post)
                    }
                }
            }
        }
    }
})
