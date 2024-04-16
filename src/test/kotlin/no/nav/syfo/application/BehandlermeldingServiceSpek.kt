package no.nav.syfo.application

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ExternalMockEnvironment
import no.nav.syfo.UserConstants
import no.nav.syfo.domain.Behandlermelding
import no.nav.syfo.domain.serialize
import no.nav.syfo.generator.generateBehandlermelding
import no.nav.syfo.generator.generateVedtak
import no.nav.syfo.infrastructure.database.dropData
import no.nav.syfo.infrastructure.database.getBehandlerMelding
import no.nav.syfo.infrastructure.database.repository.BehandlermeldingRepository
import no.nav.syfo.infrastructure.database.repository.VedtakRepository
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.kafka.BehandlermeldingProducer
import no.nav.syfo.infrastructure.kafka.BehandlermeldingRecord
import no.nav.syfo.infrastructure.mock.mockedJournalpostId
import no.nav.syfo.util.nowUTC
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldNotBeNull
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID
import java.util.concurrent.Future

class BehandlermeldingServiceSpek : Spek({

    val externalMockEnvironment = ExternalMockEnvironment.instance
    val database = externalMockEnvironment.database

    val mockBehandlerMeldingProducer = mockk<KafkaProducer<String, BehandlermeldingRecord>>(relaxed = true)
    val behandlermeldingProducer = BehandlermeldingProducer(producer = mockBehandlerMeldingProducer)
    val behandlermeldingRepository = BehandlermeldingRepository(database = database)
    val journalforingService = JournalforingService(
        dokarkivClient = externalMockEnvironment.dokarkivClient,
        pdlClient = externalMockEnvironment.pdlClient,
        dialogmeldingBehandlerClient = externalMockEnvironment.dialogmeldingBehandlerClient
    )

    val behandlermeldingService = BehandlermeldingService(
        behandlermeldingRepository = behandlermeldingRepository,
        behandlermeldingProducer = behandlermeldingProducer,
        journalforingService = journalforingService,
    )

    val vedtakRepository = VedtakRepository(database = database)

    fun createBehandlermelding(behandlerRef: UUID = UserConstants.BEHANDLER_REF): Behandlermelding = vedtakRepository.createVedtak(
        vedtak = generateVedtak(),
        vedtakPdf = UserConstants.PDF_VEDTAK,
        behandlermelding = generateBehandlermelding(behandlerRef = behandlerRef),
        behandlermeldingPdf = UserConstants.PDF_BEHANDLER_MELDING,
    ).second

    beforeEachTest {
        clearAllMocks()
        coEvery { mockBehandlerMeldingProducer.send(any()) } returns mockk<Future<RecordMetadata>>(relaxed = true)
    }

    afterEachTest {
        database.dropData()
    }

    describe("publishBehandlermeldinger") {
        it("publishes unpublished behandlermelding") {
            val behandlermelding = createBehandlermelding()

            val (success, failed) = behandlermeldingService.publishBehandlermeldinger().partition { it.isSuccess }

            failed.size shouldBeEqualTo 0
            success.size shouldBeEqualTo 1

            val producerRecordSlot = slot<ProducerRecord<String, BehandlermeldingRecord>>()
            verify(exactly = 1) { mockBehandlerMeldingProducer.send(capture(producerRecordSlot)) }

            val publishedBehandlermelding = success.first().getOrThrow()
            publishedBehandlermelding.uuid shouldBeEqualTo behandlermelding.uuid

            val pBehandlerMelding = database.getBehandlerMelding(publishedBehandlermelding.uuid)!!
            pBehandlerMelding.uuid shouldBeEqualTo behandlermelding.uuid
            pBehandlerMelding.publishedAt.shouldNotBeNull()

            behandlermeldingRepository.getUnpublishedBehandlermeldinger().shouldBeEmpty()

            val behandlermeldingRecord = producerRecordSlot.captured.value()
            behandlermeldingRecord.behandlerRef shouldBeEqualTo behandlermelding.behandlerRef
            behandlermeldingRecord.personIdent shouldBeEqualTo UserConstants.ARBEIDSTAKER_PERSONIDENT.value

            behandlermeldingRecord.dialogmeldingTekst shouldBeEqualTo behandlermelding.document.serialize()
            behandlermeldingRecord.dialogmeldingVedlegg[0] shouldBeEqualTo UserConstants.PDF_BEHANDLER_MELDING[0]
            behandlermeldingRecord.dialogmeldingType shouldBeEqualTo "DIALOG_NOTAT"
            behandlermeldingRecord.dialogmeldingKodeverk shouldBeEqualTo "8127"
            behandlermeldingRecord.dialogmeldingKode shouldBeEqualTo 2
        }

        it("publishes nothing when no behandlermeldinger") {
            val results = behandlermeldingService.publishBehandlermeldinger()

            results.shouldBeEmpty()
            verify(exactly = 0) { mockBehandlerMeldingProducer.send(any()) }
        }

        it("published nothing when behandlermelding already published") {
            val behandlermelding = createBehandlermelding()
            behandlermeldingRepository.update(behandlermelding.copy(publishedAt = nowUTC()))

            val results = behandlermeldingService.publishBehandlermeldinger()

            results.shouldBeEmpty()
            verify(exactly = 0) { mockBehandlerMeldingProducer.send(any()) }
        }

        it("fails publishing when kafka-producer fails") {
            val behandlermelding = createBehandlermelding()

            every { mockBehandlerMeldingProducer.send(any()) } throws Exception("Error producing to kafka")

            val (success, failed) = behandlermeldingService.publishBehandlermeldinger().partition { it.isSuccess }
            failed.size shouldBeEqualTo 1
            success.size shouldBeEqualTo 0

            verify(exactly = 1) { mockBehandlerMeldingProducer.send(any()) }

            val vurderingList = behandlermeldingRepository.getUnpublishedBehandlermeldinger()
            vurderingList.size shouldBeEqualTo 1
            vurderingList.first().second.uuid shouldBeEqualTo behandlermelding.uuid
        }
    }

    describe("journalforBehandlermeldinger") {
        it("journalfører behandlermeldinger som ikke er journalført") {
            createBehandlermelding()

            val journalforteBehandlermeldinger = runBlocking { behandlermeldingService.journalforBehandlermeldinger() }

            val (success, failed) = journalforteBehandlermeldinger.partition { it.isSuccess }

            failed.shouldBeEmpty()
            success.size shouldBeEqualTo 1

            val journalfortBehandlermelding = success.first().getOrThrow()
            journalfortBehandlermelding.journalpostId shouldBeEqualTo mockedJournalpostId

            val pBehandlermelding = database.getBehandlerMelding(behandlerMeldingUuid = journalfortBehandlermelding.uuid)!!
            pBehandlermelding.updatedAt shouldBeGreaterThan pBehandlermelding.createdAt
            pBehandlermelding.journalpostId shouldBeEqualTo mockedJournalpostId
        }

        it("journalfører ikke når ingen behandlermeldinger") {
            val journalforteBehandlermeldinger = runBlocking { behandlermeldingService.journalforBehandlermeldinger() }

            journalforteBehandlermeldinger.shouldBeEmpty()
        }

        it("journalfører ikke når behandlermelding allerede er journalført") {
            val behandlermelding = createBehandlermelding()
            val journalfortBehandlermelding = behandlermelding.copy(journalpostId = mockedJournalpostId)
            behandlermeldingRepository.update(journalfortBehandlermelding)

            val journalforteBehandlermeldinger = runBlocking { behandlermeldingService.journalforBehandlermeldinger() }

            journalforteBehandlermeldinger.shouldBeEmpty()
        }

        it("journalføring feiler") {
            createBehandlermelding(behandlerRef = UUID.randomUUID())

            val journalforteBehandlermeldinger = runBlocking { behandlermeldingService.journalforBehandlermeldinger() }

            val (success, failed) = journalforteBehandlermeldinger.partition { it.isSuccess }

            failed.size shouldBeEqualTo 1
            success.shouldBeEmpty()
        }
    }
})
