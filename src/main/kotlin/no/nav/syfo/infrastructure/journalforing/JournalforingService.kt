package no.nav.syfo.infrastructure.journalforing

import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.clients.behandler.DialogmeldingBehandlerClient
import no.nav.syfo.infrastructure.clients.behandler.fullName
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.*
import no.nav.syfo.infrastructure.clients.pdl.PdlClient

class JournalforingService(
    private val dokarkivClient: DokarkivClient,
    private val pdlClient: PdlClient,
    private val dialogmeldingBehandlerClient: DialogmeldingBehandlerClient
) : IJournalforingService {

    override suspend fun journalfor(vedtak: Vedtak, pdf: ByteArray): Result<JournalpostId> = runCatching {
        val navn = pdlClient.getPerson(vedtak.personident).fullName
        val journalpostRequest = createJournalpostRequest(
            vedtak = vedtak,
            navn = navn,
            pdf = pdf
        )

        journalfor(journalpostRequest)
    }

    override suspend fun journalfor(personident: Personident, behandlermelding: Behandlermelding, pdf: ByteArray): Result<JournalpostId> = runCatching {
        val behandler = dialogmeldingBehandlerClient.getBehandler(behandlermelding.behandlerRef) ?: throw RuntimeException("Failed to get behander from isdialogmelding")
        val behandlerFnr = behandler.fnr ?: throw RuntimeException("Behandler missing fnr")
        val behandlerNavn = behandler.fullName()

        val journalpostRequest = createJournalpostRequest(personident, behandlermelding, pdf, behandlerFnr, behandlerNavn)

        journalfor(journalpostRequest)
    }

    private suspend fun journalfor(journalpostRequest: JournalpostRequest): JournalpostId {
        val journalpostId = dokarkivClient.journalfor(journalpostRequest).journalpostId

        return JournalpostId(journalpostId.toString())
    }

    private fun createJournalpostRequest(
        personident: Personident,
        behandlermelding: Behandlermelding,
        pdf: ByteArray,
        behandlerFnr: String,
        behandlerNavn: String
    ): JournalpostRequest {
        val avsenderMottaker = AvsenderMottaker.create(
            id = behandlerFnr,
            idType = BrukerIdType.PERSON_IDENT,
            navn = behandlerNavn,
        )
        val bruker = Bruker.create(
            id = personident.value,
            idType = BrukerIdType.PERSON_IDENT,
        )

        val tittel = "Melding til behandler om friskmelding til arbeidsformidling"
        val dokumenter = listOf(
            createPdfDokument(
                pdf = pdf,
                brevkode = BrevkodeType.BEHANDLERMELDING_FRISKMELDING_TIL_ARBEIDSFORMIDLING,
                tittel = tittel
            ),
        )

        return JournalpostRequest(
            avsenderMottaker = avsenderMottaker,
            tittel = tittel,
            bruker = bruker,
            dokumenter = dokumenter,
            kanal = JournalpostKanal.HELSENETTET.name,
            overstyrInnsynsregler = OverstyrInnsynsregler.VISES_MASKINELT_GODKJENT.name,
            eksternReferanseId = behandlermelding.uuid.toString(),
        )
    }

    private fun createJournalpostRequest(
        vedtak: Vedtak,
        navn: String,
        pdf: ByteArray,
    ): JournalpostRequest {
        val avsenderMottaker = AvsenderMottaker.create(
            id = vedtak.personident.value,
            idType = BrukerIdType.PERSON_IDENT,
            navn = navn,
        )
        val bruker = Bruker.create(
            id = vedtak.personident.value,
            idType = BrukerIdType.PERSON_IDENT,
        )

        val tittel = "Vedtak om friskmelding til arbeidsformidling"
        val dokumenter = listOf(
            createPdfDokument(
                pdf = pdf,
                brevkode = BrevkodeType.VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING,
                tittel = tittel
            ),
        )

        return JournalpostRequest(
            avsenderMottaker = avsenderMottaker,
            tittel = tittel,
            bruker = bruker,
            dokumenter = dokumenter,
            eksternReferanseId = vedtak.uuid.toString(),
        )
    }

    private fun createPdfDokument(pdf: ByteArray, brevkode: BrevkodeType, tittel: String): Dokument =
        Dokument.create(
            brevkode = brevkode,
            dokumentvarianter = listOf(
                Dokumentvariant.create(
                    filnavn = tittel,
                    filtype = FiltypeType.PDFA,
                    fysiskDokument = pdf,
                    variantformat = VariantformatType.ARKIV,
                )
            ),
            tittel = tittel,
        )
}
