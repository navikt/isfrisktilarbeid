package no.nav.syfo.infrastructure.journalforing

import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.*
import no.nav.syfo.infrastructure.clients.pdl.PdlClient

class JournalforingService(
    private val dokarkivClient: DokarkivClient,
    private val pdlClient: PdlClient
) : IJournalforingService {

    override suspend fun journalfor(vedtak: Vedtak, pdf: ByteArray): Result<JournalpostId> = runCatching {
        val navn = pdlClient.getPerson(vedtak.personident).fullName
        val journalpostRequest = createJournalpostRequest(
            vedtak = vedtak,
            navn = navn,
            pdf = pdf
        )

        val journalpostId = dokarkivClient.journalfor(journalpostRequest).journalpostId
        JournalpostId(journalpostId.toString())
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
            Dokument.create(
                brevkode = BrevkodeType.VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING,
                dokumentvarianter = listOf(
                    Dokumentvariant.create(
                        filnavn = tittel,
                        filtype = FiltypeType.PDFA,
                        fysiskDokument = pdf,
                        variantformat = VariantformatType.ARKIV,
                    )
                ),
                tittel = tittel,
            ),
        )

        return JournalpostRequest(
            avsenderMottaker = avsenderMottaker,
            tittel = "Vedtak om friskmelding til arbeidsformidling",
            bruker = bruker,
            dokumenter = dokumenter,
            eksternReferanseId = vedtak.uuid.toString(),
        )
    }
}
