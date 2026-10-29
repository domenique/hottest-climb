package be.hottestclimb.web

import be.hottestclimb.domain.AdminEntry
import be.hottestclimb.domain.ClimbService
import be.hottestclimb.domain.ClimberId
import be.hottestclimb.domain.ClimberSummary
import be.hottestclimb.domain.EntryAdmin
import be.hottestclimb.domain.NotFound
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

class AdminApiTest {
    private val admin = mockk<EntryAdmin>()
    private val password = "correct horse battery staple"

    private val entryId = UUID.randomUUID()
    private val climberId = ClimberId(UUID.randomUUID())

    @Test
    fun `rejects requests without the admin password`() = adminTest { client ->
        client.get("/api/admin/entries").status shouldBe HttpStatusCode.Unauthorized
        client.get("/api/admin/entries") { bearerAuth("wrong") }.status shouldBe HttpStatusCode.Unauthorized
    }

    @Test
    fun `admin endpoints do not exist when no admin password is configured`() = adminTest(adminPassword = null) { client ->
        client.get("/api/admin/entries") { bearerAuth("anything") }.status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `lists entries`() = adminTest { client ->
        every { admin.entries() } returns listOf(
            AdminEntry(entryId, climberId, "Bea", 30, Instant.parse("2026-12-18T19:30:00Z"), paid = true),
        )

        val response = client.get("/api/admin/entries") { asAdmin() }

        response.body<List<AdminEntryResponse>>() shouldBe listOf(
            AdminEntryResponse(entryId.toString(), climberId.value.toString(), "Bea", 30, "2026-12-18T19:30:00Z", true),
        )
    }

    @Test
    fun `changes meters and paid state of an entry`() = adminTest { client ->
        every { admin.changeMeters(entryId, 45) } just Runs
        every { admin.markPaid(entryId, true) } just Runs

        val response = client.patch("/api/admin/entries/$entryId") {
            asAdmin()
            contentType(ContentType.Application.Json)
            setBody(EntryChangeRequest(meters = 45, paid = true))
        }

        response.status shouldBe HttpStatusCode.NoContent
        verify { admin.changeMeters(entryId, 45) }
        verify { admin.markPaid(entryId, true) }
    }

    @Test
    fun `deletes an entry`() = adminTest { client ->
        every { admin.deleteEntry(entryId) } just Runs

        client.delete("/api/admin/entries/$entryId") { asAdmin() }.status shouldBe HttpStatusCode.NoContent
    }

    @Test
    fun `an unknown entry is not found`() = adminTest { client ->
        every { admin.deleteEntry(entryId) } throws NotFound("entry")

        client.delete("/api/admin/entries/$entryId") { asAdmin() }.status shouldBe HttpStatusCode.NotFound
    }

    @Test
    fun `a malformed id is a bad request`() = adminTest { client ->
        client.delete("/api/admin/entries/not-a-uuid") { asAdmin() }.status shouldBe HttpStatusCode.BadRequest
    }

    @Test
    fun `lists climbers, resets a pin and deletes a climber`() = adminTest { client ->
        every { admin.climbers() } returns listOf(ClimberSummary(climberId, "Bea", entryCount = 2, meters = 60))
        every { admin.resetPin(climberId, "9876") } just Runs
        every { admin.deleteClimber(climberId) } just Runs

        client.get("/api/admin/climbers") { asAdmin() }.body<List<ClimberSummaryResponse>>() shouldBe
            listOf(ClimberSummaryResponse(climberId.value.toString(), "Bea", 2, 60))
        client.put("/api/admin/climbers/${climberId.value}/pin") {
            asAdmin()
            contentType(ContentType.Application.Json)
            setBody(PinResetRequest("9876"))
        }.status shouldBe HttpStatusCode.NoContent
        client.delete("/api/admin/climbers/${climberId.value}") { asAdmin() }.status shouldBe HttpStatusCode.NoContent
    }

    private fun HttpRequestBuilder.asAdmin() = bearerAuth(Base64.getEncoder().encodeToString(password.toByteArray()))

    private fun adminTest(
        adminPassword: String? = password,
        block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
    ) = testApplication {
        application { api(mockk<ClimbService>(), admin, adminPassword) }
        block(createClient { install(ContentNegotiation) { json() } })
    }
}
