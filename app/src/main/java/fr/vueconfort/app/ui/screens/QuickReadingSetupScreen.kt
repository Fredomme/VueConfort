package fr.vueconfort.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.vueconfort.app.model.VisualProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickReadingSetupScreen(
    currentProfile: VisualProfile,
    onBack: () -> Unit,
    onCompleted: (VisualProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember {
        mutableIntStateOf(0)
    }

    var blurryVision by remember {
        mutableStateOf(false)
    }

    var fontSize by remember {
        mutableFloatStateOf(
            currentProfile.fontSizeSp
                .coerceAtLeast(32f)
        )
    }

    var fontWeight by remember {
        mutableIntStateOf(
            currentProfile.fontWeight
                .coerceAtLeast(500)
        )
    }

    var letterSpacing by remember {
        mutableFloatStateOf(
            currentProfile.letterSpacingSp
                .coerceAtLeast(0.25f)
        )
    }

    var warmBackground by remember {
        mutableStateOf(
            currentProfile.backgroundArgb == 0xFFFFF8E7
        )
    }

    val backgroundColor =
        if (warmBackground) {
            Color(0xFFFFF8E7)
        } else {
            Color.White
        }

    val foregroundColor =
        Color(0xFF111111)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Réglage rapide",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    OutlinedButton(
                        onClick = {
                            if (step > 0) {
                                step--
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = if (step > 0) {
                                "Précédent"
                            } else {
                                "Retour"
                            },
                            fontSize = 18.sp
                        )
                    }
                }
            )
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(innerPadding)
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            when (step) {
                0 -> {
                    ReadingSample(
                        text = "Pouvez-vous lire ce texte facilement ?",
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        letterSpacing = letterSpacing,
                        foregroundColor = foregroundColor
                    )

                    LargeActionButton(
                        text = "Oui, je peux lire",
                        fontSize = fontSize,
                        onClick = {
                            step = 1
                        }
                    )

                    LargeSecondaryButton(
                        text = "Je vois flou",
                        fontSize = fontSize,
                        onClick = {
                            blurryVision = true
                            fontSize = 52f
                            fontWeight = 850
                            letterSpacing = 1.6f
                            warmBackground = false
                            step = 1
                        }
                    )

                    LargeSecondaryButton(
                        text = "Plus grand",
                        fontSize = fontSize,
                        onClick = {
                            fontSize =
                                (fontSize + 4f)
                                    .coerceAtMost(64f)
                        }
                    )
                }

                1 -> {
                    ReadingSample(
                        text = "Comparez les tailles. Choisissez celle qui reste la plus facile à reconnaître.",
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        letterSpacing = letterSpacing,
                        foregroundColor = foregroundColor
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                fontSize =
                                    (fontSize - 2f)
                                        .coerceAtLeast(26f)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(84.dp)
                        ) {
                            Text(
                                text = "−",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                fontSize =
                                    (fontSize + 2f)
                                        .coerceAtMost(64f)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(84.dp)
                        ) {
                            Text(
                                text = "+",
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = "${fontSize.toInt()} sp",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = foregroundColor
                    )

                    LargeActionButton(
                        text = "Cette taille me convient",
                        fontSize = fontSize,
                        onClick = {
                            step = 2
                        }
                    )
                }

                2 -> {
                    ReadingSample(
                        text = "Quelle épaisseur est la plus facile à lire ?",
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        letterSpacing = letterSpacing,
                        foregroundColor = foregroundColor
                    )

                    LargeSecondaryButton(
                        text = "Fin — Aa 123",
                        fontSize = fontSize,
                        fontWeight = 400,
                        onClick = {
                            fontWeight = 400
                            step = 3
                        }
                    )

                    LargeSecondaryButton(
                        text = "Moyen — Aa 123",
                        fontSize = fontSize,
                        fontWeight = 550,
                        onClick = {
                            fontWeight = 550
                            step = 3
                        }
                    )

                    LargeSecondaryButton(
                        text = "Fort — Aa 123",
                        fontSize = fontSize,
                        fontWeight = 750,
                        onClick = {
                            fontWeight = 750
                            step = 3
                        }
                    )

                    LargeSecondaryButton(
                        text = "Très fort — Aa 123",
                        fontSize = fontSize,
                        fontWeight = 900,
                        onClick = {
                            fontWeight = 900
                            blurryVision = true
                            step = 3
                        }
                    )
                }

                3 -> {
                    ReadingSample(
                        text = "Quel espacement rend les lettres plus distinctes ?",
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        letterSpacing = letterSpacing,
                        foregroundColor = foregroundColor
                    )

                    SpacingChoice(
                        title = "Très serré",
                        sample = "Il1 O0 rnm B8",
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        letterSpacing = 0f,
                        onClick = {
                            letterSpacing = 0f
                            step = 4
                        }
                    )

                    SpacingChoice(
                        title = "Normal",
                        sample = "I l 1   O 0   rn m   B 8",
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        letterSpacing = 0.5f,
                        onClick = {
                            letterSpacing = 0.5f
                            step = 4
                        }
                    )

                    SpacingChoice(
                        title = "Large",
                        sample = "I  l  1    O  0    rn  m    B  8",
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        letterSpacing = 1.5f,
                        onClick = {
                            letterSpacing = 1.5f
                            step = 4
                        }
                    )

                    SpacingChoice(
                        title = "Très large — vision floue",
                        sample = "I   l   1     O   0     rn   m     B   8",
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        letterSpacing = 2.8f,
                        onClick = {
                            letterSpacing = 2.8f
                            blurryVision = true
                            step = 4
                        }
                    )
                }

                4 -> {
                    ReadingSample(
                        text = "Quel contraste rend les lettres les plus faciles à distinguer ?",
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        letterSpacing = letterSpacing,
                        foregroundColor = foregroundColor
                    )

                    LargeSecondaryButton(
                        text = "Fond blanc",
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        onClick = {
                            warmBackground = false
                            step = 5
                        }
                    )

                    LargeSecondaryButton(
                        text = "Fond chaud",
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        onClick = {
                            warmBackground = true
                            step = 5
                        }
                    )

                    LargeSecondaryButton(
                        text = "Contraste maximal noir sur blanc",
                        fontSize = fontSize,
                        fontWeight = 900,
                        onClick = {
                            warmBackground = false
                            fontWeight = fontWeight.coerceAtLeast(800)
                            blurryVision = true
                            step = 5
                        }
                    )
                }

                else -> {
                    ReadingSample(
                        text = "Le questionnaire utilisera maintenant ces réglages.",
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        letterSpacing = letterSpacing,
                        foregroundColor = foregroundColor
                    )

                    Text(
                        text = buildString {
                            append("Taille : ")
                            append(fontSize.toInt())
                            append(" sp\n")

                            append("Épaisseur : ")
                            append(fontWeight)
                            append("\n")

                            append("Espacement : ")
                            append(letterSpacing)
                            append(" sp\n")

                            append(
                                if (warmBackground) {
                                    "Fond : chaud"
                                } else {
                                    "Fond : blanc"
                                }
                            )

                            if (blurryVision) {
                                append("\nMode vision floue activé")
                            }
                        },
                        fontSize = 22.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.Medium,
                        color = foregroundColor,
                        textAlign = TextAlign.Center
                    )

                    LargeActionButton(
                        text = "Continuer vers le questionnaire",
                        fontSize = fontSize,
                        onClick = {
                            val now =
                                System.currentTimeMillis()

                            onCompleted(
                                currentProfile.copy(
                                    fontSizeSp = fontSize,
                                    fontWeight = fontWeight,
                                    letterSpacingSp = letterSpacing,
                                    lineHeightMultiplier =
                                        if (blurryVision) {
                                            1.70f
                                        } else {
                                            1.48f
                                        },
                                    foregroundArgb = 0xFF111111,
                                    backgroundArgb =
                                        if (warmBackground) {
                                            0xFFFFF8E7
                                        } else {
                                            0xFFFFFFFF
                                        },
                                    horizontalMarginDp =
                                        if (blurryVision) {
                                            14f
                                        } else {
                                            18f
                                        },
                                    localZoomEnabled = blurryVision,
                                    updatedAtMillis = now
                                )
                            )
                        }
                    )
                }
            }

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            Text(
                text = "Ce réglage améliore la lisibilité de l’écran. " +
                    "Il ne corrige pas la vue et ne remplace pas un examen.",
                fontSize = 17.sp,
                lineHeight = 23.sp,
                color = foregroundColor.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ReadingSample(
    text: String,
    fontSize: Float,
    fontWeight: Int,
    letterSpacing: Float,
    foregroundColor: Color
) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        fontSize = fontSize.sp,
        lineHeight = (fontSize * 1.35f).sp,
        fontWeight = FontWeight(fontWeight),
        letterSpacing = letterSpacing.sp,
        color = foregroundColor,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun LargeActionButton(
    text: String,
    fontSize: Float,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(
                if (fontSize >= 40f) {
                    132.dp
                } else {
                    104.dp
                }
            ),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFF143A5A),
            contentColor = Color.White
        )
    ) {
        Text(
            text = text,
            fontSize = (fontSize - 7f)
                .coerceAtLeast(22f)
                .sp,
            lineHeight = (fontSize * 1.05f).sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun LargeSecondaryButton(
    text: String,
    fontSize: Float,
    fontWeight: Int = 650,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(
                if (fontSize >= 40f) {
                    132.dp
                } else {
                    104.dp
                }
            ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(
            text = text,
            fontSize = (fontSize - 7f)
                .coerceAtLeast(22f)
                .sp,
            lineHeight = (fontSize * 1.05f).sp,
            fontWeight = FontWeight(fontWeight),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SpacingChoice(
    title: String,
    sample: String,
    fontSize: Float,
    fontWeight: Int,
    letterSpacing: Float,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = sample,
                fontSize = (fontSize - 6f)
                    .coerceAtLeast(24f)
                    .sp,
                fontWeight = FontWeight(fontWeight),
                letterSpacing = letterSpacing.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
