package fr.vueconfort.app.nativevision

import android.app.UiAutomation
import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.test.platform.app.InstrumentationRegistry
import fr.vueconfort.app.BuildConfig
import fr.vueconfort.app.MainActivity
import fr.vueconfort.app.model.VisualProfile
import fr.vueconfort.app.ui.screens.HomeScreen
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Real Activity navigation. The only cross-app observation is an event's package and class name. */
class NativeVisionNavigationDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun homeOpensNativeSettingsThenPublicSamsungPageAndRevalidatesOnReturn() {
        check(rule.activity.packageName == "fr.vueconfort.app.preview" && !BuildConfig.NATIVE_VISION_LAB)
        val adapter = SamsungNativeVisionAdapter(rule.activity)
        assumeTrue("Navigation ciblée sur le S25 attesté", adapter.isAttestedS25)
        val controller = NativeVisionController.get(rule.activity)
        rule.waitUntil(10_000) { controller.state.value.loaded }
        runBlocking { controller.refresh().join() }
        rule.waitForIdle()
        val before = controller.state.value.profile
        val settingsBefore = adapter.read(NativeVisionCapability.RELUMINO).settings
        val usedExistingHome = rule.onAllNodesWithTag("native_open").fetchSemanticsNodes().isNotEmpty()
        if (!usedExistingHome) {
            // A fresh preview can be at onboarding. Do not change onboarding or grant any permission for a test.
            // Use the real Home composable and its real Native Vision destination with an in-memory route.
            rule.runOnUiThread {
                rule.activity.setContent {
                    var native by remember { mutableStateOf(false) }
                    MaterialTheme {
                        if (native) NativeVisionScreen(onBack = { native = false })
                        else HomeScreen(profile = VisualProfile(), onQuestionnaire = {}, onCalibration = {},
                            onEqualizer = {}, onVisualAssessment = {}, onReading = {}, onProfile = {},
                            onSettings = {}, onCoreStatus = {}, onHelp = {}, onMagnifierSetup = {},
                            onOpticalPrescription = {}, onNativeVision = { native = true })
                    }
                }
            }
        }
        rule.onNodeWithTag("native_open").performScrollTo().performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithTag("native_configure_relumino").fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking { controller.refresh().join() }
        rule.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        val resumeEvents = AtomicInteger()
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeEvents.incrementAndGet()
        }
        rule.runOnUiThread { rule.activity.lifecycle.addObserver(lifecycleObserver) }
        var settingsOpened = false
        try {
            val event = automation.executeAndWaitForEvent({
                rule.onNodeWithTag("native_configure_relumino").performScrollTo().performClick()
            }, { candidate ->
                candidate.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    candidate.packageName?.toString() == "com.android.settings" &&
                    candidate.className?.toString()?.startsWith("com.android.settings.") == true
            }, 10_000)
            settingsOpened = true
            val actualClass = event.className.toString()
            assertEquals("com.android.settings", event.packageName.toString())
            @Suppress("DEPRECATION")
            event.recycle()

            // Settings can reuse an existing task: observe its settled foreground before returning.
            var settingsStableSince: Long? = null
            assertTrue("Settings must be stably foreground before Back", awaitCondition(10_000) {
                if (foregroundPackage(automation) != "com.android.settings") {
                    settingsStableSince = null
                    false
                } else {
                    val now = SystemClock.uptimeMillis()
                    if (settingsStableSince == null) settingsStableSince = now
                    now - requireNotNull(settingsStableSince) >= 500
                }
            })
            val beforeResumeTimestamp = controller.state.value.capabilities!!.observedAtMillis
            val beforeResumeEvents = resumeEvents.get()
            // Back may expose an older Settings page or the launcher in a reused Settings task.
            // The fallback brings the existing source Activity forward through a public Intent only.
            pressBack(automation)
            val returnMode = if (awaitCondition(2_000) {
                    foregroundPackage(automation) == rule.activity.packageName && resumeEvents.get() > beforeResumeEvents
                }) "BACK" else {
                returnToSource()
                "EXPLICIT_REORDER_TO_FRONT"
            }
            assertTrue("The source Activity must actually resume in the foreground", awaitCondition(10_000) {
                foregroundPackage(automation) == rule.activity.packageName && resumeEvents.get() > beforeResumeEvents
            })
            settingsOpened = false
            // Do not call refresh here: this must be the Native Vision screen's ON_RESUME refresh.
            rule.waitUntil(10_000) {
                controller.state.value.capabilities?.observedAtMillis?.let { it > beforeResumeTimestamp } == true
            }
            rule.onNodeWithTag("native_configure_relumino").assertExists()
            rule.waitForIdle()
            val after = controller.state.value.profile
            assertTrue("Navigation must preserve requested native preferences", before.requested == after.requested)
            assertTrue("Navigation must preserve recommendations and disable choice", before.preferences == after.preferences)
            assertEquals(settingsBefore, adapter.read(NativeVisionCapability.RELUMINO).settings)
            File(rule.activity.filesDir, "native-vision-commercial-ui-navigation.json").writeText(JSONObject()
                .put("result", "PASS").put("homeRoute", if (usedExistingHome) "EXISTING_HOME" else "REAL_HOME_COMPOSABLE_WITHOUT_ONBOARDING_WRITE")
                .put("settingsActivityObserved", actualClass).put("publicNavigation", true)
                .put("returnMode", returnMode).put("sourceResumeObserved", true)
                .put("automaticRefreshObservedBeforeManualRefresh", true)
                .put("capabilitiesBeforeReturnMillis", beforeResumeTimestamp)
                .put("capabilitiesAfterReturnMillis", controller.state.value.capabilities!!.observedAtMillis)
                .put("returnedAndRevalidated", true).put("requestedPreferencesUnchanged", true)
                .put("reluminoSettingsUnchanged", true).put("screenCaptured", false).toString(2))
        } finally {
            try {
                if (settingsOpened || foregroundPackage(automation) == "com.android.settings") returnToSource()
            } finally {
                rule.runOnUiThread { rule.activity.lifecycle.removeObserver(lifecycleObserver) }
            }
        }
    }

    private fun returnToSource() = rule.runOnUiThread {
        rule.activity.startActivity(Intent(rule.activity, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    private fun foregroundPackage(automation: UiAutomation): String? {
        val root = automation.rootInActiveWindow ?: return null
        return try { root.packageName?.toString() } finally {
            @Suppress("DEPRECATION")
            root.recycle()
        }
    }

    private fun awaitCondition(timeoutMillis: Long, condition: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return true
            SystemClock.sleep(50)
        }
        return condition()
    }

    private fun pressBack(automation: UiAutomation) {
        val now = SystemClock.uptimeMillis()
        assertTrue(automation.injectInputEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK, 0), true))
        assertTrue(automation.injectInputEvent(KeyEvent(now, now + 20, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK, 0), true))
    }
}
