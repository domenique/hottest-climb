package be.hottestclimb.web

import be.hottestclimb.domain.Leaderboard
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(val name: String, val pin: String)

@Serializable
data class ClimberResponse(val name: String)

@Serializable
data class LogClimbRequest(val name: String, val pin: String, val meters: Int)

@Serializable
data class LogClimbResponse(val name: String, val total: Int)

@Serializable
data class LeaderboardRowResponse(val rank: Int, val names: List<String>, val meters: Int)

@Serializable
data class LeaderboardResponse(val rows: List<LeaderboardRowResponse>, val grandTotal: Int, val climberCount: Int)

@Serializable
data class ErrorResponse(val error: String)

fun Leaderboard.toResponse() = LeaderboardResponse(
    rows = rows.map { LeaderboardRowResponse(it.rank, it.names, it.meters) },
    grandTotal = grandTotal,
    climberCount = climberCount,
)
