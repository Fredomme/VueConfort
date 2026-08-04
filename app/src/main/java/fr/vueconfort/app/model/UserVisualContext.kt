package fr.vueconfort.app.model

data class UserVisualContext(
    val ageRange: AgeRange = AgeRange.NOT_SPECIFIED,
    val dailyScreenTimeHours: Float = 4f,
    val usualReadingDistanceCm: Int = 35,

    val wearsGlasses: Boolean = false,
    val glassesForNearVision: Boolean = false,

    val knownPresbyopia: Boolean = false,
    val knownAstigmatism: Boolean = false,
    val lightSensitivity: Boolean = false,
    val migraineSensitivity: Boolean = false,
    val dryEyeSymptoms: Boolean = false,

    val initialFatigueScore: Int = 0,
    val blurAfterScreenUse: Boolean = false,
    val headacheAfterScreenUse: Boolean = false,
    val squinting: Boolean = false,
    val changesReadingDistance: Boolean = false,

    val primaryUsage: PrimaryUsage = PrimaryUsage.GENERAL
)

enum class AgeRange {
    UNDER_30,
    FROM_30_TO_39,
    FROM_40_TO_49,
    FROM_50_TO_59,
    FROM_60_TO_69,
    OVER_70,
    NOT_SPECIFIED
}

enum class PrimaryUsage {
    GENERAL,
    LONG_READING,
    WORK,
    WEB,
    SOCIAL_NETWORKS,
    NIGHT,
    OUTDOOR
}
