import java.util.Properties
import com.android.build.api.artifact.SingleArtifact
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseVersionCode = providers.gradleProperty("VERSION_CODE").get().toInt()
val releaseVersionName = providers.gradleProperty("VERSION_NAME").get()
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}
val releaseSigningConfigured = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .all { !keystoreProperties.getProperty(it).isNullOrBlank() }

android {
    namespace = "fr.vueconfort.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.vueconfort.app"
        minSdk = 28
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "NATIVE_VISION_LAB", "false")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isShrinkResources = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        create("lab") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".lab"
            versionNameSuffix = "-lab"
            buildConfigField("boolean", "NATIVE_VISION_LAB", "true")
            matchingFallbacks += "debug"
        }
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            matchingFallbacks += "debug"
        }
    }

    // Exact numerical reference sources, without their original research launchers or transports.
    sourceSets.getByName("main").java.srcDir("src/opticalReference/java")
    // Exercises the commercial main sources in an isolated package, without the research launcher.
    sourceSets.getByName("preview").java.srcDir("src/release/java")
    sourceSets.getByName("lab").java.srcDir("src/release/java")
    listOf("release", "preview", "debug").forEach {
        sourceSets.getByName(it).java.srcDir("src/nativeCommercial/java")
    }
    if (providers.gradleProperty("nativeLabTests").isPresent) {
        testBuildType = "lab"
        sourceSets.getByName("androidTest").java.setSrcDirs(listOf("src/androidTestLab/java"))
    }
    if (providers.gradleProperty("commercialPreviewTests").isPresent) {
        testBuildType = "preview"
        sourceSets.getByName("androidTest").java.setSrcDirs(listOf("src/androidTestPreview/java"))
    }

}

// Verify the merged artifact, including manifests contributed by dependencies.
// A release build must fail if a testing entry point or a privileged permission leaks in.
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val verifyManifest = tasks.register("verifyCommercialReleaseManifest") {
            group = "verification"
            description = "Checks the commercial package, permissions and exposed components."
            inputs.file(mergedManifest)
            doLast {
                val factory = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                }
                val document = factory.newDocumentBuilder().parse(mergedManifest.get().asFile)
                val androidNamespace = "http://schemas.android.com/apk/res/android"
                fun org.w3c.dom.Element.android(name: String) = getAttributeNS(androidNamespace, name)
                fun elements(tag: String): List<org.w3c.dom.Element> {
                    val nodes = document.getElementsByTagName(tag)
                    return (0 until nodes.length).map { nodes.item(it) as org.w3c.dom.Element }
                }
                check(document.documentElement.getAttribute("package") == "fr.vueconfort.app")
                val allowedPermissions = setOf("android.permission.POST_NOTIFICATIONS",
                    "fr.vueconfort.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
                val requested = (elements("uses-permission") + elements("uses-permission-sdk-23"))
                    .map { it.android("name") }.toSet()
                check(requested.all { it in allowedPermissions }) {
                    "Unexpected commercial permissions: ${requested - allowedPermissions}"
                }
                val app = elements("application").single()
                check(app.android("debuggable") != "true" && app.android("testOnly") != "true")
                check(app.android("allowBackup") == "false")
                val ownComponents = mapOf(
                    "fr.vueconfort.app.MainActivity" to ("activity" to ""),
                    "fr.vueconfort.app.magnifier.ScreenMagnifierService" to
                        ("service" to "android.permission.BIND_ACCESSIBILITY_SERVICE"),
                    "fr.vueconfort.app.core.VueConfortTileService" to
                        ("service" to "android.permission.BIND_QUICK_SETTINGS_TILE"),
                    "fr.vueconfort.app.core.CoreActionReceiver" to ("receiver" to "")
                )
                listOf("activity", "activity-alias", "service", "receiver", "provider").forEach { tag ->
                    elements(tag).filter { it.android("name").startsWith("fr.vueconfort.") }.forEach { component ->
                        val name = component.android("name")
                        check(ownComponents[name]?.first == tag) { "Unexpected commercial component: $name" }
                        check(component.android("permission") == ownComponents.getValue(name).second)
                        if (tag == "receiver") check(component.android("exported") == "false")
                    }
                }
                logger.lifecycle("Commercial Release manifest verified: no test entry point, network or privileged permission.")
            }
        }
        tasks.matching { it.name in setOf("assembleRelease", "bundleRelease", "lintRelease") }
            .configureEach { dependsOn(verifyManifest) }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("com.google.mlkit:text-recognition:16.0.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
