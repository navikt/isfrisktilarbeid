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
        documentComponents: List<DocumentComponent>,
        datoSendt: LocalDate
    ) : this(
        mottakerFodselsnummer = mottakerFodselsnummer,
        mottakerNavn = mottakerNavn,
        datoSendt = datoSendt.format(formatter),
        documentComponents = documentComponents.sanitizeForPdfGen()
    )

    class VedtakPdfModel(
        mottakerFodselsnummer: Personident,
        mottakerNavn: String,
        documentComponents: List<DocumentComponent>,
        datoSendt: LocalDate = LocalDate.now(),
    ) : PdfModel(
        mottakerFodselsnummer = mottakerFodselsnummer.value,
        mottakerNavn = mottakerNavn,
        documentComponents = documentComponents,
        datoSendt = datoSendt,
    )

    companion object {
        private val formatter = DateTimeFormatter.ofPattern("d. MMMM yyyy", Locale("no", "NO"))
    }
}
