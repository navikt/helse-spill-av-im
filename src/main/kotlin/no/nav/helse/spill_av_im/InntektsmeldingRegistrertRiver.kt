package no.nav.helse.spill_av_im

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.util.*

internal class InntektsmeldingRegistrertRiver(
    rapidsConnection: RapidsConnection,
    private val dao: InntektsmeldingDao
): River.PacketListener {

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(InntektsmeldingRegistrertRiver::class.java)
        private val zoneId = ZoneId.systemDefault()
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "inntektsmelding")
                it.requireKey("@id", "inntektsmeldingId", "arbeidstakerFnr")
                it.interestedIn("virksomhetsnummer", "avsenderSystem.navn")
                it.require("mottattDato", JsonNode::asLocalDateTime)
                it.interestedIn("inntektsdato", JsonNode::asLocalDate)
                it.interestedIn("foersteFravaersdag", JsonNode::asLocalDate)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.info("Håndterer ikke inntektsmelding pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke inntektsmelding pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val internId = packet["@id"].asUUID()
        logg.info("Håndterer inntektsmelding {}", kv("meldingsreferanseId", internId))
        sikkerlogg.info("Håndterer inntektsmelding {}", kv("meldingsreferanseId", internId))
        dao.lagreInntektsmelding(
            fnr = packet["arbeidstakerFnr"].asText(),
            virksomhetsnummer = packet["virksomhetsnummer"].takeIf(JsonNode::isTextual)?.asText(),
            eksternId = packet["inntektsmeldingId"].asUUID(),
            internId = internId,
            innsendt = packet["mottattDato"].asLocalDateTime().atZone(zoneId),
            avsendersystem = packet["avsenderSystem.navn"].takeIf(JsonNode::isTextual)?.asText(),
            førsteFraværsdag = packet["foersteFravaersdag"].asOptionalLocalDate(),
            inntektsdato = packet["inntektsdato"].asOptionalLocalDate(),
            data = packet.toJson()
        )
    }

    private fun JsonNode.asUUID() = UUID.fromString(asText())
}