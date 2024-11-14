package no.nav.helse.spill_av_im

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import java.time.ZoneId
import java.util.*

internal class InntektsmeldingHåndtertRiver(
    rapidsConnection: RapidsConnection,
    private val dao: InntektsmeldingDao
): River.PacketListener {

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(InntektsmeldingHåndtertRiver::class.java)
        private val zoneId = ZoneId.systemDefault()
    }

    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "inntektsmelding_håndtert") }
            validate {
                it.requireKey("inntektsmeldingId", "fødselsnummer", "vedtaksperiodeId")
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        logg.info("Håndterer ikke inntektsmelding_håndtert pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke inntektsmelding_håndtert pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val internId = packet["inntektsmeldingId"].asUUID()
        logg.info("Håndterer inntektsmelding_håndtert {}", kv("meldingsreferanseId", internId))
        sikkerlogg.info("Håndterer inntektsmelding_håndtert {}", kv("meldingsreferanseId", internId))
        dao.lagreHåndtering(
            fnr = packet["fødselsnummer"].asText(),
            vedtaksperiodeId = packet["vedtaksperiodeId"].asUUID(),
            internId = internId,
            håndtertTidspunkt = packet["@opprettet"].asLocalDateTime().atZone(zoneId)
        )
    }

    private fun JsonNode.asUUID() = UUID.fromString(asText())
}