package be.hottestclimb.web

import be.hottestclimb.domain.AdminEntry
import be.hottestclimb.domain.ClimberId
import be.hottestclimb.domain.ClimberSummary
import be.hottestclimb.domain.EntryAdmin
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

private const val ADMIN_AUTH = "admin"

@Serializable
data class AdminEntryResponse(
    val id: String,
    val climberId: String,
    val climberName: String,
    val meters: Int,
    val loggedAt: String,
    val paid: Boolean,
)

@Serializable
data class ClimberSummaryResponse(val id: String, val name: String, val entryCount: Int, val meters: Int)

@Serializable
data class EntryChangeRequest(val meters: Int? = null, val paid: Boolean? = null)

@Serializable
data class PinResetRequest(val pin: String)

// Clients send base64(UTF-8 password) as the bearer token: a raw password may contain spaces or
// characters that aren't legal in a token, and Ktor rejects such a header before authenticating.
fun Application.installAdminAuth(adminPassword: String) {
    val expected = Base64.getEncoder().encodeToString(adminPassword.toByteArray()).toByteArray()
    install(Authentication) {
        bearer(ADMIN_AUTH) {
            authenticate { credential ->
                // Constant-time, so response timing doesn't leak how much of a guess was right.
                if (MessageDigest.isEqual(credential.token.toByteArray(), expected)) UserIdPrincipal("admin") else null
            }
        }
    }
}

fun Route.adminRoutes(admin: EntryAdmin, enabled: Boolean) = route("/admin") {
    if (!enabled) {
        // Explicit 404, otherwise the SPA fallback would answer with index.html.
        route("{...}") { handle { call.respond(HttpStatusCode.NotFound) } }
        return@route
    }
    authenticate(ADMIN_AUTH) {
        route("/entries") {
            get { call.respond(io { admin.entries() }.map { it.toResponse() }) }
            patch("/{id}") {
                val id = call.uuid()
                val change = call.receive<EntryChangeRequest>()
                io {
                    change.meters?.let { admin.changeMeters(id, it) }
                    change.paid?.let { admin.markPaid(id, it) }
                }
                call.respond(HttpStatusCode.NoContent)
            }
            delete("/{id}") {
                val id = call.uuid()
                io { admin.deleteEntry(id) }
                call.respond(HttpStatusCode.NoContent)
            }
        }
        route("/climbers") {
            get { call.respond(io { admin.climbers() }.map { it.toResponse() }) }
            put("/{id}/pin") {
                val id = ClimberId(call.uuid())
                val request = call.receive<PinResetRequest>()
                io { admin.resetPin(id, request.pin) }
                call.respond(HttpStatusCode.NoContent)
            }
            delete("/{id}") {
                val id = ClimberId(call.uuid())
                io { admin.deleteClimber(id) }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

private fun RoutingCall.uuid(): UUID = try {
    UUID.fromString(parameters["id"])
} catch (_: IllegalArgumentException) {
    throw BadRequestException("Malformed id")
}

private fun AdminEntry.toResponse() = AdminEntryResponse(
    id = id.toString(),
    climberId = climberId.value.toString(),
    climberName = climberName,
    meters = meters,
    loggedAt = loggedAt.toString(),
    paid = paid,
)

private fun ClimberSummary.toResponse() = ClimberSummaryResponse(id.value.toString(), name, entryCount, meters)
