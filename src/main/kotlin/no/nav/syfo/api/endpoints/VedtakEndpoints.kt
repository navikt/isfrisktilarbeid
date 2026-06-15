package no.nav.syfo.api.endpoints

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.syfo.api.model.VedtakRequestDTO
import no.nav.syfo.api.model.VedtakResponseDTO
import no.nav.syfo.api.model.VilkarResponseDTO
import no.nav.syfo.application.VedtakService
import no.nav.syfo.common.tilgangskontroll.client.TilgangskontrollClient
import no.nav.syfo.common.tilgangskontroll.checkPersonAndSyfoTilgang
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.clients.arbeidssokeroppslag.ArbeidssokeroppslagClient
import java.util.UUID

const val vedtakUUIDParam = "vedtakUUID"
const val apiBasePath = "/api/internad/v1/frisktilarbeid"
const val vedtakPath = "/vedtak"
const val vilkarPath = "/vedtak-vilkar"
const val ferdigbehandlingPath = "/vedtak/{$vedtakUUIDParam}/ferdigbehandling"

fun Route.registerVedtakEndpoints(
    tilgangskontrollClient: TilgangskontrollClient,
    vedtakService: VedtakService,
    arbeidssokeroppslagClient: ArbeidssokeroppslagClient,
    dispatcher: CoroutineDispatcher,
) {
    val coroutineScope = CoroutineScope(SupervisorJob() + dispatcher)

    route(apiBasePath) {
        get(vilkarPath) {
            checkPersonAndSyfoTilgang(
                action = "get vilkar for person",
                tilgangskontrollClient = tilgangskontrollClient,
            ) { authorizedUser, targetPersonIdent, callId ->
                val personident = Personident(targetPersonIdent.value)

                val isArbeidssoker =
                    arbeidssokeroppslagClient.isArbeidssoker(
                        callId,
                        personident,
                        authorizedUser.token
                    )

                call.respond(HttpStatusCode.OK, VilkarResponseDTO(isArbeidssoker))
            }
        }

        get(vedtakPath) {
            checkPersonAndSyfoTilgang(
                action = "get vedtak for person",
                tilgangskontrollClient = tilgangskontrollClient,
            ) { _, targetPersonIdent, _ ->
                val personident = Personident(targetPersonIdent.value)

                val vedtak = vedtakService.getVedtak(personident = personident)
                val responseDTO = vedtak.map { VedtakResponseDTO.createFromVedtak(it) }

                call.respond(HttpStatusCode.OK, responseDTO)
            }
        }

        post(vedtakPath) {
            val log = call.application.log

            checkPersonAndSyfoTilgang(
                action = "create vedtak",
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) { authorizedUser, targetPersonIdent, callId ->
                val personident = Personident(targetPersonIdent.value)

                val requestDTO = call.receive<VedtakRequestDTO>()
                if (requestDTO.begrunnelse.isBlank() || requestDTO.document.isEmpty()) {
                    throw IllegalArgumentException("Vedtak can't have an empty begrunnelse or document")
                }
                if (requestDTO.tom.isBefore(requestDTO.fom)) {
                    throw IllegalArgumentException("Tildato i vedtak kan ikke være før fradato.")
                }

                val existingVedtak = vedtakService.getVedtak(personident)
                val currentVedtak = existingVedtak.firstOrNull()
                if (existingVedtak.any { !it.isFerdigbehandlet() }) {
                    call.respond(HttpStatusCode.Conflict, "Finnes allerede et åpent vedtak for personen")
                } else if (currentVedtak != null && currentVedtak.tom.isAfter(requestDTO.fom)) {
                    log.warn("Forsøker å opprette vedtak som overlapper med et tidligere vedtak")
                    call.respond(HttpStatusCode.Conflict, "Vedtaksperioden overlapper med et tidligere vedtak")
                } else if (!arbeidssokeroppslagClient.isArbeidssoker(
                        callId,
                        personident,
                        authorizedUser.token
                    )
                ) {
                    log.warn("Forsøker å opprette vedtak for person som ikke er registrert som arbeidssøker")
                    call.respond(HttpStatusCode.BadRequest, "Personen er ikke registrert som arbeidssøker")
                } else {
                    val (newVedtak, pdf) = vedtakService.createVedtak(
                        personident = personident,
                        veilederident = authorizedUser.navIdent.value,
                        begrunnelse = requestDTO.begrunnelse,
                        document = requestDTO.document,
                        fom = requestDTO.fom,
                        tom = requestDTO.tom,
                        callId = callId,
                    )
                    coroutineScope.launch {
                        try {
                            val journalfortVedtak = vedtakService.journalforVedtak(newVedtak, pdf).getOrThrow()
                            if (journalfortVedtak.isJournalfort()) {
                                vedtakService.createGosysOppgaveForVedtak(journalfortVedtak)
                            }
                        } catch (exc: Exception) {
                            log.error("Journalforing eller gosysoppgave feilet, cronjob vil forsøke på nytt", exc)
                        }
                    }
                    vedtakService.sendVedtakToInfotrygd(vedtak = newVedtak)
                    delay(1000) // Wait for infotrygd kvittering to be consumed

                    val vedtak = vedtakService.getVedtak(uuid = newVedtak.uuid)
                    val response = VedtakResponseDTO.createFromVedtak(vedtak = vedtak)
                    log.info("Created vedtak with infotrygd status: ${response.infotrygdStatus}, isJournalfort: ${response.isJournalfort}, hasGosysOppgave: ${response.hasGosysOppgave}")
                    call.respond(HttpStatusCode.Created, response)
                }
            }
        }

        put(ferdigbehandlingPath) {
            checkPersonAndSyfoTilgang(
                action = "update ferdigbehandling for person",
                tilgangskontrollClient = tilgangskontrollClient,
                requiresWriteAccess = true,
            ) { authorizedUser, targetPersonIdent, _ ->
                val personident = Personident(targetPersonIdent.value)
                val vedtakUUID = UUID.fromString(
                    requireNotNull(call.parameters[vedtakUUIDParam]) { "Missing vedtakUUID" }
                )

                val vedtak = vedtakService.getVedtak(personident).firstOrNull { it.uuid == vedtakUUID }
                if (vedtak == null || vedtak.isFerdigbehandlet()) {
                    call.respond(HttpStatusCode.BadRequest, "Finner ikke åpent vedtak med uuid=$vedtakUUID")
                } else {
                    val ferdigbehandletVedtak = vedtakService.ferdigbehandleVedtak(
                        vedtak = vedtak,
                        veilederident = authorizedUser.navIdent.value,
                    )
                    call.respond(
                        HttpStatusCode.OK,
                        VedtakResponseDTO.createFromVedtak(vedtak = ferdigbehandletVedtak)
                    )
                }
            }
        }
    }
}
