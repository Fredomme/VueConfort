package fr.vueconfort.app.nativevision

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeVisionLabPermissionDeviceTest {
    /** Run this class before granting the development permission externally. */
    @Test fun withoutDevelopmentPermissionDoesNotWrite() {
        val fixture = NativeVisionLabDeviceFixture("lab-without-permission")
        assertFalse("Revoke the development permission externally before this test", fixture.hasPermission())
        val adapter = fixture.adapter()
        try {
            val capability = fixture.capabilities()[NativeVisionCapability.RELUMINO]!!
            assertFalse(capability.canApplyAutomatically)
            val result = adapter.apply(fixture.request(NativeVisionValue.Relumino(true, ReluminoThickness.FOUR, ReluminoColor.GREEN), 1)).single()
            fixture.event("REFUSAL", org.json.JSONObject().put("status", result.status.name).put("reason", result.reason))
            assertEquals(NativeVisionApplicationStatus.NEEDS_USER_PERMISSION, result.status)
            assertNull(result.applied)
            assertFalse(adapter.hasPendingRestoration())
        } finally { fixture.finishReadOnly() }
    }
}
