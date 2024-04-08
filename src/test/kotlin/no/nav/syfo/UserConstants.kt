package no.nav.syfo

import no.nav.syfo.domain.Personident
import java.util.*

object UserConstants {
    val ARBEIDSTAKER_PERSONIDENT = Personident("12345678910")
    val ARBEIDSTAKER_PERSONIDENT_VEILEDER_NO_ACCESS = Personident("11111111111")
    val ARBEIDSTAKER_PERSONIDENT_NAME_WITH_DASH = Personident("11111111234")
    val ARBEIDSTAKER_PERSONIDENT_NO_NAME = Personident("11111111222")
    val ARBEIDSTAKER_PERSONIDENT_PDL_FAILS = Personident("11111111666")

    val PDF_VEDTAK = byteArrayOf(0x2E, 0x28)
    val PDF_BEHANDLER_MELDING = byteArrayOf(0x2E, 0x30)
    const val VEILEDER_IDENT = "Z999999"

    const val PERSON_FORNAVN = "Fornavn"
    const val PERSON_MELLOMNAVN = "Mellomnavn"
    const val PERSON_ETTERNAVN = "Etternavnesen"
    const val PERSON_FORNAVN_DASH = "For-Navn"
    const val PERSON_FULLNAME_DASH = "For-Navn Mellomnavn Etternavnesen"

    val EXISTING_EKSTERN_REFERANSE_UUID: UUID = UUID.fromString("e7e8e9e0-e1e2-e3e4-e5e6-e7e8e9e0e1e2")
    val FAILING_EKSTERN_REFERANSE_UUID: UUID = UUID.randomUUID()
}
