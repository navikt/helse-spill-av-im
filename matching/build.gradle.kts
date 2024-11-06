// finn ny versjon her: https://github.com/navikt/inntektsmelding-kontrakt/packages/36094
val innteksmeldingKontraktVersion = "2024.05.21-09-56-5528e"
val junitJupiterVersion = "5.10.2"

plugins {
    kotlin("jvm") version "2.0.21"
    `maven-publish`
}

dependencies {
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

