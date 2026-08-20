// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.LocalAppStateStore
import app.LocalUpdateAppState
import app.R
import app.collectAppState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

private const val OnboardingPageCount = 5
private val OnboardingRootBackground = Color(0xFF0F1014)
private val OnboardingHeaderChipBg = Color(0xFF1C1D24)
private val OnboardingHeaderChipBorder = Color(0xFF2C2D38)

@Composable
fun OnboardingPage(
    padding: PaddingValues,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { OnboardingPageCount })
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val stateStore = LocalAppStateStore.current
    val appState by stateStore.collectAppState()
    val updateAppState = LocalUpdateAppState.current

    val currentPage = pagerState.currentPage
    val isLastPage = currentPage == OnboardingPageCount - 1

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(OnboardingRootBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Header: Step pill and Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Step Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(OnboardingHeaderChipBg)
                        .border(1.dp, OnboardingHeaderChipBorder, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "${currentPage + 1} / $OnboardingPageCount",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }

                // Skip button
                if (!isLastPage) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(OnboardingHeaderChipBg)
                            .border(1.dp, OnboardingHeaderChipBorder, RoundedCornerShape(20.dp))
                            .clickable {
                                runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                onFinish()
                            }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.onboarding_skip),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF9E9EA8),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
            }

            // Pager content
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                when (page) {
                    0 -> OnboardingWelcomePage(
                        appState = appState,
                        updateAppState = updateAppState,
                    )
                    1 -> OnboardingPermissionsPage()
                    2 -> OnboardingAppearancePage(
                        appState = appState,
                        updateAppState = updateAppState,
                    )
                    3 -> OnboardingImportPage(
                        appState = appState,
                        updateAppState = updateAppState,
                    )
                    4 -> OnboardingCompletePage(
                        appState = appState,
                    )
                }
            }

            // Bottom Navigation: Indicator + Next / Finish Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Back button (shown on steps 1..4)
                    if (currentPage > 0) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(OnboardingHeaderChipBg)
                                .border(1.dp, OnboardingHeaderChipBorder, CircleShape)
                                .clickable {
                                    runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                    scope.launch {
                                        pagerState.animateScrollToPage(currentPage - 1)
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = MiuixIcons.Back,
                                contentDescription = stringResource(R.string.onboarding_back),
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(50.dp))
                    }

                    // Expanding Pill Page Indicator
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(OnboardingPageCount) { index ->
                            val isSelected = currentPage == index
                            val width by animateDpAsState(
                                targetValue = if (isSelected) 28.dp else 8.dp,
                                animationSpec = spring(dampingRatio = 0.74f, stiffness = Spring.StiffnessMediumLow),
                                label = "dot_w_$index",
                            )
                            val color by animateColorAsState(
                                targetValue = if (isSelected) OnboardingPrimaryGreen else Color(0xFF2C2D38),
                                label = "dot_c_$index",
                            )

                            Box(
                                modifier = Modifier
                                    .size(width = width, height = 8.dp)
                                    .clip(CircleShape)
                                    .background(color),
                            )
                        }
                    }

                    // Next / Get Started Action Button
                    Box(
                        modifier = Modifier
                            .height(50.dp)
                            .clip(RoundedCornerShape(25.dp))
                            .background(OnboardingPrimaryGreen)
                            .clickable {
                                runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                                if (isLastPage) {
                                    onFinish()
                                } else {
                                    scope.launch {
                                        pagerState.animateScrollToPage(currentPage + 1)
                                    }
                                }
                            }
                            .padding(horizontal = 26.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isLastPage) {
                                stringResource(R.string.onboarding_get_started)
                            } else {
                                stringResource(R.string.onboarding_next)
                            },
                            color = Color(0xFF062E1A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }
}
