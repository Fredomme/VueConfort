plugins { base }

repositories { mavenCentral() }

val compiler by configurations.creating
val harnessRuntime by configurations.creating
dependencies {
    compiler("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.20")
    harnessRuntime("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
}

val engineDirectory = layout.projectDirectory.dir("../app/src/main/java/fr/vueconfort/vision")
val sourceFiles = files(
    engineDirectory.file("OpticalExperiment.kt"),
    engineDirectory.file("RectangularFft.kt"),
    layout.projectDirectory.file("AuditHarness.kt"),
)
val harnessClasses = layout.buildDirectory.dir("classes/harness")
val compileHarness by tasks.registering(JavaExec::class) {
    description = "Compile the unmodified app engine and independent export harness without Android."
    classpath = compiler
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    inputs.files(sourceFiles)
    inputs.files(harnessRuntime)
    outputs.dir(harnessClasses)
    doFirst {
        harnessClasses.get().asFile.mkdirs()
        args = listOf("-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath",
            harnessRuntime.asPath, "-d", harnessClasses.get().asFile.absolutePath) +
            sourceFiles.files.map { it.absolutePath }
    }
}

tasks.register<JavaExec>("runHarness") {
    description = "Export transfer grids, FFT examples, and eight model cases for the Python reference."
    dependsOn(compileHarness)
    classpath = files(harnessClasses) + harnessRuntime
    mainClass.set("AuditHarnessKt")
    maxHeapSize = "1024m"
    doFirst {
        val freshVectors = layout.buildDirectory.file("otf-vectors.tsv").get().asFile
        val vectors = if (freshVectors.isFile) freshVectors else file("otf-vectors.tsv")
        args(layout.buildDirectory.dir("kotlin").get().asFile.absolutePath, vectors.absolutePath)
    }
}
