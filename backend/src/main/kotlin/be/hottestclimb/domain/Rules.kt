package be.hottestclimb.domain

object Rules {
    val METERS_PER_ENTRY = 1..500
    val NAME_LENGTH = 2..30
    const val EUROS_PER_ENTRY = 5
    private val PIN_FORMAT = Regex("\\d{4,6}")

    fun requireValidMeters(meters: Int) {
        if (meters !in METERS_PER_ENTRY) throw InvalidInput("Meters must be between 1 and ${METERS_PER_ENTRY.last}")
    }

    fun requireValidPin(pin: String) {
        if (!PIN_FORMAT.matches(pin)) throw InvalidInput("Pin must be 4 to 6 digits")
    }

    fun cleanName(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.length !in NAME_LENGTH) throw InvalidInput("Name must be ${NAME_LENGTH.first}-${NAME_LENGTH.last} characters")
        return clean
    }
}
