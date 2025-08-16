// finn ny versjon her: https://github.com/navikt/inntektsmelding-kontrakt/packages/36094
val innteksmeldingKontraktVersion = "2025.01.09-03-43-0eb17"
val junitJupiterVersion = "5.12.1"
val jacksonVersion = "2.18.3"

plugins {
    kotlin("jvm") version "2.2.10"
    `maven-publish`
}

dependencies {
    constraints {
        api("com.fasterxml.jackson:jackson-bom:$jacksonVersion") {
            because("Alle moduler skal bruke samme versjon av jackson")
        }
    }
    api("no.nav.sykepenger.kontrakter:inntektsmelding-kontrakt:$innteksmeldingKontraktVersion")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks {
    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("skipped", "failed")
        }
    }
}

configure<JavaPluginExtension> {
    withSourcesJar()
}

configure<PublishingExtension> {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.github.navikt.spill_av_im"
            artifactId = project.name
            version = "${project.version}"
        }
    }
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/navikt/helse-spill-av-im")
            credentials {
                username = "x-access-token"
                password = System.getenv("GITHUB_PASSWORD")
            }
        }
    }
}

