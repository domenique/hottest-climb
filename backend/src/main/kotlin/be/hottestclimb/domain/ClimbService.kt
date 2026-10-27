package be.hottestclimb.domain

import java.time.Clock
import java.util.UUID

class ClimbService(
    private val climbers: ClimberRepository,
    private val entries: ClimbEntryRepository,
    private val pinHasher: PinHasher,
    private val clock: Clock,
) {
    private val lockout = PinLockout(clock)

    fun register(name: String, pin: String): Climber {
        val cleanName = validName(name)
        requireValidPin(pin)
        if (climbers.findByName(cleanName) != null) throw NameTaken(cleanName)

        val climber = Climber(ClimberId(UUID.randomUUID()), cleanName, pinHasher.hash(pin))
        climbers.save(climber)
        return climber
    }

    fun logClimb(name: String, pin: String, meters: Int): Int {
        if (meters !in METERS_PER_ENTRY) throw InvalidInput("Meters must be between 1 and ${METERS_PER_ENTRY.last}")
        val climber = authenticate(name, pin)

        entries.save(ClimbEntry(UUID.randomUUID(), climber.id, meters, clock.instant()))
        return entries.totalFor(climber.id)
    }

    fun leaderboard(): Leaderboard {
        val totals = entries.totals().sortedWith(compareByDescending<ClimberTotal> { it.meters }.thenBy { it.name })
        var climbersAhead = 0
        val rows = totals.groupBy { it.meters }.map { (meters, tied) ->
            LeaderboardRow(climbersAhead + 1, tied.map { it.name }, meters).also { climbersAhead += tied.size }
        }
        return Leaderboard(rows, totals.sumOf { it.meters }, totals.size)
    }

    private fun authenticate(name: String, pin: String): Climber {
        lockout.requireNotLocked(name)
        val climber = climbers.findByName(name.trim())
        if (climber == null || !pinHasher.matches(pin, climber.pinHash)) {
            lockout.recordFailure(name)
            throw InvalidCredentials()
        }
        lockout.recordSuccess(name)
        return climber
    }

    private fun validName(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.length !in NAME_LENGTH) throw InvalidInput("Name must be ${NAME_LENGTH.first}-${NAME_LENGTH.last} characters")
        return clean
    }

    private fun requireValidPin(pin: String) {
        if (!PIN_FORMAT.matches(pin)) throw InvalidInput("Pin must be 4 to 6 digits")
    }

    companion object {
        val METERS_PER_ENTRY = 1..500
        val NAME_LENGTH = 2..30
        private val PIN_FORMAT = Regex("\\d{4,6}")
    }
}
