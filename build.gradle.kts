val rapidsAndRiversVersion = "2026011411051768385145.e8ebad1177b4"
val flywayCoreVersion = "12.9.0"
val hikariCPVersion = "6.3.0"
val postgresqlVersion = "42.7.11"
val kotliqueryVersion = "1.9.1"

val tbdLibsVersion = "20260616.1253"
val junitJupiterVersion = "6.1.0"

plugins {
    kotlin("jvm") version "2.4.0"
}

allprojects {
    // Sett opp repositories basert på om vi kjører i CI eller ikke
    // Jf. https://github.com/navikt/utvikling/blob/main/docs/teknisk/Konsumere%20biblioteker%20fra%20Github%20Package%20Registry.md
    repositories {
        mavenCentral()
        if (providers.environmentVariable("GITHUB_ACTIONS").orNull == "true") {
            maven {
                url = uri("https://maven.pkg.github.com/navikt/maven-release")
                credentials {
                    username = "token"
                    password = providers.environmentVariable("GITHUB_TOKEN").orNull!!
                }
            }
        } else {
            maven("https://repo.adeo.no/repository/github-package-registry-navikt/")
        }
    }
}

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")

    api("org.flywaydb:flyway-database-postgresql:$flywayCoreVersion")
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    implementation(project("matching"))

    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:$tbdLibsVersion")
    testImplementation("com.github.navikt.tbd-libs:postgres-testdatabaser:$tbdLibsVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitJupiterVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of("21"))
    }
}

tasks {
    withType<Jar> {
        archiveBaseName.set("app")

        manifest.attributes(
            mapOf(
                "Main-Class" to "no.nav.helse.spill_av_im.AppKt",
                "Class-Path" to configurations.runtimeClasspath
                    .get()
                    .joinToString(" ") { it.name }
            )
        )
    }

    val copyDeps = register<Sync>("copyDeps") {
        description = "Kopierer runtime-avhengigheter til libs-mappa"
        from(configurations.runtimeClasspath)
        into(layout.buildDirectory.dir("libs"))
    }
    named("assemble") {
        dependsOn(copyDeps)
    }

    withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("skipped", "failed")
        }
        systemProperty("junit.jupiter.execution.parallel.enabled", "true")
        systemProperty("junit.jupiter.execution.parallel.mode.default", "concurrent")
        systemProperty("junit.jupiter.execution.parallel.config.strategy", "fixed")
        systemProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "4")
    }
}

