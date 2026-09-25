package fr.vueconfort.app.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.vueconfort.app.equalizer.EqualizerScreen
import fr.vueconfort.app.calibration.CalibrationViewModel
import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.recommendation.RecommendationEngine
import fr.vueconfort.app.ui.screens.CalibrationScreen
import fr.vueconfort.app.ui.screens.HomeScreen
import fr.vueconfort.app.ui.screens.OpticalPrescriptionScreen
import fr.vueconfort.app.ui.screens.ProfileScreen
import fr.vueconfort.app.ui.screens.QuestionnaireScreen
import fr.vueconfort.app.ui.screens.QuickReadingSetupScreen
import fr.vueconfort.app.ui.screens.ReadingScreen
import fr.vueconfort.app.ui.screens.SettingsScreen
import fr.vueconfort.app.ui.screens.CoreStatusScreen
import fr.vueconfort.app.ui.screens.ProductWelcomeScreen
import fr.vueconfort.app.ui.screens.PersonalProfileScreen
import fr.vueconfort.app.ui.screens.PermissionsScreen
import fr.vueconfort.app.ui.screens.VisionEntry
import fr.vueconfort.app.ui.screens.PrescriptionEntry
import fr.vueconfort.app.ui.screens.VueConfortColors
import fr.vueconfort.app.BuildConfig
import fr.vueconfort.app.ui.screens.FirstLaunchScreen
import fr.vueconfort.app.ui.screens.GuidedSetupScreen
import fr.vueconfort.app.ui.screens.HelpScreen
import fr.vueconfort.app.ui.screens.AboutScreen
import fr.vueconfort.app.ui.screens.PrivacyScreen
import fr.vueconfort.app.viewmodel.VueConfortViewModel
import fr.vueconfort.app.assessment.AssessmentHistoryScreen
import fr.vueconfort.app.assessment.StandardizedAssessmentScreen
import fr.vueconfort.app.optical.OpticalSettingsScreen
import fr.vueconfort.app.core.VueConfortCoreState

@Composable
fun VueConfortApp(
    modifier: Modifier = Modifier,
    mainViewModel: VueConfortViewModel = viewModel()
) {
    val context = LocalContext.current
    val navController =
        rememberNavController()

    val profile by
        mainViewModel.profile.collectAsStateWithLifecycle()
    val assistProfiles by
        mainViewModel.assistProfiles.collectAsStateWithLifecycle()
    val activeAssistProfile by
        mainViewModel.activeAssistProfile.collectAsStateWithLifecycle()
    val automationRules by
        mainViewModel.automationRules.collectAsStateWithLifecycle()
    val automationStatus by
        mainViewModel.automationStatus.collectAsStateWithLifecycle()
    val visualAssessments by
        mainViewModel.visualAssessments.collectAsStateWithLifecycle()
    val standardizedAssessments by
        mainViewModel.standardizedAssessments.collectAsStateWithLifecycle()
    val onboardingCompleted by
        mainViewModel.onboardingCompleted.collectAsStateWithLifecycle()

    val opticalPrescription by
        mainViewModel.opticalPrescription.collectAsStateWithLifecycle()
    var prescriptionEntry by rememberSaveable { mutableStateOf(PrescriptionEntry.CHOOSE) }
    var prescriptionCalibrationBase by remember {
        mutableStateOf<fr.vueconfort.app.model.VisualProfile?>(null)
    }

    if (onboardingCompleted == null) {
        MaterialTheme(colorScheme = VueConfortColors) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        return
    }

    val initialRoute = remember { if (onboardingCompleted == true) AppRoute.Home.route else AppRoute.Welcome.route }
    MaterialTheme(colorScheme = VueConfortColors) {
        NavHost(
            navController = navController,
            startDestination = initialRoute,
            modifier = modifier
        ) {
            composable(
                route = AppRoute.Home.route
            ) {
                HomeScreen(
                    profile = profile,
                    onNativeVision = { navController.navigate(AppRoute.NativeVision.route) },
                    onEqualizer = { navController.navigate(AppRoute.Equalizer.route) },
                    onQuestionnaire = {
                        navController.navigate(
                            AppRoute.Welcome.route
                        )
                    },
                    onCalibration = {
                        navController.navigate(
                            AppRoute.Calibration.route
                        )
                    },
                    onVisualAssessment = {
                        navController.navigate(AppRoute.VisualAssessment.route)
                    },
                    onReading = {
                        navController.navigate(
                            AppRoute.Reading.route
                        )
                    },
                    onProfile = {
                        navController.navigate(
                            AppRoute.Profile.route
                        )
                    },
                    onSettings = {
                        navController.navigate(
                            AppRoute.Settings.route
                        )
                    },
                    onCoreStatus = {
                        navController.navigate(AppRoute.CoreStatus.route)
                    },
                    onHelp = {
                        navController.navigate(AppRoute.Help.route)
                    },
                    onMagnifierSetup = {
                        navController.navigate(AppRoute.Permissions.route)
                    },
                    onOpticalPrescription = {
                        prescriptionEntry = PrescriptionEntry.CHOOSE
                        navController.navigate(AppRoute.OpticalPrescription.route)
                    }
                )
            }

            composable(route = AppRoute.NativeVision.route) {
                fr.vueconfort.app.nativevision.NativeVisionScreen(onBack = { navController.popBackStack() },
                    onMagnifierPermission = { navController.navigate(AppRoute.Permissions.route) })
            }

            composable(route = AppRoute.Equalizer.route) {
                EqualizerScreen(
                    onBack = { navController.popBackStack() },
                    onBilan = { prescriptionEntry = PrescriptionEntry.CHOOSE; navController.navigate(AppRoute.OpticalPrescription.route) },
                    onNativeVision = { navController.navigate(AppRoute.NativeVision.route) }
                )
            }

            composable(
                route = AppRoute.QuickReadingSetup.route
            ) {
                QuickReadingSetupScreen(
                    currentProfile = profile,
                    onBack = {
                        navController.popBackStack()
                    },
                    onCompleted = { quickProfile ->
                        mainViewModel.saveProfile(
                            quickProfile
                        )

                        navController.navigate(
                            AppRoute.Questionnaire.route
                        ) {
                            popUpTo(
                                AppRoute.QuickReadingSetup.route
                            ) {
                                inclusive = true
                            }
                        }
                    }
                )
            }

            composable(
                route = AppRoute.Questionnaire.route
            ) {
                QuestionnaireScreen(
                    onBack = {
                        navController.popBackStack()
                    },
                    onCompleted = { answers ->
                        val customized = RecommendationEngine.generateAssistProfile(
                            answers = answers,
                            previous = assistProfiles.firstOrNull {
                                it.id == AssistProfile.CUSTOM_ID
                            }
                        )
                        mainViewModel.saveAssistProfile(customized)
                        mainViewModel.activateAssistProfile(customized.id)
                        navController.popBackStack(AppRoute.Home.route, false)
                    }
                )
            }

            composable(route = AppRoute.Calibration.route) {
                val calibrationViewModel: CalibrationViewModel = viewModel()
                var saving by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                CalibrationScreen(
                    baseProfile = prescriptionCalibrationBase ?: profile,
                    viewModel = calibrationViewModel,
                    saving = saving, operationError = error,
                    onCalibrationCompleted = { calibrated ->
                        if (!saving) {
                            saving = true; error = null
                            mainViewModel.saveCalibratedProfile(calibrated) { result ->
                                saving = false
                                if (result.isSuccess) {
                                    prescriptionCalibrationBase = null
                                    navController.navigate(AppRoute.Equalizer.route) {
                                        popUpTo(AppRoute.Calibration.route) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                } else error = "Vos choix n’ont pas pu être enregistrés. Réessayez avant de quitter."
                            }
                        }
                    },
                    onBack = { if (!saving) { prescriptionCalibrationBase = null; navController.popBackStack() } }
                )
            }

            composable(route = AppRoute.VisualAssessment.route) {
                StandardizedAssessmentScreen(
                    onSaveReport = mainViewModel::saveStandardizedAssessment,
                    onSaveProfile = mainViewModel::saveAssistProfile,
                    onTryProfile = mainViewModel::activateAssistProfile,
                    onAdjustProfile = {
                        navController.navigate(AppRoute.Profile.route)
                    },
                    onHistory = {
                        navController.navigate(AppRoute.AssessmentHistory.route)
                    },
                    onMeasurementActive = { active ->
                        mainViewModel.pauseAutomation(if (active) null else 0L)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(route = AppRoute.AssessmentHistory.route) {
                AssessmentHistoryScreen(
                    standardized = standardizedAssessments,
                    legacy = visualAssessments,
                    onDeleteStandardized = mainViewModel::deleteStandardizedAssessment,
                    onDeleteLegacy = mainViewModel::deleteVisualAssessment,
                    onRepeat = {
                        navController.navigate(AppRoute.VisualAssessment.route)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = AppRoute.Reading.route
            ) {
                ReadingScreen(
                    profile = profile,
                    assistProfile = activeAssistProfile,
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = AppRoute.ToolProfiles.route
            ) {
                ProfileScreen(
                    profiles = assistProfiles,
                    activeProfile = activeAssistProfile,
                    onActivate = mainViewModel::activateAssistProfile,
                    onCreate = { mainViewModel.createAssistProfile() },
                    onDuplicate = { mainViewModel.createAssistProfile(it) },
                    onSave = mainViewModel::saveAssistProfile,
                    onDelete = mainViewModel::deleteAssistProfile,
                    onRestore = mainViewModel::restoreAssistProfile,
                    onOpticalSettings = {
                        navController.navigate(AppRoute.OpticalSettings.route)
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(route = AppRoute.Profile.route) {
                PersonalProfileScreen(
                    onEqualizer = { navController.navigate(AppRoute.Equalizer.route) },
                    onBilan = { prescriptionEntry = PrescriptionEntry.CHOOSE; navController.navigate(AppRoute.OpticalPrescription.route) },
                    onCalibration = { navController.navigate(AppRoute.Calibration.route) },
                    onNativeVision = { navController.navigate(AppRoute.NativeVision.route) },
                    onTools = { navController.navigate(AppRoute.ToolProfiles.route) },
                    onHistory = { navController.navigate(AppRoute.AssessmentHistory.route) },
                    onExercises = { navController.navigate(AppRoute.VisualAssessment.route) },
                    onBack = { navController.popBackStack() })
            }
            composable(route = AppRoute.Permissions.route) { PermissionsScreen { navController.popBackStack() } }

            composable(
                route = AppRoute.Settings.route
            ) {
                var resetting by remember { mutableStateOf(false) }
                var resetError by remember { mutableStateOf<String?>(null) }
                SettingsScreen(
                    resetting = resetting, operationError = resetError,
                    profiles = assistProfiles,
                    rules = automationRules,
                    status = automationStatus,
                    onSaveRule = mainViewModel::saveAutomationRule,
                    onDeleteRule = mainViewModel::deleteAutomationRule,
                    onPauseAutomation = mainViewModel::pauseAutomation,
                    onHelp = { navController.navigate(AppRoute.Help.route) },
                    onAbout = { navController.navigate(AppRoute.About.route) },
                    onPrivacy = { navController.navigate(AppRoute.Privacy.route) },
                    onPermissions = { navController.navigate(AppRoute.Permissions.route) },
                    onNativeVision = { navController.navigate(AppRoute.NativeVision.route) },
                    onRedoSetup = {
                        navController.navigate(AppRoute.Welcome.route)
                    },
                    onResetProfiles = mainViewModel::resetAssistProfiles,
                    onClearHistory = mainViewModel::clearAssessmentHistory,
                    onClearRules = mainViewModel::clearAutomationRules,
                    onResetAll = {
                        if (!resetting) {
                            resetting = true; resetError = null
                            mainViewModel.resetAll { result ->
                                resetting = false
                                if (result.isSuccess) navController.navigate(AppRoute.Welcome.route) {
                                    popUpTo(navController.graph.id) { inclusive = false }
                                } else resetError = "La restauration ou la suppression n’a pas abouti. Vos données sont conservées ; réessayez."
                            }
                        }
                    },
                    onBack = {
                        navController.popBackStack()
                    }
                )
            }

            composable(route = AppRoute.OpticalSettings.route) {
                OpticalSettingsScreen(
                    profile = activeAssistProfile,
                    onSave = mainViewModel::saveAssistProfile,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(route = AppRoute.OpticalPrescription.route) {
                var saving by remember { mutableStateOf(false) }
                var operationError by remember { mutableStateOf<String?>(null) }
                OpticalPrescriptionScreen(
                    saved = opticalPrescription,
                    currentAssistProfile = activeAssistProfile,
                    currentVisualProfile = profile,
                    latestLegacyAssessment = visualAssessments.maxByOrNull { it.createdAtMillis },
                    latestStandardizedAssessment = standardizedAssessments.maxByOrNull { it.createdAtMillis },
                    onApplyAndCalibrate = { prescription, recommendation ->
                        if (!saving) {
                            saving = true
                            operationError = null
                            mainViewModel.saveConfirmedPrescription(prescription) { result ->
                                saving = false
                                if (result.isSuccess) {
                                    navController.navigate(AppRoute.Equalizer.route) {
                                        popUpTo(AppRoute.Equalizer.route) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                } else {
                                    operationError = "Enregistrement impossible. Vos valeurs restent à l’écran ; réessayez."
                                }
                            }
                        }
                    },
                    onDelete = {
                        if (!saving) {
                            saving = true
                            operationError = null
                            mainViewModel.deleteOpticalPrescription { result ->
                                saving = false
                                if (result.isSuccess) navController.popBackStack()
                                else operationError = "Suppression impossible. Réessayez avant de quitter cet écran."
                            }
                        }
                    },
                    onBack = { if (!saving) navController.popBackStack() },
                    saving = saving,
                    operationError = operationError,
                    initialEntry = prescriptionEntry
                )
            }

            composable(route = AppRoute.CoreStatus.route) {
                CoreStatusScreen(
                    onBack = { navController.popBackStack() },
                    onContinue = {
                        navController.navigate(AppRoute.Home.route) {
                            popUpTo(AppRoute.CoreStatus.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(route = AppRoute.Welcome.route) {
                var busy by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }
                ProductWelcomeScreen(busy = busy, error = error,
                    onChoose = { entry ->
                        if (!busy) {
                            busy = true; error = null
                            mainViewModel.completeInitialSetup { result ->
                                busy = false
                                if (result.isSuccess) {
                                    prescriptionEntry = when (entry) {
                                        VisionEntry.KNOWN -> PrescriptionEntry.MANUAL
                                        VisionEntry.DOCUMENT -> PrescriptionEntry.DOCUMENT
                                        else -> PrescriptionEntry.CHOOSE
                                    }
                                    navController.navigate(AppRoute.Home.route) {
                                        popUpTo(navController.graph.id) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                    navController.navigate(when (entry) {
                                        VisionEntry.TRY -> AppRoute.Equalizer.route
                                        VisionEntry.UNKNOWN -> AppRoute.Calibration.route
                                        else -> AppRoute.OpticalPrescription.route
                                    })
                                } else error = "La préparation du profil a échoué. Vous pouvez réessayer."
                            }
                        }
                    },
                    onPrivacy = { navController.navigate(AppRoute.Privacy.route) },
                    onBack = if (onboardingCompleted == true) ({ navController.popBackStack(); Unit }) else null)
            }

            composable(route = AppRoute.Setup.route) {
                GuidedSetupScreen(
                    profiles = assistProfiles,
                    activeProfile = activeAssistProfile,
                    onActivateProfile = mainViewModel::activateAssistProfile,
                    onComplete = {
                        mainViewModel.setOnboardingCompleted(true)
                        navController.navigate(AppRoute.Home.route) { popUpTo(AppRoute.Welcome.route) { inclusive = true } }
                    },
                    onTemporaryExit = { navController.navigate(AppRoute.Home.route) },
                    onBackFromFirst = { navController.popBackStack() }
                )
            }

            composable(route = AppRoute.Help.route) { HelpScreen(onBack = { navController.popBackStack() },
                onPermissions = { navController.navigate(AppRoute.Permissions.route) }) }
            composable(route = AppRoute.Privacy.route) { PrivacyScreen { navController.popBackStack() } }
            composable(route = AppRoute.About.route) {
                AboutScreen(
                    onBack = { navController.popBackStack() },
                    onHelp = { navController.navigate(AppRoute.Help.route) },
                    onPrivacy = { navController.navigate(AppRoute.Privacy.route) },
                    onResetWelcome = {
                        mainViewModel.setOnboardingCompleted(false)
                        navController.navigate(AppRoute.Welcome.route)
                    }
                )
            }
        }
    }
}
