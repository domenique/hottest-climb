package be.hottestclimb.persistence

import be.hottestclimb.domain.ClimbEntry
import be.hottestclimb.domain.ClimbEntryRepository
import be.hottestclimb.domain.Climber
import be.hottestclimb.domain.ClimberId
import be.hottestclimb.domain.ClimberRepository
import be.hottestclimb.domain.ClimberTotal
import be.hottestclimb.domain.NameTaken
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.ZoneOffset

private object Climbers : Table("climber") {
    val id = javaUUID("id")
    val name = varchar("name", 30)
    val pinHash = varchar("pin_hash", 100)
    override val primaryKey = PrimaryKey(id)
}

private object ClimbEntries : Table("climb_entry") {
    val id = javaUUID("id")
    val climberId = javaUUID("climber_id").references(Climbers.id)
    val meters = integer("meters")
    val loggedAt = timestampWithTimeZone("logged_at")
    override val primaryKey = PrimaryKey(id)
}

class ExposedClimberRepository(private val db: Database) : ClimberRepository {
    override fun findByName(name: String): Climber? = transaction(db) {
        Climbers.selectAll()
            .where { Climbers.name.lowerCase() eq name.lowercase() }
            .singleOrNull()
            ?.let { Climber(ClimberId(it[Climbers.id]), it[Climbers.name], it[Climbers.pinHash]) }
    }

    override fun save(climber: Climber) {
        try {
            transaction(db) {
                Climbers.insert {
                    it[id] = climber.id.value
                    it[name] = climber.name
                    it[pinHash] = climber.pinHash
                }
            }
        } catch (e: ExposedSQLException) {
            // Two registrations racing past the service's findByName check.
            if (e.sqlState == UNIQUE_VIOLATION) throw NameTaken(climber.name) else throw e
        }
    }

    private companion object {
        const val UNIQUE_VIOLATION = "23505"
    }
}

class ExposedClimbEntryRepository(private val db: Database) : ClimbEntryRepository {
    override fun save(entry: ClimbEntry) {
        transaction(db) {
            ClimbEntries.insert {
                it[id] = entry.id
                it[climberId] = entry.climberId.value
                it[meters] = entry.meters
                it[loggedAt] = entry.loggedAt.atOffset(ZoneOffset.UTC)
            }
        }
    }

    override fun totalFor(climberId: ClimberId): Int = transaction(db) {
        val total = ClimbEntries.meters.sum()
        ClimbEntries.select(total)
            .where { ClimbEntries.climberId eq climberId.value }
            .single()[total] ?: 0
    }

    override fun totals(): List<ClimberTotal> = transaction(db) {
        val total = ClimbEntries.meters.sum()
        (Climbers innerJoin ClimbEntries)
            .select(Climbers.name, total)
            .groupBy(Climbers.id, Climbers.name)
            .map { ClimberTotal(it[Climbers.name], it[total] ?: 0) }
    }
}
