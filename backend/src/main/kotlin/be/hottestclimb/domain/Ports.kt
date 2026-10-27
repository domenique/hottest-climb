package be.hottestclimb.domain

interface ClimberRepository {
    // Case-insensitive: "alex" and "Alex" are the same climber.
    fun findByName(name: String): Climber?
    fun save(climber: Climber)
}

interface ClimbEntryRepository {
    fun save(entry: ClimbEntry)
    fun totalFor(climberId: ClimberId): Int
    fun totals(): List<ClimberTotal>
}

interface PinHasher {
    fun hash(pin: String): String
    fun matches(pin: String, hash: String): Boolean
}
