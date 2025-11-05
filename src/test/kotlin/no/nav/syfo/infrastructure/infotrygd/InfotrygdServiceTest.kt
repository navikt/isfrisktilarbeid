package no.nav.syfo.infrastructure.infotrygd

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.mq.InfotrygdMQSender
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class InfotrygdServiceTest {
    private val externalMockEnvironment = ExternalMockEnvironment.instance
    private val mqSender = mockk<InfotrygdMQSender>(relaxed = true)
    private val infotrygdService = InfotrygdService(
        pdlClient = externalMockEnvironment.pdlClient,
        mqSender = mqSender,
    )
    private val fixedTime = OffsetDateTime.of(
        LocalDateTime.of(2024, Month.MARCH, 1, 12, 30, 23),
        ZoneOffset.UTC
    )

    @BeforeEach
    fun setup() {
        clearAllMocks()
        justRun { mqSender.sendToMQ(any(), any()) }
    }

    @Test
    fun `sends message to MQ`() {
        runBlocking {
            val vedtak = generateVedtak().copy(
                fom = fixedTime.toLocalDate(),
                tom = fixedTime.toLocalDate().plusDays(30),
                createdAt = fixedTime,
            )
            infotrygdService.sendMessageToInfotrygd(vedtak)
            val payloadSlot = slot<String>()
            val correlationIdSlot = slot<UUID>()
            verify(exactly = 1) { mqSender.sendToMQ(capture(payloadSlot), capture(correlationIdSlot)) }
            val payload = payloadSlot.captured
            val expectedPayload = getFileAsString("src/test/resources/infotrygd.txt")
                .replace("PPPPPPPPPPP", UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                .replace("KKKK", UserConstants.KOMMUNE)
            assertEquals(expectedPayload, payload)
            assertEquals(vedtak.uuid, correlationIdSlot.captured)
        }
    }

    @Test
    fun `sends message to MQ for person in bydel`() {
        runBlocking {
            val vedtak = generateVedtak().copy(
                personident = UserConstants.ARBEIDSTAKER_PERSONIDENT_BYDEL,
                fom = fixedTime.toLocalDate(),
                tom = fixedTime.toLocalDate().plusDays(30),
                createdAt = fixedTime,
            )
            infotrygdService.sendMessageToInfotrygd(vedtak)
            val payloadSlot = slot<String>()
            verify(exactly = 1) { mqSender.sendToMQ(capture(payloadSlot), any()) }
            val payload = payloadSlot.captured
            val expectedPayload = getFileAsString("src/test/resources/infotrygd.txt")
                .replace("PPPPPPPPPPP", UserConstants.ARBEIDSTAKER_PERSONIDENT_BYDEL.value)
                .replace("KKKK", UserConstants.KOMMUNE_FOR_BYDEL)
            assertEquals(expectedPayload, payload)
        }
    }

    @Test
    fun `sends message to MQ for person in UTLAND`() {
        runBlocking {
            val vedtak = generateVedtak().copy(
                personident = UserConstants.ARBEIDSTAKER_PERSONIDENT_UTLAND,
                fom = fixedTime.toLocalDate(),
                tom = fixedTime.toLocalDate().plusDays(30),
                createdAt = fixedTime,
            )
            infotrygdService.sendMessageToInfotrygd(vedtak)
            val payloadSlot = slot<String>()
            verify(exactly = 1) { mqSender.sendToMQ(capture(payloadSlot), any()) }
            val payload = payloadSlot.captured
            val expectedPayload = getFileAsString("src/test/resources/infotrygd.txt")
                .replace("PPPPPPPPPPP", UserConstants.ARBEIDSTAKER_PERSONIDENT_UTLAND.value)
                .replace("KKKK", INFOTRYGD_BOSTED_UTLAND)
            assertEquals(expectedPayload, payload)
        }
    }

    @Test
    fun `send message to MQ fails for kode 6`() {
        runBlocking {
            val vedtak = generateVedtak().copy(
                personident = UserConstants.ARBEIDSTAKER_PERSONIDENT_GRADERT,
                fom = fixedTime.toLocalDate(),
                tom = fixedTime.toLocalDate().plusDays(30),
                createdAt = fixedTime,
            )
            val thrown = assertThrows<RuntimeException> {
                runBlocking { infotrygdService.sendMessageToInfotrygd(vedtak) }
            }
            verify(exactly = 0) { mqSender.sendToMQ(any(), any()) }
            assertTrue(thrown.message!!.startsWith("Cannot send to Infotrygd: bostedskommune missing"))
        }
    }
}

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
