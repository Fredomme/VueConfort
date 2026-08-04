package fr.vueconfort.app.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fr.vueconfort.app.model.AgeRange
import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.model.AmbientLightLevel
import fr.vueconfort.app.model.AutomationRule
import fr.vueconfort.app.model.AutomationStatus
import fr.vueconfort.app.model.AutomationTrigger
import fr.vueconfort.app.model.PrimaryUsage
import fr.vueconfort.app.model.UserVisualContext
import fr.vueconfort.app.model.VisualProfile
import fr.vueconfort.app.assessment.AssessmentDistance
import fr.vueconfort.app.assessment.EyeComfortResult
import fr.vueconfort.app.assessment.ResultReliability
import fr.vueconfort.app.assessment.TestedEye
import fr.vueconfort.app.assessment.VisualComfortAssessment
import fr.vueconfort.app.assessment.AcuityMethod
import fr.vueconfort.app.assessment.AmslerResult
import fr.vueconfort.app.assessment.PhysicalDisplayCalibration
import fr.vueconfort.app.assessment.StandardAcuityResult
import fr.vueconfort.app.assessment.StandardContrastResult
import fr.vueconfort.app.assessment.StandardEye
import fr.vueconfort.app.assessment.StandardProtocol
import fr.vueconfort.app.assessment.StandardizedAssessmentReport
import fr.vueconfort.app.assessment.TestDistance
import fr.vueconfort.app.optical.OpticalQuality
import fr.vueconfort.app.optical.OpticalSettings
import fr.vueconfort.app.core.ErrorCategory
import fr.vueconfort.app.core.ErrorReporter
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class OverlayPreferences(
    val buttonX: Int = 0,
    val buttonY: Int = 180,
    val panelX: Int = 24,
    val panelY: Int = 120,
    val expanded: Boolean = false,
    val locked: Boolean = false,
    val panelAlpha: Float = 0.92f,
    val activeProfileId: String = VisualProfile.DEFAULT_PROFILE_ID,
    val magnificationScale: Float = 2f
)

private val Context.vueConfortDataStore by preferencesDataStore(
    name = "vueconfort_settings"
)

class VisualProfileRepository(
    private val context: Context
) {
    private val safePreferences = context.vueConfortDataStore.data.catch { throwable ->
        if (throwable is IOException) {
            ErrorReporter.from(ErrorCategory.STORAGE_INACCESSIBLE, "datastore_read", throwable)
            emit(emptyPreferences())
        } else {
            throw throwable
        }
    }

    val onboardingCompleted: Flow<Boolean> =
        safePreferences.map { it[Keys.ONBOARDING_COMPLETED] ?: false }
    val visualAssessments: Flow<List<VisualComfortAssessment>> =
        safePreferences.map {
            decodeAssessments(it[Keys.VISUAL_ASSESSMENTS])
                .sortedByDescending { result -> result.createdAtMillis }
        }

    val standardizedAssessments: Flow<List<StandardizedAssessmentReport>> =
        safePreferences.map {
            decodeStandardReports(it[Keys.STANDARDIZED_ASSESSMENTS])
                .sortedByDescending { report -> report.createdAtMillis }
        }
    val automationRules: Flow<List<AutomationRule>> =
        safePreferences.map {
            decodeRules(it[Keys.AUTOMATION_RULES])
        }

    val automationStatus: Flow<AutomationStatus> =
        safePreferences.map {
            AutomationStatus(
                manualUntilMillis = it[Keys.MANUAL_UNTIL] ?: 0L,
                source = it[Keys.AUTOMATION_SOURCE] ?: "Manuel",
                reason = it[Keys.AUTOMATION_REASON] ?: "",
                activeRuleId = it[Keys.ACTIVE_RULE_ID] ?: "",
                profileId = it[Keys.ACTIVE_ASSIST_PROFILE_ID]
                    ?: AssistProfile.STANDARD_ID,
                lastAppliedMillis = it[Keys.LAST_AUTOMATION_AT] ?: 0L
            )
        }
    val assistProfiles: Flow<List<AssistProfile>> =
        safePreferences.map { preferences ->
            decodeProfiles(preferences[Keys.ASSIST_PROFILES]).ifEmpty {
                migratedDefaults(
                    scale = preferences[Keys.OVERLAY_SCALE] ?: 2f,
                    alpha = preferences[Keys.OVERLAY_ALPHA] ?: 0.82f,
                    locked = preferences[Keys.OVERLAY_LOCKED] ?: false,
                    expanded = preferences[Keys.OVERLAY_EXPANDED] ?: true
                )
            }
        }

    val activeAssistProfileId: Flow<String> =
        safePreferences.map { preferences ->
            preferences[Keys.ACTIVE_ASSIST_PROFILE_ID]
                ?: preferences[Keys.OVERLAY_PROFILE_ID]
                ?: AssistProfile.STANDARD_ID
        }

    val activeAssistProfile: Flow<AssistProfile> =
        combine(assistProfiles, activeAssistProfileId) { profiles, activeId ->
            profiles.firstOrNull { it.id == activeId }
                ?: profiles.firstOrNull()
                ?: AssistProfile.defaults().first()
        }

    val overlayPreferences: Flow<OverlayPreferences> =
        safePreferences.map { preferences ->
            OverlayPreferences(
                buttonX = preferences[Keys.OVERLAY_BUTTON_X] ?: 0,
                buttonY = preferences[Keys.OVERLAY_BUTTON_Y] ?: 180,
                panelX = preferences[Keys.OVERLAY_PANEL_X] ?: 24,
                panelY = preferences[Keys.OVERLAY_PANEL_Y] ?: 120,
                expanded = preferences[Keys.OVERLAY_EXPANDED] ?: false,
                locked = preferences[Keys.OVERLAY_LOCKED] ?: false,
                panelAlpha = (preferences[Keys.OVERLAY_ALPHA] ?: 0.92f)
                    .coerceIn(0.55f, 1f),
                activeProfileId = preferences[Keys.OVERLAY_PROFILE_ID]
                    ?: VisualProfile.DEFAULT_PROFILE_ID,
                magnificationScale =
                    (preferences[Keys.OVERLAY_SCALE] ?: 2f).coerceIn(1f, 8f)
            )
        }

    val profile: Flow<VisualProfile> =
        safePreferences.map { preferences ->
            VisualProfile(
                id = preferences[Keys.PROFILE_ID]
                    ?: VisualProfile.DEFAULT_PROFILE_ID,
                name = preferences[Keys.PROFILE_NAME]
                    ?: "Profil principal",
                fontSizeSp = preferences[Keys.FONT_SIZE] ?: 19f,
                fontWeight = preferences[Keys.FONT_WEIGHT] ?: 450,
                letterSpacingSp = preferences[Keys.LETTER_SPACING] ?: 0.15f,
                lineHeightMultiplier = preferences[Keys.LINE_HEIGHT] ?: 1.40f,
                foregroundArgb = preferences[Keys.FOREGROUND] ?: 0xFF202020,
                backgroundArgb = preferences[Keys.BACKGROUND] ?: 0xFFF7F5EF,
                columnWidthPercent = preferences[Keys.COLUMN_WIDTH] ?: 100f,
                horizontalMarginDp = preferences[Keys.HORIZONTAL_MARGIN] ?: 18f,
                brightnessPercent = preferences[Keys.BRIGHTNESS] ?: 50,
                warmthPercent = preferences[Keys.WARMTH] ?: 10,
                desaturationPercent = preferences[Keys.DESATURATION] ?: 0,
                readingGuideEnabled = preferences[Keys.READING_GUIDE] ?: false,
                lineFocusEnabled = preferences[Keys.LINE_FOCUS] ?: false,
                localZoomEnabled = preferences[Keys.LOCAL_ZOOM] ?: false,
                calibrated = preferences[Keys.CALIBRATED] ?: false,
                calibrationConfidence =
                    preferences[Keys.CALIBRATION_CONFIDENCE] ?: 0f,
                createdAtMillis =
                    preferences[Keys.CREATED_AT] ?: System.currentTimeMillis(),
                updatedAtMillis =
                    preferences[Keys.UPDATED_AT] ?: System.currentTimeMillis()
            )
        }

    val userContext: Flow<UserVisualContext> =
        safePreferences.map { preferences ->
            UserVisualContext(
                ageRange = enumValueOrDefault(
                    preferences[Keys.AGE_RANGE],
                    AgeRange.NOT_SPECIFIED
                ),
                dailyScreenTimeHours =
                    preferences[Keys.SCREEN_TIME] ?: 4f,
                usualReadingDistanceCm =
                    preferences[Keys.READING_DISTANCE] ?: 35,
                wearsGlasses =
                    preferences[Keys.WEARS_GLASSES] ?: false,
                glassesForNearVision =
                    preferences[Keys.NEAR_GLASSES] ?: false,
                knownPresbyopia =
                    preferences[Keys.PRESBYOPIA] ?: false,
                knownAstigmatism =
                    preferences[Keys.ASTIGMATISM] ?: false,
                lightSensitivity =
                    preferences[Keys.LIGHT_SENSITIVITY] ?: false,
                migraineSensitivity =
                    preferences[Keys.MIGRAINE_SENSITIVITY] ?: false,
                dryEyeSymptoms =
                    preferences[Keys.DRY_EYE] ?: false,
                initialFatigueScore =
                    preferences[Keys.FATIGUE_SCORE] ?: 0,
                blurAfterScreenUse =
                    preferences[Keys.BLUR] ?: false,
                headacheAfterScreenUse =
                    preferences[Keys.HEADACHE] ?: false,
                squinting =
                    preferences[Keys.SQUINTING] ?: false,
                changesReadingDistance =
                    preferences[Keys.CHANGES_DISTANCE] ?: false,
                primaryUsage = enumValueOrDefault(
                    preferences[Keys.PRIMARY_USAGE],
                    PrimaryUsage.GENERAL
                )
            )
        }

    suspend fun saveProfile(profile: VisualProfile) {
        context.vueConfortDataStore.edit { preferences ->
            preferences[Keys.PROFILE_ID] = profile.id
            preferences[Keys.PROFILE_NAME] = profile.name
            preferences[Keys.FONT_SIZE] = profile.fontSizeSp
            preferences[Keys.FONT_WEIGHT] = profile.fontWeight
            preferences[Keys.LETTER_SPACING] = profile.letterSpacingSp
            preferences[Keys.LINE_HEIGHT] = profile.lineHeightMultiplier
            preferences[Keys.FOREGROUND] = profile.foregroundArgb
            preferences[Keys.BACKGROUND] = profile.backgroundArgb
            preferences[Keys.COLUMN_WIDTH] = profile.columnWidthPercent
            preferences[Keys.HORIZONTAL_MARGIN] = profile.horizontalMarginDp
            preferences[Keys.BRIGHTNESS] = profile.brightnessPercent
            preferences[Keys.WARMTH] = profile.warmthPercent
            preferences[Keys.DESATURATION] = profile.desaturationPercent
            preferences[Keys.READING_GUIDE] = profile.readingGuideEnabled
            preferences[Keys.LINE_FOCUS] = profile.lineFocusEnabled
            preferences[Keys.LOCAL_ZOOM] = profile.localZoomEnabled
            preferences[Keys.CALIBRATED] = profile.calibrated
            preferences[Keys.CALIBRATION_CONFIDENCE] =
                profile.calibrationConfidence
            preferences[Keys.CREATED_AT] = profile.createdAtMillis
            preferences[Keys.UPDATED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun saveUserContext(userContext: UserVisualContext) {
        context.vueConfortDataStore.edit { preferences ->
            preferences[Keys.AGE_RANGE] = userContext.ageRange.name
            preferences[Keys.SCREEN_TIME] =
                userContext.dailyScreenTimeHours
            preferences[Keys.READING_DISTANCE] =
                userContext.usualReadingDistanceCm
            preferences[Keys.WEARS_GLASSES] =
                userContext.wearsGlasses
            preferences[Keys.NEAR_GLASSES] =
                userContext.glassesForNearVision
            preferences[Keys.PRESBYOPIA] =
                userContext.knownPresbyopia
            preferences[Keys.ASTIGMATISM] =
                userContext.knownAstigmatism
            preferences[Keys.LIGHT_SENSITIVITY] =
                userContext.lightSensitivity
            preferences[Keys.MIGRAINE_SENSITIVITY] =
                userContext.migraineSensitivity
            preferences[Keys.DRY_EYE] =
                userContext.dryEyeSymptoms
            preferences[Keys.FATIGUE_SCORE] =
                userContext.initialFatigueScore
            preferences[Keys.BLUR] =
                userContext.blurAfterScreenUse
            preferences[Keys.HEADACHE] =
                userContext.headacheAfterScreenUse
            preferences[Keys.SQUINTING] =
                userContext.squinting
            preferences[Keys.CHANGES_DISTANCE] =
                userContext.changesReadingDistance
            preferences[Keys.PRIMARY_USAGE] =
                userContext.primaryUsage.name
        }
    }

    suspend fun saveOverlayPreferences(value: OverlayPreferences) {
        context.vueConfortDataStore.edit { preferences ->
            preferences[Keys.OVERLAY_BUTTON_X] = value.buttonX
            preferences[Keys.OVERLAY_BUTTON_Y] = value.buttonY
            preferences[Keys.OVERLAY_PANEL_X] = value.panelX
            preferences[Keys.OVERLAY_PANEL_Y] = value.panelY
            preferences[Keys.OVERLAY_EXPANDED] = value.expanded
            preferences[Keys.OVERLAY_LOCKED] = value.locked
            preferences[Keys.OVERLAY_ALPHA] = value.panelAlpha.coerceIn(0.55f, 1f)
            preferences[Keys.OVERLAY_PROFILE_ID] = value.activeProfileId
            preferences[Keys.OVERLAY_SCALE] =
                value.magnificationScale.coerceIn(1f, 8f)
        }
    }

    suspend fun ensureAssistProfilesMigrated() {
        context.vueConfortDataStore.edit { preferences ->
            if (decodeProfiles(preferences[Keys.ASSIST_PROFILES]).isEmpty()) {
                val profiles = migratedDefaults(
                    preferences[Keys.OVERLAY_SCALE] ?: 2f,
                    preferences[Keys.OVERLAY_ALPHA] ?: 0.82f,
                    preferences[Keys.OVERLAY_LOCKED] ?: false,
                    preferences[Keys.OVERLAY_EXPANDED] ?: true
                )
                preferences[Keys.ASSIST_PROFILES] = encodeProfiles(profiles)
                preferences[Keys.ACTIVE_ASSIST_PROFILE_ID] =
                    preferences[Keys.OVERLAY_PROFILE_ID]
                        ?.takeIf { id -> profiles.any { it.id == id } }
                        ?: AssistProfile.STANDARD_ID
            }
        }
    }

    suspend fun activateAssistProfile(id: String) {
        context.vueConfortDataStore.edit { preferences ->
            val profiles = decodeProfiles(preferences[Keys.ASSIST_PROFILES])
                .ifEmpty { AssistProfile.defaults() }
            val selected = profiles.firstOrNull { it.id == id } ?: profiles.first()
            preferences[Keys.ACTIVE_ASSIST_PROFILE_ID] = selected.id
            preferences[Keys.OVERLAY_PROFILE_ID] = selected.id
            preferences[Keys.OVERLAY_SCALE] = selected.magnificationScale
            preferences[Keys.OVERLAY_ALPHA] = selected.overlayAlpha
            preferences[Keys.OVERLAY_LOCKED] = selected.locked
            preferences[Keys.OVERLAY_EXPANDED] = selected.expanded
            preferences[Keys.MANUAL_UNTIL] = System.currentTimeMillis() + 15 * 60_000L
            preferences[Keys.AUTOMATION_SOURCE] = "Manuel"
            preferences[Keys.AUTOMATION_REASON] = "Choix manuel"
            preferences[Keys.ACTIVE_RULE_ID] = ""
            preferences[Keys.LAST_AUTOMATION_AT] = System.currentTimeMillis()
        }
    }

    suspend fun applyAutomatedProfile(
        profileId: String,
        source: String,
        reason: String,
        ruleId: String
    ) {
        context.vueConfortDataStore.edit { preferences ->
            val profiles = decodeProfiles(preferences[Keys.ASSIST_PROFILES])
                .ifEmpty { AssistProfile.defaults() }
            val selected = profiles.firstOrNull { it.id == profileId } ?: return@edit
            val alreadyApplied =
                preferences[Keys.ACTIVE_ASSIST_PROFILE_ID] == selected.id &&
                    preferences[Keys.ACTIVE_RULE_ID] == ruleId &&
                    preferences[Keys.AUTOMATION_SOURCE] == source
            if (alreadyApplied) return@edit
            preferences[Keys.ACTIVE_ASSIST_PROFILE_ID] = selected.id
            preferences[Keys.OVERLAY_PROFILE_ID] = selected.id
            preferences[Keys.OVERLAY_SCALE] = selected.magnificationScale
            preferences[Keys.OVERLAY_ALPHA] = selected.overlayAlpha
            preferences[Keys.OVERLAY_LOCKED] = selected.locked
            preferences[Keys.OVERLAY_EXPANDED] = selected.expanded
            preferences[Keys.AUTOMATION_SOURCE] = source
            preferences[Keys.AUTOMATION_REASON] = reason.take(80)
            preferences[Keys.ACTIVE_RULE_ID] = ruleId
            preferences[Keys.LAST_AUTOMATION_AT] = System.currentTimeMillis()
        }
    }

    suspend fun setManualPause(durationMillis: Long?) {
        context.vueConfortDataStore.edit {
            it[Keys.MANUAL_UNTIL] = when (durationMillis) {
                null -> Long.MAX_VALUE
                0L -> 0L
                else -> System.currentTimeMillis() + durationMillis.coerceAtLeast(0L)
            }
            if (durationMillis == 0L) {
                it[Keys.AUTOMATION_SOURCE] = "Automatisation"
                it[Keys.AUTOMATION_REASON] = "Réévaluation en cours"
            } else {
                it[Keys.AUTOMATION_SOURCE] = "Manuel"
                it[Keys.AUTOMATION_REASON] = "Automatismes suspendus"
                it[Keys.ACTIVE_RULE_ID] = ""
            }
        }
    }

    suspend fun upsertAutomationRule(rule: AutomationRule) {
        context.vueConfortDataStore.edit {
            val rules = decodeRules(it[Keys.AUTOMATION_RULES]).toMutableList()
            val clean = rule.sanitized()
            val index = rules.indexOfFirst { existing -> existing.id == clean.id }
            if (index >= 0) rules[index] = clean else rules += clean
            it[Keys.AUTOMATION_RULES] = encodeRules(rules)
        }
    }

    suspend fun deleteAutomationRule(id: String) {
        context.vueConfortDataStore.edit {
            val rules = decodeRules(it[Keys.AUTOMATION_RULES]).filterNot { rule -> rule.id == id }
            it[Keys.AUTOMATION_RULES] = encodeRules(rules)
            if (it[Keys.ACTIVE_RULE_ID] == id) it[Keys.ACTIVE_RULE_ID] = ""
        }
    }

    suspend fun saveVisualAssessment(value: VisualComfortAssessment) {
        context.vueConfortDataStore.edit {
            val values = decodeAssessments(it[Keys.VISUAL_ASSESSMENTS])
                .filterNot { old -> old.id == value.id } + value
            it[Keys.VISUAL_ASSESSMENTS] = encodeAssessments(values)
        }
    }

    suspend fun deleteVisualAssessment(id: String) {
        context.vueConfortDataStore.edit {
            it[Keys.VISUAL_ASSESSMENTS] = encodeAssessments(
                decodeAssessments(it[Keys.VISUAL_ASSESSMENTS]).filterNot { value -> value.id == id }
            )
        }
    }

    suspend fun saveStandardizedAssessment(value: StandardizedAssessmentReport) {
        context.vueConfortDataStore.edit {
            val values = decodeStandardReports(it[Keys.STANDARDIZED_ASSESSMENTS])
                .filterNot { old -> old.id == value.id } + value
            it[Keys.STANDARDIZED_ASSESSMENTS] = encodeStandardReports(values)
        }
    }

    suspend fun deleteStandardizedAssessment(id: String) {
        context.vueConfortDataStore.edit {
            it[Keys.STANDARDIZED_ASSESSMENTS] = encodeStandardReports(
                decodeStandardReports(it[Keys.STANDARDIZED_ASSESSMENTS])
                    .filterNot { value -> value.id == id }
            )
        }
    }

    suspend fun upsertAssistProfile(profile: AssistProfile) {
        context.vueConfortDataStore.edit { preferences ->
            val profiles = decodeProfiles(preferences[Keys.ASSIST_PROFILES])
                .ifEmpty { AssistProfile.defaults() }
                .toMutableList()
            val clean = uniqueName(profile.sanitized(), profiles)
            val index = profiles.indexOfFirst { it.id == clean.id }
            if (index >= 0) profiles[index] = clean else profiles += clean
            preferences[Keys.ASSIST_PROFILES] = encodeProfiles(profiles)
        }
    }

    suspend fun deleteAssistProfile(id: String) {
        context.vueConfortDataStore.edit { preferences ->
            val profiles = decodeProfiles(preferences[Keys.ASSIST_PROFILES])
                .ifEmpty { AssistProfile.defaults() }
            val target = profiles.firstOrNull { it.id == id }
            if (target == null || target.predefined) return@edit
            val remaining = profiles.filterNot { it.id == id }.ifEmpty { AssistProfile.defaults() }
            preferences[Keys.ASSIST_PROFILES] = encodeProfiles(remaining)
            if (preferences[Keys.ACTIVE_ASSIST_PROFILE_ID] == id) {
                preferences[Keys.ACTIVE_ASSIST_PROFILE_ID] = AssistProfile.STANDARD_ID
                preferences[Keys.OVERLAY_PROFILE_ID] = AssistProfile.STANDARD_ID
            }
        }
    }

    suspend fun restorePredefinedProfile(id: String) {
        val default = AssistProfile.defaults().firstOrNull { it.id == id } ?: return
        upsertAssistProfile(default)
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.vueConfortDataStore.edit { it[Keys.ONBOARDING_COMPLETED] = completed }
    }

    suspend fun resetAssistProfiles() {
        context.vueConfortDataStore.edit {
            it[Keys.ASSIST_PROFILES] = encodeProfiles(AssistProfile.defaults())
            it[Keys.ACTIVE_ASSIST_PROFILE_ID] = AssistProfile.STANDARD_ID
            it[Keys.OVERLAY_PROFILE_ID] = AssistProfile.STANDARD_ID
        }
    }

    suspend fun clearAssessmentHistory() {
        context.vueConfortDataStore.edit {
            it[Keys.VISUAL_ASSESSMENTS] = ""
            it[Keys.STANDARDIZED_ASSESSMENTS] = ""
        }
    }

    suspend fun clearAutomationRules() {
        context.vueConfortDataStore.edit {
            it[Keys.AUTOMATION_RULES] = ""
            it[Keys.ACTIVE_RULE_ID] = ""
        }
    }

    suspend fun reset() {
        context.vueConfortDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(
        value: String?,
        default: T
    ): T {
        return value?.let {
            runCatching {
                enumValueOf<T>(it)
            }.getOrDefault(default)
        } ?: default
    }

    private fun migratedDefaults(
        scale: Float,
        alpha: Float,
        locked: Boolean,
        expanded: Boolean
    ): List<AssistProfile> = AssistProfile.defaults().map {
        if (it.id == AssistProfile.CUSTOM_ID) {
            it.copy(
                magnificationScale = scale.coerceIn(1f, 8f),
                overlayAlpha = alpha.coerceIn(0.55f, 1f),
                locked = locked,
                expanded = expanded
            )
        } else it
    }

    private fun uniqueName(
        profile: AssistProfile,
        profiles: List<AssistProfile>
    ): AssistProfile {
        val duplicate = profiles.any {
            it.id != profile.id && it.name.equals(profile.name, ignoreCase = true)
        }
        return if (duplicate) profile.copy(name = "${profile.name} (copie)") else profile
    }

    private fun encodeProfiles(profiles: List<AssistProfile>): String =
        profiles.joinToString("\n") { profile ->
            listOf(
                Uri.encode(profile.id),
                Uri.encode(profile.name),
                Uri.encode(profile.description),
                profile.magnificationScale.toString(),
                profile.magnificationEnabled.toString(),
                profile.overlayAlpha.toString(),
                profile.locked.toString(),
                profile.expanded.toString(),
                profile.predefined.toString(),
                profile.updatedAtMillis.toString(),
                Uri.encode(encodeOptical(profile.optical))
            ).joinToString("|")
        }

    private fun decodeProfiles(raw: String?): List<AssistProfile> =
        raw.orEmpty().lineSequence().mapNotNull { line ->
            val fields = line.split('|')
            if (fields.size !in 10..11) return@mapNotNull null
            runCatching {
                AssistProfile(
                    id = Uri.decode(fields[0]),
                    name = Uri.decode(fields[1]),
                    description = Uri.decode(fields[2]),
                    magnificationScale = fields[3].toFloat(),
                    magnificationEnabled = fields[4].toBooleanStrict(),
                    overlayAlpha = fields[5].toFloat(),
                    locked = fields[6].toBooleanStrict(),
                    expanded = fields[7].toBooleanStrict(),
                    predefined = fields[8].toBooleanStrict(),
                    updatedAtMillis = fields[9].toLong(),
                    optical = fields.getOrNull(10)?.let {
                        decodeOptical(Uri.decode(it))
                    } ?: OpticalSettings.Neutral
                ).sanitized()
            }.getOrNull()
        }.filter { it.id.isNotBlank() }.distinctBy { it.id }.toList()

    private fun encodeOptical(value: OpticalSettings) = listOf(
        value.enabled, value.sharpness, value.localContrast, value.gamma,
        value.brightness, value.saturation, value.temperature, value.whiteReduction,
        value.edgeEnhancement, value.horizontalStretch, value.verticalStretch,
        value.cylindricalDistortion, value.distortionAxisDegrees,
        value.globalIntensity, value.quality.name
    ).joinToString(",")

    private fun decodeOptical(raw: String): OpticalSettings {
        val f = raw.split(',')
        if (f.size != 15) return OpticalSettings.Neutral
        return runCatching {
            OpticalSettings(
                f[0].toBooleanStrict(), f[1].toFloat(), f[2].toFloat(),
                f[3].toFloat(), f[4].toFloat(), f[5].toFloat(), f[6].toFloat(),
                f[7].toFloat(), f[8].toFloat(), f[9].toFloat(), f[10].toFloat(),
                f[11].toFloat(), f[12].toFloat(), f[13].toFloat(),
                OpticalQuality.valueOf(f[14])
            ).sanitized()
        }.getOrDefault(OpticalSettings.Neutral)
    }

    private fun encodeRules(rules: List<AutomationRule>): String =
        rules.joinToString("\n") { rule ->
            listOf(
                Uri.encode(rule.id), Uri.encode(rule.name), rule.enabled,
                rule.priority, Uri.encode(rule.profileId), rule.trigger.name,
                Uri.encode(rule.packageName), rule.startMinutes, rule.endMinutes,
                rule.daysMask, rule.lightLevel.name, rule.createdAtMillis
            ).joinToString("|")
        }

    private fun decodeRules(raw: String?): List<AutomationRule> =
        raw.orEmpty().lineSequence().mapNotNull { line ->
            val f = line.split('|')
            if (f.size != 12) return@mapNotNull null
            runCatching {
                AutomationRule(
                    Uri.decode(f[0]), Uri.decode(f[1]), f[2].toBooleanStrict(),
                    f[3].toInt(), Uri.decode(f[4]), AutomationTrigger.valueOf(f[5]),
                    Uri.decode(f[6]), f[7].toInt(), f[8].toInt(), f[9].toInt(),
                    AmbientLightLevel.valueOf(f[10]), f[11].toLong()
                ).sanitized()
            }.getOrNull()
        }.filter { it.id.isNotBlank() && it.profileId.isNotBlank() }
            .distinctBy { it.id }.toList()

    private fun encodeAssessments(values: List<VisualComfortAssessment>): String =
        values.joinToString("\n") { value ->
            listOf(
                Uri.encode(value.id), value.createdAtMillis, Uri.encode(value.ageRange),
                value.wearsCorrection, value.testedWithCorrection, value.usualDistanceCm,
                value.physicalCalibrationFactor, value.physicalCalibrationConfirmed,
                value.distanceConfirmed, value.interruptedCount,
                Uri.encode(encodeEye(value.right)), Uri.encode(encodeEye(value.left)),
                Uri.encode(encodeEye(value.both)), value.reliability.name,
                value.doubleVision, value.recentDistortion, value.missingOrDarkArea
            ).joinToString("|")
        }

    private fun decodeAssessments(raw: String?): List<VisualComfortAssessment> =
        raw.orEmpty().lineSequence().mapNotNull { line ->
            val f = line.split('|')
            if (f.size != 17) return@mapNotNull null
            runCatching {
                VisualComfortAssessment(
                    Uri.decode(f[0]), f[1].toLong(), Uri.decode(f[2]),
                    f[3].toBooleanStrict(), f[4].toBooleanStrict(), f[5].toInt(),
                    f[6].toFloat(), f[7].toBooleanStrict(), f[8].toBooleanStrict(),
                    f[9].toInt(), decodeEye(Uri.decode(f[10])),
                    decodeEye(Uri.decode(f[11])), decodeEye(Uri.decode(f[12])),
                    ResultReliability.valueOf(f[13]), f[14].toBooleanStrict(),
                    f[15].toBooleanStrict(), f[16].toBooleanStrict()
                )
            }.getOrNull()
        }.distinctBy { it.id }.toList()

    private fun encodeEye(value: EyeComfortResult?): String =
        value?.let {
            listOf(
                it.eye.name, it.distanceCm, it.withCorrection, it.smallestOptotypeMm,
                it.acuityScore, it.errorRatePercent, it.contrastScore, it.overloadScore,
                it.minimumReadableSp, it.comfortableTextSp, it.preferredMagnification,
                it.trialCount
            ).joinToString(",")
        }.orEmpty()

    private fun decodeEye(raw: String): EyeComfortResult? {
        if (raw.isBlank()) return null
        val f = raw.split(',')
        if (f.size != 12) return null
        return runCatching {
            EyeComfortResult(
                TestedEye.valueOf(f[0]), f[1].toInt(), f[2].toBooleanStrict(),
                f[3].toFloat(), f[4].toInt(), f[5].toInt(), f[6].toInt(),
                f[7].toInt(), f[8].toInt(), f[9].toInt(), f[10].toFloat(),
                f[11].toInt()
            )
        }.getOrNull()
    }

    private fun encodeStandardReports(values: List<StandardizedAssessmentReport>) =
        values.joinToString("\n") { report ->
            listOf(
                Uri.encode(report.id), report.createdAtMillis,
                Uri.encode(encodeCalibration(report.calibration)),
                report.conditionsConfirmed,
                Uri.encode(report.acuityResults.joinToString(";") { encodeAcuity(it) }),
                Uri.encode(report.contrastResults.joinToString(";") { encodeContrast(it) }),
                Uri.encode(report.amslerResults.joinToString(";") { encodeAmsler(it) })
            ).joinToString("|")
        }

    private fun decodeStandardReports(raw: String?): List<StandardizedAssessmentReport> =
        raw.orEmpty().lineSequence().mapNotNull { line ->
            val f = line.split('|')
            if (f.size != 7) return@mapNotNull null
            runCatching {
                StandardizedAssessmentReport(
                    Uri.decode(f[0]), StandardProtocol.STANDARDIZED_V2, f[1].toLong(),
                    decodeCalibration(Uri.decode(f[2])), f[3].toBooleanStrict(),
                    Uri.decode(f[4]).split(';').mapNotNull(::decodeAcuity),
                    Uri.decode(f[5]).split(';').mapNotNull(::decodeContrast),
                    Uri.decode(f[6]).split(';').mapNotNull(::decodeAmsler)
                )
            }.getOrNull()
        }.distinctBy { it.id }.toList()

    private fun encodeCalibration(c: PhysicalDisplayCalibration) = listOf(
        c.horizontalFactor, c.verticalFactor, c.xdpi, c.ydpi, c.density,
        Uri.encode(c.orientation), c.calculatedWidthMm, c.calculatedHeightMm,
        Uri.encode(c.deviceModel), c.calibratedAtMillis, c.valid
    ).joinToString(",")

    private fun decodeCalibration(raw: String): PhysicalDisplayCalibration {
        val f = raw.split(',')
        return PhysicalDisplayCalibration(
            f[0].toFloat(), f[1].toFloat(), f[2].toFloat(), f[3].toFloat(),
            f[4].toFloat(), Uri.decode(f[5]), f[6].toFloat(), f[7].toFloat(),
            Uri.decode(f[8]), f[9].toLong(), f[10].toBooleanStrict()
        )
    }

    private fun encodeAcuity(v: StandardAcuityResult) = listOf(
        v.method.name, v.eye.name, v.distance.name, v.withCorrection,
        v.logMar ?: "", v.decimalAcuity ?: "", v.tenths ?: "",
        v.correctAnswers, v.totalTrials, v.errorRate, v.smallestDisplayableLogMar,
        v.smallestValidatedLogMar ?: "", v.reliability.name, v.measuredAtMillis
    ).joinToString(",")

    private fun decodeAcuity(raw: String): StandardAcuityResult? {
        if (raw.isBlank()) return null
        val f = raw.split(',')
        return StandardAcuityResult(
            AcuityMethod.valueOf(f[0]), StandardEye.valueOf(f[1]),
            TestDistance.valueOf(f[2]), f[3].toBooleanStrict(),
            f[4].toFloatOrNull(), f[5].toFloatOrNull(), f[6].toFloatOrNull(),
            f[7].toInt(), f[8].toInt(), f[9].toFloat(), f[10].toFloat(),
            f[11].toFloatOrNull(), ResultReliability.valueOf(f[12]), f[13].toLong()
        )
    }

    private fun encodeContrast(v: StandardContrastResult) = listOf(
        v.eye.name, v.minimumContrast ?: "", v.contrastSensitivity ?: "",
        v.logContrastSensitivity ?: "", v.correctAnswers, v.totalTrials,
        v.errorRate, v.declaredBrightnessStable, v.reliability.name
    ).joinToString(",")

    private fun decodeContrast(raw: String): StandardContrastResult? {
        if (raw.isBlank()) return null
        val f = raw.split(',')
        return StandardContrastResult(
            StandardEye.valueOf(f[0]), f[1].toFloatOrNull(), f[2].toFloatOrNull(),
            f[3].toFloatOrNull(), f[4].toInt(), f[5].toInt(), f[6].toFloat(),
            f[7].toBooleanStrict(), ResultReliability.valueOf(f[8])
        )
    }

    private fun encodeAmsler(v: AmslerResult) = listOf(
        v.eye.name, v.linesStraight, v.wavyLines, v.missingArea, v.darkArea,
        v.blurredArea, v.centralPointVisible, v.recentOrSudden,
        v.annotationX ?: "", v.annotationY ?: ""
    ).joinToString(",")

    private fun decodeAmsler(raw: String): AmslerResult? {
        if (raw.isBlank()) return null
        val f = raw.split(',')
        return AmslerResult(
            StandardEye.valueOf(f[0]), f[1].toBooleanStrict(), f[2].toBooleanStrict(),
            f[3].toBooleanStrict(), f[4].toBooleanStrict(), f[5].toBooleanStrict(),
            f[6].toBooleanStrict(), f[7].toBooleanStrict(),
            f[8].toFloatOrNull(), f[9].toFloatOrNull()
        )
    }

    private object Keys {
        val PROFILE_ID = stringPreferencesKey("profile_id")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val FONT_SIZE = floatPreferencesKey("font_size")
        val FONT_WEIGHT = intPreferencesKey("font_weight")
        val LETTER_SPACING = floatPreferencesKey("letter_spacing")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val FOREGROUND = longPreferencesKey("foreground")
        val BACKGROUND = longPreferencesKey("background")
        val COLUMN_WIDTH = floatPreferencesKey("column_width")
        val HORIZONTAL_MARGIN = floatPreferencesKey("horizontal_margin")
        val BRIGHTNESS = intPreferencesKey("brightness")
        val WARMTH = intPreferencesKey("warmth")
        val DESATURATION = intPreferencesKey("desaturation")
        val READING_GUIDE = booleanPreferencesKey("reading_guide")
        val LINE_FOCUS = booleanPreferencesKey("line_focus")
        val LOCAL_ZOOM = booleanPreferencesKey("local_zoom")
        val CALIBRATED = booleanPreferencesKey("calibrated")
        val CALIBRATION_CONFIDENCE =
            floatPreferencesKey("calibration_confidence")
        val CREATED_AT = longPreferencesKey("created_at")
        val UPDATED_AT = longPreferencesKey("updated_at")

        val AGE_RANGE = stringPreferencesKey("age_range")
        val SCREEN_TIME = floatPreferencesKey("screen_time")
        val READING_DISTANCE = intPreferencesKey("reading_distance")
        val WEARS_GLASSES = booleanPreferencesKey("wears_glasses")
        val NEAR_GLASSES = booleanPreferencesKey("near_glasses")
        val PRESBYOPIA = booleanPreferencesKey("presbyopia")
        val ASTIGMATISM = booleanPreferencesKey("astigmatism")
        val LIGHT_SENSITIVITY =
            booleanPreferencesKey("light_sensitivity")
        val MIGRAINE_SENSITIVITY =
            booleanPreferencesKey("migraine_sensitivity")
        val DRY_EYE = booleanPreferencesKey("dry_eye")
        val FATIGUE_SCORE = intPreferencesKey("fatigue_score")
        val BLUR = booleanPreferencesKey("blur")
        val HEADACHE = booleanPreferencesKey("headache")
        val SQUINTING = booleanPreferencesKey("squinting")
        val CHANGES_DISTANCE =
            booleanPreferencesKey("changes_distance")
        val PRIMARY_USAGE = stringPreferencesKey("primary_usage")
        val OVERLAY_BUTTON_X = intPreferencesKey("overlay_button_x")
        val OVERLAY_BUTTON_Y = intPreferencesKey("overlay_button_y")
        val OVERLAY_PANEL_X = intPreferencesKey("overlay_panel_x")
        val OVERLAY_PANEL_Y = intPreferencesKey("overlay_panel_y")
        val OVERLAY_EXPANDED = booleanPreferencesKey("overlay_expanded")
        val OVERLAY_LOCKED = booleanPreferencesKey("overlay_locked")
        val OVERLAY_ALPHA = floatPreferencesKey("overlay_alpha")
        val OVERLAY_PROFILE_ID = stringPreferencesKey("overlay_profile_id")
        val OVERLAY_SCALE = floatPreferencesKey("overlay_scale")
        val ASSIST_PROFILES = stringPreferencesKey("assist_profiles_v1")
        val ACTIVE_ASSIST_PROFILE_ID = stringPreferencesKey("active_assist_profile_id")
        val AUTOMATION_RULES = stringPreferencesKey("automation_rules_v1")
        val MANUAL_UNTIL = longPreferencesKey("manual_automation_until")
        val AUTOMATION_SOURCE = stringPreferencesKey("automation_source")
        val AUTOMATION_REASON = stringPreferencesKey("automation_reason")
        val ACTIVE_RULE_ID = stringPreferencesKey("active_automation_rule_id")
        val LAST_AUTOMATION_AT = longPreferencesKey("last_automation_at")
        val VISUAL_ASSESSMENTS = stringPreferencesKey("visual_assessments_v1")
        val STANDARDIZED_ASSESSMENTS = stringPreferencesKey("standardized_assessments_v2")
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed_v1")
    }
}
