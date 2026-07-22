package co.realmate.roshambo

enum class Gesture(val label: String, val emoji: String) {
    ROCK("Rock", "✊"),
    PAPER("Paper", "✋"),
    SCISSORS("Scissors", "✌️"),
    NONE("—", "❓");

    /** Standard rock-paper-scissors dominance. */
    fun beats(other: Gesture): Boolean = when (this to other) {
        ROCK to SCISSORS, SCISSORS to PAPER, PAPER to ROCK -> true
        else -> false
    }

    /** Lowercase token the backend expects ("rock"/"paper"/"scissors"); NONE has none. */
    val wire: String? get() = if (this == NONE) null else name.lowercase()

    companion object {
        val throwable = listOf(ROCK, PAPER, SCISSORS)

        /** Parse a backend move token back into a Gesture, defaulting to NONE. */
        fun fromWire(value: String?): Gesture =
            throwable.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
    }
}

enum class RoundOutcome { WIN, LOSS, DRAW }
