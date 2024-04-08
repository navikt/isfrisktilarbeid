package no.nav.syfo.infrastructure.infotrygd

import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.mq.MQSender
import org.amshove.kluent.shouldBeEqualTo
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
    val mqSender = mockk<MQSender>(relaxed = true)

    val infotrygdService = InfotrygdService(
        mqQueueName = externalMockEnvironment.environment.mq.mqQueueName,
        mqSender = mqSender,
    )

    describe(InfotrygdService::class.java.simpleName) {
        it("sends message to MQ") {
            val fixedTime = OffsetDateTime.of(
                LocalDateTime.of(2024, Month.MARCH, 1, 12, 30, 23),
                ZoneOffset.UTC
            )
            val vedtak = generateVedtak().copy(
                fom = fixedTime.toLocalDate(),
                tom = fixedTime.toLocalDate().plusDays(30),
                createdAt = fixedTime,
            )
            infotrygdService.sendMessageToInfotrygd(vedtak)
            val payloadSlot = slot<String>()
            verify(exactly = 1) {
                mqSender.sendToMQ(any(), capture(payloadSlot))
            }
            val payload = payloadSlot.captured
            val expectedPayload = getFileAsString("src/test/resources/infotrygd.txt")
            payload shouldBeEqualTo expectedPayload
        }
    }
})

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
