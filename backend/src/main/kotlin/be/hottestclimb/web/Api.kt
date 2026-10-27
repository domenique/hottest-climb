package be.hottestclimb.web

import be.hottestclimb.domain.ClimbError
import be.hottestclimb.domain.ClimbService
import be.hottestclimb.domain.InvalidCredentials
import be.hottestclimb.domain.InvalidInput
import be.hottestclimb.domain.NameTaken
import be.hottestclimb.domain.TooManyAttempts
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

fun Application.api(service: ClimbService) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<ClimbError> { call, error ->
            val status = when (error) {
                is NameTaken -> HttpStatusCode.Conflict
                is InvalidInput -> HttpStatusCode.BadRequest
                is InvalidCredentials -> HttpStatusCode.Unauthorized
                is TooManyAttempts -> HttpStatusCode.TooManyRequests
            }
            call.respond(status, ErrorResponse(error.message!!))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Malformed request"))
        }
    }
    // Generous on purpose: the whole gym shares one IP. PIN guessing is handled per name in the domain.
    install(RateLimit) {
        register { rateLimiter(limit = 300, refillPeriod = 1.minutes) }
    }

    routing {
        rateLimit {
            route("/api") {
                post("/climbers") {
                    val request = call.receive<RegisterRequest>()
                    val climber = withContext(Dispatchers.IO) { service.register(request.name, request.pin) }
                    call.respond(HttpStatusCode.Created, ClimberResponse(climber.name))
                }
                post("/climbs") {
                    val request = call.receive<LogClimbRequest>()
                    val total = withContext(Dispatchers.IO) {
                        service.logClimb(request.name, request.pin, request.meters)
                    }
                    call.respond(LogClimbResponse(request.name.trim(), total))
                }
                get("/leaderboard") {
                    val board = withContext(Dispatchers.IO) { service.leaderboard() }
                    call.respond(board.toResponse())
                }
            }
        }
        singlePageApplication {
            useResources = true
            filesPath = "static"
        }
    }
}
