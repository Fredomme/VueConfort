package fr.vueconfort.app.orchestration

import fr.vueconfort.app.assessment.*
import fr.vueconfort.app.calibration.*
import fr.vueconfort.app.equalizer.*
import fr.vueconfort.app.model.*
import fr.vueconfort.app.nativevision.*
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test

class VisionProfileSnapshotTest {
    @Test fun optionalEvidenceIsUnknownAndDoesNotBlockPerceptualPreferences() {
        val profile = EqualizerProfile(preferences = EqualizerPreferences(sharpness = .5f, contrast = 1.2f))
        val snapshot = VisionProfileSnapshot(profile)
        assertSame(profile, snapshot.sourceProfile)
        assertSame(profile.preferences, snapshot.equalizerPreferences)
        assertEquals(VisionPrescriptionStatus.ABSENT, snapshot.prescriptionStatus)
        assertNull(snapshot.confirmedPrescription)
        assertNull(snapshot.calculatedParameters)
        assertFalse(snapshot.calibration.hasRecordedEvidence)
        assertEquals(VisionQuantitativeUncertainty.NOT_RECORDED, snapshot.calibration.quantitativeUncertainty)
        assertTrue(snapshot.calibration.references.isEmpty())
        assertTrue(snapshot.calibration.conditions.isEmpty())
        assertNull(snapshot.declaredDistanceCm)
        assertNull(snapshot.correctionWorn)
    }

    @Test fun oldProfileMigrationPreservesExistingParametersAndDoesNotInventNativeOrClinicalData() {
        val source = EqualizerProfile(preferences = EqualizerPreferences(sharpness = .4f), revision = 9,
            context = EqualizerContext(42f, false),
            calculated = EqualizerCalculatedParameters("existing-engine", 9, mapOf("existing.parameter" to .2f), "RECORDED"),
            extensions = mapOf("future.optical.request" to "existing opaque extension"))
        val legacy = EqualizerProfileCodec.encode(source).lineSequence()
            .filterNot { it.startsWith(b64("nativeVision") + "=") }
            .map { if (it.startsWith(b64("schema") + "=")) b64("schema") + "=" + b64("1") else it }.joinToString("\n")
        val migrated = requireNotNull(EqualizerProfileCodec.decode(legacy))
        val snapshot = VisionProfileSnapshot(migrated)
        assertEquals(source.preferences, snapshot.equalizerPreferences)
        assertEquals(source.calculated, snapshot.calculatedParameters)
        assertEquals(source.extensions, snapshot.sourceProfile.extensions)
        assertEquals(EqualizerProfile.CURRENT_SCHEMA_VERSION, snapshot.sourceSchemaVersion)
        assertEquals(9L, snapshot.sourceRevision)
        assertEquals(42f, snapshot.declaredDistanceCm)
        assertEquals(false, snapshot.correctionWorn)
        assertTrue(snapshot.nativeRequested.values.isEmpty())
        assertNull(snapshot.confirmedPrescription)
    }

    @Test fun matchedConfirmedBilanRetainsExactlyTheProvidedMeasurements() {
        val prescription = prescription()
        val profile = EqualizerProfile(confirmedBilan = ConfirmedBilanReference.from(prescription))
        val snapshot = VisionProfileSnapshot(profile, prescription)
        assertEquals(VisionPrescriptionStatus.CONFIRMED, snapshot.prescriptionStatus)
        assertSame(prescription, snapshot.confirmedPrescription)
        assertTrue(snapshot.prescriptionReferenceMatchesProfile)
        assertEquals(-1.25f, snapshot.confirmedPrescription!!.rightEye.sphere)
        assertNull(snapshot.calculatedParameters)
        assertFalse(snapshot.calibration.hasRecordedEvidence)
    }

    @Test fun confirmedBilanCanBeViewedWithoutPretendingAMissingProfileLinkWasRecorded() {
        val prescription = prescription()
        val snapshot = VisionProfileSnapshot(prescription = prescription)
        assertSame(prescription, snapshot.confirmedPrescription)
        assertEquals(VisionPrescriptionStatus.CONFIRMED, snapshot.prescriptionStatus)
        assertFalse(snapshot.prescriptionReferenceMatchesProfile)
        assertNull(snapshot.sourceProfile.confirmedBilan)
    }

    @Test fun calibrationEnrichmentNeverErasesWhyAProvidedBilanIsUnavailable() {
        val valid = prescription()
        val source = EqualizerProfile(confirmedBilan = ConfirmedBilanReference.from(valid))
        val calibration = VisionCalibrationSnapshot(perceptualSession = CalibrationSession(startedAtMillis = 12))
        for (document in listOf(null, valid, valid.copy(confirmedByUser = false),
            valid.copy(rightEye = EyePrescription(sphere = Float.NaN)), valid.copy(updatedAtMillis = 99))) {
            val original = VisionProfileSnapshot(source, document)
            val enriched = original.withCalibration(calibration)
            assertEquals(original.prescriptionStatus, enriched.prescriptionStatus)
            assertSame(original.confirmedPrescription, enriched.confirmedPrescription)
            assertSame(original.sourceProfile, enriched.sourceProfile)
            assertSame(calibration, enriched.calibration)
            assertEquals(original.prescriptionReferenceMatchesProfile, enriched.prescriptionReferenceMatchesProfile)
        }
    }

    @Test fun unconfirmedInvalidOrMismatchedBilanNeverBecomesCurrentConfirmedEvidence() {
        val valid = prescription()
        val cases = listOf(
            valid.copy(confirmedByUser = false) to VisionPrescriptionStatus.UNCONFIRMED,
            valid.copy(rightEye = EyePrescription(sphere = Float.NaN)) to VisionPrescriptionStatus.INVALID,
            valid.copy(updatedAtMillis = valid.updatedAtMillis + 1) to VisionPrescriptionStatus.PROFILE_REFERENCE_MISMATCH
        )
        val profile = EqualizerProfile(confirmedBilan = ConfirmedBilanReference.from(valid))
        for ((value, expected) in cases) {
            val snapshot = VisionProfileSnapshot(profile, value)
            assertEquals(expected, snapshot.prescriptionStatus)
            assertNull(snapshot.confirmedPrescription)
            assertFalse(snapshot.prescriptionReferenceMatchesProfile)
        }
        assertNull(VisionProfileSnapshot(profile).confirmedPrescription)
    }

    @Test fun requestedRecommendedAndAppliedStatesRetainTheirOwnRevisionsAndOrigins() {
        val cap = NativeVisionCapability.RELUMINO
        val value = NativeVisionValue.Relumino(true, ReluminoThickness.FOUR, ReluminoColor.GREEN)
        val result = NativeVisionApplicationResult(cap, value, NativeVisionApplicationStatus.NEEDS_USER_ACTION,
            engine = NativeVisionEngine.SAMSUNG_SETTINGS, provenance = "USER_CONFIRMED", reason = "User confirmation only",
            timestampMillis = 50, profileRevision = 12, confirmation = NativeVisionConfirmation.USER_CONFIRMED)
        val profile = EqualizerProfile(revision = 4,
            applied = EqualizerRenderRecord("agsl-perceptual-1", EqualizerRenderDecision.APPLY,
                mapOf("sharpness" to .4f), sourceRevision = 4),
            nativeVision = NativeVisionProfile(enabled = true,
                preferences = NativeVisionPreferences(recommended = NativeVisionRequestedState(mapOf(cap to value.copy(thickness = ReluminoThickness.ONE)))),
                requested = NativeVisionRequestedState(mapOf(cap to value), revision = 12),
                applied = NativeVisionAppliedState(mapOf(cap to result))))
        val snapshot = VisionProfileSnapshot(profile)
        assertEquals(4L, snapshot.sourceRevision)
        assertEquals(12L, snapshot.nativeRevision)
        assertSame(profile.applied, snapshot.equalizerApplied)
        assertSame(profile.nativeVision.requested, snapshot.nativeRequested)
        assertSame(profile.nativeVision.applied, snapshot.nativeApplied)
        assertSame(profile.nativeVision.preferences, snapshot.nativePreferences)
        assertEquals(result, snapshot.currentNativeResult(cap))
        assertNull(snapshot.currentNativeResult(cap)!!.applied)
        assertEquals(NativeVisionApplicationStatus.NEEDS_USER_ACTION, snapshot.currentNativeResult(cap)!!.status)
        val changed = profile.copy(nativeVision = profile.nativeVision.copy(requested = profile.nativeVision.requested.copy(revision = 13)))
        assertNull(VisionProfileSnapshot(changed).currentNativeResult(cap))
    }

    @Test fun constructingSnapshotDoesNotWriteOrReplaceTheExistingProfile() {
        val profile = EqualizerProfile(preferences = EqualizerPreferences(sharpness = .2f),
            extensions = mapOf("optical.future" to "uninterpreted"))
        val before = EqualizerProfileCodec.encode(profile)
        val snapshot = VisionProfileSnapshot(profile, prescription(), VisionCalibrationSnapshot(), VisualProfile(calibrated = true))
        assertSame(profile, snapshot.sourceProfile)
        assertEquals(before, EqualizerProfileCodec.encode(profile))
        assertNull(snapshot.sourceProfile.confirmedBilan)
        assertEquals(mapOf("optical.future" to "uninterpreted"), snapshot.sourceProfile.extensions)
    }

    @Test fun requestFactoryKeepsNativeAndPerceptualIdentitiesScopesAndRevisionsSeparate() {
        val cap = NativeVisionCapability.RELUMINO
        val contours = NativeVisionValue.Relumino(true, ReluminoThickness.FOUR, ReluminoColor.BLACK)
        val profile = EqualizerProfile(revision = 5, preferences = EqualizerPreferences(sharpness = .4f),
            nativeVision = NativeVisionProfile(enabled = true,
                requested = NativeVisionRequestedState(mapOf(cap to contours), revision = 13)))
        val requests = VisionProfileSnapshot(profile).requests()
        assertEquals(2, requests.size)
        assertEquals("perceptual.equalizer", requests[0].id)
        assertEquals(EnginePayload.Perceptual(profile.preferences, profile.scene), requests[0].payload)
        assertEquals(5L, requests[0].sourceRevision)
        assertEquals(setOf(VisionTransport.INTERNAL_PREVIEW), requests[0].allowedTransports)
        assertEquals("native.RELUMINO", requests[1].id)
        assertEquals(EnginePayload.Native(cap, contours), requests[1].payload)
        assertEquals(13L, requests[1].sourceRevision)
        assertEquals(setOf(VisionTransport.ANDROID_NATIVE, VisionTransport.SAMSUNG_NATIVE), requests[1].allowedTransports)
        assertEquals(listOf(requests[1]), VisionProfileSnapshot(profile).requests(includePreview = false))
    }

    @Test fun disabledProfileHasNoAutomaticNativeIntentButExplicitOffRemainsAvailableForTransitionValidation() {
        val off = NativeVisionValue.Relumino(false)
        val profile = EqualizerProfile(nativeVision = NativeVisionProfile(enabled = false,
            requested = NativeVisionRequestedState(mapOf(NativeVisionCapability.RELUMINO to off), revision = 3)))
        val snapshot = VisionProfileSnapshot(profile)
        assertEquals(1, snapshot.requests().size)
        assertTrue(snapshot.requests(includePreview = false).isEmpty())
        assertEquals(EnginePayload.Native(NativeVisionCapability.RELUMINO, off), snapshot.nativeRequests().single().payload)
        assertEquals(3L, snapshot.nativeRequests().single().sourceRevision)
        assertTrue(VisionProfileSnapshot().nativeRequests().isEmpty())
    }

    @Test fun neitherBilanNorCalibrationNorOpaqueExtensionCreatesAnUnrequestedOpticalTreatment() {
        val snapshot = VisionProfileSnapshot(
            sourceProfile = EqualizerProfile(extensions = mapOf("optical.future" to "uninterpreted")),
            prescription = prescription(),
            calibration = VisionCalibrationSnapshot(perceptualSession = CalibrationSession(confidenceScore = 1f)))
        assertTrue(snapshot.requests().none { it.payload is EnginePayload.Optical })
        assertTrue(snapshot.requests().none { VisionTransport.FUTURE_DISPLAY_LENS in it.allowedTransports })
        assertTrue(snapshot.nativeRequests().isEmpty())
    }

    @Test fun storedQuestionnaireAndReaderDefaultsNeverBecomeMeasuredViewingConditions() {
        val questionnaire = UserVisualContext()
        val reader = VisualProfile(calibrated = true, calibrationConfidence = 1f)
        val snapshot = VisionProfileSnapshot(visualProfile = reader, declaredUserContext = questionnaire)
        assertSame(questionnaire, snapshot.declaredUserContext)
        assertSame(reader, snapshot.visualProfile)
        assertNull(snapshot.declaredDistanceCm)
        assertNull(snapshot.correctionWorn)
        assertFalse(snapshot.calibration.hasRecordedEvidence)
        assertNull(snapshot.confirmedPrescription)
        assertNull(snapshot.calculatedParameters)
    }

    @Test fun perceptualSessionPreservesChoicesAndConfidenceWithoutInventingPhysicalOrOpticalEvidence() {
        val session = CalibrationSession(startedAtMillis = 10, completedAtMillis = 20,
            trials = listOf(CalibrationTrial(CalibrationParameter.FONT_SIZE, VisualProfile(fontSizeSp = 18f),
                VisualProfile(fontSizeSp = 22f), 1, CalibrationChoice.OPTION_B, 650)),
            resultingProfile = VisualProfile(fontSizeSp = 22f, calibrated = true, calibrationConfidence = 1f), confidenceScore = 1f)
        val calibration = VisionCalibrationSnapshot(perceptualSession = session)
        val snapshot = VisionProfileSnapshot(calibration = calibration)
        assertSame(session, snapshot.calibration.perceptualSession)
        assertEquals(1f, snapshot.calibration.perceptualSession!!.confidenceScore)
        assertEquals(VisionCalibrationKind.PERCEPTUAL_PREFERENCES, calibration.references.single().kind)
        assertNull(calibration.references.single().recordId)
        assertEquals(10L, calibration.references.single().recordedAtMillis)
        val conditions = calibration.conditions.single()
        assertNull(conditions.conditionsConfirmed)
        assertNull(conditions.distanceConfirmed)
        assertNull(conditions.physicalDisplayCalibrationConfirmed)
        assertTrue(conditions.trials.isEmpty())
        assertEquals(VisionQuantitativeUncertainty.NOT_RECORDED, calibration.quantitativeUncertainty)
        assertNull(snapshot.confirmedPrescription)
        assertNull(snapshot.calculatedParameters)
    }

    @Test fun comfortAssessmentKeepsOriginalResultsReliabilityAndDeclaredConditions() {
        val eye = EyeComfortResult(TestedEye.BOTH, 40, false, 2f, 60, 20, 50, 10, 18, 22, 2f, 8)
        val assessment = VisualComfortAssessment("comfort-1", 123, "NOT_SPECIFIED", false, false, 40,
            1.03f, false, true, 2, null, null, eye, ResultReliability.LOW, false, false, false)
        val calibration = VisionCalibrationSnapshot(comfortAssessment = assessment)
        assertSame(assessment, calibration.comfortAssessment)
        assertEquals("comfort-1", calibration.references.single().recordId)
        assertEquals(123L, calibration.references.single().recordedAtMillis)
        assertEquals(ResultReliability.LOW, calibration.comfortAssessment!!.reliability)
        val conditions = calibration.conditions.single()
        assertEquals(false, conditions.physicalDisplayCalibrationConfirmed)
        assertEquals(true, conditions.distanceConfirmed)
        assertEquals(2, conditions.interruptedCount)
        assertNull(conditions.conditionsConfirmed)
        assertEquals(40, conditions.trials.single().recordedDistanceCm)
        assertEquals(false, conditions.trials.single().withCorrection)
        assertEquals(VisionQuantitativeUncertainty.NOT_RECORDED, calibration.quantitativeUncertainty)
        assertNull(VisionProfileSnapshot(calibration = calibration).confirmedPrescription)
    }

    @Test fun standardizedAssessmentPreservesMissingValuesAndDoesNotBorrowConditionsAcrossTests() {
        val acuity = StandardAcuityResult(AcuityMethod.LANDOLT_C, StandardEye.OD, TestDistance.NEAR_40, true,
            null, null, null, 3, 5, .4f, .1f, null, ResultReliability.LOW, 110)
        val contrast = StandardContrastResult(StandardEye.OU, .2f, 5f, null, 4, 6, .333f, false, ResultReliability.MEDIUM)
        val report = StandardizedAssessmentReport("standard-1", StandardProtocol.STANDARDIZED_V2, 120,
            PhysicalDisplayCalibration(1f, 1f, 400f, 400f, 3f, "portrait", 65f, 140f, "synthetic", 100, true),
            false, listOf(acuity), listOf(contrast), emptyList())
        val calibration = VisionCalibrationSnapshot(standardizedAssessment = report)
        assertSame(report, calibration.standardizedAssessment)
        assertNull(calibration.standardizedAssessment!!.acuityResults.single().logMar)
        assertEquals("standard-1", calibration.references.single().recordId)
        val conditions = calibration.conditions.single()
        assertEquals(false, conditions.conditionsConfirmed)
        assertNull("A valid physical calibration is not a confirmation flag", conditions.physicalDisplayCalibrationConfirmed)
        assertEquals(40, conditions.trials[0].recordedDistanceCm)
        assertEquals(true, conditions.trials[0].withCorrection)
        assertNull(conditions.trials[1].recordedDistanceCm)
        assertNull(conditions.trials[1].withCorrection)
        assertEquals(false, conditions.trials[1].brightnessDeclaredStable)
        assertEquals(VisionQuantitativeUncertainty.NOT_RECORDED, calibration.quantitativeUncertainty)
        assertNull(VisionProfileSnapshot(calibration = calibration).calculatedParameters)
    }

    private fun prescription() = OpticalPrescription(rightEye = EyePrescription(sphere = -1.25f, cylinder = -.5f, axisDegrees = 90),
        source = PrescriptionSource.PDF, updatedAtMillis = 25)
    private fun b64(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
}
