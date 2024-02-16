package no.nav.helse.spill_av_im

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.test_support.CleanupStrategy
import com.github.navikt.tbd_libs.test_support.DatabaseContainers
import com.github.navikt.tbd_libs.test_support.TestDataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.rapids_rivers.testsupport.TestRapid
import no.nav.inntektsmeldingkontrakt.*
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class E2ETest {
    private companion object {
        const val FNR = "12345678911"
        const val A1 = "987654321"
        const val A2 = "112233445"
        private val databaseContainer = DatabaseContainers.container("spekemat", CleanupStrategy.tables("inntektsmelding,handtering,replay_foresporsel,replay"))
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    private lateinit var dataSource: TestDataSource
    private val dao = InntektsmeldingDao { dataSource.ds }

    private val testRapid = TestRapid().apply {
        InntektsmeldingRegistrertRiver(this, dao)
        InntektsmeldingHåndtertRiver(this, dao)
    }

    @BeforeEach
    fun setup() {
        dataSource = databaseContainer.nyTilkobling()
    }

    @AfterEach
    fun teardown() {
        databaseContainer.droppTilkobling(dataSource)
        testRapid.reset()
    }

    @Test
    fun `registrerer inntektsmelding`() {
        val internId = UUID.randomUUID()
        testRapid.sendTestMessage(lagInntektsmelding(internId))
        verifiserInntektsmeldingFinnes(internId)
    }


    @Test
    fun `håndterer inntektsmelding`() {
        val internId = UUID.randomUUID()
        val vedtaksperiodeId = UUID.randomUUID()
        testRapid.sendTestMessage(lagInntektsmelding(internId))
        testRapid.sendTestMessage(lagInntektsmeldingHåndtert(internId, vedtaksperiodeId))
        verifiserInntektsmeldingHåndtert(internId, vedtaksperiodeId)
    }

    private fun verifiserInntektsmeldingFinnes(id: UUID) {
        @Language("PostgreSQL")
        val stmt = "SELECT EXISTS(SELECT 1 FROM inntektsmelding WHERE intern_dokument_id = ?)"
        assertEquals(true, sessionOf(dataSource.ds).use {
            it.run(queryOf(stmt, id).map { row -> row.boolean(1) }.asSingle)
        })
    }

    private fun verifiserInntektsmeldingHåndtert(id: UUID, vedtaksperiodeId: UUID) {
        @Language("PostgreSQL")
        val stmt = "SELECT EXISTS(SELECT 1 FROM handtering WHERE vedtaksperiode_id = ? AND inntektsmelding_id = (SELECT id FROM inntektsmelding WHERE intern_dokument_id = ?))"
        assertEquals(true, sessionOf(dataSource.ds).use {
            it.run(queryOf(stmt, vedtaksperiodeId, id).map { row -> row.boolean(1) }.asSingle)
        })
    }

    private fun lagInntektsmeldingHåndtert(
        internId: UUID,
        vedtaksperiodeId: UUID = UUID.randomUUID(),
        håndterttidspunkt: LocalDateTime = LocalDateTime.now()
    ): String {
        @Language("JSON")
        val body = """{
            "@event_name": "inntektsmelding_håndtert",
            "@opprettet": "$håndterttidspunkt",
            "inntektsmeldingId": "$internId",
            "fødselsnummer": "$FNR",
            "vedtaksperiodeId": "$vedtaksperiodeId"
        }"""
        return body
    }

    private fun lagInntektsmelding(
        internId: UUID = UUID.randomUUID(),
        eksternId: UUID = UUID.randomUUID(),
        virksomhetsnummer: String? = A1,
        førsteFraværsdag: LocalDate? = LocalDate.of(2018, 1, 1),
        inntektsdato: LocalDate? = LocalDate.of(2018, 1, 1),
        mottattidspunkt: LocalDateTime = LocalDateTime.now(),
        avsendersystem: String? = "NAV_NO"
    ) = Inntektsmelding(
        inntektsmeldingId = eksternId.toString(),
        arbeidstakerFnr = FNR,
        virksomhetsnummer = virksomhetsnummer,
        arbeidsgiverperioder = emptyList(),
        foersteFravaersdag = førsteFraværsdag,
        inntektsdato = inntektsdato,
        avsenderSystem = avsendersystem?.let { AvsenderSystem(it, "1.0.0") },
        arbeidsgivertype = Arbeidsgivertype.VIRKSOMHET,
        arbeidsgiverAktorId = null,
        arbeidstakerAktorId = "",
        arkivreferanse = "",
        endringIRefusjoner = emptyList(),
        ferieperioder = emptyList(),
        gjenopptakelseNaturalytelser = emptyList(),
        innsenderFulltNavn = "",
        innsenderTelefon = "",
        mottattDato = mottattidspunkt,
        naerRelasjon = null,
        opphoerAvNaturalytelser = emptyList(),
        refusjon = Refusjon(null, null),
        status = Status.GYLDIG
    ).let { dto ->
        objectMapper.convertValue<Map<String, Any?>>(dto) + mapOf(
            "@id" to internId,
            "@event_name" to "inntektsmelding"
        )
    }.let { jsonMap ->
        objectMapper.writeValueAsString(jsonMap)
    }
}