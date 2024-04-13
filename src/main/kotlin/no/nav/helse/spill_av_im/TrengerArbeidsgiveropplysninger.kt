package no.nav.helse.spill_av_im

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.LocalDate
import java.util.*

internal class TrengerArbeidsgiveropplysninger(
    rapidsConnection: RapidsConnection,
    private val dao: InntektsmeldingDao
): River.PacketListener {

    private companion object {
        private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
        private val logg = LoggerFactory.getLogger(TrengerArbeidsgiveropplysninger::class.java)
    }

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "trenger_opplysninger_fra_arbeidsgiver")
                it.requireKey("@id", "fødselsnummer", "organisasjonsnummer", "vedtaksperiodeId")
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
                it.requireArray("forespurteOpplysninger") {
                    requireKey("opplysningstype")
                }
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        logg.info("Håndterer ikke trenger_opplysninger_fra_arbeidsgiver pga. problem: se sikker logg")
        sikkerlogg.info("Håndterer ikke trenger_opplysninger_fra_arbeidsgiver pga. problem: {}", problems.toExtendedReport())
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        MDC.putCloseable("meldingsreferanseId", packet["@id"].asText()).use {
            val vedtaksperiodeId = packet["vedtaksperiodeId"].asText().toUUID()
            MDC.putCloseable("vedtaksperiodeId", vedtaksperiodeId.toString()).use {
                val forespørsel = Forespørsel(
                    fnr = packet["fødselsnummer"].asText(),
                    orgnr = packet["organisasjonsnummer"].asText(),
                    vedtaksperiodeId = vedtaksperiodeId,
                    skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate(),
                    førsteFraværsdager = packet["førsteFraværsdager"].map { FørsteFraværsdag(it.path("organisasjonsnummer").asText(), it.path("førsteFraværsdag").asLocalDate()) },
                    sykmeldingsperioder = packet["sykmeldingsperioder"].map { Periode(it.path("fom").asLocalDate(), it.path("tom").asLocalDate()) },
                    egenmeldinger = packet["egenmeldingsperioder"].map { Periode(it.path("fom").asLocalDate(), it.path("tom").asLocalDate()) },
                    harForespurtArbeidsgiverperiode = packet["forespurteOpplysninger"].any {
                        it.path("opplysningstype").asText() == "Arbeidsgiverperiode"
                    }
                )
                håndterForespørselOmInntektsmelding(forespørsel)
            }
        }
    }

    private fun håndterForespørselOmInntektsmelding(forespørsel: Forespørsel) {
        logg.info("Håndterer trenger_opplysninger_fra_arbeidsgiver")
        sikkerlogg.info("Håndterer trenger_opplysninger_fra_arbeidsgiver:\n\t$forespørsel")

        val inntektsmeldinger = dao.finnUhåndterteInntektsmeldinger(
            fnr = forespørsel.fnr,
            orgnr = forespørsel.orgnr
        )

        if (inntektsmeldinger.isEmpty()) return ingenUhåndterteInntektsmeldinger()

        val aktuelleForReplay = inntektsmeldinger.filter { forespørsel.erInntektsmeldingRelevant(it) }
        if (aktuelleForReplay.isEmpty()) return ingenAktuelleInntektsmeldinger()

        logg.info("Ville replayet ${aktuelleForReplay.size} inntektsmeldinger")
        sikkerlogg.info("Ville replayet ${aktuelleForReplay.size} inntektsmeldinger")
    }

    private fun ingenUhåndterteInntektsmeldinger() {
        logg.info("Fant ingen uhåndterte inntektsmeldinger")
        sikkerlogg.info("Fant ingen uhåndterte inntektsmeldinger")
    }

    private fun ingenAktuelleInntektsmeldinger() {
        logg.info("Fant ingen relevante inntektsmeldinger til tross for at det er uhåndterte inntektsmeldinger")
        sikkerlogg.info("Fant ingen relevante inntektsmeldinger til tross for at det er uhåndterte inntektsmeldinger")
    }

    data class Forespørsel(
        val fnr: String,
        val orgnr: String,
        val vedtaksperiodeId: UUID,
        val skjæringstidspunkt: LocalDate,
        val førsteFraværsdager: List<FørsteFraværsdag>,
        val sykmeldingsperioder: List<Periode>,
        val egenmeldinger: List<Periode>,
        val harForespurtArbeidsgiverperiode: Boolean
    ) {
        private val førsteFraværsdag = førsteFraværsdager.firstOrNull { it.orgnr == orgnr }

        private fun overlapperPeriodeMedForespørsel(dato: LocalDate) = overlapperPeriodeMedForespørsel(Periode(dato, dato))
        private fun overlapperPeriodeMedForespørsel(datoperiode: Periode): Boolean {
            return (førsteFraværsdag != null && datoperiode.overlapper(førsteFraværsdag.dato))
                    || sykmeldingsperioder.any { datoperiode.overlapper(it) }
                    || egenmeldinger.any { datoperiode.overlapper(it) }
        }

        fun erInntektsmeldingRelevant(inntektsmeldingDto: InntektsmeldingDto): Boolean {
            return false
        }
    }
    data class Periode(val fom: LocalDate, val tom: LocalDate) {
        fun overlapper(dato: LocalDate) = dato in fom..tom
        fun overlapper(other: Periode): Boolean {
            return maxOf(this.fom, other.fom) <= minOf(this.tom, other.tom)
        }
    }
    data class FørsteFraværsdag(val orgnr: String, val dato: LocalDate)
}