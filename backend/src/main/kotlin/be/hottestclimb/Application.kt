package be.hottestclimb

import at.favre.lib.crypto.bcrypt.BCrypt
import be.hottestclimb.domain.ClimbService
import be.hottestclimb.domain.EntryAdmin
import be.hottestclimb.domain.PinHasher
import be.hottestclimb.persistence.DatabaseConfig
import be.hottestclimb.persistence.Databases
import be.hottestclimb.persistence.ExposedClimbEntryRepository
import be.hottestclimb.persistence.ExposedClimberRepository
import be.hottestclimb.web.api
import io.ktor.server.application.Application
import io.ktor.server.application.log
import java.time.Clock

fun Application.module() {
    val config = environment.config
    val db = Databases.connect(
        DatabaseConfig.from(
            url = config.property("database.url").getString(),
            user = config.propertyOrNull("database.user")?.getString(),
            password = config.propertyOrNull("database.password")?.getString(),
        )
    )
    val climbers = ExposedClimberRepository(db)
    val entries = ExposedClimbEntryRepository(db)
    val service = ClimbService(climbers, entries, BCryptPinHasher, Clock.systemUTC())
    val admin = EntryAdmin(climbers, entries, BCryptPinHasher)
    val adminPassword = config.propertyOrNull("admin.password")?.getString()
    when {
        adminPassword.isNullOrBlank() -> log.info("Admin API disabled: ADMIN_PASSWORD is not set")
        adminPassword == DEV_ADMIN_PASSWORD -> log.warn("Admin API uses the development password '$DEV_ADMIN_PASSWORD'")
    }
    api(service, admin, adminPassword)
}

private const val DEV_ADMIN_PASSWORD = "admin"

object BCryptPinHasher : PinHasher {
    override fun hash(pin: String): String = BCrypt.withDefaults().hashToString(10, pin.toCharArray())
    override fun matches(pin: String, hash: String) = BCrypt.verifyer().verify(pin.toCharArray(), hash).verified
}
