package fr.vueconfort.app.product

import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fr.vueconfort.app.BuildConfig
import fr.vueconfort.app.MainActivity
import fr.vueconfort.app.data.VisualProfileRepository
import fr.vueconfort.app.nativevision.NativeVisionCapability
import fr.vueconfort.app.nativevision.NativeSettingRead
import fr.vueconfort.app.nativevision.SamsungNativeVisionAdapter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Navigates the actual commercial graph, including idempotent setup with an existing profile.
 * No fake Activity content, form edit, system grant or magnification command. Run after completing
 * the first-launch recipe in the isolated Preview app; its data is preserved by strict assertions.
 */
class ProductJourneyDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private lateinit var repository: VisualProfileRepository

    @Before fun requireConfiguredCommercialPreview() {
        check(rule.activity.packageName == "fr.vueconfort.app.preview")
        check(!BuildConfig.NATIVE_VISION_LAB)
        assertEquals(PackageManager.PERMISSION_DENIED,
            rule.activity.checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS"))
        repository = VisualProfileRepository(rule.activity)
        assumeTrue("Complete the first-launch recipe first; this test never replaces an existing profile",
            runBlocking { repository.onboardingCompleted.first() })
        waitFor("home_configure")
    }

    @Test fun existingProfileReachesEveryCommercialToolAndReturnsWithoutChangingUserChoices() {
        val before = userChoices()
        val systemBefore = systemSettings()

        click("home_configure")
        listOf("welcome_try", "welcome_unknown", "welcome_known", "welcome_document").forEach {
            rule.onNodeWithTag(it).performScrollTo().assertIsDisplayed()
        }
        backTo("home_configure")

        // All four entry points must converge on the same saved profile, not merely be visible.
        click("home_configure")
        click("welcome_known")
        waitFor("bilan_screen")
        waitForText("Vérifions vos informations")
        rule.onNodeWithText("Œil droit · OD").performScrollTo().assertIsDisplayed()
        backTo("home_configure")
        assertEquals("Known correction setup must preserve existing choices", before, userChoices())

        click("home_configure")
        click("welcome_document")
        waitFor("bilan_screen")
        waitForText("Importez votre bilan")
        rule.onNodeWithText("Importer une photo ou un PDF").performScrollTo().assertIsDisplayed()
        backTo("home_configure")
        assertEquals("Document setup must not import or replace anything before a document is chosen", before, userChoices())

        click("home_configure")
        click("welcome_unknown")
        waitForText("Ajustement guidé")
        waitForText("Version A")
        backTo("home_configure")
        assertEquals("Starting calibration without answering must preserve existing choices", before, userChoices())

        click("home_configure")
        click("welcome_try")
        waitFor("eq_preview")
        rule.onNodeWithTag("eq_preview").assertIsDisplayed()
        backTo("home_configure")
        assertEquals("Immediate trial must reopen the existing profile without a second profile", before, userChoices())

        click("home_profile")
        click("profile_equalizer")
        waitFor("eq_preview")
        rule.onNodeWithTag("eq_preview").assertIsDisplayed()
        rule.onNodeWithTag("eq_original").assertIsDisplayed()
        rule.onNodeWithTag("eq_slider_contrast").performScrollTo().assertIsDisplayed()
        backTo("profile_equalizer")

        click("profile_bilan")
        waitFor("bilan_screen")
        backTo("profile_equalizer")

        click("profile_native")
        waitFor("native_more")
        backTo("profile_equalizer")

        click("profile_tools")
        rule.onNodeWithText("Profils du lecteur et de la loupe").assertIsDisplayed()
        rule.onNodeWithText("Réglages optiques expérimentaux").assertDoesNotExist()
        backTo("profile_equalizer")
        backTo("home_configure")

        click("home_settings")
        click("settings_permissions")
        waitFor("permission_accessibility")
        rule.onNodeWithTag("permission_service_state").performScrollTo().assertIsDisplayed()
        click("permission_return")
        backTo("home_configure")

        assertEquals("Navigation must preserve the user's settings, bilan and loupe profiles", before, userChoices())
        assertEquals("Opening a page does not apply a Samsung setting", systemBefore, systemSettings())
        receipt("navigation", "Four real setup choices converge on the unchanged profile; Home/Profile/Equalizer/Bilan/Native/tools/permissions navigation; no document selection, form edit or grant")
    }

    @Test fun activityRecreationKeepsTheProfileAndHomeWithoutRestartingSetup() {
        val before = userChoices()
        rule.activityRule.scenario.recreate()
        waitFor("home_configure")
        rule.onNodeWithTag("welcome_try").assertDoesNotExist()
        click("home_profile")
        waitFor("profile_equalizer")
        assertEquals(before, userChoices())
        backTo("home_configure")
        receipt("recreation", "Real Activity recreation; saved profile unchanged, onboarding not shown again (not a device reboot)")
    }

    private fun click(tag: String) {
        waitFor(tag)
        rule.onNodeWithTag(tag).performScrollTo().performClick()
        rule.waitForIdle()
    }

    private fun backTo(tag: String) {
        rule.onNodeWithText("Retour").performClick()
        waitFor(tag)
    }

    private fun waitFor(tag: String) {
        rule.waitUntil(15_000) { rule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    private fun waitForText(text: String) {
        rule.waitUntil(15_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
    }

    private fun userChoices(): List<Any?> = runBlocking {
        val personal = repository.equalizerProfile.first()
        // A Native page may refresh a readback receipt; it must not alter any user request.
        listOf(repository.onboardingCompleted.first(), repository.profile.first(),
            repository.userContext.first(), repository.opticalPrescription.first(),
            repository.opticalPrescriptionHistory.first(), repository.assistProfiles.first(),
            repository.activeAssistProfileId.first(), personal?.id, personal?.schemaVersion,
            personal?.revision, personal?.preferences, personal?.scene,
            personal?.context, personal?.confirmedBilan, personal?.nativeVision?.enabled,
            personal?.nativeVision?.requested)
    }

    private fun systemSettings(): Map<String, NativeSettingRead> {
        val adapter = SamsungNativeVisionAdapter(rule.activity)
        return NativeVisionCapability.entries.filter { it != NativeVisionCapability.MAGNIFICATION }
            .flatMap { capability -> adapter.read(capability).settings.map { (key, value) ->
                "${capability.name}/$key" to value
            } }.toMap()
    }

    private fun receipt(name: String, evidence: String) {
        File(rule.activity.filesDir, "product-journey-$name.json").writeText(JSONObject()
            .put("result", "PASS").put("evidence", evidence)
            .put("packageName", rule.activity.packageName).put("grantedPrivilegedPermission", false)
            .put("timestampMillis", System.currentTimeMillis()).toString(2))
    }
}
