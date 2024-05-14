package no.nav.syfo.infrastructure.database

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.infrastructure.database.repository.*
import no.nav.syfo.infrastructure.database.repository.toPPdf
import no.nav.syfo.infrastructure.database.repository.toPVedtak
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.time.OffsetDateTime
import java.util.*

class TestDatabase : DatabaseInterface {
    private val pg: EmbeddedPostgres = try {
        EmbeddedPostgres.start()
    } catch (e: Exception) {
        EmbeddedPostgres.builder().start()
    }

    override val connection: Connection
        get() = pg.postgresDatabase.connection.apply { autoCommit = false }

    init {

        Flyway.configure().run {
            dataSource(pg.postgresDatabase).validateMigrationNaming(true).load().migrate()
        }
    }

    fun stop() {
        pg.close()
    }
}

fun TestDatabase.dropData() {
    val queryList = listOf(
        """
        DELETE FROM VEDTAK
        """.trimIndent(),
        """
        DELETE FROM PDF
        """.trimIndent(),
        """
        DELETE FROM BEHANDLER_MELDING
        """.trimIndent(),
    )
    this.connection.use { connection ->
        queryList.forEach { query ->
            connection.prepareStatement(query).execute()
        }
        connection.commit()
    }
}

private const val queryGetVedtakPdf =
    """
        SELECT pdf.*
        FROM pdf INNER JOIN vedtak v ON v.pdf_id=pdf.id 
        WHERE v.uuid = ?
    """

fun TestDatabase.getVedtakPdf(
    vedtakUuid: UUID,
): PPdf? =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetVedtakPdf).use {
            it.setString(1, vedtakUuid.toString())
            it.executeQuery()
                .toList { toPPdf() }
                .singleOrNull()
        }
    }

fun TestDatabase.getPublishedInfotrygdAt(
    vedtakUuid: UUID,
): OffsetDateTime? =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetPublishedInfotrygdAt).use {
            it.setString(1, vedtakUuid.toString())
            it.executeQuery()
                .toList { getObject("published_infotrygd_at", OffsetDateTime::class.java) }
                .singleOrNull()
        }
    }

fun TestDatabase.getVedtakVarselPublishedAt(
    vedtakUuid: UUID,
): OffsetDateTime? =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetVedtakVarselPublishedAt).use {
            it.setString(1, vedtakUuid.toString())
            it.executeQuery()
                .toList { getObject("varsel_published_at", OffsetDateTime::class.java) }
                .singleOrNull()
        }
    }

fun TestDatabase.getVedtakInfotrygdKvittering(
    vedtakUuid: UUID,
): Boolean? =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetVedtakInfotrygdKvittering).use {
            it.setString(1, vedtakUuid.toString())
            it.executeQuery()
                .toList { getBoolean("infotrygd_ok") }
                .singleOrNull()
        }
    }

fun TestDatabase.getVedtakInfotrygdFeilmelding(
    vedtakUuid: UUID,
): String? =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetVedtakInfotrygdFeilmelding).use {
            it.setString(1, vedtakUuid.toString())
            it.executeQuery()
                .toList { getString("infotrygd_feilmelding") }
                .singleOrNull()
        }
    }

fun TestDatabase.getVedtakStatusPublishedAt(
    vedtakStatusUuid: UUID,
): OffsetDateTime? =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetVedtakStatusPublishedAt).use {
            it.setString(1, vedtakStatusUuid.toString())
            it.executeQuery()
                .toList { getObject("published_at", OffsetDateTime::class.java) }
                .singleOrNull()
        }
    }

private const val queryGetVedtak =
    """
        SELECT * FROM vedtak WHERE uuid = ?
    """

private const val queryGetPublishedInfotrygdAt =
    """
        SELECT published_infotrygd_at FROM vedtak WHERE uuid = ?
    """

private const val queryGetVedtakVarselPublishedAt =
    """
        SELECT varsel_published_at FROM vedtak WHERE uuid = ?
    """

private const val queryGetVedtakInfotrygdKvittering =
    """
        SELECT infotrygd_ok FROM vedtak WHERE uuid = ?
    """

private const val queryGetVedtakInfotrygdFeilmelding =
    """
        SELECT infotrygd_feilmelding FROM vedtak WHERE uuid = ?
    """

private const val queryGetVedtakStatusPublishedAt =
    """
        SELECT published_at FROM vedtak_status WHERE uuid = ?
    """

private const val queryGetBehandlerMelding =
    """
        SELECT * FROM behandler_melding WHERE uuid = ?
    """

fun TestDatabase.getVedtak(
    vedtakUuid: UUID
): PVedtak? = this.connection.use { connection ->
    connection.prepareStatement(queryGetVedtak).use {
        it.setString(1, vedtakUuid.toString())
        it.executeQuery().toList { toPVedtak() }.singleOrNull()
    }
}

fun TestDatabase.getBehandlerMelding(
    behandlerMeldingUuid: UUID
): PBehandlerMelding? =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetBehandlerMelding).use {
            it.setString(1, behandlerMeldingUuid.toString())
            it.executeQuery().toList { toPBehandlerMelding() }.singleOrNull()
        }
    }

class TestDatabaseNotResponding : DatabaseInterface {

    override val connection: Connection
        get() = throw Exception("Not working")
}
