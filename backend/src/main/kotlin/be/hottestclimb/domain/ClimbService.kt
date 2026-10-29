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
        val cleanName = Rules.cleanName(name)
        Rules.requireValidPin(pin)
        if (climbers.findByName(cleanName) != null) throw NameTaken(cleanName)

        val climber = Climber(ClimberId(UUID.randomUUID()), cleanName, pinHasher.hash(pin))
        climbers.save(climber)
        return climber
    }

    fun logClimb(name: String, pin: String, meters: Int): Int {
        Rules.requireValidMeters(meters)
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
        return Leaderboard(
            rows = rows,
            grandTotal = totals.sumOf { it.meters },
            climberCount = totals.size,
            raisedEuros = entries.count(paid = true) * Rules.EUROS_PER_ENTRY,
            pendingEuros = entries.count(paid = false) * Rules.EUROS_PER_ENTRY,
        )
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
}
