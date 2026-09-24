package fr.vueconfort.app.equalizer

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import fr.vueconfort.app.MainActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Real screen, real controls and actual rendered pixels. Only the in-memory draft is changed:
 * no save/delete/reset repository operation is used, so an existing bilan/loupe/profile is safe.
 */
class EqualizerInteractionDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var model: EqualizerViewModel

    @Before fun showEqualizer() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        check(rule.activity.packageName == "fr.vueconfort.app.preview")
        rule.runOnUiThread {
            model = ViewModelProvider(rule.activity, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EqualizerViewModel(rule.activity.application, SavedStateHandle()) as T
            })["equalizer-interaction-test", EqualizerViewModel::class.java]
            rule.activity.setContent { EqualizerScreen(onBack = {}, onBilan = {}, viewModel = model) }
        }
        rule.waitUntil(10_000) { model.uiState.value.loaded }
        draft(EqualizerPreferences.Neutral)
        rule.runOnUiThread { model.selectScene(EqualizerScene.TEXT) }
        rule.waitForIdle()
    }

    @Test fun everyWiredControlChangesTheActualStationaryPreview() {
        val sceneBounds = rule.onNodeWithTag("eq_preview").fetchSemanticsNode().boundsInRoot
        val neutral = preview()
        val controls = listOf(
            "size" to 1.4f,
            "sharpness" to 0.65f,
            "contrast" to 1.4f,
            "comfort" to 0.7f,
            "text_weight" to 700f
        )
        for ((tag, value) in controls) {
            draft(EqualizerPreferences.Neutral)
            slider(tag, value)
            assertTrue("$tag must change rendered pixels", changed(neutral, preview()) > 20)
            assertEquals("scrolling controls must retain the preview position", sceneBounds,
                rule.onNodeWithTag("eq_preview").fetchSemanticsNode().boundsInRoot)
        }
        draft(EqualizerPreferences(contrast = 1.4f, lightComfort = .7f, fontWeight = 700))
        val full = preview()
        slider("intensity", 0f)
        assertTrue("global intensity is visibly connected", changed(full, preview()) > 20)
        assertArrayEquals("zero intensity restores neutral pixels", neutral, preview())
    }

    @Test fun holdOriginalPreservesExactZoomFrameAndContentThenRestoresTreatment() {
        val chosen = EqualizerPreferences(sizeScale = 1.4f, sharpness = .65f, contrast = 1.4f,
            lightComfort = .7f, fontWeight = 730, intensity = .85f)
        draft(EqualizerPreferences.Neutral.copy(sizeScale = chosen.sizeScale))
        val originalAtSameZoom = preview()
        val frame = rule.onNodeWithTag("eq_preview").fetchSemanticsNode().boundsInRoot
        draft(chosen)
        val treated = preview()
        assertTrue(changed(originalAtSameZoom, treated) > 100)
        val profileBefore = model.uiState.value.draft
        val savedBefore = model.uiState.value.savedProfile
        rule.onNodeWithTag("eq_original").performTouchInput { down(center) }
        try {
            rule.waitForIdle()
            assertArrayEquals("held original uses exact same content, crop, position and zoom",
                originalAtSameZoom, preview())
            assertEquals(frame, rule.onNodeWithTag("eq_preview").fetchSemanticsNode().boundsInRoot)
            assertEquals("comparison does not change the draft", profileBefore, model.uiState.value.draft)
            assertEquals("comparison does not change the saved profile", savedBefore, model.uiState.value.savedProfile)
        } finally {
            rule.onNodeWithTag("eq_original").performTouchInput { up() }
        }
        rule.waitForIdle()
        assertArrayEquals("release returns the exact previous treatment", treated, preview())
        assertEquals(chosen, model.uiState.value.draft.preferences)
    }

    @Test fun zeroIntensityAtNonDefaultSizeAndWeightIsExactNeutralAtTheSameSize() {
        val chosen = EqualizerPreferences(sizeScale = 1.4f, sharpness = .8f, contrast = 1.5f,
            lightComfort = 1f, fontWeight = 800, intensity = 0f)
        draft(EqualizerPreferences.Neutral.copy(sizeScale = chosen.sizeScale))
        val reference = preview()
        draft(chosen)
        assertArrayEquals("zero intensity removes text thickness and pixel processing, preserving size",
            reference, preview())
        assertEquals(chosen, model.uiState.value.draft.preferences)
    }

    @Test fun sceneButtonsKeepAllPreferencesAndRestoreTheSameTextPixels() {
        val chosen = EqualizerPreferences(sizeScale = 1.25f, sharpness = .55f, contrast = 1.3f,
            lightComfort = .3f, fontWeight = 650, intensity = .8f)
        draft(chosen)
        val text = preview()
        rule.onNodeWithTag("eq_scene_detail").performClick()
        rule.waitForIdle()
        assertEquals(EqualizerScene.DETAILS, model.uiState.value.draft.scene)
        assertEquals(chosen, model.uiState.value.draft.preferences)
        val details = preview()
        assertTrue("details scene supplies different visual content", changed(text, details) > 100)
        rule.onNodeWithTag("eq_slider_text_weight").assertIsNotEnabled()
        rule.onNodeWithTag("eq_scene_image").performClick()
        rule.waitForIdle()
        assertEquals(EqualizerScene.PHOTO, model.uiState.value.draft.scene)
        assertEquals(chosen, model.uiState.value.draft.preferences)
        assertTrue("local image supplies actual detailed pixels", changed(details, preview()) > 100)
        rule.onNodeWithTag("eq_slider_text_weight").assertIsNotEnabled()
        rule.onNodeWithTag("eq_scene_reading").performClick()
        rule.waitForIdle()
        assertEquals(chosen, model.uiState.value.draft.preferences)
        assertArrayEquals("returning to reading restores the same content and treatment", text, preview())
    }

    @Test fun resetAndCancelAreDistinctDraftActionsAndKeepTheSavedProfileAndBilan() {
        val savedBefore = model.uiState.value.savedProfile
        val bilanBefore = model.uiState.value.confirmedPrescription
        draft(EqualizerPreferences(sizeScale = 1.4f, sharpness = .7f, contrast = 1.4f,
            lightComfort = .4f, fontWeight = 710))
        rule.onNodeWithTag("eq_menu").performClick()
        rule.onNodeWithTag("eq_reset").performClick()
        rule.waitForIdle()
        assertEquals(EqualizerPreferences.Neutral, model.uiState.value.draft.preferences)
        assertEquals(savedBefore, model.uiState.value.savedProfile)
        assertEquals(bilanBefore, model.uiState.value.confirmedPrescription)
        // Make cancellation available even when no profile had been saved before this test.
        draft(EqualizerPreferences(sizeScale = 1.61f, contrast = 1.37f, fontWeight = 791))
        rule.onNodeWithTag("eq_cancel").performClick()
        rule.waitForIdle()
        assertEquals(savedBefore?.preferences ?: EqualizerPreferences.Neutral,
            model.uiState.value.draft.preferences)
        assertEquals(savedBefore?.scene ?: EqualizerScene.TEXT, model.uiState.value.draft.scene)
        assertEquals(savedBefore, model.uiState.value.savedProfile)
        assertEquals(bilanBefore, model.uiState.value.confirmedPrescription)
        assertFalse(model.uiState.value.hasUnsavedChanges)
    }

    private fun draft(preferences: EqualizerPreferences) {
        rule.runOnUiThread { model.updatePreferences { preferences } }
        rule.waitForIdle()
    }

    private fun slider(name: String, value: Float) {
        rule.onNodeWithTag("eq_slider_$name").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { action ->
                assertTrue("slider accepts the actual user command", action(value))
            }
        rule.waitForIdle()
    }

    private fun preview(): IntArray {
        rule.waitForIdle()
        return rule.onNodeWithTag("eq_preview").captureToImage().pixels()
    }

    private fun ImageBitmap.pixels(): IntArray = IntArray(width * height).also {
        asAndroidBitmap().getPixels(it, 0, width, 0, 0, width, height)
    }

    private fun changed(first: IntArray, second: IntArray): Int {
        assertEquals("preview extent remains fixed", first.size, second.size)
        return first.indices.count { first[it] != second[it] }
    }
}
