import fr.vueconfort.vision.OpticalExperiment
import fr.vueconfort.vision.OpticalHypothesis
import fr.vueconfort.vision.RectangularFft
import java.io.DataOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt

private fun json(value: Any?): String = when (value) {
    null -> "null"
    is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
    is Number -> if (value.toDouble().isFinite()) value.toString() else "null"
    is Boolean -> value.toString()
    is Map<*, *> -> value.entries.joinToString(",", "{", "}") { json(it.key.toString()) + ":" + json(it.value) }
    is Iterable<*> -> value.joinToString(",", "[", "]") { json(it) }
    else -> error("Unsupported JSON value")
}

private fun doubles(file: File, values: DoubleArray) {
    DataOutputStream(file.outputStream().buffered()).use { stream -> values.forEach { stream.writeDouble(it) } }
}

private fun ints(file: File, values: IntArray) {
    DataOutputStream(file.outputStream().buffered()).use { stream -> values.forEach { stream.writeInt(it) } }
}

private fun gray(linear: Double): Int {
    val encoded = if (linear <= .0031308) 12.92 * linear else 1.055 * linear.pow(1 / 2.4) - .055
    val code = (encoded * 255).roundToInt().coerceIn(0, 255)
    return (255 shl 24) or (code shl 16) or (code shl 8) or code
}

private fun stimulus(name: String): IntArray = IntArray(768 * 256) { i ->
    val x = i % 768
    val y = i / 768
    val value = when (name) {
        "bars" -> if (x in 32..735 && y in 32..223 && x % 16 < 8)
            ((124.0 / 255.0 + .055) / 1.055).pow(2.4) else .5
        "moderate" -> if (x in 32..735 && y in 32..223) .5 + .2 * cos(2 * PI * x / 32) else .5
        "text-like" -> if (x in 24..743 && y in 32..223 &&
            ((x % 29 in 2..4) || (y % 31 in 2..4 && x % 29 < 20))) .15 else .75
        "black-white" -> if (x / 9 % 2 == 0 && y in 20..235) 0.0 else 1.0
        "border-stress" -> if (x < 16 || y < 16) .05 else if (x > 751 || y > 239) .95 else .5
        "constant" -> .5
        else -> error("Unknown stimulus")
    }
    gray(value)
}

fun main(args: Array<String>) {
    val root = File(args[0]).apply { mkdirs() }
    val vectorFile = File(args[1])
    val vectors = vectorFile.readLines().drop(1).filter { it.isNotBlank() }.map { it.split('\t') }
    val checks = mutableListOf<Map<String, Any>>()
    for ((profile, rows) in vectors.groupBy { it[0].toDouble() to it[1].toDouble() }) {
        val (distance, sphere) = profile
        val started = System.nanoTime()
        val grid = OpticalExperiment.buildTransferForValidation(distance, OpticalHypothesis(sphere, 0.0, 0.0))
        val maximumError = rows.maxOf { row ->
            val actual = grid.real[row[3].toInt() * grid.width + row[2].toInt()]
            kotlin.math.abs(actual - row[4].toDouble())
        }
        val label = "s${(sphere * 1000).roundToInt()}-d${distance.roundToInt()}"
        checks += mapOf("profile" to label, "maxErrorVsDirect2DPupil" to maximumError,
            "converged" to grid.converged, "maxIntegralError" to grid.maxIntegralError,
            "maxPanels" to grid.maxPanels, "radialSamples" to grid.computedRadialSamples,
            "buildMs" to (System.nanoTime() - started) / 1e6,
            "negativeTransferSamples" to grid.real.count { it < 0 }, "dc" to grid.real[0])
        if (distance == 400.0) doubles(File(root, "transfer-$label.f64be"), grid.real)
        println("OTF $label maxAbsoluteError=$maximumError converged=${grid.converged}")
        check(maximumError <= 2e-5 && grid.converged) { "Independent optical transfer reference failed: $label" }
    }
    File(root, "point-checks.json").writeText(json(checks) + "\n")

    val re = DoubleArray(32) { i -> kotlin.math.sin(i * .71) + (if (i == 3) 2.0 else 0.0) }
    val im = DoubleArray(32) { i -> kotlin.math.cos(i * .39) / 3 }
    doubles(File(root, "fft-input-real.f64be"), re)
    doubles(File(root, "fft-input-imag.f64be"), im)
    RectangularFft.transform(re, im, 8, 4)
    doubles(File(root, "fft-output-real.f64be"), re)
    doubles(File(root, "fft-output-imag.f64be"), im)
    RectangularFft.transform(re, im, 8, 4, true)
    doubles(File(root, "fft-roundtrip-real.f64be"), re)
    doubles(File(root, "fft-roundtrip-imag.f64be"), im)

    for (sphere in listOf(.15, .25, .4)) {
        val names = if (sphere == .25) listOf("moderate", "bars", "text-like", "black-white", "border-stress", "constant")
            else listOf("moderate")
        for (name in names) {
            val distance = 400.0
            val input = stimulus(name)
            val result = OpticalExperiment.process(input, distance, OpticalHypothesis(sphere, 0.0, 0.0))
            val folder = File(root, "$name-s${(sphere * 1000).roundToInt()}-d400").apply { mkdirs() }
            ints(File(folder, "input.argb.i32be"), input)
            ints(File(folder, "baseline.argb.i32be"), result.baseline)
            ints(File(folder, "candidate.argb.i32be"), result.candidate)
            File(folder, "metadata.json").writeText(json(mapOf(
                "sphereD" to sphere, "distanceMm" to distance, "pupilMm" to 3.0,
                "width" to 2048, "height" to 1024, "stimulus" to name,
                "applied" to result.applied, "explanation" to result.explanation,
                "metrics" to result.metrics, "report" to result.report
            )) + "\n")
            println("PROCESS $name S=$sphere applied=${result.applied} reason=${result.report["reason"]} totalMs=${result.metrics["totalMs"]}")
        }
    }
}
