val rapidsAndRiversVersion = "2024111509181731658731.11009b44c672"
val flywayCoreVersion = "10.21.0"
val hikariCPVersion = "6.1.0"
val postgresqlVersion = "42.7.4"
val kotliqueryVersion = "1.9.0"

val innteksmeldingKontraktVersion = "2024.03.11-02-07-32abf"
val tbdLibsVersion = "2024.11.15-09.09-08ca346b"
val junitJupiterVersion = "5.11.3"

plugins {
    kotlin("jvm") version "2.0.21"
}

allprojects {
    repositories {
        val githubPassword: String? by project
        mavenCentral()
        /* ihht. https://github.com/navikt/utvikling/blob/main/docs/teknisk/Konsumere%20biblioteker%20fra%20Github%20Package%20Registry.md
            så plasseres github-maven-repo (med autentisering) før nav-mirror slik at github actions kan anvende førstnevnte.
            Det er fordi nav-mirroret kjører i Google Cloud og da ville man ellers fått unødvendige utgifter til datatrafikk mellom Google Cloud og GitHub
         */
        maven {
            url = uri("https://maven.pkg.github.com/navikt/maven-release")
            credentials {
                username = "x-access-token"
                password = githubPassword
            }
        }
        maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
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

        doFirst {
            manifest {
                val runtimeClasspath by configurations
                attributes["Main-Class"] = "no.nav.helse.spill_av_im.AppKt"
                attributes["Class-Path"] = runtimeClasspath.joinToString(separator = " ") {
                    it.name
                }
            }
        }
    }

    val copyDeps by registering(Sync::class) {
        val runtimeClasspath by configurations
        from(runtimeClasspath)
        into("build/libs")
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

