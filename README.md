![Build status](https://github.com/navikt/isfrisktilarbeid/workflows/main/badge.svg?branch=main)

# isfrisktilarbeid

Applikasjon for sending av vedtak for friskmelding til arbeidsformidling (§8-5). § 8-5 gjelder når man er såkalt
yrkesutfør.
Når man er for syk for å bli værende i nåværende jobb, men er frisk nok til en annen jobb.
Friskmelding til arbeidsformidling er en ytelse der man får sykepenger i 12 uker imens man leter etter ny jobb.
Etter denne perioden går man over på APP.

## Technologies used

* Docker
* Gradle
* Kafka (Soon)
* Kotlin
* Ktor
* Postgres (Soon)

##### Test Libraries:

* Kluent
* Mockk
* Spek

#### Requirements

* JDK 17

### Build

Run `./gradlew clean shadowJar`

### Lint (Ktlint)

##### Command line

Run checking: `./gradlew --continue ktlintCheck`

Run formatting: `./gradlew ktlintFormat`

##### Git Hooks

Apply checking: `./gradlew addKtlintCheckGitPreCommitHook`

Apply formatting: `./gradlew addKtlintFormatGitPreCommitHook`

## Contact

### For NAV employees

We are available at the Slack channel `#isyfo`.