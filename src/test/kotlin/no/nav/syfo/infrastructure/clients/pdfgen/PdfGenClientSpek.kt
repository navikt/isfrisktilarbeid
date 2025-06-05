package no.nav.syfo.infrastructure.clients.pdfgen

import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateDocumentComponent
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.Month

class PdfGenClientSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val pdfGenClient = externalMockEnvironment.pdfgenClient

    describe(PdfGenClient::class.java.simpleName) {
        it("returns bytearray of pdf for vedtak") {
            val pdf = runBlocking {
                pdfGenClient.createVedtakPdf(
                    callId = "",
                    payload = PdfModel.VedtakPdfModel(
                        mottakerFodselsnummer = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                        mottakerNavn = UserConstants.PERSON_FULLNAME_DASH,
                        documentComponents = generateDocumentComponent("Litt fritekst"),
                    )
                )
            }

            pdf shouldBeEqualTo UserConstants.PDF_VEDTAK
        }

        it("Ingen ledende 0 i datoSendt") {
            val pdf = PdfModel.VedtakPdfModel(
                mottakerFodselsnummer = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                mottakerNavn = UserConstants.PERSON_FULLNAME_DASH,
                documentComponents = generateDocumentComponent("Litt fritekst"),
                datoSendt = LocalDate.of(2025, Month.JUNE, 4)
            )

            pdf.datoSendt shouldBeEqualTo "4. juni 2025"
        }
    }
})
