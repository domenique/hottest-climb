package be.hottestclimb.domain

import java.util.UUID

interface ClimberRepository {
    // Case-insensitive: "alex" and "Alex" are the same climber.
    fun findByName(name: String): Climber?
    fun save(climber: Climber)
    fun summaries(): List<ClimberSummary>
    fun updatePinHash(id: ClimberId, pinHash: String): Boolean
    fun delete(id: ClimberId): Boolean
}

interface ClimbEntryRepository {
    fun save(entry: ClimbEntry)
    fun totalFor(climberId: ClimberId): Int
    fun totals(): List<ClimberTotal>
    fun count(paid: Boolean): Int
    fun all(): List<AdminEntry>
    fun updateMeters(id: UUID, meters: Int): Boolean
    fun setPaid(id: UUID, paid: Boolean): Boolean
    fun delete(id: UUID): Boolean
}

interface PinHasher {
    fun hash(pin: String): String
    fun matches(pin: String, hash: String): Boolean
}
