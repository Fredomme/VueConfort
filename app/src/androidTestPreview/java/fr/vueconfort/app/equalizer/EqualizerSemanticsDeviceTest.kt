package fr.vueconfort.app.equalizer

import android.os.Build
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotEnabled
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
import fr.vueconfort.app.nativevision.NativeVisionController
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Interaction and geometry checks only. Does not read or export any pixels. */
class EqualizerSemanticsDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var model: EqualizerViewModel

    @Before fun openSyntheticEqualizerDraft() {
        assumeTrue("Égaliseur complet à partir d’Android 13", Build.VERSION.SDK_INT >= 33)
        check(rule.activity.packageName == "fr.vueconfort.app.preview")
        rule.runOnUiThread {
            model = ViewModelProvider(rule.activity, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EqualizerViewModel(rule.activity.application, SavedStateHandle()) as T
            })["equalizer-semantics-only", EqualizerViewModel::class.java]
            rule.activity.setContent { EqualizerScreen(onBack = {}, onBilan = {}, viewModel = model) }
        }
        rule.waitUntil(10_000) { model.uiState.value.loaded }
        runBlocking { NativeVisionController.get(rule.activity).refresh().join() }
        rule.runOnUiThread {
            model.updatePreferences { EqualizerPreferences.Neutral }
            model.selectScene(EqualizerScene.TEXT)
        }
        rule.waitForIdle()
    }

    @Test fun fiveControlsAndSceneChoicesPreserveViewportAndOnlyModifyDraft() {
        val saved = model.uiState.value.savedProfile
        val bilan = model.uiState.value.confirmedPrescription
        val native = NativeVisionController.get(rule.activity).state.value.profile.requested
        val bounds = rule.onNodeWithTag("eq_preview").fetchSemanticsNode().boundsInRoot
        slider("size", 1.4f)
        assertEquals(1.4f, model.uiState.value.draft.preferences.sizeScale, .001f)
        slider("sharpness", .65f)
        assertEquals(.65f, model.uiState.value.draft.preferences.sharpness, .001f)
        slider("contrast", 1.4f)
        assertEquals(1.4f, model.uiState.value.draft.preferences.contrast, .001f)
        slider("comfort", .7f)
        assertEquals(.7f, model.uiState.value.draft.preferences.lightComfort, .001f)
        slider("text_weight", 700f)
        assertEquals(700, model.uiState.value.draft.preferences.fontWeight)
        assertEquals(bounds, rule.onNodeWithTag("eq_preview").fetchSemanticsNode().boundsInRoot)
        val choices = model.uiState.value.draft.preferences
        for ((tag, scene) in listOf("detail" to EqualizerScene.DETAILS, "image" to EqualizerScene.PHOTO,
                "reading" to EqualizerScene.TEXT)) {
            rule.onNodeWithTag("eq_scene_$tag").performClick()
            rule.waitForIdle()
            assertEquals(scene, model.uiState.value.draft.scene)
            assertEquals(choices, model.uiState.value.draft.preferences)
            assertEquals(bounds, rule.onNodeWithTag("eq_preview").fetchSemanticsNode().boundsInRoot)
            if (scene != EqualizerScene.TEXT) rule.onNodeWithTag("eq_slider_text_weight").assertIsNotEnabled()
        }
        assertSavedEqualizerChoicesUnchanged(saved)
        assertTrue("Bilan must remain untouched", bilan == model.uiState.value.confirmedPrescription)
        assertTrue("Native requests must remain untouched", native == NativeVisionController.get(rule.activity).state.value.profile.requested)
        receipt("controls", "Five controls, three scenes and fixed viewport verified without capture")
    }

    @Test fun holdingOriginalPreservesDraftAndGeometryThenRestoresComparisonState() {
        rule.runOnUiThread { model.updatePreferences { EqualizerPreferences(sizeScale = 1.4f, sharpness = .65f,
            contrast = 1.4f, lightComfort = .7f, fontWeight = 730, intensity = .85f) } }
        rule.waitForIdle()
        val before = model.uiState.value.draft
        val saved = model.uiState.value.savedProfile
        val native = NativeVisionController.get(rule.activity).state.value.profile.requested
        val bounds = rule.onNodeWithTag("eq_preview").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("eq_original").performTouchInput { down(center) }
        try {
            rule.waitForIdle()
            rule.onNodeWithTag("eq_original").assertContentDescriptionEquals("Original affiché, relâcher pour retrouver mon réglage")
            assertEquals(before, model.uiState.value.draft)
            assertEquals(bounds, rule.onNodeWithTag("eq_preview").fetchSemanticsNode().boundsInRoot)
        } finally {
            rule.onNodeWithTag("eq_original").performTouchInput { up() }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("eq_original").assertContentDescriptionEquals("Maintenir pour voir l’original")
        assertEquals(before, model.uiState.value.draft)
        assertSavedEqualizerChoicesUnchanged(saved)
        assertEquals("Original must not change native requests", native,
            NativeVisionController.get(rule.activity).state.value.profile.requested)
        assertEquals(bounds, rule.onNodeWithTag("eq_preview").fetchSemanticsNode().boundsInRoot)
        receipt("original", "Original press and release preserve draft, scene and viewport without capture")
    }

    private fun slider(name: String, value: Float) {
        rule.onNodeWithTag("eq_slider_$name").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(value)) }
        rule.waitForIdle()
    }

    private fun receipt(name: String, evidence: String) {
        File(rule.activity.filesDir, "native-vision-equalizer-semantics-$name.json").writeText(JSONObject()
            .put("result", "PASS").put("evidence", evidence).put("screenCaptured", false)
            .put("savedEqualizerChoicesModifiedByControls", false)
            .put("nativeRequestedPreferencesModifiedByControls", false)
            .put("nativeObservationsMayRefresh", true).toString(2))
    }

    private fun assertSavedEqualizerChoicesUnchanged(before: EqualizerProfile?) {
        val after = model.uiState.value.savedProfile
        if (before == null || after == null) assertEquals(before, after)
        else {
            // The independent native controller revalidates on resume and may update its own
            // observations plus the profile timestamp. It must not alter equalizer/bilan fields.
            assertEquals("Saved equalizer and bilan fields must remain untouched",
                before.copy(nativeVision = after.nativeVision, provenance = after.provenance), after)
        }
    }
}
