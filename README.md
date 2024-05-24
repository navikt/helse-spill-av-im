spill av inntektsmelding
========================

Appen registrerer alle innsendte inntektsmeldinger og hvorvidt de er behandlet eller ikke.
Når Spleis ber om det, så replayes uhåndterte inntektsmeldinger.

Det er Spleis som bestemmer hvorvidt noe er håndtert.

## events

- `inntektsmelding_håndtert` – sendes av spleis
- `inntektsmelding` – sendes av spedisjon
- `trenger_inntektsmelding_replay`sendes av spleis

## essensen

- sender ut `inntektsmeldinger_replay` på uhåndtere inntektsmeldinger som matcher 

# Henvendelser
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

## For NAV-ansatte
Interne henvendelser kan sendes via Slack i kanalen #team-bømlo-værsågod.
