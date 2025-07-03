package no.nav.syfo.infrastructure.journalforing

import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.*
import no.nav.syfo.infrastructure.clients.pdl.PdlClient
import org.slf4j.LoggerFactory

class JournalforingService(
    private val dokarkivClient: DokarkivClient,
    private val pdlClient: PdlClient,
    private val isJournalforingRetryEnabled: Boolean,
) : IJournalforingService {

    override suspend fun journalfor(vedtak: Vedtak, pdf: ByteArray): Result<JournalpostId> = runCatching {
        val navn = pdlClient.getPerson(vedtak.personident).fullName
        val journalpostRequest = createJournalpostRequest(
            vedtak = vedtak,
            navn = navn,
            pdf = pdf
        )

        val journalpostId = try {
            dokarkivClient.journalfor(journalpostRequest).journalpostId
        } catch (exc: Exception) {
            if (isJournalforingRetryEnabled) {
                throw exc
            } else {
                log.error("Journalføring failed, skipping retry (should only happen in dev-gcp)", exc)
                // Defaulting'en til DEFAULT_FAILED_JP_ID skal bare forekomme i dev-gcp:
                // Har dette fordi vi ellers spammer ned dokarkiv med forsøk på å journalføre
                // på personer som mangler aktør-id.
                DEFAULT_FAILED_JP_ID
            }
        }
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
    companion object {
        const val DEFAULT_FAILED_JP_ID = 0
        private val log = LoggerFactory.getLogger(JournalforingService::class.java)
    }
}
