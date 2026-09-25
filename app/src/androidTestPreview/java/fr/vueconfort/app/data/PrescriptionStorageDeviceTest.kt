package fr.vueconfort.app.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.model.EyePrescription
import fr.vueconfort.app.model.OpticalPrescription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Synthetic values in a separate private file; never resets the user's preview settings. */
@RunWith(AndroidJUnit4::class)
class PrescriptionStorageDeviceTest {
    private lateinit var repository: VisualProfileRepository
    private lateinit var scope: CoroutineScope
    private lateinit var file: File

    @Before fun createIsolatedStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName == "fr.vueconfort.app.preview")
        file = File(context.filesDir, "datastore/prescription-test-${UUID.randomUUID()}.preferences_pb")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        repository = VisualProfileRepository(context, store)
    }

    @After fun closeAndDeleteIsolatedStore() = runBlocking {
        if (::scope.isInitialized) scope.coroutineContext[Job]!!.cancelAndJoin()
        if (::file.isInitialized) file.delete()
        Unit
    }

    @Test fun saveCommitsBilanAndSelectedProfileThenDeleteRemovesAllBilanHistory() = runBlocking {
        val repo = repository
        val prescription = OpticalPrescription(rightEye = EyePrescription(sphere = -1.25f))
        repo.saveOpticalPrescription(prescription)
        val assist = AssistProfile.defaults().first().copy(
            id = "synthetic-bilan", name = "Démonstration", predefined = false, magnificationScale = 2.5f)
        repo.savePrescriptionAndAssistProfile(prescription.copy(leftEye = EyePrescription(sphere = -0.5f)), assist)
        assertEquals(-0.5f, repo.opticalPrescription.first()!!.leftEye.sphere!!, 0f)
        assertEquals("synthetic-bilan", repo.activeAssistProfile.first().id)
        assertEquals(2.5f, repo.activeAssistProfile.first().magnificationScale, 0f)
        assertTrue(repo.opticalPrescriptionHistory.first().isNotEmpty())
        val retainedProfiles = repo.assistProfiles.first()
        repo.deleteOpticalPrescription()
        assertNull(repo.opticalPrescription.first())
        assertTrue(repo.opticalPrescriptionHistory.first().isEmpty())
        assertEquals(retainedProfiles, repo.assistProfiles.first())
    }

    @Test fun unconfirmedImportCannotChangeSavedBilanOrProfile() = runBlocking {
        val repo = repository
        val profileBefore = repo.activeAssistProfile.first().id
        try {
            repo.savePrescriptionAndAssistProfile(
                OpticalPrescription(rightEye = EyePrescription(sphere = -1f), confirmedByUser = false),
                AssistProfile.defaults().last())
            fail("Unconfirmed values must be rejected before any storage mutation")
        } catch (_: IllegalArgumentException) { }
        assertNull(repo.opticalPrescription.first())
        assertTrue(repo.opticalPrescriptionHistory.first().isEmpty())
        assertEquals(profileBefore, repo.activeAssistProfile.first().id)
    }
}
