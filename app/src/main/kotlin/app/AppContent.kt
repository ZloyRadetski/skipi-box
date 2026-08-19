// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
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
import features.settings.SkipiUrlSchemesPage
import features.settings.SubscriptionPingSettingsPage
import features.settings.SubscriptionUserAgentsPage
import features.subscription.SubscriptionGroupListPage
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDisplayMode
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
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

@Composable
fun AppContent(
    padding: PaddingValues,
) {
    val languageMode = LocalAppChromeState.current.languageMode
    val pagerState = rememberPagerState(pageCount = { MainNavigation.NAVIGATION_ITEMS_COUNT })
    val mainPagerState = rememberMainPagerState(pagerState)
    LaunchedEffect(mainPagerState.pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    val backStack = remember { mutableStateListOf<NavKey>().apply { add(Route.Main) } }
    val navigator = remember { Navigator(backStack) }

    MainScreenBackHandler(mainPagerState, navigator)

    val isWideScreen = shouldShowSplitPane()

    CompositionLocalProvider(
        LocalNavigator provides navigator,
        LocalIsWideScreen provides isWideScreen,
        LocalProxyPageScrollToTopRequest provides mainPagerState.proxyPageScrollToTopRequest,
    ) {
        val entryProvider = remember(backStack, languageMode) {
            entryProvider<NavKey> {
                entry<Route.Main> {
                    key(languageMode) {
                        Home(
                            padding = padding,
                            mainPagerState = mainPagerState,
                        )
                    }
                }
                entry<Route.SettingsAppearance> {
                    key(languageMode) {
                        SettingsAppearancePage(padding = padding)
                    }
                }
                entry<Route.SettingsVpn> {
                    key(languageMode) {
                        SettingsVpnPage(padding = padding)
                    }
                }
                entry<Route.SettingsSubscriptions> {
                    key(languageMode) {
                        SettingsSubscriptionsPage(padding = padding)
                    }
                }
                entry<Route.SettingsIntegration> {
                    key(languageMode) {
                        SettingsIntegrationPage(padding = padding)
                    }
                }
                entry<Route.SettingsLogs> {
                    key(languageMode) {
                        SettingsLogsPage(padding = padding)
                    }
                }
                entry<Route.SettingsBackupReset> {
                    key(languageMode) {
                        SettingsBackupResetPage(padding = padding)
                    }
                }
                entry<Route.About> {
                    key(languageMode) {
                        AboutPage(padding = padding)
                    }
                }
                entry<Route.License> {
                    key(languageMode) {
                        LicensePage(padding = padding)
                    }
                }
                entry<Route.CoreLogs> {
                    key(languageMode) {
                        CoreLogsPage(padding = padding)
                    }
                }
                entry<Route.AccessLogs> {
                    key(languageMode) {
                        AccessLogsPage(padding = padding)
                    }
                }
                entry<Route.LogcatLogs> {
                    key(languageMode) {
                        LogcatLogsPage(padding = padding)
                    }
                }
                entry<Route.ResourceManagement> { route ->
                    key(languageMode) {
                        ResourceManagementPage(
                            padding = padding,
                            trafficConfigId = route.trafficConfigId,
                        )
                    }
                }
                entry<Route.TrafficConfigEditor> { route ->
                    key(languageMode) {
                        TrafficConfigEditorPage(
                            padding = padding,
                            trafficConfigId = route.trafficConfigId,
                        )
                    }
                }
                entry<Route.TrafficConfigRawEditor> { route ->
                    key(languageMode) {
                        TrafficConfigRawEditorPage(
                            padding = padding,
                            trafficConfigId = route.trafficConfigId,
                        )
                    }
                }
                entry<Route.TrafficConfigSection> { route ->
                    key(languageMode) {
                        TrafficConfigSectionPage(
                            padding = padding,
                            trafficConfigId = route.trafficConfigId,
                            section = route.section,
                        )
                    }
                }
                entry<Route.TrafficConfigRuleEditor> { route ->
                    key(languageMode) {
                        TrafficConfigRuleEditorPage(
                            padding = padding,
                            trafficConfigId = route.trafficConfigId,
                            ruleLineNumber = route.ruleLineNumber,
                        )
                    }
                }
                entry<Route.SubscriptionGroupList> {
                    key(languageMode) {
                        SubscriptionGroupListPage(padding = padding)
                    }
                }
                entry<Route.ProxyAppList> { route ->
                    key(languageMode) {
                        ProxyAppListPage(
                            padding = padding,
                            trafficConfigId = route.trafficConfigId,
                        )
                    }
                }
                entry<Route.LocalProxySettings> {
                    key(languageMode) {
                        LocalProxySettingsPage(padding = padding)
                    }
                }
                entry<Route.SubscriptionPingSettings> {
                    key(languageMode) {
                        SubscriptionPingSettingsPage(padding = padding)
                    }
                }
                entry<Route.SubscriptionUserAgents> {
                    key(languageMode) {
                        SubscriptionUserAgentsPage(padding = padding)
                    }
                }
                entry<Route.SkipiUrlSchemes> {
                    key(languageMode) {
                        SkipiUrlSchemesPage(padding = padding)
                    }
                }
                entry<Route.ProxyServerEditor> {
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
                entry<Route.StrategyGroupMemberSelector> { route ->
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
                entry<Route.RouteOutboundSelector> { route ->
                    key(languageMode) {
                        RouteOutboundSelectorPage(
                            padding = padding,
                            selectedTag = route.selectedTag,
                            resultKey = route.resultKey,
                            trafficConfigId = route.trafficConfigId,
                        )
                    }
                }
                entry<Route.RoutingRuleEditor> { route ->
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
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
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
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background),
        bottomBar = {
            NavigationBar(
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
private fun NavigationBar(
    navigationItems: List<NavigationItem>,
    mainPagerState: MainPagerState,
    modifier: Modifier = Modifier,
) {
    val page = mainPagerState.selectedPage
    val navShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val accentColor = AppTheme.colors.accent
    Box(
        modifier = Modifier
            .clip(navShape)
            .background(accentColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
            .then(modifier),
    ) {
        NavigationBar(
            modifier = Modifier.clip(navShape),
            color = accentColor,
            mode = NavigationBarDisplayMode.IconAndText,
        ) {
            navigationItems.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = page == index,
                    onClick = { mainPagerState.animateToPage(index) },
                    icon = item.icon,
                    label = item.label,
                )
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
            .fillMaxSize()
            .background(AppTheme.colors.background),
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
                pagerState.scroll(MutatePriority.UserInput) {
                    val distance = abs(targetIndex - pagerState.currentPage).coerceAtLeast(2)
                    val duration = 100 * distance + 100
                    val layoutInfo = pagerState.layoutInfo
                    val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
                    val currentDistanceInPages =
                        targetIndex - pagerState.currentPage - pagerState.currentPageOffsetFraction
                    val scrollPixels = currentDistanceInPages * pageSize

                    var previousValue = 0f
                    animate(
                        initialValue = 0f,
                        targetValue = scrollPixels,
                        animationSpec = tween(easing = EaseInOut, durationMillis = duration),
                    ) { currentValue, _ ->
                        previousValue += scrollBy(currentValue - previousValue)
                    }
                }

                if (pagerState.currentPage != targetIndex) {
                    pagerState.scrollToPage(targetIndex)
                }
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
