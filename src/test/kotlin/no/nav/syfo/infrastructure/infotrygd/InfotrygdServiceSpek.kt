package no.nav.syfo.infrastructure.infotrygd

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.mq.InfotrygdMQSender
import org.amshove.kluent.internal.assertFailsWith
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldStartWith
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.Month
import java.time.OffsetDateTime
import java.time.ZoneOffset

class InfotrygdServiceSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val mqSender = mockk<InfotrygdMQSender>(relaxed = true)

    val infotrygdService = InfotrygdService(
        pdlClient = externalMockEnvironment.pdlClient,
        mqSender = mqSender,
    )
    val fixedTime = OffsetDateTime.of(
        LocalDateTime.of(2024, Month.MARCH, 1, 12, 30, 23),
        ZoneOffset.UTC
    )

    beforeEachTest {
        clearAllMocks()
        justRun { mqSender.sendToMQ(any(), any()) }
    }

    describe(InfotrygdService::class.java.simpleName) {
        it("sends message to MQ") {
            val vedtak = generateVedtak().copy(
                fom = fixedTime.toLocalDate(),
                tom = fixedTime.toLocalDate().plusDays(30),
                createdAt = fixedTime,
            )
            runBlocking {
                infotrygdService.sendMessageToInfotrygd(vedtak, 1)
            }
            val payloadSlot = slot<String>()
            val correlationIdSlot = slot<Int>()
            verify(exactly = 1) {
                mqSender.sendToMQ(capture(payloadSlot), capture(correlationIdSlot))
            }
            val payload = payloadSlot.captured
            val expectedPayload = getFileAsString("src/test/resources/infotrygd.txt")
                .replace("PPPPPPPPPPP", UserConstants.ARBEIDSTAKER_PERSONIDENT.value)
                .replace("KKKK", UserConstants.KOMMUNE)
            payload shouldBeEqualTo expectedPayload
            correlationIdSlot.captured shouldBeEqualTo 1
        }
        it("sends message to MQ for person in bydel") {
            val vedtak = generateVedtak().copy(
                personident = UserConstants.ARBEIDSTAKER_PERSONIDENT_BYDEL,
                fom = fixedTime.toLocalDate(),
                tom = fixedTime.toLocalDate().plusDays(30),
                createdAt = fixedTime,
            )
            runBlocking {
                infotrygdService.sendMessageToInfotrygd(vedtak, 1)
            }
            val payloadSlot = slot<String>()
            verify(exactly = 1) {
                mqSender.sendToMQ(capture(payloadSlot), any())
            }
            val payload = payloadSlot.captured
            val expectedPayload = getFileAsString("src/test/resources/infotrygd.txt")
                .replace("PPPPPPPPPPP", UserConstants.ARBEIDSTAKER_PERSONIDENT_BYDEL.value)
                .replace("KKKK", UserConstants.KOMMUNE_FOR_BYDEL)
            payload shouldBeEqualTo expectedPayload
        }
        it("send message to MQ fails for kode 6") {
            val vedtak = generateVedtak().copy(
                personident = UserConstants.ARBEIDSTAKER_PERSONIDENT_GRADERT,
                fom = fixedTime.toLocalDate(),
                tom = fixedTime.toLocalDate().plusDays(30),
                createdAt = fixedTime,
            )
            val thrown = assertFailsWith<RuntimeException> {
                runBlocking {
                    infotrygdService.sendMessageToInfotrygd(vedtak, 1)
                }
            }
            verify(exactly = 0) {
                mqSender.sendToMQ(any(), any())
            }
            thrown.message!! shouldStartWith "Cannot send to Infotrygd: bostedskommune missing"
        }
    }
})

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
