package no.nav.syfo.infrastructure.mock

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*

fun MockRequestHandleScope.azureAdMockResponse(): HttpResponseData = respond(
    content = """{"access_token":"token","expires_in":3600,"token_type":"Bearer"}""",
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)
