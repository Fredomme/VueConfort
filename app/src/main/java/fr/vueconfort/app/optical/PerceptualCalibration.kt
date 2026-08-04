package fr.vueconfort.app.optical

enum class PerceptualChoice { A, B, IDENTICAL, UNCOMFORTABLE }

data class CalibrationStep(val lower: Float, val upper: Float, val a: Float, val b: Float, val rounds: Int)

/** Bounded A/B search for perceptual preferences; values have no medical meaning. */
class PerceptualCalibration(
    lower: Float,
    upper: Float,
    private val minimumInterval: Float,
    private val maximumRounds: Int = 6
) {
    private var low = minOf(lower, upper)
    private var high = maxOf(lower, upper)
    private var rounds = 0
    private var lastPreferred = (low + high) / 2f

    fun step(): CalibrationStep {
        val third = (high - low) / 3f
        return CalibrationStep(low, high, low + third, high - third, rounds)
    }

    fun answer(choice: PerceptualChoice): CalibrationStep {
        val current = step()
        when (choice) {
            PerceptualChoice.A -> { high = current.b; lastPreferred = current.a }
            PerceptualChoice.B -> { low = current.a; lastPreferred = current.b }
            PerceptualChoice.IDENTICAL -> { low = current.a; high = current.b; lastPreferred = (current.a + current.b) / 2f }
            PerceptualChoice.UNCOMFORTABLE -> { high = current.a; lastPreferred = low }
        }
        rounds++
        return step()
    }

    fun isComplete() = rounds >= maximumRounds || high - low <= minimumInterval
    fun result() = lastPreferred.coerceIn(low, high)
    fun confidence() = if (rounds == 0) 0f else (rounds.toFloat() / maximumRounds).coerceIn(0f, 1f) *
        (1f - ((high - low) / (step().upper - step().lower + minimumInterval)).coerceIn(0f, 1f))
}
