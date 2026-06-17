package no.nav.syfo.infrastructure.mock

import no.nav.syfo.UserConstants
import no.nav.syfo.common.mock.tilgangskontroll.MockUserSyfoTilgangLevel
import no.nav.syfo.common.mock.tilgangskontroll.MockUserTilgangDetails
import no.nav.syfo.common.types.ident.Navident
import no.nav.syfo.common.types.ident.Personident

val mockTilgangDetailsPerNavident =
    mapOf(
        Navident(UserConstants.VEILEDER_IDENT) to MockUserTilgangDetails(
            syfoTilgangLevel = MockUserSyfoTilgangLevel.FULL,
            personsUserHasAccessTo = setOf(
                Personident(UserConstants.ARBEIDSTAKER_PERSONIDENT.value),
                Personident(UserConstants.ARBEIDSTAKER_PERSONIDENT_UTLAND.value)
            )
        ),
        Navident(UserConstants.VEILEDER_IDENT_OTHER) to MockUserTilgangDetails(
            syfoTilgangLevel = MockUserSyfoTilgangLevel.FULL,
            personsUserHasAccessTo = setOf(
                Personident(UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
            )
        ),
        Navident(UserConstants.VEILEDER_IDENT_READ_ACCESS) to MockUserTilgangDetails(
            syfoTilgangLevel = MockUserSyfoTilgangLevel.READ,
            personsUserHasAccessTo = setOf(
                Personident(UserConstants.ARBEIDSTAKER_PERSONIDENT.value),
                Personident(UserConstants.ARBEIDSTAKER_PERSONIDENT_UTLAND.value)
            )
        )
    )
