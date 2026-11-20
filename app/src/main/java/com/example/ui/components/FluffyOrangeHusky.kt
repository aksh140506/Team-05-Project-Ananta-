package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenWith
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.data.model.AppLanguage
import com.example.data.model.HuskyMood
import com.example.ui.i18n.BilingualStrings
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun MovableHuskyOverlay(
    mood: HuskyMood,
    bubbleText: String,
    isBubbleVisible: Boolean,
    isQuickMenuVisible: Boolean,
    voiceEnabled: Boolean,
    language: AppLanguage,
    savedOffsetX: Float,
    savedOffsetY: Float,
    onOffsetCommitted: (Float, Float) -> Unit,
    onHuskyTapped: () -> Unit,
    onSpeakBubble: () -> Unit,
    onToggleMute: () -> Unit,
    onDismissBubble: () -> Unit,
    onQuickVoiceAsk: () -> Unit,
    onQuickScanScam: () -> Unit,
    onQuickOpenBag: () -> Unit,
    onQuickNearestCash: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = isQuickMenuVisible || isBubbleVisible) {
        onDismissBubble()
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val coroutineScope = rememberCoroutineScope()

        val petSizeDp = 88.dp
        val petSizePx = with(density) { petSizeDp.toPx() }
        val horizontalPaddingPx = with(density) { 8.dp.toPx() }
        val topMarginPx = with(density) { 10.dp.toPx() }
        val bottomMarginPx = with(density) { 14.dp.toPx() }

        val maxX = (constraints.maxWidth.toFloat() - petSizePx - horizontalPaddingPx).coerceAtLeast(horizontalPaddingPx)
        val maxY = (constraints.maxHeight.toFloat() - petSizePx - bottomMarginPx).coerceAtLeast(topMarginPx)

        val defaultX = maxX
        val defaultY = (maxY * 0.74f).coerceIn(topMarginPx, maxY)

        val animX = remember {
            Animatable(if (savedOffsetX >= 0f) savedOffsetX.coerceIn(horizontalPaddingPx, maxX) else defaultX)
        }
        val animY = remember {
            Animatable(if (savedOffsetY >= 0f) savedOffsetY.coerceIn(topMarginPx, maxY) else defaultY)
        }
        var isDragging by remember { mutableStateOf(false) }

        LaunchedEffect(constraints.maxWidth, constraints.maxHeight, savedOffsetX, savedOffsetY) {
            if (!isDragging) {
                val targetX = if (savedOffsetX >= 0f) savedOffsetX.coerceIn(horizontalPaddingPx, maxX) else defaultX
                val targetY = if (savedOffsetY >= 0f) savedOffsetY.coerceIn(topMarginPx, maxY) else defaultY
                animX.snapTo(targetX)
                animY.snapTo(targetY)
            }
        }

        val dragScale by animateFloatAsState(
            targetValue = if (isDragging) 1.10f else 1.0f,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
            label = "husky_drag_scale"
        )

        val currentX = animX.value
        val currentY = animY.value
        val isOnRightHalf = currentX > (constraints.maxWidth / 2f - petSizePx / 2f)
        val isNearBottom = currentY > (constraints.maxHeight * 0.52f)

        val cardWidthDp = if (isQuickMenuVisible) 264.dp else 236.dp
        val cardWidthPx = with(density) { cardWidthDp.toPx() }
        val estimatedCardHeightPx = with(density) { if (isQuickMenuVisible) 246.dp.toPx() else 128.dp.toPx() }

        // Guidance speech bubble & Quick-Action Menu anchored cleanly without ever clipping off-screen
        AnimatedVisibility(
            visible = (isQuickMenuVisible || (isBubbleVisible && bubbleText.isNotBlank())),
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.92f, animationSpec = tween(160)),
            exit = fadeOut(tween(130)) + scaleOut(targetScale = 0.92f, animationSpec = tween(130)),
            modifier = Modifier
                .offset {
                    val gapPx = with(density) { 8.dp.toPx() }
                    val bx = if (isOnRightHalf) {
                        (currentX - cardWidthPx - gapPx).coerceAtLeast(horizontalPaddingPx)
                    } else {
                        (currentX + petSizePx + gapPx)
                            .coerceAtMost((constraints.maxWidth - cardWidthPx - horizontalPaddingPx).coerceAtLeast(horizontalPaddingPx))
                    }
                    val maxCardY = (constraints.maxHeight - estimatedCardHeightPx - bottomMarginPx).coerceAtLeast(topMarginPx)
                    val preferredY = if (isNearBottom) {
                        currentY + petSizePx - estimatedCardHeightPx
                    } else {
                        currentY - with(density) { 8.dp.toPx() }
                    }
                    val by = preferredY.coerceIn(topMarginPx, maxCardY)
                    IntOffset(bx.roundToInt(), by.roundToInt())
                }
        ) {
            val isAlert = mood == HuskyMood.ALERT
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (isAlert) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .widthIn(min = 200.dp, max = cardWidthDp)
                    .border(
                        width = 1.5.dp,
                        color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .testTag("husky_guidance_bubble")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = BilingualStrings.pick(
                                language,
                                "Sheru • Helper",
                                "शेरू • बैंकसाथी",
                                "शेरू • तुंदा साथी"
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (isAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onSpeakBubble,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("husky_speak_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = BilingualStrings.pick(language, "Speak tip", "आवाज़ सुनें", "सुणो"),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("husky_mute_button")
                        ) {
                            Icon(
                                imageVector = if (voiceEnabled) Icons.AutoMirrored.Rounded.VolumeOff
                                else Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = BilingualStrings.pick(language, "Mute voice", "आवाज़ बंद करें", "आवाज़ बंद करो"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDismissBubble,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("husky_dismiss_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = BilingualStrings.pick(language, "Close menu", "बंद करें", "बंद करो"),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (bubbleText.isNotBlank()) {
                        Text(
                            text = bubbleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isAlert) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = if (isQuickMenuVisible) 4 else 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isQuickMenuVisible) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SheruQuickActionButton(
                                    icon = Icons.Rounded.Mic,
                                    label = BilingualStrings.pick(language, "Voice Ask", "बोलकर पूछें", "बोली पुच्छो"),
                                    onClick = onQuickVoiceAsk,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sheru_action_voice")
                                )
                                SheruQuickActionButton(
                                    icon = Icons.Rounded.Shield,
                                    label = BilingualStrings.pick(language, "Scan Scam", "स्कैम जांच", "ठगी जांच"),
                                    onClick = onQuickScanScam,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sheru_action_scam")
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SheruQuickActionButton(
                                    icon = Icons.Rounded.Checklist,
                                    label = BilingualStrings.pick(language, "Bag Check", "कागज़ जांच", "कागज़ जांच"),
                                    onClick = onQuickOpenBag,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sheru_action_bag")
                                )
                                SheruQuickActionButton(
                                    icon = Icons.Rounded.LocationOn,
                                    label = BilingualStrings.pick(language, "Cash Near", "पास केंद्र", "नेड़े केंद्र"),
                                    onClick = onQuickNearestCash,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("sheru_action_facilities")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Frictionless 1:1 Movable Sheru Companion (single deterministic gesture handler: zero drag-vs-tap conflict)
        Box(
            modifier = Modifier
                .offset { IntOffset(currentX.roundToInt(), currentY.roundToInt()) }
                .size(petSizeDp)
                .graphicsLayer {
                    scaleX = dragScale
                    scaleY = dragScale
                }
                .pointerInput(maxX, maxY) {
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragged = false
                        var accumDx = 0f
                        var accumDy = 0f

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val delta = change.positionChange()
                            accumDx += delta.x
                            accumDy += delta.y
                            if (!dragged && (abs(accumDx) > touchSlop || abs(accumDy) > touchSlop)) {
                                dragged = true
                                isDragging = true
                            }
                            if (dragged) {
                                change.consume()
                                val nextX = (animX.value + delta.x).coerceIn(horizontalPaddingPx, maxX)
                                val nextY = (animY.value + delta.y).coerceIn(topMarginPx, maxY)
                                coroutineScope.launch {
                                    animX.snapTo(nextX)
                                    animY.snapTo(nextY)
                                }
                            }
                        }

                        if (dragged) {
                            isDragging = false
                            val finalX = animX.value.coerceIn(horizontalPaddingPx, maxX)
                            val finalY = animY.value.coerceIn(topMarginPx, maxY)
                            onOffsetCommitted(finalX, finalY)
                        } else {
                            onHuskyTapped()
                        }
                    }
                }
                .semantics {
                    role = Role.Button
                    contentDescription = "Sheru banking companion. Tap for quick actions or drag smoothly anywhere."
                }
                .testTag("movable_husky_pet"),
            contentAlignment = Alignment.Center
        ) {
            FluffyOrangeHuskyMascot(
                mood = mood,
                modifier = Modifier.size(82.dp)
            )
            // Drag indicator badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(24.dp)
                    .shadow(2.dp, CircleShape)
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenWith,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun SheruQuickActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FilledTonalButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier.height(48.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FluffyOrangeHuskyMascot(
    mood: HuskyMood,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "husky_motion")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )
    val tailAngle by infiniteTransition.animateFloat(
        initialValue = if (mood == HuskyMood.ALERT) -6f else -14f,
        targetValue = if (mood == HuskyMood.ALERT) 6f else 18f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (mood) {
                    HuskyMood.GUIDING, HuskyMood.LISTENING -> 340
                    HuskyMood.ALERT -> 220
                    else -> 680
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tail_wag"
    )
    val earWiggle by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = if (mood == HuskyMood.LISTENING) 7f else 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 720, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ear_wiggle"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val orangeFur = Color(0xFFE6782B)
        val deepOrange = Color(0xFFC85917)
        val creamFur = Color(0xFFFFF8EE)
        val warmShadow = Color(0x336D3E1C)
        val scarfGreen = if (mood == HuskyMood.ALERT) Color(0xFFBA2D2D) else Color(0xFF2E7D5B)
        val eyeBrown = Color(0xFF332014)

        drawOval(
            color = warmShadow,
            topLeft = Offset(w * 0.16f, h * 0.86f),
            size = Size(w * 0.68f, h * 0.11f)
        )

        rotate(degrees = tailAngle, pivot = Offset(w * 0.72f, h * 0.70f)) {
            val tailPath = Path().apply {
                moveTo(w * 0.68f, h * 0.72f)
                cubicTo(w * 0.96f, h * 0.66f, w * 0.98f, h * 0.42f, w * 0.84f, h * 0.36f)
                cubicTo(w * 0.76f, h * 0.34f, w * 0.72f, h * 0.48f, w * 0.74f, h * 0.62f)
                close()
            }
            drawPath(tailPath, color = orangeFur)
            drawCircle(
                color = creamFur,
                radius = w * 0.095f,
                center = Offset(w * 0.85f, h * 0.41f)
            )
        }

        val bodyCenterY = h * 0.71f * (2f - breathScale)
        drawOval(
            color = orangeFur,
            topLeft = Offset(w * 0.24f, bodyCenterY - h * 0.14f),
            size = Size(w * 0.52f, h * 0.34f)
        )
        drawOval(
            color = creamFur,
            topLeft = Offset(w * 0.34f, bodyCenterY - h * 0.08f),
            size = Size(w * 0.32f, h * 0.26f)
        )
        drawCircle(color = creamFur, radius = w * 0.075f, center = Offset(w * 0.36f, h * 0.86f))
        drawCircle(color = creamFur, radius = w * 0.075f, center = Offset(w * 0.64f, h * 0.86f))

        drawRoundRect(
            color = scarfGreen,
            topLeft = Offset(w * 0.28f, h * 0.56f),
            size = Size(w * 0.44f, h * 0.10f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f)
        )
        drawRoundRect(
            color = scarfGreen,
            topLeft = Offset(w * 0.52f, h * 0.62f),
            size = Size(w * 0.10f, h * 0.14f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )

        rotate(degrees = -earWiggle, pivot = Offset(w * 0.32f, h * 0.28f)) {
            drawHuskyEar(
                tip = Offset(w * 0.23f, h * 0.05f),
                baseLeft = Offset(w * 0.20f, h * 0.33f),
                baseRight = Offset(w * 0.41f, h * 0.24f),
                outerColor = orangeFur,
                innerColor = creamFur
            )
        }
        rotate(degrees = earWiggle, pivot = Offset(w * 0.68f, h * 0.28f)) {
            drawHuskyEar(
                tip = Offset(w * 0.77f, h * 0.05f),
                baseLeft = Offset(w * 0.59f, h * 0.24f),
                baseRight = Offset(w * 0.80f, h * 0.33f),
                outerColor = orangeFur,
                innerColor = creamFur
            )
        }

        drawCircle(color = deepOrange, radius = w * 0.305f, center = Offset(w * 0.50f, h * 0.38f))
        drawOval(
            color = orangeFur,
            topLeft = Offset(w * 0.15f, h * 0.20f),
            size = Size(w * 0.70f, h * 0.42f)
        )
        drawCircle(color = creamFur, radius = w * 0.14f, center = Offset(w * 0.30f, h * 0.46f))
        drawCircle(color = creamFur, radius = w * 0.14f, center = Offset(w * 0.70f, h * 0.46f))

        val maskPath = Path().apply {
            moveTo(w * 0.50f, h * 0.22f)
            cubicTo(w * 0.44f, h * 0.35f, w * 0.28f, h * 0.38f, w * 0.28f, h * 0.49f)
            cubicTo(w * 0.28f, h * 0.59f, w * 0.72f, h * 0.59f, w * 0.72f, h * 0.49f)
            cubicTo(w * 0.72f, h * 0.38f, w * 0.56f, h * 0.35f, w * 0.50f, h * 0.22f)
            close()
        }
        drawPath(maskPath, color = creamFur)

        drawOval(color = creamFur, topLeft = Offset(w * 0.33f, h * 0.29f), size = Size(w * 0.10f, h * 0.055f))
        drawOval(color = creamFur, topLeft = Offset(w * 0.57f, h * 0.29f), size = Size(w * 0.10f, h * 0.055f))

        val eyeRadius = if (mood == HuskyMood.LISTENING || mood == HuskyMood.ALERT) w * 0.052f else w * 0.045f
        drawCircle(color = eyeBrown, radius = eyeRadius, center = Offset(w * 0.38f, h * 0.41f))
        drawCircle(color = eyeBrown, radius = eyeRadius, center = Offset(w * 0.62f, h * 0.41f))
        drawCircle(color = Color.White, radius = w * 0.017f, center = Offset(w * 0.392f, h * 0.398f))
        drawCircle(color = Color.White, radius = w * 0.017f, center = Offset(w * 0.632f, h * 0.398f))

        drawOval(
            color = eyeBrown,
            topLeft = Offset(w * 0.455f, h * 0.455f),
            size = Size(w * 0.09f, h * 0.06f)
        )
        if (mood == HuskyMood.ALERT) {
            drawCircle(color = Color(0xFFBA2D2D), radius = w * 0.032f, center = Offset(w * 0.50f, h * 0.545f))
        } else {
            val smilePath = Path().apply {
                moveTo(w * 0.43f, h * 0.525f)
                quadraticTo(w * 0.47f, h * 0.56f, w * 0.50f, h * 0.525f)
                quadraticTo(w * 0.53f, h * 0.56f, w * 0.57f, h * 0.525f)
            }
            drawPath(
                path = smilePath,
                color = eyeBrown,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = w * 0.024f,
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

private fun DrawScope.drawHuskyEar(
    tip: Offset,
    baseLeft: Offset,
    baseRight: Offset,
    outerColor: Color,
    innerColor: Color
) {
    val outer = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(baseLeft.x, baseLeft.y)
        lineTo(baseRight.x, baseRight.y)
        close()
    }
    drawPath(outer, color = outerColor)

    val center = Offset(
        (tip.x + baseLeft.x + baseRight.x) / 3f,
        (tip.y + baseLeft.y + baseRight.y) / 3f
    )
    val inner = Path().apply {
        moveTo((tip.x + center.x) / 2f, (tip.y + center.y) / 2f)
        lineTo((baseLeft.x + center.x) / 2f, (baseLeft.y + center.y) / 2f)
        lineTo((baseRight.x + center.x) / 2f, (baseRight.y + center.y) / 2f)
        close()
    }
    drawPath(inner, color = innerColor)
}
