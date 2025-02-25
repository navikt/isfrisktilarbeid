package no.nav.syfo.infrastructure.kafka.identhendelse

import no.nav.syfo.application.IVedtakRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IdenthendelseService(private val vedtakRepository: IVedtakRepository) {

    fun handle(identhendelse: KafkaIdenthendelseDTO) {
        val (aktivIdent, inaktiveIdenter) = identhendelse.getFolkeregisterIdenter()
        if (aktivIdent != null) {
            val vedtakMedInaktivIdent = inaktiveIdenter.flatMap { vedtakRepository.getVedtak(it) }

            if (vedtakMedInaktivIdent.isNotEmpty()) {
                vedtakRepository.updatePersonident(
                    nyPersonident = aktivIdent,
                    vedtak = vedtakMedInaktivIdent,
                )
                log.info("Identhendelse: Updated ${vedtakMedInaktivIdent.size} vedtak based on Identhendelse from PDL")
            }
        } else {
            log.warn("Identhendelse ignored - Mangler aktiv ident i PDL")
        }
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(this::class.java)
    }
}
