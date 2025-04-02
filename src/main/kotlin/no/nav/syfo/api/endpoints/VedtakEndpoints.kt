package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import no.nav.syfo.api.model.VedtakRequestDTO
import no.nav.syfo.api.model.VedtakResponseDTO
import no.nav.syfo.api.model.ValiderResponseDTO
import no.nav.syfo.application.VedtakService
import no.nav.syfo.infrastructure.NAV_PERSONIDENT_HEADER
import no.nav.syfo.infrastructure.clients.arbeidssokeroppslag.ArbeidssokeroppslagClient
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollPlugin
import no.nav.syfo.util.getBearerHeader
import no.nav.syfo.util.getCallId
import no.nav.syfo.util.getNAVIdent
import no.nav.syfo.util.getPersonident
import java.util.UUID

const val vedtakUUIDParam = "vedtakUUID"
const val apiBasePath = "/api/internad/v1/frisktilarbeid"
const val vedtakPath = "/vedtak"
const val validerPath = "/valider"
const val ferdigbehandlingPath = "/vedtak/{$vedtakUUIDParam}/ferdigbehandling"

private const val API_ACTION = "access vedtak for person"

fun Route.registerVedtakEndpoints(
    veilederTilgangskontrollClient: VeilederTilgangskontrollClient,
    vedtakService: VedtakService,
    arbeidssokeroppslagClient: ArbeidssokeroppslagClient,
) {
    route(apiBasePath) {
        install(VeilederTilgangskontrollPlugin) {
            this.action = API_ACTION
            this.veilederTilgangskontrollClient = veilederTilgangskontrollClient
        }

        get(validerPath) {
            val personident = call.getPersonident()
                ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
            val token = call.getBearerHeader() ?: throw IllegalArgumentException("Failed to $API_ACTION: No bearer token supplied in request header")
            val callId = call.getCallId()
            val isArbeidssoker = arbeidssokeroppslagClient.isArbeidssoker(callId, personident, token)

            call.respond(HttpStatusCode.OK, ValiderResponseDTO(isArbeidssoker))
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
            val log = call.application.log
            val requestDTO = call.receive<VedtakRequestDTO>()
            if (requestDTO.begrunnelse.isBlank() || requestDTO.document.isEmpty()) {
                throw IllegalArgumentException("Vedtak can't have an empty begrunnelse or document")
            }
            if (requestDTO.tom.isBefore(requestDTO.fom)) {
                throw IllegalArgumentException("Tildato i vedtak kan ikke være før fradato.")
            }

            val personident = call.getPersonident()
                ?: throw IllegalArgumentException("Failed to $API_ACTION: No $NAV_PERSONIDENT_HEADER supplied in request header")
            val token = call.getBearerHeader() ?: throw IllegalArgumentException("Failed to $API_ACTION: No bearer token supplied in request header")
            val navIdent = call.getNAVIdent()
            val callId = call.getCallId()

            val existingVedtak = vedtakService.getVedtak(personident)
            val currentVedtak = existingVedtak.firstOrNull()
            if (existingVedtak.any { !it.isFerdigbehandlet() }) {
                call.respond(HttpStatusCode.Conflict, "Finnes allerede et åpent vedtak for personen")
            } else if (currentVedtak != null && currentVedtak.tom.isAfter(requestDTO.fom)) {
                log.warn("Forsøker å opprette vedtak som overlapper med et tidligere vedtak")
                call.respond(HttpStatusCode.Conflict, "Vedtaksperioden overlapper med et tidligere vedtak")
            } else if (!arbeidssokeroppslagClient.isArbeidssoker(callId, personident, token)) {
                log.warn("Forsøker å opprette vedtak for person som ikke er registrert som arbeidssøker")
                call.respond(HttpStatusCode.BadRequest, "Personen er ikke registrert som arbeidssøker")
            } else {
                val newVedtak = vedtakService.createVedtak(
                    personident = personident,
                    veilederident = navIdent,
                    begrunnelse = requestDTO.begrunnelse,
                    document = requestDTO.document,
                    fom = requestDTO.fom,
                    tom = requestDTO.tom,
                    callId = callId,
                )
                vedtakService.sendVedtakToInfotrygd(vedtak = newVedtak)
                delay(1000) // Wait for infotrygd kvittering to be consumed

                val vedtak = vedtakService.getVedtak(uuid = newVedtak.uuid)
                log.info("Created vedtak with infotrygd status: ${vedtak.infotrygdStatus}")

                call.respond(HttpStatusCode.Created, VedtakResponseDTO.createFromVedtak(vedtak = vedtak))
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
