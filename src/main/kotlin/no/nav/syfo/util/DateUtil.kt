package no.nav.syfo.util

import java.time.*

val defaultZoneOffset: ZoneOffset = ZoneOffset.UTC

val osloZoneId: ZoneId = ZoneId.of("Europe/Oslo")

fun nowUTC(): OffsetDateTime = OffsetDateTime.now(defaultZoneOffset)
