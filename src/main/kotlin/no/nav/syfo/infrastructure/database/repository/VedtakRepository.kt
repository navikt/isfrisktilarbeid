package no.nav.syfo.infrastructure.database.repository

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.IVedtakRepository
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.getNullableBoolean
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

    override fun getVedtak(personident: Personident): List<Vedtak> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_VEDTAK).use {
                it.setString(1, personident.value)
                it.executeQuery().toList { toPVedtak() }
            }.map { pVedtak ->
                pVedtak.toVedtak(connection.getVedtakStatus(pVedtak.id))
            }
        }

    override fun getVedtak(uuid: UUID): Vedtak =
        database.connection.use { connection ->
            connection.getPVedtak(uuid).let { pVedtak ->
                pVedtak.toVedtak(connection.getVedtakStatus(pVedtak.id))
            }
        }

    private fun Connection.getPVedtak(uuid: UUID): PVedtak =
        this.prepareStatement(GET_VEDTAK_FOR_UUID).use {
            it.setString(1, uuid.toString())
            it.executeQuery().toList { toPVedtak() }.single()
        }

    private fun Connection.getVedtakStatus(vedtakId: Int) =
        prepareStatement(GET_VEDTAK_STATUS).use {
            it.setInt(1, vedtakId)
            it.executeQuery().toList { toPVedtakStatus() }
        }

    override fun createVedtak(
        vedtak: Vedtak,
        vedtakPdf: ByteArray,
    ): Vedtak {
        database.connection.use { connection ->
            val pVedtakPdf = connection.createPdf(pdf = vedtakPdf)
            val pVedtak = connection.createVedtak(
                vedtak = vedtak,
                pdfId = pVedtakPdf.id
            )
            val pVedtakStatusListe = vedtak.statusListe.map {
                connection.createVedtakStatus(
                    vedtakId = pVedtak.id,
                    vedtakStatus = it,
                )
            }

            connection.commit()
            return pVedtak.toVedtak(pVedtakStatusListe)
        }
    }

    override fun getUnpublishedInfotrygd(): List<Vedtak> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_UNPUBLISHED_INFOTRYGD).use {
                it.executeQuery().toList { toPVedtak() }
            }.map { pVedtak ->
                pVedtak.toVedtak(connection.getVedtakStatus(pVedtak.id))
            }
        }

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
            }.map { (pVedtak, pdf) ->
                pVedtak.toVedtak(connection.getVedtakStatus(pVedtak.id)) to pdf
            }
        }

    override fun getVedtakUtenGosysOppgave(): List<Vedtak> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_VEDTAK_UTEN_OPPGAVE).use {
                it.executeQuery().toList { toPVedtak() }
            }.map { pVedtak ->
                pVedtak.toVedtak(connection.getVedtakStatus(pVedtak.id))
            }
        }

    override fun setJournalpostId(vedtak: Vedtak) =
        database.connection.use { connection ->
            connection.prepareStatement(SET_JOURNALPOST_ID).use {
                it.setString(1, vedtak.journalpostId?.value)
                it.setObject(2, nowUTC())
                it.setString(3, vedtak.uuid.toString())
                val updated = it.executeUpdate()
                if (updated != 1) {
                    throw SQLException("Expected a single row to be updated, got update count $updated")
                }
            }
            connection.commit()
        }

    override fun setGosysOppgaveId(vedtak: Vedtak) =
        database.connection.use { connection ->
            connection.prepareStatement(SET_GOSYS_OPPGAVE_ID).use {
                it.setString(1, vedtak.gosysOppgaveId?.value)
                it.setObject(2, vedtak.gosysOppgaveAt)
                it.setObject(3, nowUTC())
                it.setString(4, vedtak.uuid.toString())
                val updated = it.executeUpdate()
                if (updated != 1) {
                    throw SQLException("Expected a single row to be updated, got update count $updated")
                }
            }
            connection.commit()
        }

    override fun addVedtakStatus(vedtak: Vedtak, vedtakStatus: VedtakStatus) =
        database.connection.use { connection ->
            val pVedtak = connection.getPVedtak(vedtak.uuid)
            connection.createVedtakStatus(pVedtak.id, vedtakStatus)
            connection.commit()
        }

    override fun getUnpublishedVedtakVarsler(): List<Vedtak> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_UNPUBLISHED_VEDTAK_VARSLER).use {
                it.executeQuery().toList { toPVedtak() }
            }.map { pVedtak ->
                pVedtak.toVedtak(connection.getVedtakStatus(pVedtak.id))
            }
        }

    override fun setVedtakVarselPublished(vedtak: Vedtak) =
        database.connection.use { connection ->
            connection.prepareStatement(SET_PUBLISHED_VEDTAK_VARSEL).use {
                it.setString(1, vedtak.uuid.toString())
                val updated = it.executeUpdate()
                if (updated != 1) {
                    throw SQLException("Expected a single row to be updated, got update count $updated")
                }
            }
            connection.commit()
        }

    override fun getUnpublishedVedtakStatus(): List<Pair<Vedtak, VedtakStatus>> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_UNPUBLISHED_VEDTAK_STATUS).use {
                it.executeQuery().toList { toPVedtak() }
            }.flatMap { pVedtak ->
                val pVedtakStatusListe = connection.getVedtakStatus(pVedtak.id)
                pVedtakStatusListe.filter { it.publishedAt == null }.map { pVedtakStatus ->
                    Pair(
                        pVedtak.toVedtak(pVedtakStatusListe),
                        pVedtakStatus.toVedtakStatus(),
                    )
                }
            }
        }

    override fun setVedtakStatusPublished(vedtakStatus: VedtakStatus) =
        database.connection.use { connection ->
            connection.prepareStatement(SET_PUBLISHED_VEDTAK_STATUS).use {
                it.setString(1, vedtakStatus.uuid.toString())
                val updated = it.executeUpdate()
                if (updated != 1) {
                    throw SQLException("Expected a single row to be updated, got update count $updated")
                }
            }
            connection.commit()
        }

    override fun setInfotrygdKvitteringReceived(vedtak: Vedtak, ok: Boolean, feilmelding: String?) =
        database.connection.use { connection ->
            connection.prepareStatement(SET_INFOTRYGD_KVITTERING).use {
                it.setBoolean(1, ok)
                it.setString(2, feilmelding)
                it.setString(3, vedtak.uuid.toString())
                val updated = it.executeUpdate()
                if (updated != 1) {
                    throw SQLException("Expected a single row to be updated, got update count $updated")
                }
            }
            connection.commit()
        }

    override fun updatePersonident(nyPersonident: Personident, vedtak: List<Vedtak>) = database.connection.use { connection ->
        connection.prepareStatement(UPDATE_PERSONIDENT).use {
            vedtak.forEach { vedtak ->
                it.setString(1, nyPersonident.value)
                it.setString(2, vedtak.uuid.toString())
                val updated = it.executeUpdate()
                if (updated != 1) {
                    throw SQLException("Expected a single row to be updated, got update count $updated")
                }
            }
        }
        connection.commit()
    }

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
            it.setDate(5, Date.valueOf(vedtak.fom))
            it.setDate(6, Date.valueOf(vedtak.tom))
            it.setString(7, vedtak.begrunnelse)
            it.setObject(8, mapper.writeValueAsString(vedtak.document))
            it.setInt(9, pdfId)
            it.executeQuery().toList { toPVedtak() }.single()
        }

    private fun Connection.createVedtakStatus(vedtakId: Int, vedtakStatus: VedtakStatus): PVedtakStatus =
        prepareStatement(CREATE_VEDTAK_STATUS).use {
            it.setString(1, vedtakStatus.uuid.toString())
            it.setObject(2, vedtakStatus.createdAt)
            it.setInt(3, vedtakId)
            it.setString(4, vedtakStatus.veilederident)
            it.setString(5, vedtakStatus.status.name)
            it.executeQuery().toList { toPVedtakStatus() }.single()
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
                    fom,
                    tom,
                    begrunnelse,
                    document,
                    pdf_id
                ) values (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                RETURNING *
            """

        private const val CREATE_VEDTAK_STATUS =
            """
                INSERT INTO VEDTAK_STATUS (
                    id,
                    uuid,
                    created_at,
                    vedtak_id,
                    veilederident,
                    status
                ) values (DEFAULT, ?, ?, ?, ?, ?)
                RETURNING *
            """

        private const val GET_VEDTAK =
            """
                SELECT * FROM VEDTAK WHERE personident=? ORDER BY created_at DESC
            """

        private const val GET_VEDTAK_FOR_UUID =
            """
                SELECT * FROM VEDTAK WHERE uuid=?
            """

        private const val GET_UNPUBLISHED_INFOTRYGD =
            """
                SELECT * FROM VEDTAK WHERE published_infotrygd_at IS NULL AND created_at < now() - interval '1 minutes'
            """

        private const val SET_PUBLISHED_INFOTRYGD =
            """
                UPDATE VEDTAK SET published_infotrygd_at=now() WHERE uuid=?
            """

        private const val SET_JOURNALPOST_ID =
            """
                UPDATE VEDTAK SET 
                    journalpost_id = ?, 
                    updated_at = ? 
                WHERE uuid = ?
            """

        private const val SET_GOSYS_OPPGAVE_ID =
            """
                UPDATE VEDTAK SET 
                    gosys_oppgave_id = ?,
                    gosys_oppgave_at = ?,
                    updated_at = ? 
                WHERE uuid = ?
            """

        private const val GET_NOT_JOURNALFORTE_VEDTAK =
            """
                 SELECT v.*, p.pdf
                 FROM vedtak v
                 INNER JOIN pdf p ON v.pdf_id = p.id
                 WHERE v.journalpost_id IS NULL AND v.created_at < now() - interval '1 minutes'
            """

        private const val GET_VEDTAK_UTEN_OPPGAVE =
            """
                 SELECT *
                 FROM vedtak
                 WHERE journalpost_id IS NOT NULL AND journalpost_id != '0' AND gosys_oppgave_id IS NULL AND created_at < now() - interval '1 minutes'
            """

        private const val GET_UNPUBLISHED_VEDTAK_VARSLER =
            """
                SELECT *
                FROM VEDTAK
                WHERE varsel_published_at IS NULL AND journalpost_id IS NOT NULL
            """

        private const val SET_PUBLISHED_VEDTAK_VARSEL =
            """
                UPDATE VEDTAK SET varsel_published_at=now() WHERE uuid=?
            """

        private const val GET_VEDTAK_STATUS =
            """
                SELECT *
                FROM VEDTAK_STATUS
                WHERE vedtak_id=? ORDER BY created_at
            """

        private const val GET_UNPUBLISHED_VEDTAK_STATUS =
            """
                SELECT DISTINCT v.* FROM VEDTAK v INNER JOIN VEDTAK_STATUS s ON (v.id = s.vedtak_id) WHERE s.published_at IS NULL ORDER BY v.created_at ASC
            """

        private const val SET_PUBLISHED_VEDTAK_STATUS =
            """
                UPDATE VEDTAK_STATUS SET published_at=now() WHERE uuid=?
            """

        private const val SET_INFOTRYGD_KVITTERING =
            """
                UPDATE VEDTAK SET infotrygd_ok=?, infotrygd_feilmelding=?, updated_at=now() WHERE uuid=?
            """

        private const val UPDATE_PERSONIDENT =
            """
                UPDATE VEDTAK SET personident=? WHERE uuid=?
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
    fom = getObject("fom", LocalDate::class.java),
    tom = getObject("tom", LocalDate::class.java),
    begrunnelse = getString("begrunnelse"),
    document = mapper.readValue(
        getString("document"),
        object : TypeReference<List<DocumentComponent>>() {}
    ),
    journalpostId = getString("journalpost_id"),
    gosysOppgaveId = getString("gosys_oppgave_id"),
    gosysOppgaveAt = getObject("gosys_oppgave_at", OffsetDateTime::class.java),
    pdfId = getInt("pdf_id"),
    publishedInfotrygdAt = getObject("published_infotrygd_at", OffsetDateTime::class.java),
    varselPublishedAt = getObject("varsel_published_at", OffsetDateTime::class.java),
    infotrygdOk = getNullableBoolean("infotrygd_ok"),
)

internal fun ResultSet.toPVedtakStatus(): PVedtakStatus = PVedtakStatus(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    vedtak_id = getInt("vedtak_id"),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    veilederident = getString("veilederident"),
    status = getString("status"),
    publishedAt = getObject("published_at", OffsetDateTime::class.java),
)
