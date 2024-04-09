package no.nav.syfo.infrastructure.database.repository

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.IVedtakRepository
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.nowUTC
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

private val mapper = configuredJacksonMapper()

class VedtakRepository(private val database: DatabaseInterface) : IVedtakRepository {

    override fun createVedtak(
        vedtak: Vedtak,
        vedtakPdf: ByteArray,
        behandlerMelding: BehandlerMelding,
        behandlerMeldingPdf: ByteArray
    ): Pair<Vedtak, BehandlerMelding> {
        database.connection.use { connection ->
            val pVedtakPdf = connection.createPdf(pdf = vedtakPdf)
            val pVedtak = connection.createVedtak(
                vedtak = vedtak,
                pdfId = pVedtakPdf.id
            )

            val pBehandlerMeldingPdf = connection.createPdf(pdf = behandlerMeldingPdf)
            val pBehandlerMelding = connection.createBehandlerMelding(
                behandlerMelding = behandlerMelding,
                vedtakId = pVedtak.id,
                pdfId = pBehandlerMeldingPdf.id
            )

            connection.commit()
            return Pair(pVedtak.toVedtak(), pBehandlerMelding.toBehandlerMelding())
        }
    }

    override fun getUnpublishedInfotrygd(): List<Vedtak> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_UNPUBLISHED_INFOTRYGD).use {
                it.executeQuery().toList { toPVedtak() }
            }
        }.map { it.toVedtak() }

    override fun setVedtakPublishedInfotrygd(vedtak: Vedtak) {
        database.connection.use { connection ->
            connection.prepareStatement(SET_PUBLISHED_INFOTRYGD).use {
                it.setString(1, vedtak.uuid.toString())
                val updated = it.executeUpdate()
                if (updated != 1) {
                    throw SQLException("Expected a single row to be updated, got update count $updated")
                }
            }
            connection.commit()
        }
    }

    override fun getNotJournalforteVedtak(): List<Pair<Vedtak, ByteArray>> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_NOT_JOURNALFORTE_VEDTAK).use {
                it.executeQuery().toList { toPVedtak() to getBytes("pdf") }
            }
        }.map { (pVedtak, pdf) ->
            pVedtak.toVedtak() to pdf
        }

    override fun update(vedtak: Vedtak) =
        database.connection.use { connection ->
            connection.prepareStatement(UPDATE_VEDTAK).use {
                it.setString(1, vedtak.journalpostId?.value)
                it.setObject(2, vedtak.varselPublishedAt)
                it.setObject(3, nowUTC())
                it.setString(4, vedtak.uuid.toString())
                val updated = it.executeUpdate()
                if (updated != 1) {
                    throw SQLException("Expected a single row to be updated, got update count $updated")
                }
            }
            connection.commit()
        }

    override fun getUnpublishedVedtakVarsler(): List<Vedtak> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_UNPUBLISHED_VEDTAK_VARSLER).use {
                it.executeQuery().toList { toPVedtak() }
            }
        }.map { it.toVedtak() }

    private fun Connection.createPdf(pdf: ByteArray): PPdf =
        prepareStatement(CREATE_PDF).use {
            it.setString(1, UUID.randomUUID().toString())
            it.setObject(2, nowUTC())
            it.setBytes(3, pdf)
            it.executeQuery().toList { toPPdf() }.single()
        }

    private fun Connection.createVedtak(vedtak: Vedtak, pdfId: Int): PVedtak =
        prepareStatement(CREATE_VEDTAK).use {
            it.setString(1, vedtak.uuid.toString())
            it.setObject(2, vedtak.createdAt)
            it.setObject(3, vedtak.createdAt)
            it.setString(4, vedtak.personident.value)
            it.setString(5, vedtak.veilederident)
            it.setDate(6, Date.valueOf(vedtak.fom))
            it.setDate(7, Date.valueOf(vedtak.tom))
            it.setString(8, vedtak.begrunnelse)
            it.setObject(9, mapper.writeValueAsString(vedtak.document))
            it.setInt(10, pdfId)
            it.executeQuery().toList { toPVedtak() }.single()
        }

    private fun Connection.createBehandlerMelding(behandlerMelding: BehandlerMelding, vedtakId: Int, pdfId: Int) =
        prepareStatement(CREATE_BEHANDLER_MELDING).use {
            it.setString(1, behandlerMelding.uuid.toString())
            it.setObject(2, behandlerMelding.createdAt)
            it.setObject(3, behandlerMelding.createdAt)
            it.setString(4, behandlerMelding.behandlerRef.toString())
            it.setObject(5, mapper.writeValueAsString(behandlerMelding.document))
            it.setInt(6, vedtakId)
            it.setInt(7, pdfId)
            it.executeQuery().toList { toPBehandlerMelding() }.single()
        }

    companion object {
        private const val CREATE_PDF =
            """
            INSERT INTO PDF (
                id,
                uuid,
                created_at,
                pdf
            ) values (DEFAULT, ?, ?, ?)
            RETURNING *
            """

        private const val CREATE_VEDTAK =
            """
                INSERT INTO VEDTAK (
                    id,
                    uuid,
                    created_at,
                    updated_at,
                    personident,
                    veilederident,
                    fom,
                    tom,
                    begrunnelse,
                    document,
                    pdf_id
                ) values (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                RETURNING *
            """

        private const val GET_UNPUBLISHED_INFOTRYGD =
            """
                SELECT * FROM VEDTAK WHERE published_infotrygd_at IS NULL
            """

        private const val SET_PUBLISHED_INFOTRYGD =
            """
                UPDATE VEDTAK SET published_infotrygd_at=now() WHERE uuid=?
            """

        private const val UPDATE_VEDTAK =
            """
                UPDATE VEDTAK SET journalpost_id = ?, varsel_published_at = ?, updated_at = ? WHERE uuid = ?
            """

        private const val GET_NOT_JOURNALFORTE_VEDTAK =
            """
                 SELECT v.*, p.pdf
                 FROM vedtak v
                 INNER JOIN pdf p ON v.pdf_id = p.id
                 WHERE v.journalpost_id IS NULL
            """

        private const val CREATE_BEHANDLER_MELDING =
            """
                INSERT INTO BEHANDLER_MELDING (
                    id,
                    uuid,
                    created_at,
                    updated_at,
                    behandler_ref,
                    document,
                    vedtak_id,
                    pdf_id
                ) values (DEFAULT, ?, ?, ?, ?, ?::jsonb, ?, ?)
                RETURNING *
            """

        private const val GET_UNPUBLISHED_VEDTAK_VARSLER =
            """
                SELECT *
                FROM VEDTAK
                WHERE varsel_published_at IS NULL AND journalpost_id IS NOT NULL
            """
    }
}

internal fun ResultSet.toPPdf(): PPdf = PPdf(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    pdf = getBytes("pdf"),
)

internal fun ResultSet.toPVedtak(): PVedtak = PVedtak(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    personident = Personident(getString("personident")),
    veilederident = getString("veilederident"),
    fom = getObject("fom", LocalDate::class.java),
    tom = getObject("tom", LocalDate::class.java),
    begrunnelse = getString("begrunnelse"),
    document = mapper.readValue(
        getString("document"),
        object : TypeReference<List<DocumentComponent>>() {}
    ),
    journalpostId = getString("journalpost_id"),
    pdfId = getInt("pdf_id"),
    publishedInfotrygdAt = getObject("published_infotrygd_at", OffsetDateTime::class.java),
    varselPublishedAt = getObject("varsel_published_at", OffsetDateTime::class.java),
)

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
)
