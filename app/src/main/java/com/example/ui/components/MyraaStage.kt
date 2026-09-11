package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.personality.MyraaMood
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MyraaStage(
    modifier: Modifier = Modifier,
    mood: MyraaMood,
    inputAmplitude: Float,
    outputAmplitude: Float,
    isUserSpeaking: Boolean,
    isMyraaSpeaking: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "stageAnimation")

    // Slow breathing rotation and float
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.97f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathScale"
    )

    val auraRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "auraRotation"
    )

    // Periodic blinking (blinks every ~3.5 seconds)
    val blinkAnim by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinkAnim"
    )
    val isBlinking = blinkAnim < 0.15f

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer Atmospheric Aura Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.42f
            val audioBoost = if (isMyraaSpeaking) outputAmplitude * 60f else if (isUserSpeaking) inputAmplitude * 40f else 0f
            val currentRadius = (baseRadius * breathScale) + audioBoost

            // Dynamic Radial Cosmic Aura
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        mood.auraColor.copy(alpha = if (isMyraaSpeaking) 0.5f else 0.35f),
                        mood.secondaryColor.copy(alpha = if (isMyraaSpeaking) 0.25f else 0.15f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = currentRadius * 1.35f
                ),
                radius = currentRadius * 1.35f,
                center = center
            )

            // Energy Wave Ring
            drawCircle(
                color = mood.auraColor.copy(alpha = 0.6f),
                radius = currentRadius,
                center = center,
                style = Stroke(width = 2.5f + (audioBoost * 0.1f))
            )

            // Orbital energy nodes
            val nodeCount = 8
            val angleStep = (2 * Math.PI / nodeCount).toFloat()
            for (i in 0 until nodeCount) {
                val angle = (i * angleStep) + Math.toRadians(auraRotation.toDouble()).toFloat()
                val nodeX = center.x + (currentRadius * 1.08f) * cos(angle)
                val nodeY = center.y + (currentRadius * 1.08f) * sin(angle)
                val nodeColor = if (i % 2 == 0) mood.auraColor else mood.secondaryColor
                drawCircle(
                    color = nodeColor.copy(alpha = 0.8f),
                    radius = 4.5f + (if (isMyraaSpeaking) 3f else 0f),
                    center = Offset(nodeX, nodeY)
                )
            }
        }

        // Inner Character Container & Anime Face Canvas
        Box(
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF221642),
                            Color(0xFF130C28),
                            Color(0xFF090615)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f

                // Hair / Base Head Background
                val hairColor = Color(0xFF6C4AB6)
                val hairHighlight = Color(0xFFB983FF)
                val skinColor = Color(0xFFFFF0EB)
                val blushColor = Color(0xFFFF6584)

                // Back hair locks
                drawCircle(
                    color = hairColor,
                    radius = w * 0.40f,
                    center = Offset(cx, cy + 4)
                )

                // Face Oval
                drawOval(
                    color = skinColor,
                    topLeft = Offset(cx - w * 0.28f, cy - h * 0.25f),
                    size = Size(w * 0.56f, h * 0.54f)
                )

                // Cute Anime Bangs / Forehead Locks
                val bangsPath = Path().apply {
                    moveTo(cx - w * 0.28f, cy - h * 0.12f)
                    quadraticBezierTo(cx - w * 0.14f, cy - h * 0.28f, cx, cy - h * 0.05f)
                    quadraticBezierTo(cx + w * 0.14f, cy - h * 0.28f, cx + w * 0.28f, cy - h * 0.12f)
                    lineTo(cx + w * 0.28f, cy - h * 0.35f)
                    lineTo(cx - w * 0.28f, cy - h * 0.35f)
                    close()
                }
                drawPath(bangsPath, hairHighlight)

                // Side Ribbons / Anime Cat-Ear Ribbon Highlights
                val ribbonColor = mood.auraColor
                drawCircle(
                    color = ribbonColor,
                    radius = 12f,
                    center = Offset(cx - w * 0.27f, cy - h * 0.26f)
                )
                drawCircle(
                    color = ribbonColor,
                    radius = 12f,
                    center = Offset(cx + w * 0.27f, cy - h * 0.26f)
                )

                // Blushing Cheeks
                val blushAlpha = when (mood) {
                    MyraaMood.SHY -> 0.75f
                    MyraaMood.CUTE -> 0.65f
                    MyraaMood.MOCK_ANGRY -> 0.70f
                    else -> 0.35f
                }
                drawOval(
                    color = blushColor.copy(alpha = blushAlpha),
                    topLeft = Offset(cx - w * 0.24f, cy + h * 0.06f),
                    size = Size(24f, 14f)
                )
                drawOval(
                    color = blushColor.copy(alpha = blushAlpha),
                    topLeft = Offset(cx + w * 0.12f, cy + h * 0.06f),
                    size = Size(24f, 14f)
                )

                // Expressive Anime Eyes
                val eyeY = cy - h * 0.02f
                val leftEyeX = cx - w * 0.14f
                val rightEyeX = cx + w * 0.14f
                val eyeWidth = w * 0.11f
                val eyeHeight = (h * 0.14f) * (if (isBlinking) 0.08f else 1f)

                if (mood == MyraaMood.HAPPY || mood == MyraaMood.EXCITED) {
                    // Curved Happy Arc Eyes ^_^
                    val leftArc = Path().apply {
                        moveTo(leftEyeX - eyeWidth * 0.6f, eyeY + 4)
                        quadraticBezierTo(leftEyeX, eyeY - 14, leftEyeX + eyeWidth * 0.6f, eyeY + 4)
                    }
                    drawPath(leftArc, Color(0xFF2C1E4A), style = Stroke(width = 4.5f, cap = StrokeCap.Round))

                    val rightArc = Path().apply {
                        moveTo(rightEyeX - eyeWidth * 0.6f, eyeY + 4)
                        quadraticBezierTo(rightEyeX, eyeY - 14, rightEyeX + eyeWidth * 0.6f, eyeY + 4)
                    }
                    drawPath(rightArc, Color(0xFF2C1E4A), style = Stroke(width = 4.5f, cap = StrokeCap.Round))
                } else if (mood == MyraaMood.PLAYFUL || mood == MyraaMood.TEASING) {
                    // Playful Wink: Left open, Right winking arc
                    drawOval(
                        color = Color(0xFF1E1338),
                        topLeft = Offset(leftEyeX - eyeWidth / 2f, eyeY - eyeHeight / 2f),
                        size = Size(eyeWidth, eyeHeight)
                    )
                    drawCircle(
                        color = mood.auraColor,
                        radius = eyeWidth * 0.35f,
                        center = Offset(leftEyeX, eyeY)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = eyeWidth * 0.15f,
                        center = Offset(leftEyeX - 3, eyeY - 4)
                    )

                    // Right Wink Arc
                    val winkArc = Path().apply {
                        moveTo(rightEyeX - eyeWidth * 0.6f, eyeY)
                        quadraticBezierTo(rightEyeX, eyeY - 12, rightEyeX + eyeWidth * 0.6f, eyeY)
                    }
                    drawPath(winkArc, Color(0xFF2C1E4A), style = Stroke(width = 4.5f, cap = StrokeCap.Round))
                } else {
                    // Standard Open Big Luminous Anime Eyes
                    for (eyeCenterX in listOf(leftEyeX, rightEyeX)) {
                        drawOval(
                            color = Color(0xFF1B0F33),
                            topLeft = Offset(eyeCenterX - eyeWidth / 2f, eyeY - eyeHeight / 2f),
                            size = Size(eyeWidth, eyeHeight)
                        )
                        // Iris gradient
                        drawOval(
                            brush = Brush.verticalGradient(
                                colors = listOf(mood.auraColor, mood.secondaryColor)
                            ),
                            topLeft = Offset(eyeCenterX - eyeWidth * 0.42f, eyeY - eyeHeight * 0.38f),
                            size = Size(eyeWidth * 0.84f, eyeHeight * 0.82f)
                        )
                        // Pupil & Sparkles
                        if (!isBlinking) {
                            drawCircle(
                                color = Color.White,
                                radius = eyeWidth * 0.20f,
                                center = Offset(eyeCenterX - 3, eyeY - 4)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = eyeWidth * 0.10f,
                                center = Offset(eyeCenterX + 3, eyeY + 5)
                            )
                        }
                    }
                }

                // Cute Eyebrows
                val browOffset = when (mood) {
                    MyraaMood.MOCK_ANGRY -> 5f
                    MyraaMood.THINKING -> -3f
                    else -> 0f
                }
                drawLine(
                    color = Color(0xFF4A3078),
                    start = Offset(leftEyeX - eyeWidth * 0.5f, eyeY - eyeHeight * 0.65f + browOffset),
                    end = Offset(leftEyeX + eyeWidth * 0.5f, eyeY - eyeHeight * 0.75f),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Color(0xFF4A3078),
                    start = Offset(rightEyeX - eyeWidth * 0.5f, eyeY - eyeHeight * 0.75f),
                    end = Offset(rightEyeX + eyeWidth * 0.5f, eyeY - eyeHeight * 0.65f + browOffset),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )

                // Animated Mouth (Syncs mouth open height to voice volume!)
                val mouthY = cy + h * 0.15f
                val mouthWidth = w * 0.08f
                val mouthOpen = if (isMyraaSpeaking) (outputAmplitude * 28f).coerceIn(4f, 22f) else 3f

                if (isMyraaSpeaking && mouthOpen > 6f) {
                    // Open talking mouth with cute pink tongue
                    drawOval(
                        color = Color(0xFF7A2048),
                        topLeft = Offset(cx - mouthWidth / 2f, mouthY),
                        size = Size(mouthWidth, mouthOpen)
                    )
                    drawCircle(
                        color = Color(0xFFFF85A1),
                        radius = mouthWidth * 0.35f,
                        center = Offset(cx, mouthY + mouthOpen * 0.6f)
                    )
                } else {
                    // Sweet resting smile arc
                    val smilePath = Path().apply {
                        moveTo(cx - mouthWidth / 2f, mouthY)
                        quadraticBezierTo(cx, mouthY + 7f, cx + mouthWidth / 2f, mouthY)
                    }
                    drawPath(smilePath, Color(0xFF993B63), style = Stroke(width = 3.5f, cap = StrokeCap.Round))
                }
            }
        }
    }
}
