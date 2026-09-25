package fr.vueconfort.app.prescription

import fr.vueconfort.app.model.EyePrescription
import fr.vueconfort.app.model.OpticalPrescription
import fr.vueconfort.app.model.PrescriptionSource
import java.text.Normalizer
import java.util.Locale

/** A transcription proposal only. Neither OCR nor this parser establishes an optical correction. */
data class PrescriptionDocumentDraft(
    val rightEye: EyePrescription = EyePrescription(),
    val leftEye: EyePrescription = EyePrescription(),
    val warnings: List<String> = emptyList(),
    val evidence: Map<String, String> = emptyMap(),
    val canPrefill: Boolean = false,
    val nearFarContext: String? = null
) {
    fun toUnconfirmedPrescription(source: PrescriptionSource, documentName: String? = null): OpticalPrescription {
        require(canPrefill) { "Ce document nécessite une saisie manuelle." }
        return OpticalPrescription(
            rightEye = rightEye,
            leftEye = leftEye,
            source = source,
            documentName = documentName,
            confirmedByUser = false
        )
    }
}

/**
 * Deliberately limited to labelled eye/field pairs or an explicit, ordered table header.
 * Unknown layouts, duplicate fields and multiple near/far prescriptions fail closed.
 * The result never converts a glasses prescription into a residual error at the screen.
 */
object PrescriptionDocumentParser {
    private enum class Field(val key: String) { SPHERE("sphere"), CYLINDER("cylinder"), AXIS("axis"), ADDITION("addition") }
    private val eyes = Regex("\\b(?:OEIL\\s+DROIT|OEIL\\s+GAUCHE|RIGHT\\s+EYE|LEFT\\s+EYE|OD|OG)\\b")
    private val fields = Regex("\\b(?:SPHERE|SPH|CYLINDRE|CYL|AXIS|AXE|ADDITION|ADD)\\b")
    private val far = Regex("\\b(?:VL|VISION\\s+DE\\s+LOIN)\\b")
    private val near = Regex("\\b(?:VP|VISION\\s+DE\\s+PRES)\\b")
    private val otherReports = Regex("\\b(?:OCT|CHAMP\\s+VISUEL|TOPOGRAPHIE|RETINOGRAPHIE|LENTILLES?\\s+DE\\s+CONTACT)\\b")
    private val number = Regex("[+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)")

    fun parse(text: String): PrescriptionDocumentDraft {
        if (text.isBlank()) return PrescriptionDocumentDraft(warnings = listOf("Aucun texte lisible. Essayez une image plus nette ou la saisie manuelle."))
        if (text.length > 100_000 || text.count { it == '\n' } > 1_000) {
            return PrescriptionDocumentDraft(warnings = listOf("Le document contient trop de texte pour une transcription fiable. Sélectionnez une seule page de prescription."))
        }
        val normalizedText = normalized(text)
        val contexts = listOfNotNull(
            "Vision de loin".takeIf { far.containsMatchIn(normalizedText) },
            "Vision de près".takeIf { near.containsMatchIn(normalizedText) }
        )
        val context = contexts.singleOrNull()
        val warnings = linkedSetOf<String>()
        var blocked = false
        if (contexts.size > 1) {
            warnings += "Plusieurs contextes (loin et près) sont présents. Choisissez les valeurs à recopier ; aucune ligne n’est sélectionnée automatiquement."
            blocked = true
        }
        if (otherReports.containsMatchIn(normalizedText)) {
            warnings += "Ce document contient un autre type de bilan ou une prescription de lentilles. Il n’est pas interprété comme une correction de lunettes."
            blocked = true
        }
        val values = linkedMapOf<String, String>()
        val evidence = linkedMapOf<String, String>()
        var currentEye: String? = null
        var tableHeader: List<Field>? = null

        fun reject(message: String) {
            blocked = true
            warnings += message
        }

        fun assign(eye: String, field: Field, rawValue: String, source: String) {
            val key = "$eye.${field.key}"
            if (key in values) {
                evidence[key] = "${evidence[key]}\n$source"
                reject("Plusieurs valeurs ou lignes concernent $eye (${field.key}). Vérifiez le document et recopiez la bonne valeur.")
                return
            }
            values[key] = rawValue
            evidence[key] = source.take(300)
        }

        fun valueToken(raw: String, field: Field): String? {
            val trimmed = raw.trim().trimStart(':', '=').trim().trimEnd(';', '|').trim()
            val withoutUnit = when (field) {
                Field.AXIS -> trimmed.removeSuffix("°").trim()
                else -> trimmed.removeSuffix("D").trim()
            }
            val normalized = PrescriptionInputValidation.normalizeNumber(withoutUnit)
            return normalized.takeIf { number.matches(it) }
        }

        fun labelled(eye: String, segment: String, source: String): Boolean {
            val matches = fields.findAll(segment).toList()
            if (matches.isEmpty()) return false
            val prefix = segment.substring(0, matches.first().range.first).trim(' ', ':', '|', ';', '\t')
            if (prefix.isNotBlank()) reject("Des valeurs sans libellé précèdent les champs de $eye. La saisie manuelle est nécessaire.")
            matches.forEachIndexed { index, match ->
                val field = field(match.value)
                val end = matches.getOrNull(index + 1)?.range?.first ?: segment.length
                val token = valueToken(segment.substring(match.range.last + 1, end), field)
                if (token == null) {
                    evidence["$eye.${field.key}"] = source.take(300)
                    reject("Une valeur de $eye (${field.key}) est illisible ou ambiguë. Vérifiez son signe et ses décimales.")
                } else assign(eye, field, token, source)
            }
            return true
        }

        for (rawLine in text.lineSequence()) {
            val line = normalized(rawLine).trim()
            if (line.isBlank()) continue
            val fieldMatches = fields.findAll(line).toList()
            val headerRemainder = fields.replace(line, "").replace(Regex("\\b(?:OEIL|EYE)\\b"), "").trim(' ', ':', '|', ';', '\t')
            if (fieldMatches.size >= 2 && headerRemainder.isBlank()) {
                val header = fieldMatches.map { field(it.value) }
                if (header.distinct().size != header.size) reject("L’en-tête du tableau est ambigu ; recopiez les valeurs manuellement.")
                tableHeader = header
                currentEye = null
                continue
            }

            val eyeMatches = eyes.findAll(line).toList()
            if (eyeMatches.isNotEmpty()) {
                val prefix = line.substring(0, eyeMatches.first().range.first).trim(' ', ':', '|', ';', '\t')
                if (prefix.isNotBlank() && fields.containsMatchIn(prefix)) {
                    reject("L’ordre des yeux et des colonnes est ambigu. Aucune valeur n’est choisie automatiquement.")
                }
                eyeMatches.forEachIndexed { index, match ->
                    val eyeLabel = match.value.replace(Regex("\\s+"), " ")
                    val eye = if (eyeLabel in listOf("OD", "OEIL DROIT", "RIGHT EYE")) "OD" else "OG"
                    currentEye = eye
                    val end = eyeMatches.getOrNull(index + 1)?.range?.first ?: line.length
                    val segment = line.substring(match.range.last + 1, end).trim(' ', ':', '|', ';', '\t')
                    if (segment.isBlank()) return@forEachIndexed
                    if (labelled(eye, segment, rawLine)) return@forEachIndexed
                    val header = tableHeader
                    if (header != null && eyeMatches.size == 1) {
                        val tokens = segment.split(Regex("[\\s|;]+"))
                        if (tokens.size != header.size) {
                            reject("Le nombre de valeurs ne correspond pas aux colonnes pour $eye. Vérifiez la page.")
                        } else header.forEachIndexed { column, field ->
                            val token = valueToken(tokens[column], field)
                            if (token == null) reject("Une cellule du tableau de $eye est illisible ; aucune valeur n’est appliquée.")
                            else assign(eye, field, token, rawLine)
                        }
                    } else if (segment.any(Char::isDigit)) {
                        reject("Des valeurs de $eye n’ont pas de libellés SPH, CYL, AXE ou ADD suffisamment explicites.")
                    }
                }
                continue
            }
            if (fieldMatches.isNotEmpty()) {
                val eye = currentEye
                if (eye == null) reject("Certains champs ne sont pas associés clairement à OD ou OG.")
                else labelled(eye, line, rawLine)
            } else {
                // Do not carry an eye or table through another paragraph or unrelated report section.
                currentEye = null
                tableHeader = null
            }
        }

        fun input(eye: String) = PrescriptionEyeInput(
            sphere = values["$eye.sphere"].orEmpty(),
            cylinder = values["$eye.cylinder"].orEmpty(),
            axis = values["$eye.axis"].orEmpty(),
            addition = values["$eye.addition"].orEmpty()
        )
        val validation = PrescriptionInputValidation.validate(input("OD"), input("OG"))
        if (!validation.isValid) {
            warnings += validation.errors
            blocked = true
        }
        if (values.isEmpty()) warnings += "Aucune prescription OD/OG clairement structurée n’a été reconnue. Un compte rendu ou une acuité ne permet pas de déduire une correction."
        if (!blocked) warnings += "Relisez chaque valeur, son signe et l’œil concerné sur le document avant de confirmer."
        return PrescriptionDocumentDraft(
            rightEye = if (blocked) EyePrescription() else validation.rightEye,
            leftEye = if (blocked) EyePrescription() else validation.leftEye,
            warnings = warnings.toList(),
            evidence = evidence.toMap(),
            canPrefill = !blocked,
            nearFarContext = context
        )
    }

    private fun field(label: String): Field = when (label) {
        "SPH", "SPHERE" -> Field.SPHERE
        "CYL", "CYLINDRE" -> Field.CYLINDER
        "AXE", "AXIS" -> Field.AXIS
        else -> Field.ADDITION
    }

    private fun normalized(value: String): String = Normalizer.normalize(
        value.replace("œ", "oe").replace("Œ", "OE"), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "").uppercase(Locale.ROOT)
        .replace('\u00A0', ' ').replace('\u202F', ' ')
}
