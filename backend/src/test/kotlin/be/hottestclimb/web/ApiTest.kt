package be.hottestclimb.web

import be.hottestclimb.domain.ClimbService
import be.hottestclimb.domain.Climber
import be.hottestclimb.domain.ClimberId
import be.hottestclimb.domain.InvalidCredentials
import be.hottestclimb.domain.InvalidInput
import be.hottestclimb.domain.Leaderboard
import be.hottestclimb.domain.LeaderboardRow
import be.hottestclimb.domain.NameTaken
import be.hottestclimb.domain.TooManyAttempts
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID

class ApiTest {
    private val service = mockk<ClimbService>()

    @Test
    fun `registers a climber`() = apiTest { client ->
        every { service.register("Bea", "1234") } returns
            Climber(ClimberId(UUID.randomUUID()), "Bea", "hash")

        val response = client.post("/api/climbers") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest("Bea", "1234"))
        }

        response.status shouldBe HttpStatusCode.Created
        response.body<ClimberResponse>() shouldBe ClimberResponse("Bea")
    }

    @Test
    fun `logs a climb and returns the new total`() = apiTest { client ->
        every { service.logClimb("Bea", "1234", 30) } returns 130

        val response = client.post("/api/climbs") {
            contentType(ContentType.Application.Json)
            setBody(LogClimbRequest("Bea", "1234", 30))
        }

        response.status shouldBe HttpStatusCode.OK
        response.body<LogClimbResponse>() shouldBe LogClimbResponse("Bea", 130)
    }

    @Test
    fun `returns the leaderboard`() = apiTest { client ->
        every { service.leaderboard(10) } returns
            Leaderboard(listOf(LeaderboardRow(1, "Bea", 130)), grandTotal = 130, climberCount = 1)

        val response = client.get("/api/leaderboard")

        response.body<LeaderboardResponse>() shouldBe
            LeaderboardResponse(listOf(LeaderboardRowResponse(1, "Bea", 130)), 130, 1)
    }

    @Test
    fun `maps domain errors to http statuses`() = apiTest { client ->
        mapOf(
            NameTaken("Bea") to HttpStatusCode.Conflict,
            InvalidInput("nope") to HttpStatusCode.BadRequest,
            InvalidCredentials() to HttpStatusCode.Unauthorized,
            TooManyAttempts() to HttpStatusCode.TooManyRequests,
        ).forEach { (error, status) ->
            every { service.logClimb(any(), any(), any()) } throws error

            val response = client.post("/api/climbs") {
                contentType(ContentType.Application.Json)
                setBody(LogClimbRequest("Bea", "1234", 30))
            }

            response.status shouldBe status
            response.body<ErrorResponse>() shouldBe ErrorResponse(error.message!!)
        }
    }

    @Test
    fun `a malformed body is a bad request`() = apiTest { client ->
        val response = client.post("/api/climbs") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Bea"}""")
        }

        response.status shouldBe HttpStatusCode.BadRequest
    }

    private fun apiTest(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        application { api(service) }
        block(createClient { install(ContentNegotiation) { json() } })
    }
}
