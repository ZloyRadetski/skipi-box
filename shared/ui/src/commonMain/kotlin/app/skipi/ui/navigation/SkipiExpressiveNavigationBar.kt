// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.text.themedFontWeight
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SkipiNavigationBarSize {
    Small,
    Medium,
    Large,
}

/** Theme values supplied by the host while geometry and interaction stay common. */
data class SkipiExpressiveNavigationColors(
    val surface: Color,
    val accent: Color,
    val onAccent: Color,
    val onSurfaceVariant: Color,
    val isDark: Boolean,
)

/**
 * SKIPI's animated compact navigation bar.
 *
 * It is deliberately platform-neutral: a host only supplies its appearance
 * tokens and localized [SkipiNavigationItem]s. The capsule gesture, haptics
 * and page-selection behaviour are shared Compose code.
 */
@Composable
fun SkipiExpressiveNavigationBar(
    mainPagerState: SkipiMainPagerState,
    colors: SkipiExpressiveNavigationColors,
    size: SkipiNavigationBarSize,
    modifier: Modifier = Modifier,
    items: List<SkipiNavigationItem> = defaultSkipiNavigationItems(),
) {
    val selectedPage = mainPagerState.selectedPage
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val dimensions = when (size) {
        SkipiNavigationBarSize.Small -> SkipiExpressiveNavigationDimensions(
            islandRadius = 24.dp,
            indicatorRadius = 18.dp,
            outerHorizontalPadding = 20.dp,
            outerVerticalPadding = 6.dp,
            innerHorizontalPadding = 4.dp,
            innerVerticalPadding = 4.dp,
            tabVerticalPadding = 6.dp,
            iconSize = 20.dp,
            textSize = 11.sp,
            iconTextSpacer = 2.dp,
            shadowElevation = 6.dp,
        )

        SkipiNavigationBarSize.Medium -> SkipiExpressiveNavigationDimensions(
            islandRadius = 28.dp,
            indicatorRadius = 21.dp,
            outerHorizontalPadding = 16.dp,
            outerVerticalPadding = 8.dp,
            innerHorizontalPadding = 5.dp,
            innerVerticalPadding = 5.dp,
            tabVerticalPadding = 8.5.dp,
            iconSize = 22.5.dp,
            textSize = 12.sp,
            iconTextSpacer = 3.dp,
            shadowElevation = 10.dp,
        )

        SkipiNavigationBarSize.Large -> SkipiExpressiveNavigationDimensions(
            islandRadius = 32.dp,
            indicatorRadius = 24.dp,
            outerHorizontalPadding = 16.dp,
            outerVerticalPadding = 10.dp,
            innerHorizontalPadding = 6.dp,
            innerVerticalPadding = 6.dp,
            tabVerticalPadding = 11.dp,
            iconSize = 25.dp,
            textSize = 13.sp,
            iconTextSpacer = 4.dp,
            shadowElevation = 10.dp,
        )
    }
    val islandShape = RoundedCornerShape(dimensions.islandRadius)
    val islandBorderColor = if (colors.isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                horizontal = dimensions.outerHorizontalPadding,
                vertical = dimensions.outerVerticalPadding,
            )
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = dimensions.shadowElevation,
                    shape = islandShape,
                    ambientColor = if (colors.isDark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.18f),
                    spotColor = if (colors.isDark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.22f),
                )
                .clip(islandShape)
                .background(colors.surface)
                .border(width = 1.dp, color = islandBorderColor, shape = islandShape)
                .padding(
                    horizontal = dimensions.innerHorizontalPadding,
                    vertical = dimensions.innerVerticalPadding,
                ),
        ) {
            val density = LocalDensity.current
            var totalWidthPx by remember { mutableIntStateOf(0) }
            var totalHeightPx by remember { mutableIntStateOf(0) }
            val tabCount = items.size.coerceAtLeast(1)
            val tabWidthPx = if (totalWidthPx > 0) totalWidthPx.toFloat() / tabCount else 0f
            val tabWidthDp = if (totalWidthPx > 0) with(density) { tabWidthPx.toDp() } else 0.dp
            val totalHeightDp = if (totalHeightPx > 0) with(density) { totalHeightPx.toDp() } else 0.dp
            val innerPaddingHorizontal = 3.dp
            val innerPaddingVertical = 2.dp
            val indicatorShape = RoundedCornerShape(dimensions.indicatorRadius)
            val indicatorWidth = (tabWidthDp - innerPaddingHorizontal * 2).coerceAtLeast(0.dp)
            val indicatorHeight = (totalHeightDp - innerPaddingVertical * 2).coerceAtLeast(0.dp)
            val maxOffsetPx = if (tabWidthPx > 0f) tabWidthPx * (tabCount - 1) else 0f
            val dragOffset = remember { Animatable(0f) }
            var isDragging by remember { mutableStateOf(false) }
            var isGrabbed by remember { mutableStateOf(false) }
            var grabTouchOffsetX by remember { mutableFloatStateOf(0f) }
            var dragVelocity by remember { mutableFloatStateOf(0f) }
            var lastHapticIndex by remember { mutableIntStateOf(selectedPage) }

            LaunchedEffect(selectedPage, tabWidthPx) {
                if (!isDragging && tabWidthPx > 0f) {
                    val targetPx = selectedPage * tabWidthPx
                    if (abs(dragOffset.value - targetPx) > 0.5f) {
                        dragOffset.animateTo(
                            targetValue = targetPx,
                            animationSpec = spring(
                                dampingRatio = 0.74f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    }
                }
            }

            val currentOffsetPx = dragOffset.value
            val currentProgress = if (tabWidthPx > 0f) currentOffsetPx / tabWidthPx else selectedPage.toFloat()
            val stretchX = if (isDragging && tabWidthPx > 0f) {
                val overdrag = when {
                    currentOffsetPx < 0f -> -currentOffsetPx / tabWidthPx
                    currentOffsetPx > maxOffsetPx -> (currentOffsetPx - maxOffsetPx) / tabWidthPx
                    else -> 0f
                }
                1f + (abs(dragVelocity) / 5000f).coerceAtMost(0.15f) - (overdrag * 0.25f).coerceAtMost(0.12f)
            } else {
                1f
            }
            val stretchY = if (isDragging) 1f / stretchX.coerceAtLeast(0.5f) else 1f

            val dragGestureModifier = if (tabWidthPx > 0f) {
                Modifier.pointerInput(tabCount, tabWidthPx, selectedPage) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val capsuleLeft = dragOffset.value
                            val capsuleRight = capsuleLeft + tabWidthPx
                            if (offset.x in (capsuleLeft - 12f)..(capsuleRight + 12f)) {
                                isGrabbed = true
                                isDragging = true
                                dragVelocity = 0f
                                grabTouchOffsetX = (offset.x - capsuleLeft).coerceIn(0f, tabWidthPx)
                                coroutineScope.launch { dragOffset.stop() }
                                lastHapticIndex = ((dragOffset.value + tabWidthPx / 2f) / tabWidthPx)
                                    .toInt()
                                    .coerceIn(0, tabCount - 1)
                            } else {
                                isGrabbed = false
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (isGrabbed) {
                                change.consume()
                                val rawOffset = change.position.x - grabTouchOffsetX
                                val dampedOffset = when {
                                    rawOffset < 0f -> rawOffset * 0.35f
                                    rawOffset > maxOffsetPx -> maxOffsetPx + (rawOffset - maxOffsetPx) * 0.35f
                                    else -> rawOffset
                                }
                                dragVelocity = dragAmount * 60f
                                coroutineScope.launch { dragOffset.snapTo(dampedOffset) }
                                val hoverIndex = ((dampedOffset + tabWidthPx / 2f) / tabWidthPx)
                                    .toInt()
                                    .coerceIn(0, tabCount - 1)
                                if (hoverIndex != lastHapticIndex) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    lastHapticIndex = hoverIndex
                                }
                            }
                        },
                        onDragEnd = {
                            if (isGrabbed) {
                                isGrabbed = false
                                isDragging = false
                                val targetIndex = ((dragOffset.value + dragVelocity * 0.08f) / tabWidthPx)
                                    .roundToInt()
                                    .coerceIn(0, tabCount - 1)
                                coroutineScope.launch {
                                    dragOffset.animateTo(
                                        targetValue = targetIndex * tabWidthPx,
                                        animationSpec = spring(
                                            dampingRatio = 0.74f,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                                val target = items.getOrNull(targetIndex)
                                if (target != null && target.destination.pageIndex != selectedPage) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    mainPagerState.animateTo(target.destination)
                                }
                            } else {
                                isDragging = false
                            }
                        },
                        onDragCancel = {
                            if (isGrabbed) {
                                isGrabbed = false
                                isDragging = false
                                coroutineScope.launch {
                                    dragOffset.animateTo(
                                        targetValue = selectedPage * tabWidthPx,
                                        animationSpec = spring(
                                            dampingRatio = 0.74f,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            } else {
                                isDragging = false
                            }
                        },
                    )
                }
            } else {
                Modifier
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged {
                        totalWidthPx = it.width
                        totalHeightPx = it.height
                    }
                    .then(dragGestureModifier),
            ) {
                if (totalWidthPx > 0 && totalHeightPx > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = innerPaddingHorizontal, top = innerPaddingVertical)
                            .size(width = indicatorWidth, height = indicatorHeight)
                            .graphicsLayer {
                                translationX = currentOffsetPx
                                scaleX = stretchX
                                scaleY = stretchY
                            }
                            .clip(indicatorShape)
                            .background(colors.accent),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val inactiveColor = colors.onSurfaceVariant.copy(alpha = 0.72f)
                    items.forEachIndexed { index, item ->
                        val distance = abs(currentProgress - index)
                        val activeFraction = (1f - distance).coerceIn(0f, 1f)
                        val selected = selectedPage == item.destination.pageIndex
                        val iconScale = 1f + (0.08f * activeFraction)
                        val iconOffsetYPx = with(density) { (-1).dp.toPx() } * activeFraction
                        val itemColor = lerp(inactiveColor, colors.onAccent, activeFraction)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(indicatorShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        if (!isDragging) {
                                            if (!selected || item.destination == SkipiMainDestination.Proxy) {
                                                if (!selected) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                                mainPagerState.animateTo(item.destination)
                                            }
                                        }
                                    },
                                )
                                .padding(vertical = dimensions.tabVerticalPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = itemColor,
                                modifier = Modifier
                                    .size(dimensions.iconSize)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                        translationY = iconOffsetYPx
                                    },
                            )
                            Spacer(modifier = Modifier.height(dimensions.iconTextSpacer))
                            Text(
                                text = item.label,
                                color = itemColor,
                                fontWeight = themedFontWeight(
                                    if (activeFraction > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
                                ),
                                fontSize = dimensions.textSize,
                                maxLines = 1,
                                letterSpacing = 0.25.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class SkipiExpressiveNavigationDimensions(
    val islandRadius: androidx.compose.ui.unit.Dp,
    val indicatorRadius: androidx.compose.ui.unit.Dp,
    val outerHorizontalPadding: androidx.compose.ui.unit.Dp,
    val outerVerticalPadding: androidx.compose.ui.unit.Dp,
    val innerHorizontalPadding: androidx.compose.ui.unit.Dp,
    val innerVerticalPadding: androidx.compose.ui.unit.Dp,
    val tabVerticalPadding: androidx.compose.ui.unit.Dp,
    val iconSize: androidx.compose.ui.unit.Dp,
    val textSize: androidx.compose.ui.unit.TextUnit,
    val iconTextSpacer: androidx.compose.ui.unit.Dp,
    val shadowElevation: androidx.compose.ui.unit.Dp,
)
