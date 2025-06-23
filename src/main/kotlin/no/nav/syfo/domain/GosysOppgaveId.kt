package no.nav.syfo.domain

@JvmInline
value class GosysOppgaveId(val value: String) {
    init {
        if (!value.all { it.isDigit() }) {
            throw IllegalArgumentException("Value is not a valid GosysOppgaveId")
        }
    }
}
