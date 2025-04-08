package no.nav.helse.spill_av_im

import no.nav.inntektsmeldingkontrakt.Inntektsmelding
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*

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

    /**
     *  Disclaimer: Denne tabellen er i skrivende stund i tråd med koden, men den er her mest for tøys. 🙊
     * ┌────────────────────────┬──────────────────────┬─────────────────┬─────────────────────────────────┬─────────────────────────────────────────┐
     * │ Opplysninger bedt om   │ Redusert utbetaling? │ Er AGP oppgitt? │ Er Første fraværsdag etter AGP? │                                         │
     * ├────────────────────────┼──────────────────────┼─────────────────┼─────────────────────────────────┼─────────────────────────────────────────┤
     * │ AGP, Inntekt, Refusjon │ Nei                  │ Nei             │ (ikke relevant)                 │ Inntektsmelding er antatt ikke relevant │
     * ├────────────────────────┼──────────────────────┼─────────────────┼─────────────────────────────────┼─────────────────────────────────────────┤
     * │ AGP, Inntekt, Refusjon │ Nei                  │ Ja              │ Ja                              │ Første fraværsdag må overlappe,         │
     * │                        │                      │                 │                                 │   men bare hvis avstanden mellom FF og  │
     * │                        │                      │                 │                                 │   siste dag i AGP er under 20.          │
     * │                        │                      │                 │                                 │   Med mindre det gjelder ferie,         │
     * │                        │                      │                 │                                 │   eller TidligereVirksomhet.            │
     * │                        │                      │                 │                                 │   Ellers må AGP overlappe               │
     * ├────────────────────────┼──────────────────────┼─────────────────┼─────────────────────────────────┼─────────────────────────────────────────┤
     * │ AGP, Inntekt, Refusjon │ Nei                  │ Ja              │ Nei                             │ AGP må overlappe                        │
     * ├────────────────────────┼──────────────────────┼─────────────────┼─────────────────────────────────┼─────────────────────────────────────────┤
     * │ AGP, Inntekt, Refusjon │ Ja                   │ Nei             │ (ikke relevant)                 │ Første fraværsdag må overlappe          │
     * ├────────────────────────┼──────────────────────┼─────────────────┼─────────────────────────────────┼─────────────────────────────────────────┤
     * │ AGP, Inntekt, Refusjon │ Ja                   │ Ja              │ Nei                             │ AGP må overlappe                        │
     * ├────────────────────────┼──────────────────────┼─────────────────┼─────────────────────────────────┼─────────────────────────────────────────┤
     * │ AGP, Inntekt, Refusjon │ Ja                   │ Ja              │ Ja                              │ Første fraværsdag må overlappe,         │
     * │                        │                      │                 │                                 │   men bare hvis avstanden mellom FF og  │
     * │                        │                      │                 │                                 │   siste dag i AGP er under 20.          │
     * │                        │                      │                 │                                 │   Med mindre det gjelder ferie,         │
     * │                        │                      │                 │                                 │   eller TidligereVirksomhet.            │
     * │                        │                      │                 │                                 │   Ellers må AGP overlappe               │
     * ├────────────────────────┼──────────────────────┼─────────────────┼─────────────────────────────────┼─────────────────────────────────────────┤
     * │////////////////////////│//////////////////////│/////////////////│/////////////////////////////////│/////////////////////////////////////////│
     * ├────────────────────────┼──────────────────────┼─────────────────┼─────────────────────────────────┼─────────────────────────────────────────┤
     * │ Inntekt, Refusjon      │ (ikke relevant)      │ Ja              │ Ja                              │ Første fraværsdag må overlappe          │
     * ├────────────────────────┼──────────────────────┼─────────────────┼─────────────────────────────────┼─────────────────────────────────────────┤
     * │ Inntekt, Refusjon      │ (ikke relevant)      │ Ja              │ Nei                             │ Siste dag i AGP må overlappe            │
     * ├────────────────────────┼──────────────────────┼─────────────────┼─────────────────────────────────┼─────────────────────────────────────────┤
     * │ Inntekt, Refusjon      │ (ikke relevant)      │ Nei             │ Ja                              │ Første fraværsdag må overlappe          │
     * ├────────────────────────┼──────────────────────┼─────────────────┼─────────────────────────────────┼─────────────────────────────────────────┤
     * │ Inntekt, Refusjon      │ (ikke relevant)      │ Nei             │ Nei                             │ Ugyldig IM, ikke mulig å overlappsjekke │
     * └────────────────────────┴──────────────────────┴─────────────────┴─────────────────────────────────┴─────────────────────────────────────────┘
     */
    fun erInntektsmeldingRelevant(inntektsmelding: Inntektsmelding): Boolean {
        if (inntektsmelding.avsenderSystem?.navn in listOf("NAV_NO", "NAV_NO_SELVBESTEMT") ) return false
        return erRelevantForArbeidsgiverperiode(inntektsmelding) || erRelevantForInntektEllerRefusjon(inntektsmelding)
    }

    private fun erRelevantForInntektEllerRefusjon(im: Inntektsmelding): Boolean {
        // om forespørselen har bedt om arbeidsgiverperiode så brukes den som utgangspunkt for relevans
        if (harForespurtArbeidsgiverperiode) return false
        // for forespørsler som ikke trenger AGP så må forespørselen overlappe
        // med første fraværsdag (eller AGP om første fraværsdag ikke er oppgitt/er inni AGP)
        val sisteDag = im.arbeidsgiverperioder.maxOfOrNull { it.tom }
        val foersteFravaersdag = im.foersteFravaersdag?.takeIf { sisteDag == null || it > sisteDag }
        val dato = foersteFravaersdag ?: sisteDag ?: return false

        // dagen må overlappe med forespørselens første fraværsdag, en sykmeldings- eller egenmeldingsperiode
        return dato == førsteFraværsdag?.dato
                || sykmeldingsperioder.any { it.overlapperEllerRettFør(dato) }
                || egenmeldinger.any { it.overlapperEllerRettFør(dato) }
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

        // inntektsmelding uten oppgitt AGP er kun relevant
        // for forespørselen dersom begrunnelse for redusert utbetaling er satt og første fraværsdag overlapper på et vis
        if (sisteDag == null) {
            // ikke oppgitt AGP og heller ikke redusert utbetaling => im gjelder nok ikke agp
            if (!redusertUtbetaling) return false
            // ikke oppgitt agp og ikke oppgitt ff => ugyldig im
            if (foersteFravaersdag == null) return false
            // ikke oppgitt agp og begrunnelse for reduksjon oppgitt => tolk ff
            return overlapperPeriodeMedForespørsel(foersteFravaersdag)
        }

        // første fraværsdag er kun relevant om den er oppgitt etter AGP
        // og det er et maksimalt antall dager mellom FF og siste dag i AGP.
        if (foersteFravaersdag != null && foersteFravaersdag > sisteDag) {
            val dagerMellom = ChronoUnit.DAYS.between(sisteDag, foersteFravaersdag)
            val tillaterStorAvstandMellomAgpOgFørsteFraværsdag =
                im.begrunnelseForReduksjonEllerIkkeUtbetalt == "FerieEllerAvspasering" || im.begrunnelseForReduksjonEllerIkkeUtbetalt == "TidligereVirksomhet"
            if (!tillaterStorAvstandMellomAgpOgFørsteFraværsdag && dagerMellom >= MAKS_ANTALL_DAGER_MELLOM_FØRSTE_FRAVÆRSDAG_OG_AGP_FOR_HÅNDTERING_AV_DAGER) return false
            if (overlapperPeriodeMedForespørsel(foersteFravaersdag)) return true
        }
        // om første fraværsdag ikke er relevant, eller ikke overlapper, så hensyntar vi arbeidsgiverperioden til slutt
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

    fun overlapper(dato: LocalDate) = dato in fom..tom
    private fun overlapper(other: Periode): Boolean {
        return maxOf(this.fom, other.fom) <= minOf(this.tom, other.tom)
    }
}
data class FørsteFraværsdag(val orgnr: String, val dato: LocalDate)
