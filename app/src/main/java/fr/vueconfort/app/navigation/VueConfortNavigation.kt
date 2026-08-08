package fr.vueconfort.app.navigation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import fr.vueconfort.app.calibration.CalibrationViewModel
import fr.vueconfort.app.model.AssistProfile
import fr.vueconfort.app.recommendation.RecommendationEngine
import fr.vueconfort.app.ui.screens.CalibrationScreen
import fr.vueconfort.app.ui.screens.HomeScreen
import fr.vueconfort.app.ui.screens.ProfileScreen
import fr.vueconfort.app.ui.screens.QuestionnaireScreen
import fr.vueconfort.app.ui.screens.QuickReadingSetupScreen
import fr.vueconfort.app.ui.screens.ReadingScreen
import fr.vueconfort.app.ui.screens.SettingsScreen
import fr.vueconfort.app.ui.screens.CoreStatusScreen
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

    if (onboardingCompleted == null) {
        MaterialTheme { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        return
    }

    MaterialTheme {
        NavHost(
            navController = navController,
            startDestination = if (onboardingCompleted == true) AppRoute.Home.route else AppRoute.Welcome.route,
            modifier = modifier
        ) {
            composable(
                route = AppRoute.Home.route
            ) {
                HomeScreen(
                    profile = profile,
                    onQuestionnaire = {
                        navController.navigate(
                            AppRoute.QuickReadingSetup.route
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
                        navController.navigate(AppRoute.Welcome.route)
                    }
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

            composable(
                route = AppRoute.Calibration.route
            ) {
                val calibrationViewModel:
                    CalibrationViewModel = viewModel()

                CalibrationScreen(
                    baseProfile = profile,
                    viewModel = calibrationViewModel,
                    onCalibrationCompleted = {
                        calibratedProfile ->

                        mainViewModel.saveProfile(
                            calibratedProfile
                        )

                        calibrationViewModel.reset()

                        navController.popBackStack(
                            route = AppRoute.Home.route,
                            inclusive = false
                        )
                    },
                    onBack = {
                        calibrationViewModel.reset()
                        navController.popBackStack()
                    }
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
                route = AppRoute.Profile.route
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

            composable(
                route = AppRoute.Settings.route
            ) {
                SettingsScreen(
                    profiles = assistProfiles,
                    rules = automationRules,
                    status = automationStatus,
                    onSaveRule = mainViewModel::saveAutomationRule,
                    onDeleteRule = mainViewModel::deleteAutomationRule,
                    onPauseAutomation = mainViewModel::pauseAutomation,
                    onHelp = { navController.navigate(AppRoute.Help.route) },
                    onAbout = { navController.navigate(AppRoute.About.route) },
                    onPrivacy = { navController.navigate(AppRoute.Privacy.route) },
                    onRedoSetup = {
                        mainViewModel.setOnboardingCompleted(false)
                        navController.navigate(AppRoute.Welcome.route)
                    },
                    onResetProfiles = mainViewModel::resetAssistProfiles,
                    onClearHistory = mainViewModel::clearAssessmentHistory,
                    onClearRules = mainViewModel::clearAutomationRules,
                    onResetAll = {
                        mainViewModel.resetAll()
                        navController.navigate(AppRoute.Welcome.route) {
                            popUpTo(AppRoute.Home.route) { inclusive = true }
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
                FirstLaunchScreen(
                    onReady = {
                        mainViewModel.setOnboardingCompleted(true)

                        navController.navigate(AppRoute.Home.route) {
                            popUpTo(AppRoute.Welcome.route) {
                                inclusive = true
                            }
                        }
                    },
                    onAdvancedSetup = {
                        navController.navigate(AppRoute.Setup.route)
                    },
                    onPrivacy = {
                        navController.navigate(AppRoute.Privacy.route)
                    }
                )
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

            composable(route = AppRoute.Help.route) { HelpScreen { navController.popBackStack() } }
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
