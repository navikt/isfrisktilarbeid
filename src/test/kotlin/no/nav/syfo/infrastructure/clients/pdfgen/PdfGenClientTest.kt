package no.nav.syfo.infrastructure.clients.pdfgen

import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateDocumentComponent
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month

class PdfGenClientTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val pdfGenClient = externalMockEnvironment.pdfgenClient

    @Test
    fun `returns bytearray of pdf for vedtak`() {
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
        assertArrayEquals(UserConstants.PDF_VEDTAK, pdf)
    }

    @Test
    fun `Ingen ledende 0`() {
        val pdf = PdfModel.VedtakPdfModel(
            mottakerFodselsnummer = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            mottakerNavn = UserConstants.PERSON_FULLNAME_DASH,
            documentComponents = generateDocumentComponent("Litt fritekst"),
            datoSendt = LocalDate.of(2025, Month.JUNE, 4)
        )
        assertEquals("4. juni 2025", pdf.datoSendt)
    }

    @Test
    fun `Dato med to sifre formateres med to sifre`() {
        val pdf = PdfModel.VedtakPdfModel(
            mottakerFodselsnummer = UserConstants.ARBEIDSTAKER_PERSONIDENT,
            mottakerNavn = UserConstants.PERSON_FULLNAME_DASH,
            documentComponents = generateDocumentComponent("Litt fritekst"),
            datoSendt = LocalDate.of(2025, Month.JUNE, 14)
        )
        assertEquals("14. juni 2025", pdf.datoSendt)
    }
}
