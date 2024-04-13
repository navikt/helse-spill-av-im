package no.nav.helse.spill_av_im

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import no.nav.inntektsmeldingkontrakt.Inntektsmelding
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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

        val aktuelleForReplay = inntektsmeldinger
            .mapNotNull {
                it.inntektsmelding.getOrElse { err ->
                    logg.info("Kunne ikke tolke inntektsmelding fordi: ${err.message}", err)
                    sikkerlogg.info("Kunne ikke tolke inntektsmelding fordi: ${err.message}", err)
                    null
                }
            }
            .filter { forespørsel.erInntektsmeldingRelevant(it) }
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
            return (førsteFraværsdag != null && Periode(førsteFraværsdag.dato, førsteFraværsdag.dato).overlapperEllerRettFør(datoperiode))
                    || sykmeldingsperioder.any { it.overlapperEllerRettFør(datoperiode) }
                    || egenmeldinger.any { it.overlapperEllerRettFør(datoperiode) }
        }

        fun erInntektsmeldingRelevant(inntektsmelding: Inntektsmelding): Boolean {
            return erRelevantForArbeidsgiverperiode(inntektsmelding)
        }

        // hvis vedtaksperioden har bedt om arbeidsgiverperiode så må
        // agp i inntektsmeldingen overlappe med forespørselen, eller så må
        // første fraværsdag overlappe – men kun viss avstanden mellom ff og agp er OK
        private fun erRelevantForArbeidsgiverperiode(im: Inntektsmelding): Boolean {
            if (!harForespurtArbeidsgiverperiode) return false
            return inntektsmeldingGjelderArbeidsgiverperiode(im)
        }

        private fun inntektsmeldingGjelderArbeidsgiverperiode(im: Inntektsmelding): Boolean {
            val sisteDag = im.arbeidsgiverperioder.maxOfOrNull { it.tom }
            val redusertUtbetaling = im.begrunnelseForReduksjonEllerIkkeUtbetalt != null
            val foersteFravaersdag = im.foersteFravaersdag

            if (sisteDag == null) {
                // ikke oppgitt AGP og heller ikke redusert utbetaling => im gjelder nok ikke agp
                if (!redusertUtbetaling) return false
                // ikke oppgitt agp og ikke oppgitt ff => ugyldig im
                if (foersteFravaersdag == null) return false
                // ikke oppgitt agp og begrunnelse for reduksjon oppgitt => tolk ff
                return overlapperPeriodeMedForespørsel(foersteFravaersdag)
            }

            if (foersteFravaersdag != null && foersteFravaersdag > sisteDag) {
                val dagerMellom = ChronoUnit.DAYS.between(sisteDag, foersteFravaersdag)
                if (dagerMellom >= MAKS_ANTALL_DAGER_MELLOM_FØRSTE_FRAVÆRSDAG_OG_AGP_FOR_HÅNDTERING_AV_DAGER) return false
                if (overlapperPeriodeMedForespørsel(foersteFravaersdag)) return true
            }
            return im.arbeidsgiverperioder.any { overlapperPeriodeMedForespørsel(Periode(it.fom, it.tom)) }
        }

        private companion object {
            private const val MAKS_ANTALL_DAGER_MELLOM_FØRSTE_FRAVÆRSDAG_OG_AGP_FOR_HÅNDTERING_AV_DAGER = 20
        }
    }
    data class Periode(val fom: LocalDate, val tom: LocalDate) {
        fun overlapperEllerRettFør(dato: LocalDate) = overlapperEllerRettFør(Periode(dato, dato))
        fun overlapperEllerRettFør(periode: Periode) =
            when (periode.tom.dayOfWeek) {
                DayOfWeek.FRIDAY -> overlapper(Periode(periode.fom, periode.tom.plusDays(3)))
                DayOfWeek.SATURDAY -> overlapper(Periode(periode.fom, periode.tom.plusDays(2)))
                else -> overlapper(Periode(periode.fom, periode.tom.plusDays(1)))
            }

        private fun overlapper(dato: LocalDate) = dato in fom..tom
        private fun overlapper(other: Periode): Boolean {
            return maxOf(this.fom, other.fom) <= minOf(this.tom, other.tom)
        }
    }
    data class FørsteFraværsdag(val orgnr: String, val dato: LocalDate)
}