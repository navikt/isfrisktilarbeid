package no.nav.syfo

import no.nav.syfo.infrastructure.clients.arbeidssokeroppslag.ArbeidssokeroppslagClient
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.clients.gosysoppgave.GosysOppgaveClient
import no.nav.syfo.infrastructure.clients.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.clients.pdl.PdlClient
import no.nav.syfo.infrastructure.clients.wellknown.WellKnown
import no.nav.syfo.infrastructure.database.TestDatabase
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import no.nav.syfo.infrastructure.mock.mockHttpClient
import java.nio.file.Paths

fun wellKnownInternalAzureAD(): WellKnown {
    val path = "src/test/resources/jwkset.json"
    val uri = Paths.get(path).toUri().toURL()
    return WellKnown(
        issuer = "https://sts.issuer.net/veileder/v2",
        jwksUri = uri.toString()
    )
}

class ExternalMockEnvironment private constructor() {
    val applicationState: ApplicationState = testAppState()
    val database = TestDatabase()
    val environment = testEnvironment()
    val mockHttpClient = mockHttpClient(environment = environment)
    val wellKnownInternalAzureAD = wellKnownInternalAzureAD()
    val azureAdClient = AzureAdClient(
        azureEnvironment = environment.azure,
        httpClient = mockHttpClient,
    )
    val pdfgenClient = PdfGenClient(
        pdfGenBaseUrl = environment.clients.ispdfgen.baseUrl,
        httpClient = mockHttpClient,
    )
    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        pdlEnvironment = environment.clients.pdl,
        httpClient = mockHttpClient,
    )
    val dokarkivClient = DokarkivClient(
        azureAdClient = azureAdClient,
        dokarkivEnvironment = environment.clients.dokarkiv,
        httpClient = mockHttpClient,
    )
    val gosysOppgaveClient = GosysOppgaveClient(
        azureAdClient = azureAdClient,
        environment = environment.clients.gosysoppgave,
        httpClient = mockHttpClient,
    )
    val arbeidssokeroppslagClient = ArbeidssokeroppslagClient(
        azureAdClient = azureAdClient,
        clientEnvironment = environment.clients.arbeidssokeroppslag,
        httpClient = mockHttpClient,
    )
    val vedtakRepository = VedtakRepository(database = database)

    companion object {
        val instance: ExternalMockEnvironment = ExternalMockEnvironment()
    }
}
