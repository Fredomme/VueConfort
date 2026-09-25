package fr.vueconfort.app.navigation

sealed class AppRoute(
    val route: String
) {
    data object Home :
        AppRoute("home")

    data object NativeVision : AppRoute("native_vision")

    data object Equalizer : AppRoute("equalizer")

    data object QuickReadingSetup :
        AppRoute("quick_reading_setup")

    data object Questionnaire :
        AppRoute("questionnaire")

    data object Calibration :
        AppRoute("calibration")

    data object VisualAssessment :
        AppRoute("visual_assessment")

    data object AssessmentHistory :
        AppRoute("assessment_history")

    data object OpticalSettings :
        AppRoute("optical_settings")

    data object OpticalPrescription :
        AppRoute("optical_prescription")

    data object Reading :
        AppRoute("reading")

    data object Profile :
        AppRoute("profile")

    data object Settings :
        AppRoute("settings")

    data object CoreStatus :
        AppRoute("core_status")

    data object Welcome : AppRoute("welcome")
    data object Setup : AppRoute("setup")
    data object Help : AppRoute("help")
    data object About : AppRoute("about")
    data object Privacy : AppRoute("privacy")
}
