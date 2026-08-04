package fr.vueconfort.app.calibration

import fr.vueconfort.app.model.VisualProfile

enum class CalibrationParameter {
    FONT_SIZE,
    FONT_WEIGHT,
    LETTER_SPACING,
    LINE_HEIGHT,
    CONTRAST,
    BACKGROUND,
    COLUMN_WIDTH,
    MARGINS,
    WARMTH,
    DESATURATION
}

enum class CalibrationChoice {
    OPTION_A,
    OPTION_B,
    NO_DIFFERENCE
}

data class CalibrationTrial(
    val parameter: CalibrationParameter,
    val optionA: VisualProfile,
    val optionB: VisualProfile,
    val repetition: Int,
    val choice: CalibrationChoice? = null,
    val responseTimeMillis: Long? = null
)

data class CalibrationSession(
    val startedAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long? = null,
    val trials: List<CalibrationTrial> = emptyList(),
    val resultingProfile: VisualProfile? = null,
    val confidenceScore: Float = 0f
)

object CalibrationEngine {

    fun createTrials(
        baseProfile: VisualProfile
    ): List<CalibrationTrial> {
        return listOf(
            trial(
                parameter = CalibrationParameter.FONT_SIZE,
                optionA = baseProfile.copy(
                    fontSizeSp = (baseProfile.fontSizeSp - 2f)
                        .coerceAtLeast(14f)
                ),
                optionB = baseProfile.copy(
                    fontSizeSp = (baseProfile.fontSizeSp + 2f)
                        .coerceAtMost(34f)
                )
            ),
            trial(
                parameter = CalibrationParameter.FONT_WEIGHT,
                optionA = baseProfile.copy(
                    fontWeight = (baseProfile.fontWeight - 100)
                        .coerceAtLeast(300)
                ),
                optionB = baseProfile.copy(
                    fontWeight = (baseProfile.fontWeight + 100)
                        .coerceAtMost(700)
                )
            ),
            trial(
                parameter = CalibrationParameter.LETTER_SPACING,
                optionA = baseProfile.copy(
                    letterSpacingSp =
                        (baseProfile.letterSpacingSp - 0.10f)
                            .coerceAtLeast(0f)
                ),
                optionB = baseProfile.copy(
                    letterSpacingSp =
                        (baseProfile.letterSpacingSp + 0.20f)
                            .coerceAtMost(1.5f)
                )
            ),
            trial(
                parameter = CalibrationParameter.LINE_HEIGHT,
                optionA = baseProfile.copy(
                    lineHeightMultiplier =
                        (baseProfile.lineHeightMultiplier - 0.15f)
                            .coerceAtLeast(1.10f)
                ),
                optionB = baseProfile.copy(
                    lineHeightMultiplier =
                        (baseProfile.lineHeightMultiplier + 0.20f)
                            .coerceAtMost(2f)
                )
            ),
            trial(
                parameter = CalibrationParameter.COLUMN_WIDTH,
                optionA = baseProfile.copy(
                    columnWidthPercent = 88f
                ),
                optionB = baseProfile.copy(
                    columnWidthPercent = 100f
                )
            ),
            trial(
                parameter = CalibrationParameter.MARGINS,
                optionA = baseProfile.copy(
                    horizontalMarginDp = 12f
                ),
                optionB = baseProfile.copy(
                    horizontalMarginDp = 24f
                )
            ),
            trial(
                parameter = CalibrationParameter.WARMTH,
                optionA = baseProfile.copy(
                    warmthPercent =
                        (baseProfile.warmthPercent - 10)
                            .coerceAtLeast(0)
                ),
                optionB = baseProfile.copy(
                    warmthPercent =
                        (baseProfile.warmthPercent + 15)
                            .coerceAtMost(100)
                )
            ),
            trial(
                parameter = CalibrationParameter.DESATURATION,
                optionA = baseProfile.copy(
                    desaturationPercent = 0
                ),
                optionB = baseProfile.copy(
                    desaturationPercent = 15
                )
            )
        )
    }

    fun applyChoice(
        currentProfile: VisualProfile,
        trial: CalibrationTrial,
        choice: CalibrationChoice
    ): VisualProfile {
        val selectedProfile = when (choice) {
            CalibrationChoice.OPTION_A -> trial.optionA
            CalibrationChoice.OPTION_B -> trial.optionB
            CalibrationChoice.NO_DIFFERENCE -> currentProfile
        }

        return selectedProfile.copy(
            id = currentProfile.id,
            name = currentProfile.name,
            createdAtMillis = currentProfile.createdAtMillis,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    fun recordChoice(
        trial: CalibrationTrial,
        choice: CalibrationChoice,
        responseTimeMillis: Long
    ): CalibrationTrial {
        return trial.copy(
            choice = choice,
            responseTimeMillis = responseTimeMillis
        )
    }

    fun confidenceScore(
        trials: List<CalibrationTrial>
    ): Float {
        if (trials.isEmpty()) {
            return 0f
        }

        val answeredCount = trials.count {
            it.choice != null
        }

        val decisiveCount = trials.count {
            it.choice == CalibrationChoice.OPTION_A ||
                it.choice == CalibrationChoice.OPTION_B
        }

        val completionScore =
            answeredCount.toFloat() / trials.size.toFloat()

        val decisivenessScore =
            if (answeredCount == 0) {
                0f
            } else {
                decisiveCount.toFloat() / answeredCount.toFloat()
            }

        return (
            completionScore * 0.70f +
                decisivenessScore * 0.30f
            ).coerceIn(0f, 1f)
    }

    fun completeSession(
        session: CalibrationSession,
        resultingProfile: VisualProfile
    ): CalibrationSession {
        val confidence = confidenceScore(session.trials)

        return session.copy(
            completedAtMillis = System.currentTimeMillis(),
            resultingProfile = resultingProfile.copy(
                calibrated = true,
                calibrationConfidence = confidence,
                updatedAtMillis = System.currentTimeMillis()
            ),
            confidenceScore = confidence
        )
    }

    private fun trial(
        parameter: CalibrationParameter,
        optionA: VisualProfile,
        optionB: VisualProfile
    ): CalibrationTrial {
        return CalibrationTrial(
            parameter = parameter,
            optionA = optionA,
            optionB = optionB,
            repetition = 1
        )
    }
}
