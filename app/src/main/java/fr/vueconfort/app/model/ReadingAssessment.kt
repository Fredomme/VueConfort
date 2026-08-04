package fr.vueconfort.app.model

data class ReadingAssessment(
    val startedAtMillis: Long,
    val completedAtMillis: Long,

    val wordCount: Int,
    val readingDurationMillis: Long,

    val rereadingCount: Int = 0,
    val perceivedEffortScore: Int = 0,
    val blurScore: Int = 0,
    val discomfortScore: Int = 0,

    val ambiguousCharactersCorrect: Int = 0,
    val ambiguousCharactersTotal: Int = 0,

    val profileId: String = VisualProfile.DEFAULT_PROFILE_ID
) {
    val wordsPerMinute: Float
        get() {
            if (readingDurationMillis <= 0L) {
                return 0f
            }

            val minutes = readingDurationMillis / 60_000f
            return wordCount / minutes
        }
}
