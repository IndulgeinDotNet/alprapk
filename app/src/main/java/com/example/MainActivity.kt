package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.PlateDetailDialog
import com.example.ui.components.RealTimeAlertBanner
import com.example.ui.screens.*
import com.example.ui.theme.AlertRed
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TechCyanPrimary
import com.example.ui.theme.TechDarkBg
import com.example.ui.theme.TechSurface
import com.example.ui.theme.TechSurfaceVariant
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScaffold(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScaffold(viewModel: MainViewModel) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val stats by viewModel.dashboardStats.collectAsStateWithLifecycle()
    val allSightings by viewModel.allSightings.collectAsStateWithLifecycle()
    val watchlist by viewModel.watchlistPlates.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val routeFilter by viewModel.selectedPlateRouteFilter.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val isAutoContinuousScanEnabled by viewModel.isAutoContinuousScanEnabled.collectAsStateWithLifecycle()
    val liveTrackingCandidate by viewModel.liveTrackingCandidate.collectAsStateWithLifecycle()
    val lastScanned by viewModel.lastScannedSighting.collectAsStateWithLifecycle()
    val activeAlert by viewModel.activeRealTimeAlert.collectAsStateWithLifecycle()
    val selectedDetail by viewModel.selectedDetailSighting.collectAsStateWithLifecycle()
    val detailHistory by viewModel.detailedSightingHistory.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(TechDarkBg),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(TechCyanPrimary)
                        )
                        Text(
                            text = "PLATETRACKER",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            letterSpacing = 2.sp,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    // Watchlist Alerts Badge
                    val activeAlertCount = stats.flaggedCountToday
                    IconButton(
                        onClick = { viewModel.setTab(AppTab.WATCHLIST) },
                        modifier = Modifier.testTag("topbar_watchlist_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (activeAlertCount > 0) {
                                    Badge(
                                        containerColor = AlertRed,
                                        contentColor = Color.White
                                    ) {
                                        Text("$activeAlertCount")
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = "Alerts",
                                tint = if (activeAlertCount > 0) AlertRed else TextSecondary
                            )
                        }
                    }

                    // Cloud Sync Button
                    IconButton(
                        onClick = { viewModel.syncWithCloud() },
                        modifier = Modifier.testTag("topbar_cloud_sync_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudSync,
                            contentDescription = "Sync Cloud",
                            tint = TechCyanPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TechDarkBg,
                    titleContentColor = TextPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = TechSurface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == AppTab.DASHBOARD,
                    onClick = { viewModel.setTab(AppTab.DASHBOARD) },
                    icon = {
                        Icon(
                            if (currentTab == AppTab.DASHBOARD) Icons.Filled.Dashboard else Icons.Outlined.Dashboard,
                            contentDescription = "Dashboard"
                        )
                    },
                    label = { Text("Dashboard", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF381E72),
                        selectedTextColor = TechCyanPrimary,
                        indicatorColor = TechCyanPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("tab_dashboard")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.SCANNER,
                    onClick = { viewModel.setTab(AppTab.SCANNER) },
                    icon = {
                        Icon(
                            if (currentTab == AppTab.SCANNER) Icons.Filled.CameraAlt else Icons.Outlined.CameraAlt,
                            contentDescription = "Live Scan"
                        )
                    },
                    label = { Text("Live Scan", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF381E72),
                        selectedTextColor = TechCyanPrimary,
                        indicatorColor = TechCyanPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("tab_scanner")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.DATABASE,
                    onClick = { viewModel.setTab(AppTab.DATABASE) },
                    icon = {
                        Icon(
                            if (currentTab == AppTab.DATABASE) Icons.Filled.Search else Icons.Outlined.Search,
                            contentDescription = "Database"
                        )
                    },
                    label = { Text("Search DB", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF381E72),
                        selectedTextColor = TechCyanPrimary,
                        indicatorColor = TechCyanPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("tab_database")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.ROUTE_MAP,
                    onClick = { viewModel.setTab(AppTab.ROUTE_MAP) },
                    icon = {
                        Icon(
                            if (currentTab == AppTab.ROUTE_MAP) Icons.Filled.Map else Icons.Outlined.Map,
                            contentDescription = "Route Map"
                        )
                    },
                    label = { Text("Route Map", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF381E72),
                        selectedTextColor = TechCyanPrimary,
                        indicatorColor = TechCyanPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("tab_route_map")
                )

                NavigationBarItem(
                    selected = currentTab == AppTab.WATCHLIST,
                    onClick = { viewModel.setTab(AppTab.WATCHLIST) },
                    icon = {
                        Icon(
                            if (currentTab == AppTab.WATCHLIST) Icons.Filled.Shield else Icons.Outlined.Shield,
                            contentDescription = "Watchlist"
                        )
                    },
                    label = { Text("Watchlist", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF381E72),
                        selectedTextColor = TechCyanPrimary,
                        indicatorColor = TechCyanPrimary,
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextSecondary
                    ),
                    modifier = Modifier.testTag("tab_watchlist")
                )
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                snackbar = { data ->
                    Snackbar(
                        snackbarData = data,
                        containerColor = TechSurfaceVariant,
                        contentColor = TextPrimary,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Main Screen by Tab
            when (currentTab) {
                AppTab.DASHBOARD -> {
                    DashboardScreen(
                        stats = stats,
                        recentSightings = allSightings,
                        onOpenScanner = { viewModel.setTab(AppTab.SCANNER) },
                        onTestScan = { viewModel.setTab(AppTab.SCANNER) },
                        onSyncCloud = { viewModel.syncWithCloud() },
                        onSelectSighting = { viewModel.openSightingDetail(it) },
                        onNavigateToRoute = { viewModel.setTab(AppTab.ROUTE_MAP) },
                        onNavigateToWatchlist = { viewModel.setTab(AppTab.WATCHLIST) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AppTab.SCANNER -> {
                    ScannerScreen(
                        isScanning = isScanning,
                        isAutoContinuousScanEnabled = isAutoContinuousScanEnabled,
                        lastScannedResult = lastScanned,
                        liveTrackingCandidate = liveTrackingCandidate,
                        offlineScanner = viewModel.offlineScanner,
                        onToggleAutoScan = { viewModel.toggleAutoContinuousScan() },
                        onCaptureImage = { viewModel.scanImage(it) },
                        onAutoPlateCaptured = { candidate, bitmap -> viewModel.onAutoPlateCaptured(candidate, bitmap) },
                        onUpdateLiveCandidate = { viewModel.updateLiveCandidate(it) },
                        onViewSightingDetail = { viewModel.openSightingDetail(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AppTab.DATABASE -> {
                    DatabaseScreen(
                        sightings = allSightings,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { viewModel.setSearchQuery(it) },
                        onSelectSighting = { viewModel.openSightingDetail(it) },
                        onDeleteSighting = { viewModel.deleteSighting(it) },
                        onClearAllSightings = { viewModel.clearAllSightings() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AppTab.ROUTE_MAP -> {
                    RouteMapScreen(
                        sightings = allSightings,
                        selectedPlateFilter = routeFilter,
                        onSelectPlateFilter = { viewModel.setRouteFilter(it) },
                        onSelectSighting = { viewModel.openSightingDetail(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                AppTab.WATCHLIST -> {
                    WatchlistScreen(
                        watchlist = watchlist,
                        onAddPlate = { plate, sev, reason, desc, caseNum ->
                            viewModel.addWatchlistPlate(plate, sev, reason, desc, caseNum)
                        },
                        onToggleActive = { viewModel.toggleWatchlistActive(it) },
                        onDeletePlate = { viewModel.deleteWatchlistPlate(it) },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Real-Time Floating Alert Banner (drops down from top when match occurs)
            RealTimeAlertBanner(
                sighting = activeAlert,
                onDismiss = { viewModel.dismissRealTimeAlert() },
                onViewDetail = { sighting ->
                    viewModel.dismissRealTimeAlert()
                    viewModel.openSightingDetail(sighting)
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            // Full Sighting Dossier Dialog
            selectedDetail?.let { sighting ->
                PlateDetailDialog(
                    sighting = sighting,
                    historySightings = detailHistory,
                    onDismiss = { viewModel.closeSightingDetail() },
                    onToggleWatchlist = { viewModel.toggleFlagFromSighting(it) },
                    onViewOnMap = { plateNumber ->
                        viewModel.closeSightingDetail()
                        viewModel.setRouteFilter(plateNumber)
                        viewModel.setTab(AppTab.ROUTE_MAP)
                    },
                    onEditPlateNumber = { sighting, newPlateNumber ->
                        viewModel.editSightingPlateNumber(sighting, newPlateNumber)
                    }
                )
            }
        }
    }
}
