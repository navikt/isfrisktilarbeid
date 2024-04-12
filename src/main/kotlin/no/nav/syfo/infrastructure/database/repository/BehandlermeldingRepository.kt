package no.nav.syfo.infrastructure.database.repository

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.IBehandlermeldingRepository
import no.nav.syfo.domain.Behandlermelding
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.Personident
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.nowUTC
import java.sql.ResultSet
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.*

private val mapper = configuredJacksonMapper()

class BehandlermeldingRepository(private val database: DatabaseInterface) : IBehandlermeldingRepository {

    override fun getUnpublishedBehandlermeldinger(): List<Triple<Personident, Behandlermelding, ByteArray>> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_UNPUBLISHED_BEHANDLERMELDING).use {
                it.executeQuery().toList {
                    Triple(
                        Personident(getString("personident")),
                        toPBehandlerMelding().toBehandlermelding(),
                        getBytes("pdf")
                    )
                }
            }
        }

    override fun update(behandlermelding: Behandlermelding) {
        database.connection.use { connection ->
            connection.prepareStatement(UPDATE_BEHANDLERMELDING).use {
                it.setObject(1, behandlermelding.publishedAt)
                it.setString(2, behandlermelding.journalpostId?.value)
                it.setObject(3, nowUTC())
                it.setString(4, behandlermelding.uuid.toString())
                val updated = it.executeUpdate()
                if (updated != 1) {
                    throw SQLException("Expected a single row to be updated, got update count $updated")
                }
            }
            connection.commit()
        }
    }

    override fun getNotJournalforteBehandlermeldinger(): List<Triple<Personident, Behandlermelding, ByteArray>> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_NOT_JOURNALFORT_BEHANDLERMELDING).use {
                it.executeQuery().toList {
                    Triple(
                        Personident(getString("personident")),
                        toPBehandlerMelding().toBehandlermelding(),
                        getBytes("pdf")
                    )
                }
            }
        }

    companion object {
        private const val GET_UNPUBLISHED_BEHANDLERMELDING =
            """
                SELECT v.personident, pdf.pdf, b.* FROM behandler_melding b
                INNER JOIN vedtak v ON b.vedtak_id = v.id
                INNER JOIN pdf ON b.pdf_id = pdf.id
                WHERE b.published_at IS NULL
            """

        private const val UPDATE_BEHANDLERMELDING =
            """
                 UPDATE behandler_melding
                 SET published_at = ?, journalpost_id = ?, updated_at = ?
                 WHERE uuid = ?
            """

        private const val GET_NOT_JOURNALFORT_BEHANDLERMELDING =
            """
                SELECT v.personident, pdf.pdf, b.* FROM behandler_melding b
                INNER JOIN vedtak v ON b.vedtak_id = v.id
                INNER JOIN pdf ON b.pdf_id = pdf.id
                WHERE b.journalpost_id IS NULL
            """
    }
}

internal fun ResultSet.toPBehandlerMelding(): PBehandlerMelding = PBehandlerMelding(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    behandlerRef = UUID.fromString(getString("behandler_ref")),
    document = mapper.readValue(
        getString("document"),
        object : TypeReference<List<DocumentComponent>>() {}
    ),
    journalpostId = getString("journalpost_id")?.let { JournalpostId(it) },
    vedtakId = getInt("vedtak_id"),
    pdfId = getInt("pdf_id"),
    publishedAt = getObject("published_at", OffsetDateTime::class.java),
)
