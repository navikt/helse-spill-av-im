import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("no.nav.sykepenger.kotlin")
    `maven-publish`
}

dependencies {
    api(libs.inntektsmeldingKontrakt)
}

// Biblioteket brukes av spleis, som fortsatt kjører Java 21
java {
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.add("-Xjdk-release=21")
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
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
