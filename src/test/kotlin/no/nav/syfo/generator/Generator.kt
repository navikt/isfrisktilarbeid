package no.nav.syfo.generator

import no.nav.syfo.UserConstants
import no.nav.syfo.common.journalforing.dto.AvsenderMottaker
import no.nav.syfo.common.journalforing.dto.Bruker
import no.nav.syfo.common.journalforing.dto.BrukerIdType
import no.nav.syfo.common.journalforing.dto.Dokument
import no.nav.syfo.common.journalforing.dto.Dokumentvariant
import no.nav.syfo.common.journalforing.dto.Filtype
import no.nav.syfo.common.journalforing.dto.JournalpostKanal
import no.nav.syfo.common.journalforing.dto.JournalpostRequest
import no.nav.syfo.common.journalforing.dto.JournalpostType
import no.nav.syfo.common.journalforing.dto.Variantformat
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Vedtak
import no.nav.syfo.infrastructure.journalforing.BrevkodeType
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

fun generateKorrigering(
    korrigertVedtak: Vedtak = generateVedtak(),
    begrunnelse: String = "En korrigert begrunnelse",
    document: List<DocumentComponent> = generateDocumentComponent(begrunnelse),
    fom: LocalDate = LocalDate.now(),
    tom: LocalDate = LocalDate.now().plusWeeks(10),
): Vedtak = Vedtak.createKorrigering(
    korrigertVedtak = korrigertVedtak,
    veilederident = UserConstants.VEILEDER_IDENT,
    begrunnelse = begrunnelse,
    document = document,
    fom = fom,
    tom = tom,
)

fun generateJournalpostRequest(
    tittel: String,
    brevkodeType: BrevkodeType,
    pdf: ByteArray,
    eksternReferanse: UUID,
    mottakerPersonident: Personident,
    mottakerNavn: String,
    brukerPersonident: Personident,
) = JournalpostRequest(
    avsenderMottaker = AvsenderMottaker(
        id = mottakerPersonident.value,
        idType = BrukerIdType.PERSONIDENT.value,
        navn = mottakerNavn,
    ),
    bruker = Bruker(
        id = brukerPersonident.value,
        idType = BrukerIdType.PERSONIDENT.value,
    ),
    tittel = tittel,
    dokumenter = listOf(
        Dokument(
            brevkode = brevkodeType.value,
            tittel = tittel,
            dokumentvarianter = listOf(
                Dokumentvariant(
                    filnavn = tittel,
                    filtype = Filtype.PDFA.value,
                    fysiskDokument = pdf,
                    variantformat = Variantformat.ARKIV.value,
                )
            ),
        )
    ),
    journalpostType = JournalpostType.UTGAAENDE.value,
    kanal = JournalpostKanal.DITT_NAV.value,
    eksternReferanseId = eksternReferanse.toString(),
)
