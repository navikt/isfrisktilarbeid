package no.nav.syfo.api

import io.ktor.server.application.*
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.application.VedtakService
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.pdf.PdfService

fun Application.testApiModule(
    externalMockEnvironment: ExternalMockEnvironment,
) {
    val database = externalMockEnvironment.database
    val veilederTilgangskontrollClient = VeilederTilgangskontrollClient(
        azureAdClient = externalMockEnvironment.azureAdClient,
        clientEnvironment = externalMockEnvironment.environment.clients.istilgangskontroll,
        httpClient = externalMockEnvironment.mockHttpClient,
    )
    val pdfService = PdfService(
        pdfGenClient = externalMockEnvironment.pdfgenClient,
        pdlClient = externalMockEnvironment.pdlClient,
    )
    val vedtakService = VedtakService(
        vedtakRepository = VedtakRepository(database = database),
        pdfService = pdfService,
        journalforingService = JournalforingService(),
    )

    this.apiModule(
        applicationState = externalMockEnvironment.applicationState,
        environment = externalMockEnvironment.environment,
        wellKnownInternalAzureAD = externalMockEnvironment.wellKnownInternalAzureAD,
        database = database,
        veilederTilgangskontrollClient = veilederTilgangskontrollClient,
        vedtakService = vedtakService,
    )
}
