package no.nav.syfo.infrastructure.infotrygd

import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.application.mq.MQSender
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.Month

class InfotrygdServiceSpek : Spek({
    val externalMockEnvironment = ExternalMockEnvironment.instance
    val mqSender = mockk<MQSender>(relaxed = true)

    val infotrygdService = InfotrygdService(
        mqQueueName = externalMockEnvironment.environment.mq.mqQueueName,
        mqSender = mqSender,
    )

    describe(InfotrygdService::class.java.simpleName) {
        it("sends message to MQ") {
            val now = LocalDateTime.of(2024, Month.MARCH, 1, 12, 30, 23)
            infotrygdService.sendMessageToInfotrygd(
                personident = UserConstants.ARBEIDSTAKER_PERSONIDENT,
                veilederId = "A123456",
                navKontor = "0219",
                now = now,
                datoFra = now.toLocalDate(),
                datoTil = now.toLocalDate().plusDays(30),
            )
            val payloadSlot = slot<String>()
            verify(exactly = 1) {
                mqSender.sendToMQ(any(), capture(payloadSlot))
            }
            val payload = payloadSlot.captured
            val expectedPayload = getFileAsString("src/test/resources/infotrygd.xml")
            payload shouldBeEqualTo expectedPayload
        }
    }
})

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
