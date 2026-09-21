// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
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
import features.settings.SettingsGeneralPage
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
import androidx.compose.ui.graphics.Color
import app.skipi.ui.navigation.SkipiMainDestination
import app.skipi.ui.navigation.SkipiExpressiveNavigationBar
import app.skipi.ui.navigation.SkipiExpressiveNavigationColors
import app.skipi.ui.navigation.SkipiNavigationBarSize
import app.skipi.ui.navigation.SkipiMainPager
import app.skipi.ui.navigation.SkipiMainPagerState
import app.skipi.ui.navigation.SkipiNavigationItem
import app.skipi.ui.navigation.rememberSkipiMainPagerState
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Layers
import top.yukonga.miuix.kmp.icon.extended.MindMap
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.layout.pageWindowPadding
import ui.layout.shouldShowSplitPane
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private object MainNavigation {
    @Composable
    fun navigationItems(): List<SkipiNavigationItem> {
        val languageMode = LocalAppChromeState.current.languageMode
        val proxy = stringResource(R.string.nav_proxy)
        val configs = stringResource(R.string.nav_configs)
        val settings = stringResource(R.string.nav_settings)

        return remember(languageMode, proxy, configs, settings) {
            listOf(
                SkipiNavigationItem(SkipiMainDestination.Proxy, proxy, MiuixIcons.Layers),
                SkipiNavigationItem(SkipiMainDestination.Configs, configs, MiuixIcons.MindMap),
                SkipiNavigationItem(SkipiMainDestination.Settings, settings, MiuixIcons.Settings),
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
    val pagerState = rememberPagerState(pageCount = { SkipiMainDestination.entries.size })
    val mainPagerState = rememberSkipiMainPagerState(pagerState)
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
                appEntry<Route.SettingsGeneral> {
                    key(languageMode) {
                        SettingsGeneralPage(padding = padding)
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
    mainPagerState: SkipiMainPagerState,
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
    navigationItems: List<SkipiNavigationItem>,
    layoutDirection: LayoutDirection,
    mainPagerState: SkipiMainPagerState,
) {
    val page = mainPagerState.selectedPage
    Row {
        NavigationRail(
            modifier = Modifier.background(MiuixTheme.colorScheme.surface),
        ) {
            navigationItems.forEachIndexed { index, item ->
                NavigationRailItem(
                    selected = page == item.destination.pageIndex,
                    onClick = { mainPagerState.animateTo(item.destination) },
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
    navigationItems: List<SkipiNavigationItem>,
    padding: PaddingValues,
    mainPagerState: SkipiMainPagerState,
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
    navigationItems: List<SkipiNavigationItem>,
    mainPagerState: SkipiMainPagerState,
    modifier: Modifier = Modifier,
) {
    val bottomBarSize = LocalAppChromeState.current.bottomBarSize
    val size = when (bottomBarSize) {
        BottomBarSizeSmall -> SkipiNavigationBarSize.Small
        BottomBarSizeMedium -> SkipiNavigationBarSize.Medium
        else -> SkipiNavigationBarSize.Large
    }
    SkipiExpressiveNavigationBar(
        items = navigationItems,
        mainPagerState = mainPagerState,
        colors = SkipiExpressiveNavigationColors(
            surface = AppTheme.colors.surface,
            accent = AppTheme.colors.accent,
            onAccent = AppTheme.colors.onAccent,
            onSurfaceVariant = AppTheme.colors.onSurfaceVariant,
            isDark = AppTheme.colors.isDark,
        ),
        size = size,
        modifier = modifier,
    )
}

@Composable
fun AppPager(
    padding: PaddingValues,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    val languageMode = LocalAppChromeState.current.languageMode
    SkipiMainPager(
        pagerState = pagerState,
        modifier = modifier
            .fillMaxSize(),
        userScrollEnabled = false,
    ) { destination ->
        key(languageMode, destination) {
            when (destination) {
                SkipiMainDestination.Proxy -> ProxyServerListPage(
                    padding = padding,
                )

                SkipiMainDestination.Configs -> TrafficConfigPage(padding = padding)

                SkipiMainDestination.Settings -> SettingsPage(padding = padding)
            }
        }
    }
}

@Composable
private fun MainScreenBackHandler(
    mainState: SkipiMainPagerState,
    navigator: Navigator,
) {
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf {
            navigator.current() is Route.Main &&
                navigator.backStackSize() == 1 &&
                mainState.selectedPage != SkipiMainDestination.Proxy.pageIndex
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = {
            mainState.animateTo(SkipiMainDestination.Proxy)
        },
    )
}
