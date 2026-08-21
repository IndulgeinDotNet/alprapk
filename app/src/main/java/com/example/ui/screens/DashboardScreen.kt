package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.DashboardStats
import com.example.data.model.PlateSighting
import com.example.ui.components.PlateBadge
import com.example.ui.components.SeverityBadge
import com.example.ui.components.formatTimeOnly
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.*
import java.io.File

@Composable
fun DashboardScreen(
    stats: DashboardStats,
    recentSightings: List<PlateSighting>,
    onOpenScanner: () -> Unit,
    onTestScan: () -> Unit,
    onSyncCloud: () -> Unit,
    onSelectSighting: (PlateSighting) -> Unit,
    onNavigateToRoute: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(TechDarkBg)
            .padding(horizontal = 16.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
    ) {
        // 1. Hero Banner with AI Status & Cloud Bridge
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(16.dp)),
                border = BorderStroke(1.dp, TechCyanPrimary.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.hero_banner_scanner_1787270794707),
                        contentDescription = "Scanner Hero",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Dark tech overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        TechDarkBg.copy(alpha = 0.92f),
                                        TechDarkBg.copy(alpha = 0.65f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = TechSurface.copy(alpha = 0.9f),
                                border = BorderStroke(1.dp, AlertGreen)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(AlertGreen)
                                    )
                                    Text(
                                        text = "Scanner active",
                                        color = AlertGreen,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Cloud Sync status pill
                            Surface(
                                modifier = Modifier.clickable { onSyncCloud() },
                                shape = RoundedCornerShape(20.dp),
                                color = TechSurface.copy(alpha = 0.9f),
                                border = BorderStroke(1.dp, TechCyanPrimary)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudSync,
                                        contentDescription = "Sync",
                                        tint = TechCyanPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${stats.syncedCount} synced",
                                        color = TechCyanPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Column {
                            Text(
                                text = "Plate Scanner",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Scan, locate, and track vehicle plates",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // 2. Executive KPI Cards Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = "Total Scans",
                        value = "${stats.totalScans}",
                        subtitle = "All logged sightings",
                        icon = Icons.Default.DocumentScanner,
                        color = TechCyanPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Unique Vehicles",
                        value = "${stats.uniquePlatesCount}",
                        subtitle = "Distinct plates seen",
                        icon = Icons.Default.DirectionsCar,
                        color = TechBlueAccent,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = "Flagged Today",
                        value = "${stats.flaggedCountToday}",
                        subtitle = "Watchlist matches",
                        icon = Icons.Default.Warning,
                        color = AlertRed,
                        isAlert = stats.flaggedCountToday > 0,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Active Watchlist",
                        value = "${stats.totalFlaggedActive}",
                        subtitle = "Flagged plates",
                        icon = Icons.Default.Shield,
                        color = AlertOrange,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 3. Quick Action Launchers
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenScanner,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(52.dp)
                        .testTag("launch_camera_scanner_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TechCyanPrimary,
                        contentColor = Color(0xFF381E72)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Scanner", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                OutlinedButton(
                    onClick = onNavigateToWatchlist,
                    modifier = Modifier
                        .weight(0.9f)
                        .height(52.dp)
                        .testTag("quick_watchlist_button"),
                    border = BorderStroke(1.5.dp, TechCyanDim),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TechCyanPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Watchlist", fontSize = 13.sp)
                }
            }
        }

        // 4. Hotspot Patrol Corridor Info Card
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = TechSurface,
                border = BorderStroke(1.dp, TechCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(TechSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = TechCyanPrimary)
                        }
                        Column {
                            Text(
                                text = "MOST SCANNED LOCATION",
                                color = TechCyanPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = stats.topLocation,
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onNavigateToRoute,
                        border = BorderStroke(1.dp, TechCardBorder),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("View Map", color = TextPrimary, fontSize = 11.sp)
                    }
                }
            }
        }

        // 5. Recent Sightings Stream Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = TechCyanPrimary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "RECENT SIGHTINGS",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = "${recentSightings.size} vehicles",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        // 6. Recent Sighting Item Cards
        if (recentSightings.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = TechSurface,
                    border = BorderStroke(1.dp, TechCardBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            tint = TechCyanPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No sightings yet",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Open the Scanner tab and point the camera at a vehicle to start logging plates.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(recentSightings.take(8)) { sighting ->
                SightingFeedCard(
                    sighting = sighting,
                    onClick = { onSelectSighting(sighting) }
                )
            }
        }
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false
) {
    Surface(
        modifier = modifier.height(105.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (isAlert) AlertRedBg else TechSurface,
        border = BorderStroke(1.dp, if (isAlert) AlertRed else TechCardBorder),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = value,
                color = if (isAlert) AlertRed else TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )

            Text(
                text = subtitle,
                color = if (isAlert) AlertRed.copy(alpha = 0.8f) else TextMuted,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun SightingFeedCard(
    sighting: PlateSighting,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("sighting_feed_card_${sighting.plateNumber}"),
        shape = RoundedCornerShape(12.dp),
        color = TechSurface,
        border = BorderStroke(1.dp, if (sighting.isFlagged) AlertRed else TechCardBorder),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            PlateBadge(
                plateNumber = sighting.plateNumber,
                stateOrRegion = sighting.stateOrRegion
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${sighting.vehicleColor} ${sighting.vehicleMake} ${sighting.vehicleModel}",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = sighting.locationName,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatTimeOnly(sighting.timestamp),
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "• ${sighting.spotSpeedMph} mph",
                        color = TextMuted,
                        fontSize = 10.sp
                    )
                    if (sighting.isFlagged) {
                        Text(
                            text = sighting.flagReason ?: "Flagged",
                            color = AlertRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(
                onClick = onClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Details",
                    tint = if (sighting.isFlagged) AlertRed else TechCyanPrimary
                )
            }
        }
    }
}
