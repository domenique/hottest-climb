package be.hottestclimb.domain

import java.util.UUID

class EntryAdmin(
    private val climbers: ClimberRepository,
    private val entries: ClimbEntryRepository,
    private val pinHasher: PinHasher,
) {
    fun entries(): List<AdminEntry> = entries.all()

    fun climbers(): List<ClimberSummary> = climbers.summaries()

    fun changeMeters(id: UUID, meters: Int) {
        Rules.requireValidMeters(meters)
        if (!entries.updateMeters(id, meters)) throw NotFound("entry")
    }

    fun markPaid(id: UUID, paid: Boolean) {
        if (!entries.setPaid(id, paid)) throw NotFound("entry")
    }

    fun deleteEntry(id: UUID) {
        if (!entries.delete(id)) throw NotFound("entry")
    }

    fun resetPin(id: ClimberId, pin: String) {
        Rules.requireValidPin(pin)
        if (!climbers.updatePinHash(id, pinHasher.hash(pin))) throw NotFound("climber")
    }

    // Also removes the climber's entries; see the cascade in V2.
    fun deleteClimber(id: ClimberId) {
        if (!climbers.delete(id)) throw NotFound("climber")
    }
}
