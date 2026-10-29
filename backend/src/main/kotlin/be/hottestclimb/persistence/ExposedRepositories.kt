package be.hottestclimb.persistence

import be.hottestclimb.domain.AdminEntry
import be.hottestclimb.domain.ClimbEntry
import be.hottestclimb.domain.ClimbEntryRepository
import be.hottestclimb.domain.Climber
import be.hottestclimb.domain.ClimberId
import be.hottestclimb.domain.ClimberRepository
import be.hottestclimb.domain.ClimberSummary
import be.hottestclimb.domain.ClimberTotal
import be.hottestclimb.domain.NameTaken
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.sum
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
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
    val paid = bool("paid")
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

    override fun summaries(): List<ClimberSummary> = transaction(db) {
        val entryCount = ClimbEntries.id.count()
        val total = ClimbEntries.meters.sum()
        (Climbers leftJoin ClimbEntries)
            .select(Climbers.id, Climbers.name, entryCount, total)
            .groupBy(Climbers.id, Climbers.name)
            .map { ClimberSummary(ClimberId(it[Climbers.id]), it[Climbers.name], it[entryCount].toInt(), it[total] ?: 0) }
    }

    override fun updatePinHash(id: ClimberId, pinHash: String): Boolean = transaction(db) {
        Climbers.update({ Climbers.id eq id.value }) { it[Climbers.pinHash] = pinHash } > 0
    }

    override fun delete(id: ClimberId): Boolean = transaction(db) {
        Climbers.deleteWhere { Climbers.id eq id.value } > 0
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
                it[paid] = entry.paid
            }
        }
    }

    override fun totalFor(climberId: ClimberId): Int = transaction(db) {
        val total = ClimbEntries.meters.sum()
        ClimbEntries.select(total)
            .where { ClimbEntries.climberId eq climberId.value }
            .single()[total] ?: 0
    }

    override fun count(paid: Boolean): Int = transaction(db) {
        ClimbEntries.selectAll().where { ClimbEntries.paid eq paid }.count().toInt()
    }

    override fun totals(): List<ClimberTotal> = transaction(db) {
        val total = ClimbEntries.meters.sum()
        (Climbers innerJoin ClimbEntries)
            .select(Climbers.name, total)
            .groupBy(Climbers.id, Climbers.name)
            .map { ClimberTotal(it[Climbers.name], it[total] ?: 0) }
    }

    override fun all(): List<AdminEntry> = transaction(db) {
        (ClimbEntries innerJoin Climbers)
            .selectAll()
            .orderBy(ClimbEntries.loggedAt, SortOrder.DESC)
            .map {
                AdminEntry(
                    id = it[ClimbEntries.id],
                    climberId = ClimberId(it[ClimbEntries.climberId]),
                    climberName = it[Climbers.name],
                    meters = it[ClimbEntries.meters],
                    loggedAt = it[ClimbEntries.loggedAt].toInstant(),
                    paid = it[ClimbEntries.paid],
                )
            }
    }

    override fun updateMeters(id: UUID, meters: Int): Boolean = transaction(db) {
        ClimbEntries.update({ ClimbEntries.id eq id }) { it[ClimbEntries.meters] = meters } > 0
    }

    override fun setPaid(id: UUID, paid: Boolean): Boolean = transaction(db) {
        ClimbEntries.update({ ClimbEntries.id eq id }) { it[ClimbEntries.paid] = paid } > 0
    }

    override fun delete(id: UUID): Boolean = transaction(db) {
        ClimbEntries.deleteWhere { ClimbEntries.id eq id } > 0
    }
}
