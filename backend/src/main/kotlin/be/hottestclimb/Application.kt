package be.hottestclimb

import at.favre.lib.crypto.bcrypt.BCrypt
import be.hottestclimb.domain.ClimbService
import be.hottestclimb.domain.PinHasher
import be.hottestclimb.persistence.DatabaseConfig
import be.hottestclimb.persistence.Databases
import be.hottestclimb.persistence.ExposedClimbEntryRepository
import be.hottestclimb.persistence.ExposedClimberRepository
import be.hottestclimb.web.api
import io.ktor.server.application.Application
import java.time.Clock

fun Application.module() {
    val config = environment.config
    val db = Databases.connect(
        DatabaseConfig(
            jdbcUrl = config.property("database.url").getString(),
            user = config.property("database.user").getString(),
            password = config.property("database.password").getString(),
        )
    )
    val service = ClimbService(
        climbers = ExposedClimberRepository(db),
        entries = ExposedClimbEntryRepository(db),
        pinHasher = BCryptPinHasher,
        clock = Clock.systemUTC(),
    )
    api(service)
}

object BCryptPinHasher : PinHasher {
    override fun hash(pin: String): String = BCrypt.withDefaults().hashToString(10, pin.toCharArray())
    override fun matches(pin: String, hash: String) = BCrypt.verifyer().verify(pin.toCharArray(), hash).verified
}
