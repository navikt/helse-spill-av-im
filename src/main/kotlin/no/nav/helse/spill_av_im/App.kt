package no.nav.helse.spill_av_im

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import java.time.Duration

private val logg = LoggerFactory.getLogger(::main.javaClass)
private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

fun main() {
    val env = System.getenv()

    val hikariConfig = HikariConfig().apply {
        jdbcUrl = String.format("jdbc:postgresql://%s:%s/%s", env.getValue("DATABASE_HOST"), env.getValue("DATABASE_PORT"), env.getValue("DATABASE_DATABASE"))
        username = env.getValue("DATABASE_USERNAME")
        password = env.getValue("DATABASE_PASSWORD")
        maximumPoolSize = 2
        initializationFailTimeout = Duration.ofMinutes(20).toMillis()
    }

    val dataSource by lazy { HikariDataSource(hikariConfig) }
    val dao = InntektsmeldingDao { dataSource }

    RapidApplication.create(env)
        .apply {
            if (System.getenv("NAIS_CLUSTER_NAME") == "dev-gcp") SlettPersonRiver(this, dao)
            InntektsmeldingRegistrertRiver(this, dao)
            InntektsmeldingHåndtertRiver(this, dao)
            TrengerInntektsmeldingReplay(this, dao)
            register(object : RapidsConnection.StatusListener {
                override fun onStartup(rapidsConnection: RapidsConnection) {
                    HikariDataSource(hikariConfig).use { ds ->
                        Flyway.configure()
                            .dataSource(ds)
                            .validateMigrationNaming(true)
                            .load()
                            .migrate()
                    }
                }
            })
        }
        .start()
}
