package be.hottestclimb.domain

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

// Per-name rather than per-IP: the whole gym shares one wifi address.
// In-memory is fine for a single instance; a restart simply forgives everyone.
class PinLockout(
    private val clock: Clock,
    private val maxFailures: Int = 5,
    private val lockDuration: Duration = Duration.ofMinutes(5),
) {
    private data class Failures(val count: Int, val lockedUntil: Instant?)

    private val failures = ConcurrentHashMap<String, Failures>()

    fun requireNotLocked(name: String) {
        val lockedUntil = failures[key(name)]?.lockedUntil ?: return
        if (clock.instant() < lockedUntil) throw TooManyAttempts()
        failures.remove(key(name))
    }

    fun recordFailure(name: String) {
        failures.compute(key(name)) { _, current ->
            val count = (current?.count ?: 0) + 1
            Failures(count, if (count >= maxFailures) clock.instant() + lockDuration else null)
        }
    }

    fun recordSuccess(name: String) {
        failures.remove(key(name))
    }

    private fun key(name: String) = name.trim().lowercase()
}
