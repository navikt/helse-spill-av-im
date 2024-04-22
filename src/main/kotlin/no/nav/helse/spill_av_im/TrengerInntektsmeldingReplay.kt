package no.nav.helse.spill_av_im

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.*
import no.nav.inntektsmeldingkontrakt.Inntektsmelding
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

internal class TrengerInntektsmeldingReplay(
    rapidsConnection: RapidsConnection,
    private val dao: InntektsmeldingDao
): River.PacketListener {

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(TrengerInntektsmeldingReplay::class.java)
        private val objectMapper = jacksonObjectMapper()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        private const val MAKSIMALT_ANTALL_INNTEKTSMELDINGER = 10
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "trenger_inntektsmelding_replay")
                it.requireKey("@id", "fødselsnummer", "aktørId", "organisasjonsnummer", "vedtaksperiodeId")
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.require("skjæringstidspunkt", JsonNode::asLocalDate)
                it.requireArray("sykmeldingsperioder") {
                    require("fom", JsonNode::asLocalDate)
                    require("tom", JsonNode::asLocalDate)
                }
                it.requireArray("egenmeldingsperioder") {
                    require("fom", JsonNode::asLocalDate)
                    require("tom", JsonNode::asLocalDate)
                }
                it.requireArray("førsteFraværsdager") {
                    requireKey("organisasjonsnummer")
                    require("førsteFraværsdag", JsonNode::asLocalDate)
                }
                it.requireKey("trengerArbeidsgiverperiode")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.info("Håndterer ikke trenger_inntektsmelding_replay pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke trenger_inntektsmelding_replay pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        MDC.putCloseable("meldingsreferanseId", packet["@id"].asText()).use {
            val vedtaksperiodeId = packet["vedtaksperiodeId"].asText().toUUID()
            MDC.putCloseable("vedtaksperiodeId", vedtaksperiodeId.toString()).use {
                val forespørsel = Forespørsel(
                    fnr = packet["fødselsnummer"].asText(),
                    aktørId = packet["aktørId"].asText(),
                    orgnr = packet["organisasjonsnummer"].asText(),
                    vedtaksperiodeId = vedtaksperiodeId,
                    skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate(),
                    førsteFraværsdager = packet["førsteFraværsdager"].map { FørsteFraværsdag(it.path("organisasjonsnummer").asText(), it.path("førsteFraværsdag").asLocalDate()) },
                    sykmeldingsperioder = packet["sykmeldingsperioder"].map { Periode(it.path("fom").asLocalDate(), it.path("tom").asLocalDate()) },
                    egenmeldinger = packet["egenmeldingsperioder"].map { Periode(it.path("fom").asLocalDate(), it.path("tom").asLocalDate()) },
                    harForespurtArbeidsgiverperiode = packet["trengerArbeidsgiverperiode"].asBoolean()
                )
                val aktuelleForReplay = håndterForespørselOmInntektsmelding(forespørsel)
                replayInntektsmeldinger(context, forespørsel, aktuelleForReplay, packet["@opprettet"].asLocalDateTime())
            }
        }
    }

    private fun replayInntektsmeldinger(context: MessageContext, forespørsel: Forespørsel, aktuelleForReplay: List<Triple<Long, UUID, Inntektsmelding>>, innsendt: LocalDateTime) {
        val inntektsmeldinger = aktuelleForReplay.take(MAKSIMALT_ANTALL_INNTEKTSMELDINGER)
        val replayId = dao.nyReplayforespørsel(forespørsel.fnr, forespørsel.orgnr, forespørsel.vedtaksperiodeId, innsendt, inntektsmeldinger.map { it.first })
        val melding = JsonMessage.newMessage("inntektsmeldinger_replay", mapOf(
            "fødselsnummer" to forespørsel.fnr,
            "aktørId" to forespørsel.aktørId,
            "organisasjonsnummer" to forespørsel.orgnr,
            "vedtaksperiodeId" to forespørsel.vedtaksperiodeId,
            "replayId" to replayId,
            "inntektsmeldinger" to inntektsmeldinger
                .map { (_, internDokumentId, im) ->
                    val inntektsmeldingSomMap = objectMapper.convertValue<Map<String, Any?>>(im)
                    mapOf(
                        "internDokumentId" to internDokumentId,
                        "inntektsmelding" to inntektsmeldingSomMap
                    )
                }
        ))
        sikkerlogg.info("publiserer: ${melding.toJson()}")
        context.publish(melding.toJson())
    }

    private fun håndterForespørselOmInntektsmelding(forespørsel: Forespørsel): List<Triple<Long, UUID, Inntektsmelding>> {
        logg.info("Håndterer trenger_inntektsmelding_replay")
        sikkerlogg.info("Håndterer trenger_inntektsmelding_replay:\n\t$forespørsel")

        val inntektsmeldinger = dao.finnUhåndterteInntektsmeldinger(
            fnr = forespørsel.fnr,
            orgnr = forespørsel.orgnr
        )

        if (inntektsmeldinger.isEmpty()) {
            ingenUhåndterteInntektsmeldinger()
            return emptyList()
        }

        val aktuelleForReplay = inntektsmeldinger
            .mapNotNull { dto ->
                dto.inntektsmelding.getOrElse { err ->
                    logg.info("Kunne ikke tolke inntektsmelding fordi: ${err.message}", err)
                    sikkerlogg.info("Kunne ikke tolke inntektsmelding fordi: ${err.message}", err)
                    null
                }?.let {
                    Triple(dto.id, dto.internDokumentId, it)
                }
            }
            .filter { (_, _, im) -> forespørsel.erInntektsmeldingRelevant(im) }
        if (aktuelleForReplay.isEmpty()) ingenAktuelleInntektsmeldinger()

        logg.info("Vil replaye ${aktuelleForReplay.size} inntektsmeldinger")
        sikkerlogg.info("Vil replaye ${aktuelleForReplay.size} inntektsmeldinger:\n${aktuelleForReplay.joinToString(separator = "\n\n")}")
        return aktuelleForReplay
    }

    private fun ingenUhåndterteInntektsmeldinger() {
        logg.info("Fant ingen uhåndterte inntektsmeldinger")
        sikkerlogg.info("Fant ingen uhåndterte inntektsmeldinger")
    }

    private fun ingenAktuelleInntektsmeldinger() {
        logg.info("Fant ingen relevante inntektsmeldinger til tross for at det er uhåndterte inntektsmeldinger")
        sikkerlogg.info("Fant ingen relevante inntektsmeldinger til tross for at det er uhåndterte inntektsmeldinger")
    }
}