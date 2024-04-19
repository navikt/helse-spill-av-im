package no.nav.helse.spill_av_im

import no.nav.inntektsmeldingkontrakt.*
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class TrengerInntektsmeldingReplayTest {
    private companion object {
        private val JANUAR_1 = LocalDate.of(2018, 1, 1)
        private val JANUAR_16 = LocalDate.of(2018, 1, 16)
        private val JANUAR_17 = LocalDate.of(2018, 1, 17)
        private val JANUAR_18 = LocalDate.of(2018, 1, 18)
        private val JANUAR_31 = LocalDate.of(2018, 1, 31)
        private val FEBRUAR_1 = LocalDate.of(2018, 2, 1)
        private val FEBRUAR_5 = LocalDate.of(2018, 2, 5)
    }

    @Test
    fun `trenger arbeidsgiverperiode - relevant hvis arbeidsgiverperioden overlapper`() {
        val forespørsel = forespørsel(
            skjæringstidspunkt = JANUAR_1,
            førsteFraværsdag = JANUAR_1,
            sykmeldingsperioder = listOf(
                TrengerInntektsmeldingReplay.Periode(JANUAR_1, JANUAR_31)
            ),
            egenmeldinger = emptyList(),
            harForespurtArbeidsgiverperiode = true
        )
        val im = im(
            arbeidsgiverperiode = listOf(Periode(JANUAR_1, JANUAR_16)),
            førsteFraværsdag = JANUAR_1,
            begrunnelseForReduksjonEllerIkkeUtbetalt = null
        )

        assertTrue(forespørsel.erInntektsmeldingRelevant(im))
    }

    @Test
    fun `trenger arbeidsgiverperiode - relevant hvis første fraværsdag er mindre enn 20 dager arbeidsgiverperiode`() {
        val forespørsel = forespørsel(
            skjæringstidspunkt = JANUAR_1,
            førsteFraværsdag = JANUAR_1,
            sykmeldingsperioder = listOf(
                TrengerInntektsmeldingReplay.Periode(JANUAR_1, JANUAR_31)
            ),
            egenmeldinger = emptyList(),
            harForespurtArbeidsgiverperiode = true
        )
        val im = im(
            arbeidsgiverperiode = listOf(Periode(JANUAR_1, JANUAR_16)),
            førsteFraværsdag = FEBRUAR_1,
            begrunnelseForReduksjonEllerIkkeUtbetalt = null
        )

        assertTrue(forespørsel.erInntektsmeldingRelevant(im))
    }

    @Test
    fun `trenger arbeidsgiverperiode - ikke relevant hvis første fraværsdag er mer enn 20 dager arbeidsgiverperiode`() {
        val forespørsel = forespørsel(
            skjæringstidspunkt = JANUAR_1,
            førsteFraværsdag = JANUAR_1,
            sykmeldingsperioder = listOf(
                TrengerInntektsmeldingReplay.Periode(JANUAR_1, JANUAR_31)
            ),
            egenmeldinger = emptyList(),
            harForespurtArbeidsgiverperiode = true
        )
        val im = im(
            arbeidsgiverperiode = listOf(Periode(JANUAR_1, JANUAR_16)),
            førsteFraværsdag = FEBRUAR_5,
            begrunnelseForReduksjonEllerIkkeUtbetalt = null
        )

        assertFalse(forespørsel.erInntektsmeldingRelevant(im))
    }

    @Test
    fun `trenger arbeidsgiverperiode - relevant hvis arbeidsgiverperioden er rett før`() {
        val forespørsel = forespørsel(
            skjæringstidspunkt = JANUAR_17,
            førsteFraværsdag = JANUAR_17,
            sykmeldingsperioder = listOf(
                TrengerInntektsmeldingReplay.Periode(JANUAR_17, JANUAR_31)
            ),
            egenmeldinger = emptyList(),
            harForespurtArbeidsgiverperiode = true
        )
        val im = im(
            arbeidsgiverperiode = listOf(Periode(JANUAR_1, JANUAR_16)),
            førsteFraværsdag = JANUAR_1,
            begrunnelseForReduksjonEllerIkkeUtbetalt = null
        )

        assertTrue(forespørsel.erInntektsmeldingRelevant(im))
    }

    @Test
    fun `trenger arbeidsgiverperiode - relevant hvis første fraværsdag overlapper og reduksjon oppgitt`() {
        val forespørsel = forespørsel(
            skjæringstidspunkt = JANUAR_17,
            førsteFraværsdag = JANUAR_17,
            sykmeldingsperioder = listOf(
                TrengerInntektsmeldingReplay.Periode(JANUAR_17, JANUAR_31)
            ),
            egenmeldinger = emptyList(),
            harForespurtArbeidsgiverperiode = true
        )
        val im = im(
            arbeidsgiverperiode = emptyList(),
            førsteFraværsdag = JANUAR_17,
            begrunnelseForReduksjonEllerIkkeUtbetalt = "Ferie"
        )

        assertTrue(forespørsel.erInntektsmeldingRelevant(im))
    }

    @Test
    fun `trenger arbeidsgiverperiode - ikke relevant hvis første fraværsdag ikke overlapper og reduksjon oppgitt`() {
        val forespørsel = forespørsel(
            skjæringstidspunkt = JANUAR_17,
            førsteFraværsdag = JANUAR_17,
            sykmeldingsperioder = listOf(
                TrengerInntektsmeldingReplay.Periode(JANUAR_17, JANUAR_31)
            ),
            egenmeldinger = emptyList(),
            harForespurtArbeidsgiverperiode = true
        )
        val im = im(
            arbeidsgiverperiode = emptyList(),
            førsteFraværsdag = JANUAR_1,
            begrunnelseForReduksjonEllerIkkeUtbetalt = "Ferie"
        )

        assertFalse(forespørsel.erInntektsmeldingRelevant(im))
    }

    @Test
    fun `trenger ikke arbeidsgiverperiode - relevant hvis første fraværsdag overlapper`() {
        val forespørsel = forespørsel(
            skjæringstidspunkt = JANUAR_18,
            førsteFraværsdag = JANUAR_18,
            sykmeldingsperioder = listOf(
                TrengerInntektsmeldingReplay.Periode(JANUAR_18, JANUAR_31)
            ),
            egenmeldinger = emptyList(),
            harForespurtArbeidsgiverperiode = false
        )
        val im = im(
            arbeidsgiverperiode = listOf(Periode(JANUAR_1, JANUAR_16)),
            førsteFraværsdag = JANUAR_18,
            begrunnelseForReduksjonEllerIkkeUtbetalt = null
        )

        assertTrue(forespørsel.erInntektsmeldingRelevant(im))
    }

    @Test
    fun `trenger ikke arbeidsgiverperiode - ikke relevant hvis første fraværsdag er før`() {
        val forespørsel = forespørsel(
            skjæringstidspunkt = JANUAR_18,
            førsteFraværsdag = JANUAR_18,
            sykmeldingsperioder = listOf(
                TrengerInntektsmeldingReplay.Periode(JANUAR_18, JANUAR_31)
            ),
            egenmeldinger = emptyList(),
            harForespurtArbeidsgiverperiode = false
        )
        val im = im(
            arbeidsgiverperiode = listOf(Periode(JANUAR_1, JANUAR_16)),
            førsteFraværsdag = JANUAR_1,
            begrunnelseForReduksjonEllerIkkeUtbetalt = null
        )

        assertFalse(forespørsel.erInntektsmeldingRelevant(im))
    }

    private fun forespørsel(
        skjæringstidspunkt: LocalDate,
        førsteFraværsdag: LocalDate,
        sykmeldingsperioder: List<TrengerInntektsmeldingReplay.Periode>,
        egenmeldinger: List<TrengerInntektsmeldingReplay.Periode>,
        harForespurtArbeidsgiverperiode: Boolean = true
    ) = TrengerInntektsmeldingReplay.Forespørsel(
        fnr = "",
        aktørId = "",
        orgnr = "",
        vedtaksperiodeId = UUID.randomUUID(),
        skjæringstidspunkt = skjæringstidspunkt,
        førsteFraværsdager = listOf(
            TrengerInntektsmeldingReplay.FørsteFraværsdag(
                orgnr = "",
                dato = førsteFraværsdag
            )
        ),
        sykmeldingsperioder = sykmeldingsperioder,
        egenmeldinger = egenmeldinger,
        harForespurtArbeidsgiverperiode = harForespurtArbeidsgiverperiode
    )

    private fun im(
        arbeidsgiverperiode: List<Periode>,
        førsteFraværsdag: LocalDate?,
        begrunnelseForReduksjonEllerIkkeUtbetalt: String? = null
    ) = Inntektsmelding(
        inntektsmeldingId = UUID.randomUUID().toString(),
        arbeidstakerFnr = "fnr",
        arbeidstakerAktorId = "aktør",
        virksomhetsnummer = "orgnr",
        arbeidsgiverFnr = null,
        arbeidsgiverAktorId = null,
        arbeidsgivertype = Arbeidsgivertype.VIRKSOMHET,
        arbeidsforholdId = null,
        beregnetInntekt = BigDecimal.ZERO,
        refusjon = Refusjon(BigDecimal.ZERO, null),
        endringIRefusjoner = emptyList(),
        opphoerAvNaturalytelser = emptyList(),
        gjenopptakelseNaturalytelser = emptyList(),
        arbeidsgiverperioder = arbeidsgiverperiode,
        status = Status.GYLDIG,
        arkivreferanse = "",
        ferieperioder = emptyList(),
        foersteFravaersdag = førsteFraværsdag,
        mottattDato = LocalDateTime.now(),
        begrunnelseForReduksjonEllerIkkeUtbetalt = begrunnelseForReduksjonEllerIkkeUtbetalt,
        naerRelasjon = null,
        innsenderTelefon = "",
        innsenderFulltNavn = "",
        avsenderSystem = AvsenderSystem("LPS", "V1.0")
    )
}
