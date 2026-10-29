package be.hottestclimb.persistence

import be.hottestclimb.domain.AdminEntry
import be.hottestclimb.domain.ClimbEntry
import be.hottestclimb.domain.Climber
import be.hottestclimb.domain.ClimberId
import be.hottestclimb.domain.ClimberSummary
import be.hottestclimb.domain.ClimberTotal
import be.hottestclimb.domain.NameTaken
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
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
    fun `counts paid and unpaid entries separately`() {
        val climber = ClimberMother.valid()
        climbers.save(climber)
        val paidBefore = entries.count(paid = true)
        val unpaidBefore = entries.count(paid = false)

        entries.save(ClimbEntryMother.valid().copy(climberId = climber.id))
        entries.save(ClimbEntryMother.valid().copy(climberId = climber.id))
        val settled = ClimbEntryMother.valid().copy(climberId = climber.id)
        entries.save(settled)
        entries.setPaid(settled.id, true)

        entries.count(paid = true) shouldBe paidBefore + 1
        entries.count(paid = false) shouldBe unpaidBefore + 2
    }

    @Test
    fun `lists entries with the climber's name, newest first`() {
        val climber = ClimberMother.valid()
        climbers.save(climber)
        val older = ClimbEntryMother.valid().copy(climberId = climber.id, loggedAt = Instant.parse("2026-12-18T10:00:00Z"))
        val newer = ClimbEntryMother.valid().copy(climberId = climber.id, loggedAt = Instant.parse("2026-12-19T10:00:00Z"))
        entries.save(older)
        entries.save(newer)

        val mine = entries.all().filter { it.climberId == climber.id }

        mine.map { it.id } shouldContainExactly listOf(newer.id, older.id)
        mine.first() shouldBe AdminEntry(newer.id, climber.id, climber.name, newer.meters, newer.loggedAt, paid = false)
    }

    @Test
    fun `edits meters, marks paid and deletes an entry`() {
        val climber = ClimberMother.valid()
        climbers.save(climber)
        val entry = ClimbEntryMother.valid().copy(climberId = climber.id, meters = 20)
        entries.save(entry)

        entries.updateMeters(entry.id, 35) shouldBe true
        entries.setPaid(entry.id, true) shouldBe true
        entries.all().single { it.id == entry.id }.let {
            it.meters shouldBe 35
            it.paid shouldBe true
        }

        entries.delete(entry.id) shouldBe true
        entries.all().map { it.id } shouldNotContain entry.id
    }

    @Test
    fun `changing an unknown entry reports false`() {
        val unknown = UUID.randomUUID()

        entries.updateMeters(unknown, 10) shouldBe false
        entries.setPaid(unknown, true) shouldBe false
        entries.delete(unknown) shouldBe false
    }

    @Test
    fun `summarises climbers including those without entries`() {
        val active = ClimberMother.valid()
        val idle = ClimberMother.valid()
        climbers.save(active)
        climbers.save(idle)
        entries.save(ClimbEntryMother.valid().copy(climberId = active.id, meters = 30))
        entries.save(ClimbEntryMother.valid().copy(climberId = active.id, meters = 12))

        val summaries = climbers.summaries()

        summaries shouldContain ClimberSummary(active.id, active.name, entryCount = 2, meters = 42)
        summaries shouldContain ClimberSummary(idle.id, idle.name, entryCount = 0, meters = 0)
    }

    @Test
    fun `stores a new pin hash`() {
        val climber = ClimberMother.valid()
        climbers.save(climber)

        climbers.updatePinHash(climber.id, "new-hash") shouldBe true

        climbers.findByName(climber.name)!!.pinHash shouldBe "new-hash"
    }

    @Test
    fun `deleting a climber removes their entries too`() {
        val climber = ClimberMother.valid()
        climbers.save(climber)
        entries.save(ClimbEntryMother.valid().copy(climberId = climber.id))

        climbers.delete(climber.id) shouldBe true

        climbers.findByName(climber.name) shouldBe null
        entries.totalFor(climber.id) shouldBe 0
        climbers.delete(climber.id) shouldBe false
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
