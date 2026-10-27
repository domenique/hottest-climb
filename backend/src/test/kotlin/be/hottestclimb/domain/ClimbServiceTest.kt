package be.hottestclimb.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class ClimbServiceTest {
    private val climbers = mockk<ClimberRepository>()
    private val entries = mockk<ClimbEntryRepository>()
    private val now = Instant.parse("2026-12-18T19:30:00Z")
    private val clock = TestClock(now)
    private val service = ClimbService(climbers, entries, PlainPinHasher, clock)

    @Nested
    inner class Register {
        @Test
        fun `registers a new climber with a hashed pin`() {
            val saved = slot<Climber>()
            every { climbers.findByName("Alex Honnold") } returns null
            every { climbers.save(capture(saved)) } just Runs

            service.register("  Alex Honnold ", "1234")

            saved.captured.name shouldBe "Alex Honnold"
            saved.captured.pinHash shouldBe PlainPinHasher.hash("1234")
        }

        @Test
        fun `rejects a name that is already taken`() {
            every { climbers.findByName("Alex Honnold") } returns ClimberMother.valid()

            shouldThrow<NameTaken> { service.register("Alex Honnold", "1234") }
        }

        @Test
        fun `rejects a pin that is not 4 to 6 digits`() {
            listOf("123", "1234567", "12a4", "").forEach { pin ->
                shouldThrow<InvalidInput> { service.register("Alex Honnold", pin) }
            }
        }

        @Test
        fun `rejects a name that is too short or too long`() {
            listOf(" a ", "x".repeat(31)).forEach { name ->
                shouldThrow<InvalidInput> { service.register(name, "1234") }
            }
        }
    }

    @Nested
    inner class `logging a climb for a registered climber` {
        private val climber = ClimberMother.valid()

        @BeforeEach
        fun setUp() {
            every { climbers.findByName(climber.name) } returns climber
            every { entries.save(any()) } just Runs
            every { entries.totalFor(climber.id) } returns 120
        }

        @Test
        fun `stores the entry and returns the climber's new total`() {
            val saved = slot<ClimbEntry>()
            every { entries.save(capture(saved)) } just Runs

            val total = service.logClimb(climber.name, ClimberMother.PIN, 45)

            total shouldBe 120
            saved.captured.climberId shouldBe climber.id
            saved.captured.meters shouldBe 45
            saved.captured.loggedAt shouldBe now
        }

        @Test
        fun `rejects a wrong pin`() {
            shouldThrow<InvalidCredentials> { service.logClimb(climber.name, "0000", 45) }
        }

        @Test
        fun `locks the name for 5 minutes after 5 wrong pins`() {
            repeat(5) { shouldThrow<InvalidCredentials> { service.logClimb(climber.name, "0000", 45) } }

            shouldThrow<TooManyAttempts> { service.logClimb(climber.name, ClimberMother.PIN, 45) }

            clock.advance(Duration.ofMinutes(5))
            service.logClimb(climber.name, ClimberMother.PIN, 45) shouldBe 120
        }

        @Test
        fun `a correct pin resets the failure count`() {
            repeat(4) { shouldThrow<InvalidCredentials> { service.logClimb(climber.name, "0000", 45) } }
            service.logClimb(climber.name, ClimberMother.PIN, 45)

            repeat(4) { shouldThrow<InvalidCredentials> { service.logClimb(climber.name, "0000", 45) } }
            service.logClimb(climber.name, ClimberMother.PIN, 45) shouldBe 120
        }

        @Test
        fun `rejects meters outside 1 to 500`() {
            listOf(0, -5, 501).forEach { meters ->
                shouldThrow<InvalidInput> { service.logClimb(climber.name, ClimberMother.PIN, meters) }
            }
        }
    }

    @Test
    fun `an unknown climber gets the same error as a wrong pin`() {
        every { climbers.findByName("Nobody") } returns null

        shouldThrow<InvalidCredentials> { service.logClimb("Nobody", "1234", 10) }
    }

    @Test
    fun `leaderboard ranks by meters with shared rank on ties and sums the grand total`() {
        every { entries.totals() } returns listOf(
            ClimberTotal("Bea", 300),
            ClimberTotal("Adam", 450),
            ClimberTotal("Chris", 300),
            ClimberTotal("Dirk", 80),
        )

        val board = service.leaderboard(limit = 3)

        board.rows shouldContainExactly listOf(
            LeaderboardRow(1, "Adam", 450),
            LeaderboardRow(2, "Bea", 300),
            LeaderboardRow(2, "Chris", 300),
        )
        board.grandTotal shouldBe 1130
        board.climberCount shouldBe 4
    }
}

private object ClimberMother {
    const val PIN = "4242"
    fun valid() = Climber(
        id = ClimberId(UUID.randomUUID()),
        name = "Alex Honnold",
        pinHash = PlainPinHasher.hash(PIN),
    )
}

private object PlainPinHasher : PinHasher {
    override fun hash(pin: String) = "plain:$pin"
    override fun matches(pin: String, hash: String) = hash == "plain:$pin"
}

private class TestClock(private var instant: Instant) : Clock() {
    fun advance(duration: Duration) {
        instant += duration
    }

    override fun instant() = instant
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId) = this
}
