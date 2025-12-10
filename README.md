Spill av inntektsmelding
========================

Appen registrerer alle innsendte inntektsmeldinger og hvorvidt de er behandlet eller ikke.
Når Spleis ber om det, så replayes uhåndterte inntektsmeldinger.

Det er Spleis som bestemmer hvorvidt noe er håndtert.

## Events

- `inntektsmelding_håndtert` – sendes av spleis
- `inntektsmelding` – sendes av spedisjon
- `trenger_inntektsmelding_replay`sendes av spleis

## Essensen

- sender ut `inntektsmeldinger_replay` på uhåndtere inntektsmeldinger som matcher 

## Jeg har lyst til å teste endringene mine i `matching`-lib'en lokalt i spleis uten at spill-av-im blir deployet med endringene!

```bash
# Til info: printer ut forskjellige tasks som er tilgjengelige i matching-modulen
gradle :mathcing:tasks

# Bygger modulen og laster den opp på din lokale maven repository (.m2/repository)
gradle :matching:publishToMavenLocal -Pversion=<fyll-inn-min-versjon-her> 

# inne i spleis må du huske å:
# 1. legge til mavenLocal() som repository i build.gradle.kts 
# 2. bruke <fyll-inn-min-versjon-her>-versjonen av matching-lib'en
```

# Henvendelser
Spørsmål knyttet til koden eller prosjektet kan stilles som issues her på GitHub.

## For NAV-ansatte
Interne henvendelser kan sendes via Slack i kanalen #team-bømlo-værsågod.
