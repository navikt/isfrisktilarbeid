package no.nav.syfo.infrastructure.clients.pdfgen

import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class PdfGenClientSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val pdfGenClient = externalMockEnvironment.pdfgenClient

    describe(PdfGenClient::class.java.simpleName) {
        it("returns bytearray of pdf for vedtak") {
            val pdf = runBlocking {
                pdfGenClient.createVedtakPdf(
                    callId = "",
                    payload = "",
                )
            }

            pdf shouldBeEqualTo UserConstants.PDF_VEDTAK
        }
    }
})
