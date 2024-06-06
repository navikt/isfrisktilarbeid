package no.nav.syfo.infrastructure.metric

import io.micrometer.core.instrument.Counter
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

const val METRICS_NS = "isfrisktilarbeid"

val METRICS_REGISTRY = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

private const val VEDTAK_FATTET = "${METRICS_NS}_fatt_vedtak"

val COUNT_FATT_VEDTAK_SUCCESS = Counter.builder("${VEDTAK_FATTET}_success_count")
    .description("Counts the number of successful calls to /api/internad/v1/frisktilarbeid/vedtak")
    .register(METRICS_REGISTRY)
val COUNT_FATT_VEDTAK_FAILURE = Counter.builder("${VEDTAK_FATTET}_failure_count")
    .description("Counts the number of failed calls to /api/internad/v1/frisktilarbeid/vedtak")
    .register(METRICS_REGISTRY)
