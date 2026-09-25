package fr.vueconfort.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.vueconfort.app.equalizer.*
import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.model.EyePrescription
import fr.vueconfort.app.model.OpticalPrescription
import fr.vueconfort.app.model.VisualProfile
import fr.vueconfort.app.nativevision.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.Base64

/** Actual private-file persistence, isolated from the user's live preview and historical loupe. */
@RunWith(AndroidJUnit4::class)
class EqualizerStorageDeviceTest {
    @Test fun profileSurvivesStoreClosureAndReopeningExactlyWithoutTouchingBilanReaderOrLoupe() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.seedOtherFeatures()
            val before = fixture.store.data.first().asMap()
            val profile = EqualizerProfile(
                preferences = EqualizerPreferences(1.2345678f, 0.3456789f, 1.1234567f, 0.456789f, 617, 0.8765432f),
                scene = EqualizerScene.PHOTO, revision = 9,
                confirmedBilan = ConfirmedBilanReference.from(fixture.repository.opticalPrescription.first()),
                context = EqualizerContext(42.5f, false),
                applied = EqualizerRenderRecord("synthetic-test-engine", EqualizerRenderDecision.APPLY,
                    mapOf("sharpness" to 0.3456789f), listOf("SYNTHETIC_STORAGE_TEST"), 9),
                extensions = mapOf("test.namespace" to "Métadonnées |\n conservées")
            )
            val saved = fixture.repository.saveEqualizerProfile(profile)
            assertEquals(saved, fixture.repository.equalizerProfile.first())
            assertEquals(before, fixture.store.data.first().asMap().filterKeys { it.name != "equalizer_profile_v1" })
            val raw = fixture.store.data.first()[EQUALIZER_KEY]
            assertTrue(fixture.file.isFile)
            fixture.reopen()
            assertEquals(saved, fixture.repository.equalizerProfile.first())
            assertEquals(raw, fixture.store.data.first()[EQUALIZER_KEY])
            assertEquals(before, fixture.store.data.first().asMap().filterKeys { it.name != "equalizer_profile_v1" })
        } finally { fixture.close() }
    }

    @Test fun neutralSaveAndProfileDeletionHaveDifferentEffectsAndNeverEraseBilanOrLoupe() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.seedOtherFeatures()
            val before = fixture.store.data.first().asMap()
            fixture.store.edit { it[LEGACY_KEY] = "unreleased global refinement sidecar" }
            val saved = fixture.repository.saveEqualizerProfile(EqualizerProfile(
                preferences = EqualizerPreferences(sharpness = 0.6f), scene = EqualizerScene.DETAILS
            ))
            assertNull(fixture.store.data.first()[LEGACY_KEY])
            val neutral = fixture.repository.saveEqualizerProfile(saved.copy(preferences = EqualizerPreferences.Neutral))
            assertEquals(EqualizerPreferences.Neutral, neutral.preferences)
            assertEquals(EqualizerScene.DETAILS, neutral.scene)
            assertNotNull(fixture.repository.equalizerProfile.first())
            assertNotNull(fixture.repository.opticalPrescription.first())
            fixture.store.edit { it[LEGACY_KEY] = "obsolete sidecar must not resurrect after deletion" }
            fixture.repository.deleteEqualizerProfile()
            assertNull(fixture.repository.equalizerProfile.first())
            assertNull(fixture.store.data.first()[LEGACY_KEY])
            assertEquals(before, fixture.store.data.first().asMap())
            fixture.reopen()
            assertNull(fixture.repository.equalizerProfile.first())
            assertEquals(before, fixture.store.data.first().asMap())
        } finally { fixture.close() }
    }

    @Test fun changingOrDeletingBilanInvalidatesItsReferencesButKeepsPerceptualChoices() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.seedOtherFeatures()
            val preferences = EqualizerPreferences(sharpness = 0.4f, lightComfort = 0.3f)
            val source = fixture.repository.opticalPrescription.first()!!
            fixture.repository.saveEqualizerProfile(EqualizerProfile(
                preferences = preferences, confirmedBilan = ConfirmedBilanReference.from(source),
                calculated = EqualizerCalculatedParameters("synthetic", 0, mapOf("test" to 1f), "SYNTHETIC_STORAGE_TEST"),
                applied = EqualizerRenderRecord("synthetic", EqualizerRenderDecision.APPLY, emptyMap(), sourceRevision = 0)
            ))
            fixture.repository.saveOpticalPrescription(source.copy(leftEye = EyePrescription(sphere = 0.5f)))
            val refreshed = fixture.repository.equalizerProfile.first()!!
            assertEquals(ConfirmedBilanReference.from(fixture.repository.opticalPrescription.first()), refreshed.confirmedBilan)
            assertNull(refreshed.calculated)
            assertNull(refreshed.applied)
            assertEquals(preferences, refreshed.preferences)
            fixture.repository.deleteOpticalPrescription()
            assertNull(fixture.repository.opticalPrescription.first())
            assertNull(fixture.repository.equalizerProfile.first()!!.confirmedBilan)
            assertEquals(preferences, fixture.repository.equalizerProfile.first()!!.preferences)
        } finally { fixture.close() }
    }

    @Test fun malformedStoredProfileIsReportedAndNeverSilentlyReplacedWithNeutral() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.store.edit { it[EQUALIZER_KEY] = "unsupported-future-or-corrupted-profile" }
            assertTrue(runCatching { fixture.repository.equalizerProfile.first() }.isFailure)
            assertEquals("unsupported-future-or-corrupted-profile", fixture.store.data.first()[EQUALIZER_KEY])
        } finally { fixture.close() }
    }

    @Test fun malformedExistingProfileCannotBeOverwrittenBySave() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.seedOtherFeatures()
            fixture.store.edit { it[EQUALIZER_KEY] = "unreadable-profile" }
            val before = fixture.store.data.first().asMap()
            assertTrue(runCatching { fixture.repository.saveEqualizerProfile(EqualizerProfile()) }.isFailure)
            assertEquals(before, fixture.store.data.first().asMap())
        } finally { fixture.close() }
    }

    @Test fun nativeUpdateAndStaleEqualizerSaveKeepBothIndependentSubsetsAcrossReopening() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.seedOtherFeatures()
            val before = fixture.store.data.first().asMap()
            val equalizer = fixture.repository.saveEqualizerProfile(EqualizerProfile(
                preferences = EqualizerPreferences(sharpness = 0.31f), revision = 4,
                applied = EqualizerRenderRecord("test", EqualizerRenderDecision.APPLY, emptyMap(), sourceRevision = 4)))
            val request = NativeVisionRequestedState(mapOf(NativeVisionCapability.RELUMINO to
                NativeVisionValue.Relumino(true, ReluminoThickness.FOUR, ReluminoColor.BLACK)), revision = 3, updatedAtMillis = 123)
            val nativeSaved = fixture.repository.updateNativeVision { it.copy(enabled = true, requested = request,
                restorationReferences = mapOf(NativeVisionCapability.RELUMINO to "lab-transaction-test")) }
            assertEquals(equalizer.preferences, nativeSaved.preferences)
            assertEquals(equalizer.revision, nativeSaved.revision)
            assertEquals(equalizer.applied, nativeSaved.applied)
            val staleSave = fixture.repository.saveEqualizerProfile(equalizer.copy(preferences = EqualizerPreferences(sharpness = 0.4f)))
            assertEquals(request, staleSave.nativeVision.requested)
            assertEquals(nativeSaved.nativeVision.restorationReferences, staleSave.nativeVision.restorationReferences)
            fixture.reopen()
            val reopened = fixture.repository.equalizerProfile.first()!!
            assertEquals(staleSave, reopened)
            assertEquals(before, fixture.store.data.first().asMap().filterKeys { it.name != "equalizer_profile_v1" })
        } finally { fixture.close() }
    }

    @Test fun nativeUpdateDoesNotReplaceMalformedExistingProfile() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.store.edit { it[EQUALIZER_KEY] = "future-profile-unknown" }
            assertTrue(runCatching { fixture.repository.updateNativeVision { NativeVisionProfile() } }.isFailure)
            assertEquals("future-profile-unknown", fixture.store.data.first()[EQUALIZER_KEY])
        } finally { fixture.close() }
    }

    @Test fun legacyProfileMigratesInMemoryAndOnlyExplicitWritePersistsSchemaTwo() = runBlocking {
        val fixture = Fixture()
        try {
            fun encoded(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
            val legacy = EqualizerProfileCodec.encode(EqualizerProfile(preferences = EqualizerPreferences(sharpness = 0.27f)))
                .lineSequence().filterNot { it.startsWith(encoded("nativeVision") + "=") }
                .map { if (it.startsWith(encoded("schema") + "=")) encoded("schema") + "=" + encoded("1") else it }.joinToString("\n")
            fixture.store.edit { it[EQUALIZER_KEY] = legacy }
            val migrated = fixture.repository.equalizerProfile.first()!!
            assertEquals(2, migrated.schemaVersion)
            assertEquals(legacy, fixture.store.data.first()[EQUALIZER_KEY])
            fixture.repository.updateNativeVision { it.copy(enabled = true) }
            assertNotEquals(legacy, fixture.store.data.first()[EQUALIZER_KEY])
            assertEquals(0.27f, fixture.repository.equalizerProfile.first()!!.preferences.sharpness)
        } finally { fixture.close() }
    }

    @Test fun futureSchemaAppearingAfterSuccessfulReadCannotBeOverwrittenByStaleSave() = runBlocking {
        val fixture = Fixture()
        try {
            fixture.repository.saveEqualizerProfile(EqualizerProfile(preferences = EqualizerPreferences(sharpness = 0.3f)))
            val draft = fixture.repository.equalizerProfile.first()!!
            fun encoded(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
            val future = EqualizerProfileCodec.encode(draft).lineSequence().map { line ->
                if (line.startsWith(encoded("schema") + "=")) encoded("schema") + "=" + encoded("99") else line
            }.joinToString("\n")
            fixture.store.edit { it[EQUALIZER_KEY] = future }
            assertTrue(runCatching { fixture.repository.saveEqualizerProfile(draft.copy(preferences = EqualizerPreferences.Neutral)) }.isFailure)
            assertEquals(future, fixture.store.data.first()[EQUALIZER_KEY])
        } finally { fixture.close() }
    }

    private class Fixture {
        private val context = ApplicationProvider.getApplicationContext<Context>().also {
            check(it.packageName == "fr.vueconfort.app.preview")
        }
        val file = File(context.filesDir, "datastore/equalizer-test-${UUID.randomUUID()}.preferences_pb")
        private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var store: DataStore<Preferences> = newStore()
            private set
        var repository = VisualProfileRepository(context, store)
            private set

        private fun newStore() = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

        suspend fun seedOtherFeatures() {
            repository.ensureAssistProfilesMigrated()
            val loupe = AssistProfile.defaults().first().copy(id = "test-loupe", name = "Loupe conservée", magnificationScale = 2.75f, predefined = false)
            repository.upsertAssistProfile(loupe)
            repository.activateAssistProfile(loupe.id)
            repository.saveOverlayPreferences(OverlayPreferences(buttonX = 55, panelY = 211, activeProfileId = loupe.id, magnificationScale = 2.75f))
            repository.saveProfile(VisualProfile(fontSizeSp = 23f, fontWeight = 620, warmthPercent = 27))
            repository.saveOpticalPrescription(OpticalPrescription(rightEye = EyePrescription(sphere = -1.25f), notes = "Synthetic storage test"))
        }

        suspend fun reopen() {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            store = newStore()
            repository = VisualProfileRepository(context, store)
        }

        suspend fun close() {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    companion object {
        private val EQUALIZER_KEY = stringPreferencesKey("equalizer_profile_v1")
        private val LEGACY_KEY = stringPreferencesKey("vision_refinement_v1")
    }
}
