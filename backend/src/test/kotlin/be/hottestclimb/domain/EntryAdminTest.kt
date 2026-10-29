package be.hottestclimb.domain

import io.kotest.assertions.throwables.shouldThrow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class EntryAdminTest {
    private val climbers = mockk<ClimberRepository>()
    private val entries = mockk<ClimbEntryRepository>()
    private val pinHasher = object : PinHasher {
        override fun hash(pin: String) = "hashed:$pin"
        override fun matches(pin: String, hash: String) = hash == "hashed:$pin"
    }
    private val admin = EntryAdmin(climbers, entries, pinHasher)

    private val entryId = UUID.randomUUID()
    private val climberId = ClimberId(UUID.randomUUID())

    @Test
    fun `changes the meters of an entry`() {
        every { entries.updateMeters(entryId, 42) } returns true

        admin.changeMeters(entryId, 42)

        verify { entries.updateMeters(entryId, 42) }
    }

    @Test
    fun `refuses meters outside 1 to 500 without touching the entry`() {
        listOf(0, 501).forEach { meters ->
            shouldThrow<InvalidInput> { admin.changeMeters(entryId, meters) }
        }
        verify(exactly = 0) { entries.updateMeters(any(), any()) }
    }

    @Test
    fun `resets a pin by storing the hash of the new one`() {
        every { climbers.updatePinHash(climberId, "hashed:9876") } returns true

        admin.resetPin(climberId, "9876")

        verify { climbers.updatePinHash(climberId, "hashed:9876") }
    }

    @Test
    fun `refuses a new pin that is not 4 to 6 digits`() {
        shouldThrow<InvalidInput> { admin.resetPin(climberId, "12") }
    }

    @Test
    fun `reports unknown entries and climbers as not found`() {
        every { entries.updateMeters(entryId, any()) } returns false
        every { entries.setPaid(entryId, any()) } returns false
        every { entries.delete(entryId) } returns false
        every { climbers.updatePinHash(climberId, any()) } returns false
        every { climbers.delete(climberId) } returns false

        shouldThrow<NotFound> { admin.changeMeters(entryId, 10) }
        shouldThrow<NotFound> { admin.markPaid(entryId, true) }
        shouldThrow<NotFound> { admin.deleteEntry(entryId) }
        shouldThrow<NotFound> { admin.resetPin(climberId, "1234") }
        shouldThrow<NotFound> { admin.deleteClimber(climberId) }
    }
}
