package be.hottestclimb.domain

import java.time.Instant
import java.util.UUID

@JvmInline
value class ClimberId(val value: UUID)

data class Climber(val id: ClimberId, val name: String, val pinHash: String)

data class ClimbEntry(val id: UUID, val climberId: ClimberId, val meters: Int, val loggedAt: Instant)

data class ClimberTotal(val name: String, val meters: Int)

data class LeaderboardRow(val rank: Int, val names: List<String>, val meters: Int)

data class Leaderboard(val rows: List<LeaderboardRow>, val grandTotal: Int, val climberCount: Int)

sealed class ClimbError(message: String) : RuntimeException(message)
class NameTaken(name: String) : ClimbError("Name '$name' is already taken")
class InvalidCredentials : ClimbError("Unknown name or wrong pin")
class InvalidInput(message: String) : ClimbError(message)
class TooManyAttempts : ClimbError("Too many wrong pins, try again in a few minutes")
