package be.hottestclimb.persistence

import be.hottestclimb.domain.ClimbEntry
import be.hottestclimb.domain.Climber
import be.hottestclimb.domain.ClimberId
import be.hottestclimb.domain.ClimberTotal
import be.hottestclimb.domain.NameTaken
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.testcontainers.postgresql.PostgreSQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class ExposedRepositoriesTest {
    private val climbers = ExposedClimberRepository(TestDatabase.db)
    private val entries = ExposedClimbEntryRepository(TestDatabase.db)

    @Test
    fun `finds a climber by name regardless of case`() {
        val climber = ClimberMother.valid()
        climbers.save(climber)

        climbers.findByName(climber.name.uppercase()) shouldBe climber
    }

    @Test
    fun `refuses a second climber whose name differs only in case`() {
        val climber = ClimberMother.valid()
        climbers.save(climber)

        shouldThrow<NameTaken> {
            climbers.save(ClimberMother.valid().copy(name = climber.name.lowercase()))
        }
    }

    @Test
    fun `sums meters per climber`() {
        val climber = ClimberMother.valid()
        climbers.save(climber)
        entries.save(ClimbEntryMother.valid().copy(climberId = climber.id, meters = 30))
        entries.save(ClimbEntryMother.valid().copy(climberId = climber.id, meters = 45))

        entries.totalFor(climber.id) shouldBe 75
        entries.totals() shouldContain ClimberTotal(climber.name, 75)
    }

    @Test
    fun `a climber without entries has a total of zero and is not on the board`() {
        val climber = ClimberMother.valid()
        climbers.save(climber)

        entries.totalFor(climber.id) shouldBe 0
        entries.totals().map { it.name } shouldNotContain climber.name
    }
}

private object ClimberMother {
    fun valid() = Climber(
        id = ClimberId(UUID.randomUUID()),
        name = "Climber ${UUID.randomUUID().toString().take(8)}",
        pinHash = "hash",
    )
}

private object ClimbEntryMother {
    fun valid() = ClimbEntry(
        id = UUID.randomUUID(),
        climberId = ClimberId(UUID.randomUUID()),
        meters = 15,
        loggedAt = Instant.now().truncatedTo(ChronoUnit.MICROS),
    )
}

object TestDatabase {
    private val postgres = PostgreSQLContainer("postgres:17-alpine").apply { start() }

    val db = Databases.connect(DatabaseConfig(postgres.jdbcUrl, postgres.username, postgres.password))
}
