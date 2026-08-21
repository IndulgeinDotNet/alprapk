package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlateSighting
import com.example.data.model.RoutePoint
import com.example.ui.theme.*
import kotlin.math.max
import kotlin.math.min

private data class MapBounds(
    val centerLat: Double,
    val centerLng: Double,
    val latSpan: Double,
    val lngSpan: Double
)

@Composable
fun RouteMapVisualizer(
    sightings: List<PlateSighting>,
    selectedPlateFilter: String? = null,
    onSelectSighting: (PlateSighting) -> Unit,
    modifier: Modifier = Modifier
) {
    // Filter sightings if a specific plate is chosen
    val filteredSightings = remember(sightings, selectedPlateFilter) {
        if (selectedPlateFilter.isNullOrBlank()) {
            sightings
        } else {
            sightings.filter { it.plateNumber.equals(selectedPlateFilter, ignoreCase = true) }
        }.sortedBy { it.timestamp }
    }

    var selectedSighting by remember { mutableStateOf<PlateSighting?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    // Frame the map around wherever the actual sightings are, instead of a fixed location -
    // otherwise sightings recorded outside a hardcoded default city would plot off-screen.
    val bounds = remember(sightings) {
        val validPoints = sightings.filter { it.latitude != 0.0 || it.longitude != 0.0 }
        if (validPoints.isEmpty()) {
            MapBounds(centerLat = 45.5152, centerLng = -122.6784, latSpan = 0.12, lngSpan = 0.14)
        } else {
            val lats = validPoints.map { it.latitude }
            val lngs = validPoints.map { it.longitude }
            val minLat = lats.min()
            val maxLat = lats.max()
            val minLng = lngs.min()
            val maxLng = lngs.max()
            MapBounds(
                centerLat = (minLat + maxLat) / 2,
                centerLng = (minLng + maxLng) / 2,
                latSpan = max(0.02, (maxLat - minLat) * 1.6),
                lngSpan = max(0.02, (maxLng - minLng) * 1.6)
            )
        }
    }
    val defaultCenterLat = bounds.centerLat
    val defaultCenterLng = bounds.centerLng
    val latSpan = bounds.latSpan
    val lngSpan = bounds.lngSpan

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(16.dp))
            .background(TechDarkBg)
            .border(BorderStroke(1.dp, TechCardBorder), RoundedCornerShape(16.dp))
            .testTag("route_map_visualizer")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(0.7f, 3.5f)
                        panOffset = Offset(
                            x = panOffset.x + pan.x,
                            y = panOffset.y + pan.y
                        )
                    }
                }
                .pointerInput(filteredSightings) {
                    detectTapGestures { tapOffset ->
                        // Hit-test on pins
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        var clicked: PlateSighting? = null

                        for (sighting in filteredSightings) {
                            val pinPos = latLngToCanvasOffset(
                                sighting.latitude,
                                sighting.longitude,
                                defaultCenterLat,
                                defaultCenterLng,
                                latSpan,
                                lngSpan,
                                w,
                                h,
                                zoomScale,
                                panOffset
                            )
                            val dist = (tapOffset - pinPos).getDistance()
                            if (dist < 32f) {
                                clicked = sighting
                                break
                            }
                        }
                        selectedSighting = clicked
                    }
                }
        ) {
            val width = size.width
            val height = size.height

            // 1. Draw Map Grid / Radar concentric circles & road lines
            drawMapGrid(width, height, zoomScale, panOffset)

            // 2. Draw arterial road vectors
            drawArterialRoads(width, height, zoomScale, panOffset)

            // 3. If tracking a single vehicle, draw connected trajectory lines with glowing gradient
            if (filteredSightings.size > 1) {
                val trajectoryPath = Path()
                var first = true

                filteredSightings.forEach { sighting ->
                    val pt = latLngToCanvasOffset(
                        sighting.latitude,
                        sighting.longitude,
                        defaultCenterLat,
                        defaultCenterLng,
                        latSpan,
                        lngSpan,
                        width,
                        height,
                        zoomScale,
                        panOffset
                    )
                    if (first) {
                        trajectoryPath.moveTo(pt.x, pt.y)
                        first = false
                    } else {
                        trajectoryPath.lineTo(pt.x, pt.y)
                    }
                }

                // Glowing outer line
                drawPath(
                    path = trajectoryPath,
                    color = if (selectedPlateFilter != null) TechCyanPrimary.copy(alpha = 0.35f) else TechBlueAccent.copy(alpha = 0.25f),
                    style = Stroke(
                        width = 8f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                    )
                )
                // Core line
                drawPath(
                    path = trajectoryPath,
                    color = if (selectedPlateFilter != null) TechCyanPrimary else TechBlueAccent,
                    style = Stroke(width = 3.5f)
                )
            }

            // 4. Draw Waypoint Pins for sightings
            filteredSightings.forEachIndexed { index, sighting ->
                val pinPos = latLngToCanvasOffset(
                    sighting.latitude,
                    sighting.longitude,
                    defaultCenterLat,
                    defaultCenterLng,
                    latSpan,
                    lngSpan,
                    width,
                    height,
                    zoomScale,
                    panOffset
                )

                val isFlagged = sighting.isFlagged
                val isSelected = selectedSighting?.id == sighting.id

                val pinColor = when {
                    isFlagged -> AlertRed
                    isSelected -> TechCyanPrimary
                    else -> TechBlueAccent
                }

                // Hotspot pulse
                drawCircle(
                    color = pinColor.copy(alpha = if (isSelected) 0.45f else 0.25f),
                    radius = if (isSelected) 24f else if (isFlagged) 18f else 14f,
                    center = pinPos
                )

                // Pin inner core
                drawCircle(
                    color = pinColor,
                    radius = if (isSelected) 10f else if (isFlagged) 8f else 6f,
                    center = pinPos
                )

                // Pin center white dot
                drawCircle(
                    color = Color.White,
                    radius = 3.5f,
                    center = pinPos
                )

                // Sequence badge if in route mode
                if (selectedPlateFilter != null && filteredSightings.size > 1) {
                    drawCircle(
                        color = Color.Black.copy(alpha = 0.7f),
                        radius = 8f,
                        center = Offset(pinPos.x + 12f, pinPos.y - 12f)
                    )
                }
            }
        }

        // Map HUD Overlay: Controls and Legend
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = TechSurface.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, TechCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = null,
                        tint = TechCyanPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (selectedPlateFilter != null) "$selectedPlateFilter (${filteredSightings.size} stops)" else "${filteredSightings.size} sightings",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Map Control buttons (Zoom In / Out / Reset)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { zoomScale = (zoomScale * 1.25f).coerceAtMost(3.5f) },
                containerColor = TechSurfaceVariant,
                contentColor = TechCyanPrimary,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
            }
            SmallFloatingActionButton(
                onClick = { zoomScale = (zoomScale / 1.25f).coerceAtLeast(0.7f) },
                containerColor = TechSurfaceVariant,
                contentColor = TechCyanPrimary,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
            }
            SmallFloatingActionButton(
                onClick = {
                    zoomScale = 1f
                    panOffset = Offset.Zero
                },
                containerColor = TechSurfaceVariant,
                contentColor = TextSecondary,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.CenterFocusStrong, contentDescription = "Reset Center", modifier = Modifier.size(18.dp))
            }
        }

        // Bottom Selected Sighting Preview Card
        AnimatedVisibility(
            visible = selectedSighting != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
        ) {
            selectedSighting?.let { s ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSighting(s) },
                    shape = RoundedCornerShape(12.dp),
                    color = TechSurface,
                    border = BorderStroke(1.5.dp, if (s.isFlagged) AlertRed else TechCyanPrimary),
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PlateBadge(plateNumber = s.plateNumber, stateOrRegion = s.stateOrRegion)

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                        ) {
                            Text(
                                text = s.locationName,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "${s.vehicleColor} ${s.vehicleMake} • ${formatTimeOnly(s.timestamp)} • ${s.spotSpeedMph} mph",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            if (s.isFlagged) {
                                Text(
                                    text = s.flagReason ?: "Flagged",
                                    color = AlertRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        IconButton(
                            onClick = { onSelectSighting(s) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Details",
                                tint = TechCyanPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun latLngToCanvasOffset(
    lat: Double,
    lng: Double,
    centerLat: Double,
    centerLng: Double,
    latSpan: Double,
    lngSpan: Double,
    canvasW: Float,
    canvasH: Float,
    zoom: Float,
    pan: Offset
): Offset {
    val normX = ((lng - (centerLng - lngSpan / 2)) / lngSpan).toFloat()
    val normY = (1f - ((lat - (centerLat - latSpan / 2)) / latSpan)).toFloat()

    val centerX = canvasW / 2
    val centerY = canvasH / 2

    val rawX = normX * canvasW
    val rawY = normY * canvasH

    val zoomedX = centerX + (rawX - centerX) * zoom + pan.x
    val zoomedY = centerY + (rawY - centerY) * zoom + pan.y

    return Offset(zoomedX, zoomedY)
}

private fun DrawScope.drawMapGrid(width: Float, height: Float, zoom: Float, pan: Offset) {
    val gridStep = 45f * zoom
    val startX = (pan.x % gridStep)
    val startY = (pan.y % gridStep)

    var x = startX
    while (x < width) {
        drawLine(
            color = TechSurfaceVariant.copy(alpha = 0.45f),
            start = Offset(x, 0f),
            end = Offset(x, height),
            strokeWidth = 1f
        )
        x += gridStep
    }

    var y = startY
    while (y < height) {
        drawLine(
            color = TechSurfaceVariant.copy(alpha = 0.45f),
            start = Offset(0f, y),
            end = Offset(width, y),
            strokeWidth = 1f
        )
        y += gridStep
    }
}

private fun DrawScope.drawArterialRoads(width: Float, height: Float, zoom: Float, pan: Offset) {
    val cx = width / 2 + pan.x
    val cy = height / 2 + pan.y

    val roadColor = TechSurfaceVariant.copy(alpha = 0.8f)

    // Diagonal arterial roads representing urban grid
    drawLine(
        color = roadColor,
        start = Offset(cx - 300f * zoom, cy - 250f * zoom),
        end = Offset(cx + 350f * zoom, cy + 250f * zoom),
        strokeWidth = 3f * zoom
    )

    drawLine(
        color = roadColor,
        start = Offset(cx - 200f * zoom, cy + 200f * zoom),
        end = Offset(cx + 300f * zoom, cy - 200f * zoom),
        strokeWidth = 2.5f * zoom
    )

    // Cross thoroughfares
    drawLine(
        color = roadColor,
        start = Offset(0f, cy - 80f * zoom),
        end = Offset(width, cy - 80f * zoom),
        strokeWidth = 2f * zoom
    )
    drawLine(
        color = roadColor,
        start = Offset(0f, cy + 90f * zoom),
        end = Offset(width, cy + 90f * zoom),
        strokeWidth = 2f * zoom
    )
    drawLine(
        color = roadColor,
        start = Offset(cx - 90f * zoom, 0f),
        end = Offset(cx - 90f * zoom, height),
        strokeWidth = 2f * zoom
    )
    drawLine(
        color = roadColor,
        start = Offset(cx + 110f * zoom, 0f),
        end = Offset(cx + 110f * zoom, height),
        strokeWidth = 2f * zoom
    )
}
