package no.nav.syfo.domain

import java.util.*

@JvmInline
value class Personident(val value: String) {
    init {
        if (!Regex("^\\d{11}\$").matches(value)) {
            throw IllegalArgumentException("Value is not a valid PersonIdent")
        }
    }
}

fun Personident.asProducerRecordKey(): String = UUID.nameUUIDFromBytes(value.toByteArray()).toString()
