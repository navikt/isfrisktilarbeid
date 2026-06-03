package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import no.nav.syfo.common.mock.MockAzureAdTokenResponse

fun MockRequestHandleScope.azureAdMockResponse(): HttpResponseData = respond(
    MockAzureAdTokenResponse(
        accessToken = "token",
        expiresIn = 3600,
        tokenType = "type",
    )
)
