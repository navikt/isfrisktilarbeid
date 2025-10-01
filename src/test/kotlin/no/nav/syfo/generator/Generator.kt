package no.nav.syfo.generator

import no.nav.syfo.UserConstants
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.*
import java.time.LocalDate
import java.util.*

fun generateVedtak(
    personident: Personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    begrunnelse: String = "En begrunnelse",
    document: List<DocumentComponent> = generateDocumentComponent(begrunnelse),
): Vedtak = Vedtak(
    personident = personident,
    veilederident = UserConstants.VEILEDER_IDENT,
    begrunnelse = begrunnelse,
    document = document,
    fom = LocalDate.now(),
    tom = LocalDate.now().plusWeeks(12),
)

fun generateJournalpostRequest(
    tittel: String,
    brevkodeType: BrevkodeType,
    pdf: ByteArray,
    eksternReferanse: UUID,
    mottakerPersonident: Personident,
    mottakerNavn: String,
    brukerPersonident: Personident,
    overstyrInnsynsregler: OverstyrInnsynsregler? = null,
) = JournalpostRequest(
    avsenderMottaker = AvsenderMottaker.create(
        id = mottakerPersonident.value,
        idType = BrukerIdType.PERSON_IDENT,
        navn = mottakerNavn,
    ),
    bruker = Bruker.create(
        id = brukerPersonident.value,
        idType = BrukerIdType.PERSON_IDENT
    ),
    tittel = tittel,
    dokumenter = listOf(
        Dokument.create(
            brevkode = brevkodeType,
            tittel = tittel,
            dokumentvarianter = listOf(
                Dokumentvariant.create(
                    filnavn = tittel,
                    filtype = FiltypeType.PDFA,
                    fysiskDokument = pdf,
                    variantformat = VariantformatType.ARKIV,
                )
            ),
        )
    ),
    overstyrInnsynsregler = overstyrInnsynsregler?.name,
    journalpostType = JournalpostType.UTGAAENDE.name,
    eksternReferanseId = eksternReferanse.toString(),
)
