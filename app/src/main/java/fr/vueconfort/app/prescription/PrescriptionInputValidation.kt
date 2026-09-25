package fr.vueconfort.app.prescription

import fr.vueconfort.app.model.EyePrescription

/** Raw input stays intact until this boundary; never remove characters to make a number valid. */
data class PrescriptionEyeInput(
    val sphere: String = "",
    val cylinder: String = "",
    val axis: String = "",
    val addition: String = "",
    val visualAcuity: String = ""
)

data class PrescriptionInputResult(
    val rightEye: EyePrescription,
    val leftEye: EyePrescription,
    val pupillaryDistanceMm: Float?,
    val recommendedReadingDistanceCm: Int?,
    val fieldErrors: Map<String, String>
) {
    val errors: List<String> get() = fieldErrors.values.toList()
    val isValid: Boolean get() = fieldErrors.isEmpty()
}

object PrescriptionInputValidation {
    private val decimal = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)")
    private val integer = Regex("[+]?[0-9]+")

    /** Normalises typography, not content: malformed decimals, units and exponents remain invalid. */
    fun normalizeNumber(value: String): String = value.trim()
        .replace('\u2212', '-').replace('\uFE63', '-').replace('\uFF0D', '-')
        .replace('\uFF0B', '+').replace(',', '.')
        .replace(Regex("^([+-])[\\s\\u00A0\\u202F]+(?=[0-9.])"), "$1")

    fun validate(
        right: PrescriptionEyeInput,
        left: PrescriptionEyeInput,
        pupillaryDistance: String = "",
        readingDistance: String = ""
    ): PrescriptionInputResult {
        val errors = linkedMapOf<String, String>()

        fun number(raw: String, key: String, label: String, range: ClosedFloatingPointRange<Float>): Float? {
            if (raw.isBlank()) return null
            val normalized = normalizeNumber(raw)
            val value = if (decimal.matches(normalized)) normalized.toFloatOrNull() else null
            if (value == null || !value.isFinite()) {
                errors[key] = "$label : saisissez un nombre valide en conservant son signe (+ ou −)."
                return null
            }
            if (value !in range) {
                errors[key] = "$label : la valeur doit être comprise entre ${range.start} et ${range.endInclusive}."
            }
            return value
        }

        fun wholeNumber(raw: String, key: String, label: String, range: IntRange): Int? {
            if (raw.isBlank()) return null
            val normalized = normalizeNumber(raw)
            val value = if (integer.matches(normalized)) normalized.toIntOrNull() else null
            if (value == null || value !in range) {
                errors[key] = "$label : saisissez un entier entre ${range.first} et ${range.last}."
            }
            return value
        }

        fun eye(input: PrescriptionEyeInput, label: String): EyePrescription {
            val sphere = number(input.sphere, "$label.sphere", "$label — sphère", -30f..30f)
            val cylinder = number(input.cylinder, "$label.cylinder", "$label — cylindre", -12f..12f)
            val axis = wholeNumber(input.axis, "$label.axis", "$label — axe", 0..180)
            val addition = number(input.addition, "$label.addition", "$label — addition", 0f..6f)
            if (cylinder != null && cylinder != 0f && axis == null && "$label.axis" !in errors) {
                errors["$label.axis"] = "$label : l’axe est nécessaire lorsque le cylindre est différent de zéro."
            }
            if (axis != null && cylinder == null && "$label.cylinder" !in errors) {
                errors["$label.cylinder"] = "$label : un axe seul ne décrit pas une correction ; vérifiez le cylindre."
            }
            if (input.visualAcuity.length > 40) errors["$label.visualAcuity"] = "$label : l’acuité indiquée est trop longue."
            return EyePrescription(sphere, cylinder, axis, addition, input.visualAcuity.trim().ifBlank { null })
        }

        val rightEye = eye(right, "OD")
        val leftEye = eye(left, "OG")
        val pupil = number(pupillaryDistance, "pupillaryDistance", "Écart pupillaire (mm)", 40f..85f)
        val distance = wholeNumber(readingDistance, "readingDistance", "Distance de lecture (cm)", 20..100)
        if (listOf(rightEye, leftEye).none { it.sphere != null || it.cylinder != null || it.addition != null }) {
            errors["eyes"] = "Renseignez au moins une sphère, un cylindre ou une addition OD ou OG ; une acuité seule ne définit pas une correction."
        }
        return PrescriptionInputResult(rightEye, leftEye, pupil, distance, errors)
    }
}
