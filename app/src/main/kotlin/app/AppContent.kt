// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import ui.background.AppBackground
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import app.modes.BottomBarSizeMedium
import app.modes.BottomBarSizeSmall
import app.navigation.NavigationBackStackSaver
import app.navigation.Navigator
import app.navigation.Route
import androidx.compose.ui.res.stringResource
import features.about.AboutPage
import features.about.LicensePage
import features.logs.AccessLogsPage
import features.logs.CoreLogsPage
import features.logs.LogcatLogsPage
import features.resources.ResourceManagementPage
import features.proxy.server.list.ProxyServerListPage
import features.proxy.app.ProxyAppListPage
import features.proxy.server.editor.ProxyServerPage
import features.proxy.server.editor.StrategyGroupMemberSelectorPage
import features.routing.RouteOutboundSelectorPage
import features.routing.RouteRuleEditorPage
import features.config.TrafficConfigPage
import features.config.TrafficConfigEditorPage
import features.config.TrafficConfigRawEditorPage
import features.config.TrafficConfigSectionPage
import features.config.TrafficConfigRuleEditorPage
import features.settings.LocalProxySettingsPage
import features.settings.SettingsAppearancePage
import features.settings.SettingsBackupResetPage
import features.settings.SettingsIntegrationPage
import features.settings.SettingsLogsPage
import features.settings.SettingsPage
import features.settings.SettingsSubscriptionsPage
import features.settings.SettingsVpnPage
import features.networkautomation.ui.SettingsNetworkAutomationPage
import features.settings.SkipiUrlSchemesPage
import features.settings.SubscriptionPingSettingsPage
import features.settings.SubscriptionUserAgentsPage
import features.subscription.SubscriptionGroupListPage
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AppRecording
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.layout.pageWindowPadding
import ui.layout.shouldShowSplitPane
import kotlin.math.abs
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private object MainNavigation {
    const val PROXY_PAGE_INDEX = 0
    const val CONFIG_PAGE_INDEX = 1
    const val SETTINGS_PAGE_INDEX = 2

    const val NAVIGATION_ITEMS_COUNT = 3

    @Composable
    fun navigationItems(): List<NavigationItem> {
        val languageMode = LocalAppChromeState.current.languageMode
        val proxy = stringResource(R.string.nav_proxy)
        val configs = stringResource(R.string.nav_configs)
        val settings = stringResource(R.string.nav_settings)

        return remember(languageMode, proxy, configs, settings) {
            listOf(
                NavigationItem(proxy, MiuixIcons.Layers),
                NavigationItem(configs, MiuixIcons.MindMap),
                NavigationItem(settings, MiuixIcons.Settings),
            )
        }
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator found!") }
val LocalIsWideScreen = staticCompositionLocalOf { false }
val LocalProxyPageScrollToTopRequest = staticCompositionLocalOf { 0 }

private inline fun <reified T : NavKey> androidx.navigation3.runtime.EntryProviderScope<NavKey>.appEntry(
    crossinline content: @Composable (T) -> Unit,
) {
    entry<T> { route ->
        content(route)
    }
}

@Composable
fun AppContent(
    padding: PaddingValues,
) {
    val languageMode = LocalAppChromeState.current.languageMode
    val stateStore = LocalAppStateStore.current
    val initialRoute = remember {
        if (stateStore.currentState.hasCompletedOnboarding) Route.Main else Route.Onboarding
    }
    val pagerState = rememberPagerState(pageCount = { MainNavigation.NAVIGATION_ITEMS_COUNT })
    val mainPagerState = rememberMainPagerState(pagerState)
    LaunchedEffect(mainPagerState.pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    val backStack = rememberSaveable(saver = NavigationBackStackSaver) {
        mutableStateListOf<NavKey>().apply { add(initialRoute) }
    }
    val navigator = remember { Navigator(backStack) }

    MainScreenBackHandler(mainPagerState, navigator)

    val isWideScreen = shouldShowSplitPane()

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalIsWideScreen provides isWideScreen,
        LocalProxyPageScrollToTopRequest provides mainPagerState.proxyPageScrollToTopRequest,
    ) {
        val backgroundStyle = LocalAppChromeState.current.backgroundStyle
        val entryProvider = remember(backStack, languageMode, backgroundStyle) {
            entryProvider<NavKey> {
                appEntry<Route.Onboarding> {
                    key(languageMode) {
                        features.onboarding.OnboardingPage(
                            padding = padding,
                            onFinish = {
                                stateStore.update { it.copy(hasCompletedOnboarding = true) }
                                if (backStack.contains(Route.Main)) {
                                    navigator.pop()
                                } else {
                                    navigator.replace(Route.Main)
                                }
                            },
                        )
                    }
                }
                appEntry<Route.Main> {
                    key(languageMode) {
                        Home(
                            padding = padding,
                            mainPagerState = mainPagerState,
                        )
                    }
                }
                appEntry<Route.SettingsAppearance> {
                    key(languageMode) {
                        SettingsAppearancePage(padding = padding)
                    }
                }
                appEntry<Route.SettingsVpn> {
                    key(languageMode) {
                        SettingsVpnPage(padding = padding)
                    }
                }
                appEntry<Route.SettingsNetworkAutomation> {
                    key(languageMode) {
                        SettingsNetworkAutomationPage(padding = padding)
                    }
                }
                appEntry<Route.SettingsSubscriptions> {
                    key(languageMode) {
                        SettingsSubscriptionsPage(padding = padding)
                    }
                }
                appEntry<Route.SettingsIntegration> {
                    key(languageMode) {
                        SettingsIntegrationPage(padding = padding)
                    }
                }
                appEntry<Route.SettingsLogs> {
                    key(languageMode) {
                        SettingsLogsPage(padding = padding)
                    }
                }
                appEntry<Route.SettingsBackupReset> {
                    key(languageMode) {
                        SettingsBackupResetPage(padding = padding)
                    }
                }
                appEntry<Route.About> {
                    key(languageMode) {
                        AboutPage(padding = padding)
                    }
                }
                appEntry<Route.License> {
                    key(languageMode) {
                        LicensePage(padding = padding)
                    }
                }
                appEntry<Route.CoreLogs> {
                    key(languageMode) {
                        CoreLogsPage(padding = padding)
                    }
                }
                appEntry<Route.AccessLogs> {
                    key(languageMode) {
                        AccessLogsPage(padding = padding)
                    }
                }
                appEntry<Route.LogcatLogs> {
                    key(languageMode) {
                        LogcatLogsPage(padding = padding)
                    }
                }
                appEntry<Route.SpeedTest> {
                    key(languageMode) {
                        features.tools.speedtest.SpeedTestPage(padding = padding)
                    }
                }
                appEntry<Route.DnsLeakTest> {
                    key(languageMode) {
                        features.tools.dnsleak.DnsLeakTestPage(padding = padding)
                    }
                }
                appEntry<Route.IpInfo> {
                    key(languageMode) {
                        features.tools.ipinfo.IpInfoPage(padding = padding)
                    }
                }
                appEntry<Route.ResourceManagement> { route ->
                    key(languageMode) {
                        ResourceManagementPage(
                            padding = padding,
                            trafficConfigId = route.trafficConfigId,
                        )
                    }
                }
                appEntry<Route.TrafficConfigEditor> { route ->
                    key(languageMode) {
                        TrafficConfigEditorPage(
                            padding = padding,
                            trafficConfigId = route.trafficConfigId,
                        )
                    }
                }
                appEntry<Route.TrafficConfigRawEditor> { route ->
                    key(languageMode) {
                        TrafficConfigRawEditorPage(
                            padding = padding,
                            trafficConfigId = route.trafficConfigId,
                        )
                    }
                }
                appEntry<Route.TrafficConfigSection> { route ->
                    key(languageMode) {
                        TrafficConfigSectionPage(
                            padding = padding,
                            trafficConfigId = route.trafficConfigId,
                            section = route.section,
                        )
                    }
                }
                appEntry<Route.TrafficConfigRuleEditor> { route ->
                    key(languageMode) {
                        TrafficConfigRuleEditorPage(
                            padding = padding,
                            trafficConfigId = route.trafficConfigId,
                            ruleLineNumber = route.ruleLineNumber,
                        )
                    }
                }
                appEntry<Route.SubscriptionGroupList> {
                    key(languageMode) {
                        SubscriptionGroupListPage(padding = padding)
                    }
                }
                appEntry<Route.ProxyAppList> { route ->
                    key(languageMode) {
                        ProxyAppListPage(
                            padding = padding,
                            trafficConfigId = route.trafficConfigId,
                        )
                    }
                }
                appEntry<Route.LocalProxySettings> {
                    key(languageMode) {
                        LocalProxySettingsPage(padding = padding)
                    }
                }
                appEntry<Route.SubscriptionPingSettings> {
                    key(languageMode) {
                        SubscriptionPingSettingsPage(padding = padding)
                    }
                }
                appEntry<Route.SubscriptionUserAgents> {
                    key(languageMode) {
                        SubscriptionUserAgentsPage(padding = padding)
                    }
                }
                appEntry<Route.SkipiUrlSchemes> {
                    key(languageMode) {
                        SkipiUrlSchemesPage(padding = padding)
                    }
                }
                appEntry<Route.ProxyServerEditor> {
                    key(languageMode) {
                        ProxyServerPage(
                            padding = padding,
                            ps = it.ps,
                            serverId = it.serverId,
                            groupId = it.groupId,
                            returnGroupId = it.returnGroupId,
                            resultKey = it.resultKey,
                        )
                    }
                }
                appEntry<Route.StrategyGroupMemberSelector> { route ->
                    key(languageMode) {
                        StrategyGroupMemberSelectorPage(
                            padding = padding,
                            selectedServerIds = route.selectedServerIds,
                            excludedServerId = route.excludedServerId,
                            resultKey = route.resultKey,
                            requireServerRemarks = route.requireServerRemarks,
                        )
                    }
                }
                appEntry<Route.RouteOutboundSelector> { route ->
                    key(languageMode) {
                        RouteOutboundSelectorPage(
                            padding = padding,
                            selectedTag = route.selectedTag,
                            resultKey = route.resultKey,
                            trafficConfigId = route.trafficConfigId,
                        )
                    }
                }
                appEntry<Route.RoutingRuleEditor> { route ->
                    key(languageMode) {
                        RouteRuleEditorPage(
                            padding = padding,
                            ruleId = route.ruleId,
                        )
                    }
                }
            }
        }

        val entries = rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
            entryProvider = entryProvider,
        )

        val transitionEffects = remember {
            NavDisplayTransitionEffects(
                enableCornerClip = true,
                dimAmount = 0.5f,
                blockInputDuringTransition = true,
                popDirectionFollowsSwipeEdge = false,
            )
        }

        NavDisplay(
            entries = entries,
            onBack = { navigator.pop() },
            transitionEffects = transitionEffects,
        )
    }
}

@Composable
private fun Home(
    padding: PaddingValues,
    mainPagerState: MainPagerState,
) {
    val isWideScreen = LocalIsWideScreen.current
    val layoutDirection = LocalLayoutDirection.current
    val navigationItems = MainNavigation.navigationItems()
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize(),
    ) {
        if (isWideScreen) {
            WideScreenContent(
                navigationItems = navigationItems,
                layoutDirection = layoutDirection,
                mainPagerState = mainPagerState,
            )
        } else {
            CompactScreenLayout(
                navigationItems = navigationItems,
                padding = padding,
                mainPagerState = mainPagerState,
            )
        }
    }
}

@Composable
private fun WideScreenContent(
    navigationItems: List<NavigationItem>,
    layoutDirection: LayoutDirection,
    mainPagerState: MainPagerState,
) {
    val page = mainPagerState.selectedPage
    Row {
        NavigationRail(
            modifier = Modifier.background(MiuixTheme.colorScheme.surface),
        ) {
            navigationItems.forEachIndexed { index, item ->
                NavigationRailItem(
                    selected = page == index,
                    onClick = { mainPagerState.animateToPage(index) },
                    icon = item.icon,
                    label = item.label,
                )
            }
        }
        Scaffold(
            containerColor = Color.Transparent,
            modifier = Modifier
                .fillMaxSize(),
            contentWindowInsets =
                WindowInsets.systemBars.union(
                    WindowInsets.displayCutout.exclude(
                        WindowInsets.displayCutout.only(WindowInsetsSides.Start),
                    ),
                ),
        ) { padding ->
            AppPager(
                padding = PaddingValues(top = padding.calculateTopPadding()),
                pagerState = mainPagerState.pagerState,
                modifier = Modifier
                    .imePadding()
                    .padding(end = padding.calculateEndPadding(layoutDirection)),
            )
        }
    }
}

@Composable
private fun CompactScreenLayout(
    navigationItems: List<NavigationItem>,
    padding: PaddingValues,
    mainPagerState: MainPagerState,
) {
    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize(),
        bottomBar = {
            ExpressiveFloatingNavigationBar(
                navigationItems = navigationItems,
                mainPagerState = mainPagerState,
            )
        },
    ) { innerPadding ->
        AppPager(
            padding = innerPadding,
            pagerState = mainPagerState.pagerState,
            modifier = Modifier.pageWindowPadding(padding),
        )
    }
}

@Composable
private fun ExpressiveFloatingNavigationBar(
    navigationItems: List<NavigationItem>,
    mainPagerState: MainPagerState,
    modifier: Modifier = Modifier,
) {
    val selectedPage = mainPagerState.selectedPage
    val haptic = LocalHapticFeedback.current
    val isDark = AppTheme.colors.isDark
    val bottomBarSize = LocalAppChromeState.current.bottomBarSize
    val coroutineScope = rememberCoroutineScope()

    val (islandRadius, indicatorRadius) = when (bottomBarSize) {
        BottomBarSizeSmall -> 24.dp to 18.dp
        BottomBarSizeMedium -> 28.dp to 21.dp
        else -> 32.dp to 24.dp
    }
    val islandHorizontalPadding = when (bottomBarSize) {
        BottomBarSizeSmall -> 20.dp
        BottomBarSizeMedium -> 16.dp
        else -> 16.dp
    }
    val islandVerticalPadding = when (bottomBarSize) {
        BottomBarSizeSmall -> 6.dp
        BottomBarSizeMedium -> 8.dp
        else -> 10.dp
    }
    val islandInnerPaddingHorizontal = when (bottomBarSize) {
        BottomBarSizeSmall -> 4.dp
        BottomBarSizeMedium -> 5.dp
        else -> 6.dp
    }
    val islandInnerPaddingVertical = when (bottomBarSize) {
        BottomBarSizeSmall -> 4.dp
        BottomBarSizeMedium -> 5.dp
        else -> 6.dp
    }
    val tabVerticalPadding = when (bottomBarSize) {
        BottomBarSizeSmall -> 6.dp
        BottomBarSizeMedium -> 8.5.dp
        else -> 11.dp
    }
    val iconSize = when (bottomBarSize) {
        BottomBarSizeSmall -> 20.dp
        BottomBarSizeMedium -> 22.5.dp
        else -> 25.dp
    }
    val textFontSize = when (bottomBarSize) {
        BottomBarSizeSmall -> 11.sp
        BottomBarSizeMedium -> 12.sp
        else -> 13.sp
    }
    val iconTextSpacer = when (bottomBarSize) {
        BottomBarSizeSmall -> 2.dp
        BottomBarSizeMedium -> 3.dp
        else -> 4.dp
    }

    val islandShape = RoundedCornerShape(islandRadius)
    val islandBorderColor = if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = islandHorizontalPadding, vertical = islandVerticalPadding)
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .shadow(
                    elevation = if (bottomBarSize == BottomBarSizeSmall) 6.dp else 10.dp,
                    shape = islandShape,
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.18f),
                    spotColor = if (isDark) Color.Black.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.22f),
                )
                .clip(islandShape)
                .background(AppTheme.colors.surface)
                .border(
                    width = 1.dp,
                    color = islandBorderColor,
                    shape = islandShape,
                )
                .padding(horizontal = islandInnerPaddingHorizontal, vertical = islandInnerPaddingVertical),
        ) {
            val density = LocalDensity.current
            var totalWidthPx by remember { mutableIntStateOf(0) }
            var totalHeightPx by remember { mutableIntStateOf(0) }

            val tabCount = navigationItems.size.coerceAtLeast(1)
            val tabWidthPx = if (totalWidthPx > 0) totalWidthPx.toFloat() / tabCount else 0f
            val tabWidthDp = if (totalWidthPx > 0) with(density) { tabWidthPx.toDp() } else 0.dp
            val totalHeightDp = if (totalHeightPx > 0) with(density) { totalHeightPx.toDp() } else 0.dp

            val innerPaddingHorizontal = 3.dp
            val innerPaddingVertical = 2.dp
            val indicatorShape = RoundedCornerShape(indicatorRadius)

            val indicatorWidth = (tabWidthDp - innerPaddingHorizontal * 2).coerceAtLeast(0.dp)
            val indicatorHeight = (totalHeightDp - innerPaddingVertical * 2).coerceAtLeast(0.dp)
            val maxOffsetPx = if (tabWidthPx > 0f) tabWidthPx * (tabCount - 1) else 0f

            val dragOffsetAnimatable = remember { Animatable(0f) }
            var isDragging by remember { mutableStateOf(false) }
            var isGrabbed by remember { mutableStateOf(false) }
            var grabTouchOffsetX by remember { mutableFloatStateOf(0f) }
            var dragVelocity by remember { mutableFloatStateOf(0f) }
            var lastHapticIndex by remember { mutableIntStateOf(selectedPage) }

            // Sync animation when page changes outside of active dragging (e.g. pager swipe or tab tap)
            LaunchedEffect(selectedPage, tabWidthPx) {
                if (!isDragging && tabWidthPx > 0f) {
                    val targetPx = selectedPage * tabWidthPx
                    if (abs(dragOffsetAnimatable.value - targetPx) > 0.5f) {
                        dragOffsetAnimatable.animateTo(
                            targetValue = targetPx,
                            animationSpec = spring(
                                dampingRatio = 0.74f,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                        )
                    }
                }
            }

            val currentBlobOffsetPx = dragOffsetAnimatable.value
            val currentBlobProgress = if (tabWidthPx > 0f) currentBlobOffsetPx / tabWidthPx else selectedPage.toFloat()

            // Subtle liquid squash & stretch physics during drag and rubberbanding
            val stretchX = if (isDragging && tabWidthPx > 0f) {
                val overdrag = if (currentBlobOffsetPx < 0f) {
                    -currentBlobOffsetPx / tabWidthPx
                } else if (currentBlobOffsetPx > maxOffsetPx) {
                    (currentBlobOffsetPx - maxOffsetPx) / tabWidthPx
                } else 0f
                1f + (abs(dragVelocity) / 5000f).coerceAtMost(0.15f) - (overdrag * 0.25f).coerceAtMost(0.12f)
            } else 1f

            val stretchY = if (isDragging) {
                1f / stretchX.coerceAtLeast(0.5f)
            } else 1f

            val dragGestureModifier = if (tabWidthPx > 0f) {
                Modifier.pointerInput(tabCount, tabWidthPx, selectedPage) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val capsuleLeft = dragOffsetAnimatable.value
                            val capsuleRight = capsuleLeft + tabWidthPx
                            if (offset.x in (capsuleLeft - 12f)..(capsuleRight + 12f)) {
                                isGrabbed = true
                                isDragging = true
                                dragVelocity = 0f
                                grabTouchOffsetX = (offset.x - capsuleLeft).coerceIn(0f, tabWidthPx)
                                coroutineScope.launch {
                                    dragOffsetAnimatable.stop()
                                }
                                lastHapticIndex = ((dragOffsetAnimatable.value + tabWidthPx / 2f) / tabWidthPx)
                                    .toInt().coerceIn(0, tabCount - 1)
                            } else {
                                isGrabbed = false
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (isGrabbed) {
                                change.consume()
                                val fingerX = change.position.x
                                val rawNew = fingerX - grabTouchOffsetX
                                val dampedNew = if (rawNew < 0f) {
                                    rawNew * 0.35f
                                } else if (rawNew > maxOffsetPx) {
                                    maxOffsetPx + (rawNew - maxOffsetPx) * 0.35f
                                } else {
                                    rawNew
                                }
                                dragVelocity = dragAmount * 60f
                                coroutineScope.launch {
                                    dragOffsetAnimatable.snapTo(dampedNew)
                                }
                                val hoverIndex = ((dampedNew + tabWidthPx / 2f) / tabWidthPx)
                                    .toInt().coerceIn(0, tabCount - 1)
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
                                val current = dragOffsetAnimatable.value
                                val projectedOffset = current + (dragVelocity * 0.08f)
                                val targetIndex = (projectedOffset / tabWidthPx).roundToInt().coerceIn(0, tabCount - 1)
                                coroutineScope.launch {
                                    dragOffsetAnimatable.animateTo(
                                        targetValue = targetIndex * tabWidthPx,
                                        animationSpec = spring(
                                            dampingRatio = 0.74f,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                                if (targetIndex != selectedPage) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    mainPagerState.animateToPage(targetIndex)
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
                                    dragOffsetAnimatable.animateTo(
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
            } else Modifier

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged {
                        totalWidthPx = it.width
                        totalHeightPx = it.height
                    }
                    .then(dragGestureModifier),
            ) {
                // Active tab sliding capsule indicator (Rendered directly in GPU Draw phase via graphicsLayer)
                if (totalWidthPx > 0 && totalHeightPx > 0) {
                    Box(
                        modifier = Modifier
                            .padding(start = innerPaddingHorizontal, top = innerPaddingVertical)
                            .size(width = indicatorWidth, height = indicatorHeight)
                            .graphicsLayer {
                                translationX = currentBlobOffsetPx
                                scaleX = stretchX
                                scaleY = stretchY
                            }
                            .clip(indicatorShape)
                            .background(AppTheme.colors.accent),
                    )
                }

                // Tab items (Icons & Labels are ALWAYS visible)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val activeColor = AppTheme.colors.onAccent
                    val inactiveColor = AppTheme.colors.onSurfaceVariant.copy(alpha = 0.72f)

                    navigationItems.forEachIndexed { index, item ->
                        val distance = abs(currentBlobProgress - index)
                        val activeFraction = (1f - distance).coerceIn(0f, 1f)
                        val isSelected = selectedPage == index

                        val iconScale = 1.0f + (0.08f * activeFraction)
                        val iconOffsetYPx = with(density) { (-1).dp.toPx() } * activeFraction
                        val contentColor = lerp(inactiveColor, activeColor, activeFraction)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(indicatorShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        if (!isDragging) {
                                            if (!isSelected) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                mainPagerState.animateToPage(index)
                                            } else if (index == MainNavigation.PROXY_PAGE_INDEX) {
                                                mainPagerState.animateToPage(index)
                                            }
                                        }
                                    },
                                )
                                .padding(vertical = tabVerticalPadding),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                                tint = contentColor,
                                modifier = Modifier
                                    .size(iconSize)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                        translationY = iconOffsetYPx
                                    },
                            )

                            Spacer(modifier = Modifier.height(iconTextSpacer))

                            Text(
                                text = item.label,
                                color = contentColor,
                                fontWeight = if (activeFraction > 0.5f) FontWeight.SemiBold else FontWeight.Medium,
                                fontSize = textFontSize,
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

@Composable
fun AppPager(
    padding: PaddingValues,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    val languageMode = LocalAppChromeState.current.languageMode
    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxSize(),
        userScrollEnabled = false,
        verticalAlignment = Alignment.Top,
        pageContent = { page ->
            key(languageMode, page) {
                when (page) {
                    MainNavigation.PROXY_PAGE_INDEX -> ProxyServerListPage(
                        padding = padding,
                    )

                    MainNavigation.CONFIG_PAGE_INDEX -> TrafficConfigPage(padding = padding)

                    MainNavigation.SETTINGS_PAGE_INDEX -> SettingsPage(padding = padding)
                }
            }
        },
    )
}

@Composable
private fun MainScreenBackHandler(
    mainState: MainPagerState,
    navigator: Navigator,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navigator.current() is Route.Main && navigator.backStackSize() == 1 && mainState.selectedPage != 0
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = {
            mainState.animateToPage(0)
        },
    )
}

@Stable
class MainPagerState(
    val pagerState: PagerState,
    private val coroutineScope: CoroutineScope,
) {
    var selectedPage by mutableIntStateOf(pagerState.currentPage)
        private set

    var isNavigating by mutableStateOf(false)
        private set

    var proxyPageScrollToTopRequest by mutableIntStateOf(0)
        private set

    private var navJob: Job? = null

    fun animateToPage(targetIndex: Int) {
        if (targetIndex == selectedPage) {
            if (targetIndex == MainNavigation.PROXY_PAGE_INDEX) {
                proxyPageScrollToTopRequest++
            }
            return
        }

        navJob?.cancel()

        selectedPage = targetIndex
        isNavigating = true

        navJob = coroutineScope.launch {
            val myJob = coroutineContext.job
            try {
                pagerState.animateScrollToPage(
                    page = targetIndex,
                    animationSpec = tween(durationMillis = 280, easing = EaseInOut),
                )
            } finally {
                if (navJob == myJob) {
                    isNavigating = false
                    if (pagerState.currentPage != targetIndex) {
                        selectedPage = pagerState.currentPage
                    }
                }
            }
        }
    }

    fun syncPage() {
        if (!isNavigating && selectedPage != pagerState.currentPage) {
            selectedPage = pagerState.currentPage
        }
    }
}

@Composable
fun rememberMainPagerState(
    pagerState: PagerState,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
): MainPagerState = remember(pagerState, coroutineScope) {
    MainPagerState(pagerState, coroutineScope)
}
