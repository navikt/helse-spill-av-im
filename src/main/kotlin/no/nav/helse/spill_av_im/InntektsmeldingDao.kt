package no.nav.helse.spill_av_im

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Session
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.inntektsmeldingkontrakt.Inntektsmelding
import org.intellij.lang.annotations.Language
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

class InntektsmeldingDao(private val dataSource: () -> DataSource) {

    fun lagreInntektsmelding(
        fnr: String,
        virksomhetsnummer: String?,
        eksternId: UUID,
        internId: UUID,
        innsendt: ZonedDateTime,
        avsendersystem: String?,
        førsteFraværsdag: LocalDate?,
        inntektsdato: LocalDate?,
        data: String
    ) {
        sessionOf(dataSource()).use {
            it.run(queryOf(INSERT_IM, mapOf(
                "fnr" to fnr,
                "virknr" to virksomhetsnummer,
                "eid" to eksternId,
                "iid" to internId,
                "registrert" to ZonedDateTime.now(),
                "innsendt" to innsendt,
                "avsendersystem" to avsendersystem,
                "ff" to førsteFraværsdag,
                "inntektsdato" to inntektsdato,
                "data" to data
            )).asExecute)
        }
    }

    fun lagreHåndtering(fnr: String, internId: UUID, vedtaksperiodeId: UUID, håndtertTidspunkt: ZonedDateTime) {
        sessionOf(dataSource()).use {
            val inntektsmeldingId = it.finnInntektsmeldingId(internId) ?: return
            it.run(queryOf(HÅNDTERT_IM, mapOf(
                "fnr" to fnr,
                "vedtaksperiodeId" to vedtaksperiodeId,
                "internId" to internId,
                "inntektsmeldingId" to inntektsmeldingId,
                "handtert" to håndtertTidspunkt
            )).asExecute)
        }
    }

    fun finnUhåndterteInntektsmeldinger(fnr: String, orgnr: String): List<InntektsmeldingDto> {
        return sessionOf(dataSource()).use {
            it.run(queryOf(UHÅNDTERT_IM, fnr, orgnr).map { rad ->
                InntektsmeldingDto(
                    førsteFraværsdag = rad.localDateOrNull("forste_fravarsdag"),
                    data = rad.string("data")
                )
            }.asList)
        }
    }

    private fun Session.finnInntektsmeldingId(internId: UUID) =
        run(queryOf(FINN_IM_ID, mapOf("internId" to internId)).map { it.long("id") }.asSingle)

    private companion object {
        @Language("PostgreSQL")
        private const val INSERT_IM = """
            INSERT INTO inntektsmelding(fnr, virksomhetsnummer, ekstern_dokument_id, intern_dokument_id, registrert, innsendt, avsendersystem, forste_fravarsdag, inntektsdato, data)
            VALUES (:fnr, :virknr, :eid, :iid, :registrert, :innsendt, :avsendersystem, :ff, :inntektsdato, CAST(:data AS json))
            ON CONFLICT (intern_dokument_id) DO NOTHING
        """
        @Language("PostgreSQL")
        private const val FINN_IM_ID = """SELECT id FROM inntektsmelding WHERE intern_dokument_id=:internId"""
        @Language("PostgreSQL")
        private const val HÅNDTERT_IM = """
            INSERT INTO handtering(fnr, vedtaksperiode_id, inntektsmelding_id, handtert) 
            VALUES (:fnr, :vedtaksperiodeId, :inntektsmeldingId, :handtert)
        """
        @Language("PostgreSQL")
        private const val UHÅNDTERT_IM = """
            SELECT i.forste_fravarsdag, i.data
            FROM inntektsmelding i
            LEFT JOIN handtering h ON h.inntektsmelding_id=i.id 
            WHERE i.fnr = ? AND i.virksomhetsnummer = ? AND i.avsendersystem != 'NAV_NO' AND h.inntektsmelding_id IS NULL
        """
    }
}
data class InntektsmeldingDto(
    val førsteFraværsdag: LocalDate?,
    val data: String
) {
    private companion object {
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    }

    val inntektsmelding by lazy {
        runCatching {
            objectMapper.readValue<Inntektsmelding>(data)
        }
    }
}