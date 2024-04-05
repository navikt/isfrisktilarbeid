package no.nav.syfo.generator

import no.nav.syfo.UserConstants
import no.nav.syfo.domain.BehandlerMelding
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.Personident
import no.nav.syfo.domain.Vedtak
import java.time.LocalDate
import java.util.*

fun generateVedtak(
    personident: Personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
    begrunnelse: String = "En begrunnelse",
    document: List<DocumentComponent> = generateDocumentComponent(begrunnelse),
): Vedtak = Vedtak(
    personident = personident,
    veilederident = UserConstants.VEILEDER_IDENT,
    begrunnelse = begrunnelse,
    document = document,
    fom = LocalDate.now(),
    tom = LocalDate.now().plusWeeks(12),
)

fun generateBehandlerMelding(
    document: List<DocumentComponent> = generateDocumentComponent("En behandlermelding"),
): BehandlerMelding = BehandlerMelding(
    behandlerRef = UUID.randomUUID(),
    document = document,
)
