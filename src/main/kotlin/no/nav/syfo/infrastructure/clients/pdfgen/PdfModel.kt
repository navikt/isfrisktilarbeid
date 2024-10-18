package no.nav.syfo.infrastructure.clients.pdfgen

import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.sanitizeForPdfGen
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

sealed class PdfModel private constructor(
    val mottakerFodselsnummer: String?,
    val mottakerNavn: String,
    val datoSendt: String,
    val documentComponents: List<DocumentComponent>,
) {
    private constructor(
        mottakerFodselsnummer: String?,
        mottakerNavn: String,
        documentComponents: List<DocumentComponent>
    ) : this(
        mottakerFodselsnummer = mottakerFodselsnummer,
        mottakerNavn = mottakerNavn,
        datoSendt = LocalDate.now().format(formatter),
        documentComponents = documentComponents.sanitizeForPdfGen()
    )

    class VedtakPdfModel(
        mottakerFodselsnummer: Personident,
        mottakerNavn: String,
        documentComponents: List<DocumentComponent>,
    ) : PdfModel(
        mottakerFodselsnummer = mottakerFodselsnummer.value,
        mottakerNavn = mottakerNavn,
        documentComponents = documentComponents,
    )

    companion object {
        private val formatter = DateTimeFormatter.ofPattern("dd. MMMM yyyy", Locale("no", "NO"))
    }
}
