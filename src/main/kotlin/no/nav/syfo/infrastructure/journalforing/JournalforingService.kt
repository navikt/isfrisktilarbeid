package no.nav.syfo.infrastructure.journalforing

import no.nav.syfo.application.IJournalforingService
import no.nav.syfo.common.journalforing.JournalpostMottaker
import no.nav.syfo.common.journalforing.client.DokarkivClient
import no.nav.syfo.common.journalforing.createJournalpostRequest
import no.nav.syfo.common.journalforing.dto.Bruker
import no.nav.syfo.common.journalforing.dto.BrukerIdType
import no.nav.syfo.common.journalforing.dto.JournalpostKanal
import no.nav.syfo.common.journalforing.dto.JournalpostRequest
import no.nav.syfo.common.journalforing.dto.JournalpostType
import no.nav.syfo.common.types.ident.Personident
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.Vedtak
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
            dokarkivClient.journalfor(journalpostRequest).journalpostId.value
        } catch (exc: Exception) {
            if (isJournalforingRetryEnabled) {
                throw exc
            } else {
                log.error("Journalføring failed, skipping retry (should only happen in dev-gcp)", exc)
                // Defaulting'en til DEFAULT_FAILED_JP_ID skal bare forekomme i dev-gcp:
                // Har dette fordi vi ellers spammer ned dokarkiv med forsøk på å journalføre
                // på personer som mangler aktør-id.
                DEFAULT_FAILED_JP_ID.toString()
            }
        }
        JournalpostId(journalpostId)
    }

    private fun createJournalpostRequest(
        vedtak: Vedtak,
        navn: String,
        pdf: ByteArray,
    ): JournalpostRequest {
        val tittel = if (vedtak.isKorrigering()) {
            "Korrigert vedtak om friskmelding til arbeidsformidling"
        } else {
            "Vedtak om friskmelding til arbeidsformidling"
        }

        return createJournalpostRequest(
            bruker = Bruker(
                id = vedtak.personident.value,
                idType = BrukerIdType.PERSONIDENT.value,
            ),
            brevkode = BrevkodeType.VEDTAK_FRISKMELDING_TIL_ARBEIDSFORMIDLING,
            tittel = tittel,
            pdf = pdf,
            eksternReferanseId = vedtak.uuid.toString(),
            journalpostType = JournalpostType.UTGAAENDE,
            mottaker = JournalpostMottaker.Person(
                personident = Personident(vedtak.personident.value),
                navn = navn,
            ),
            kanal = JournalpostKanal.DITT_NAV,
        )
    }

    companion object {
        const val DEFAULT_FAILED_JP_ID = 0
        private val log = LoggerFactory.getLogger(JournalforingService::class.java)
    }
}
