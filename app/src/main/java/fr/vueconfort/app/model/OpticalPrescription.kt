package fr.vueconfort.app.model

import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.util.Base64

enum class PrescriptionEye { OD, OG }

enum class PrescriptionSource { MANUAL, PHOTO, IMAGE, PDF }
enum class CorrectionType { MYOPIA, HYPERMETROPIA, ASTIGMATISM, PRESBYOPIA, MULTIFOCAL_PROGRESSIVE, OTHER, NOT_SPECIFIED }

data class EyePrescription(
    val sphere: Float? = null,
    val cylinder: Float? = null,
    val axisDegrees: Int? = null,
    val addition: Float? = null,
    val visualAcuity: String? = null
) {
    val hasValues: Boolean
        get() = sphere != null || cylinder != null || axisDegrees != null || addition != null || !visualAcuity.isNullOrBlank()

    fun validationErrors(eye: PrescriptionEye): List<String> = buildList {
        sphere?.takeUnless { it in -30f..30f }?.let {
            add("${eye.name} : la sphère doit être comprise entre −30 et +30.")
        }
        cylinder?.takeUnless { it in -12f..12f }?.let {
            add("${eye.name} : le cylindre doit être compris entre −12 et +12.")
        }
        axisDegrees?.takeUnless { it in 0..180 }?.let {
            add("${eye.name} : l’axe doit être compris entre 0° et 180°.")
        }
        addition?.takeUnless { it in 0f..6f }?.let {
            add("${eye.name} : l’addition doit être comprise entre 0 et +6.")
        }
    }
}

data class OpticalPrescription(
    val rightEye: EyePrescription = EyePrescription(),
    val leftEye: EyePrescription = EyePrescription(),
    val pupillaryDistanceMm: Float? = null,
    val recommendedReadingDistanceCm: Int? = null,
    val prescriptionDate: String? = null,
    val notes: String = "",
    val correctionType: CorrectionType = CorrectionType.NOT_SPECIFIED,
    val documentUri: String? = null,
    val documentName: String? = null,
    val source: PrescriptionSource = PrescriptionSource.MANUAL,
    val confirmedByUser: Boolean = true,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val hasOpticalValues: Boolean
        get() = rightEye.hasValues || leftEye.hasValues

    fun validationErrors(): List<String> = buildList {
        addAll(rightEye.validationErrors(PrescriptionEye.OD))
        addAll(leftEye.validationErrors(PrescriptionEye.OG))
        pupillaryDistanceMm?.takeUnless { it in 40f..85f }?.let {
            add("L’écart pupillaire doit être compris entre 40 et 85 mm.")
        }
        recommendedReadingDistanceCm?.takeUnless { it in 20..100 }?.let {
            add("La distance de lecture doit être comprise entre 20 et 100 cm.")
        }
        prescriptionDate?.takeIf { it.isNotBlank() }?.let { value ->
            if (runCatching { LocalDate.parse(value) }.isFailure) {
                add("La date doit utiliser le format AAAA-MM-JJ.")
            }
        }
        if (!confirmedByUser) {
            add("Les valeurs importées doivent être confirmées avant utilisation.")
        }
        if (!hasOpticalValues) {
            add("Renseignez au moins une valeur OD ou OG.")
        }
    }

    val isValid: Boolean
        get() = validationErrors().isEmpty()
}

/**
 * Future document imports must stop at this confirmation boundary. Detected values are
 * never persisted or used by the recommendation engine until [confirmedByUser] is true.
 */
data class PrescriptionImportCandidate(
    val prescription: OpticalPrescription,
    val sourceLabel: String,
    val fieldsRequiringReview: Set<String> = emptySet()
)

internal object OpticalPrescriptionCodec {
    private const val VERSION = "2"

    fun encode(value: OpticalPrescription): String = listOf(
        VERSION,
        value.rightEye.sphere.text(), value.rightEye.cylinder.text(),
        value.rightEye.axisDegrees.text(), value.rightEye.addition.text(), value.rightEye.visualAcuity.orEmpty().encoded(),
        value.leftEye.sphere.text(), value.leftEye.cylinder.text(),
        value.leftEye.axisDegrees.text(), value.leftEye.addition.text(), value.leftEye.visualAcuity.orEmpty().encoded(),
        value.pupillaryDistanceMm.text(), value.recommendedReadingDistanceCm.text(),
        value.prescriptionDate.orEmpty().encoded(), value.notes.encoded(),
        value.source.name, value.confirmedByUser.toString(), value.updatedAtMillis.toString(),
        value.correctionType.name, value.documentUri.orEmpty().encoded(), value.documentName.orEmpty().encoded()
    ).joinToString("|")

    fun decode(raw: String?): OpticalPrescription? {
        if (raw.isNullOrBlank()) return null
        val fields = raw.split('|')
        if (fields.firstOrNull() == "1") return decodeV1(fields)
        if (fields.size != 21 || fields[0] != VERSION) return null
        return runCatching {
            OpticalPrescription(
                rightEye = EyePrescription(
                    fields[1].toFloatOrNull(), fields[2].toFloatOrNull(),
                    fields[3].toIntOrNull(), fields[4].toFloatOrNull(), fields[5].decoded().ifBlank { null }
                ),
                leftEye = EyePrescription(
                    fields[6].toFloatOrNull(), fields[7].toFloatOrNull(),
                    fields[8].toIntOrNull(), fields[9].toFloatOrNull(), fields[10].decoded().ifBlank { null }
                ),
                pupillaryDistanceMm = fields[11].toFloatOrNull(),
                recommendedReadingDistanceCm = fields[12].toIntOrNull(),
                prescriptionDate = fields[13].decoded().ifBlank { null },
                notes = fields[14].decoded(),
                source = PrescriptionSource.valueOf(fields[15]),
                confirmedByUser = fields[16].toBooleanStrict(),
                updatedAtMillis = fields[17].toLong(),
                correctionType = CorrectionType.valueOf(fields[18]),
                documentUri = fields[19].decoded().ifBlank { null },
                documentName = fields[20].decoded().ifBlank { null }
            )
        }.getOrNull()
    }

    private fun decodeV1(fields: List<String>): OpticalPrescription? {
        if (fields.size != 16) return null
        return runCatching {
            OpticalPrescription(
                rightEye = EyePrescription(fields[1].toFloatOrNull(), fields[2].toFloatOrNull(), fields[3].toIntOrNull(), fields[4].toFloatOrNull()),
                leftEye = EyePrescription(fields[5].toFloatOrNull(), fields[6].toFloatOrNull(), fields[7].toIntOrNull(), fields[8].toFloatOrNull()),
                pupillaryDistanceMm = fields[9].toFloatOrNull(), recommendedReadingDistanceCm = fields[10].toIntOrNull(),
                prescriptionDate = fields[11].decoded().ifBlank { null }, notes = fields[12].decoded(),
                source = PrescriptionSource.valueOf(fields[13]), confirmedByUser = fields[14].toBooleanStrict(),
                updatedAtMillis = fields[15].toLong()
            )
        }.getOrNull()
    }

    private fun Any?.text() = this?.toString().orEmpty()
    private fun String.encoded(): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(toByteArray(StandardCharsets.UTF_8))
    private fun String.decoded(): String = ifBlank { "" }.let {
        if (it.isBlank()) "" else String(
            Base64.getUrlDecoder().decode(it),
            StandardCharsets.UTF_8
        )
    }
}
