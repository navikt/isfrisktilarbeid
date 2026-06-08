package no.nav.syfo.infrastructure.mock

import no.nav.syfo.UserConstants
import no.nav.syfo.common.mock.tilgangskontroll.MockUserSyfoTilgangLevel
import no.nav.syfo.common.mock.tilgangskontroll.MockUserTilgangDetails
import no.nav.syfo.common.types.ident.NavIdent
import no.nav.syfo.common.types.ident.PersonIdent

val mockTilgangDetailsPerNavIdent =
    mapOf(
        NavIdent(UserConstants.VEILEDER_IDENT) to MockUserTilgangDetails(
            syfoTilgangLevel = MockUserSyfoTilgangLevel.FULL,
            personsUserHasAccessTo = setOf(
                PersonIdent(UserConstants.ARBEIDSTAKER_PERSONIDENT.value),
                PersonIdent(UserConstants.ARBEIDSTAKER_PERSONIDENT_UTLAND.value)
            )
        ),
        NavIdent(UserConstants.VEILEDER_IDENT_OTHER) to MockUserTilgangDetails(
            syfoTilgangLevel = MockUserSyfoTilgangLevel.FULL,
            personsUserHasAccessTo = setOf(
                PersonIdent(UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
            )
        ),
        NavIdent(UserConstants.VEILEDER_IDENT_READ_ACCESS) to MockUserTilgangDetails(
            syfoTilgangLevel = MockUserSyfoTilgangLevel.READ,
            personsUserHasAccessTo = setOf(
                PersonIdent(UserConstants.ARBEIDSTAKER_PERSONIDENT.value),
                PersonIdent(UserConstants.ARBEIDSTAKER_PERSONIDENT_UTLAND.value)
            )
        )
    )
