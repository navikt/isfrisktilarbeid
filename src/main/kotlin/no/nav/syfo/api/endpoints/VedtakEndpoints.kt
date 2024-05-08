package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.syfo.api.model.VedtakRequestDTO
import no.nav.syfo.api.model.VedtakResponseDTO
import no.nav.syfo.application.VedtakService
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollPlugin
import no.nav.syfo.util.getCallId
import no.nav.syfo.util.getNAVIdent
import no.nav.syfo.util.getPersonident
import java.util.UUID

const val vedtakUUIDParam = "vedtakUUID"
const val apiBasePath = "/api/internad/v1/frisktilarbeid"
const val vedtakPath = "/vedtak"
const val ferdigbehandlingPath = "/vedtak/{$vedtakUUIDParam}/ferdigbehandling"

private const val API_ACTION = "access vedtak for person"

fun Route.registerVedtakEndpoints(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    vedtakService: VedtakService,
) {
    route(apiBasePath) {
        install(VeilederTilgangskontrollPlugin) {
            this.action = API_ACTION
            this.veilederTilgangskontrollClient = veilederTilgangskontrollClient
        }

        get(vedtakPath) {
            val personident = call.getPersonident()
                ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")

            val vedtak = vedtakService.getVedtak(
                personident = personident,
            )
            val responseDTO = vedtak.map { VedtakResponseDTO.createFromVedtak(it) }
            call.respond(HttpStatusCode.OK, responseDTO)
        }

        post(vedtakPath) {
            val requestDTO = call.receive<VedtakRequestDTO>()
            if (requestDTO.begrunnelse.isBlank() || requestDTO.document.isEmpty()) {
                throw IllegalArgumentException("Vedtak can't have an empty begrunnelse or document")
            }

            val personident = call.getPersonident()
                ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
            val navIdent = call.getNAVIdent()
            val callId = call.getCallId()

            if (vedtakService.getVedtak(personident).any { it.ferdigbehandletAt == null }) {
                call.respond(HttpStatusCode.Conflict, "Finnes allerede et åpent vedtak for personen")
            } else {
                val newVedtak = vedtakService.createVedtak(
                    personident = personident,
                    veilederident = navIdent,
                    begrunnelse = requestDTO.begrunnelse,
                    document = requestDTO.document,
                    fom = requestDTO.fom,
                    tom = requestDTO.tom,
                    callId = callId,
                    behandlerRef = requestDTO.behandlerRef,
                    behandlerNavn = requestDTO.behandlerNavn,
                    behandlerDocument = requestDTO.behandlerDocument,
                )

                call.respond(HttpStatusCode.Created, VedtakResponseDTO.createFromVedtak(vedtak = newVedtak))
            }
        }
        put(ferdigbehandlingPath) {
            val vedtakUUID = UUID.fromString(this.call.parameters[vedtakUUIDParam])
            val personident = call.getPersonident()
                ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
            val navIdent = call.getNAVIdent()
            val vedtak = vedtakService.getVedtak(personident).firstOrNull { it.uuid == vedtakUUID }
            if (vedtak == null || vedtak.isFerdigbehandlet()) {
                call.respond(HttpStatusCode.BadRequest, "Finner ikke åpent vedtak med uuid=$vedtakUUID")
            } else {
                val ferdigbehandletVedtak = vedtakService.ferdigbehandleVedtak(
                    vedtak = vedtak,
                    veilederident = navIdent,
                )
                call.respond(HttpStatusCode.OK, VedtakResponseDTO.createFromVedtak(vedtak = ferdigbehandletVedtak))
            }
        }
    }
}
