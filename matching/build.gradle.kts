val innteksmeldingKontraktVersion = "2024.03.11-02-07-32abf"
val junitJupiterVersion = "5.10.2"

plugins {
    kotlin("jvm") version "1.9.22"
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

