package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.PlateSighting
import com.example.ui.theme.*
import java.io.File

@Composable
fun PlateDetailDialog(
    sighting: PlateSighting,
    historySightings: List<PlateSighting>,
    onDismiss: () -> Unit,
    onToggleWatchlist: (PlateSighting) -> Unit,
    onViewOnMap: (String) -> Unit,
    onEditPlateNumber: (PlateSighting, String) -> Unit = { _, _ -> }
) {
    var showEditDialog by remember(sighting.id) { mutableStateOf(false) }
    var showFullImage by remember(sighting.id) { mutableStateOf(false) }

    if (showEditDialog) {
        EditPlateNumberDialog(
            currentPlateNumber = sighting.plateNumber,
            onDismiss = { showEditDialog = false },
            onConfirm = { corrected ->
                onEditPlateNumber(sighting, corrected)
                showEditDialog = false
            }
        )
    }

    if (showFullImage && !sighting.snapshotUri.isNullOrBlank()) {
        FullImageViewer(
            filePath = sighting.snapshotUri,
            onDismiss = { showFullImage = false }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .testTag("plate_detail_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = TechSurface,
            border = BorderStroke(1.5.dp, if (sighting.isFlagged) AlertRed else TechCyanPrimary),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "VEHICLE DETAILS",
                            color = TechCyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "Plate: ${sighting.plateNumber}",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier.testTag("edit_plate_number_button")
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit plate number", tint = TechCyanPrimary)
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_detail_dialog_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Plate Badge & Alert Status
                    item {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = TechDarkBg,
                            border = BorderStroke(1.dp, TechCardBorder)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                PlateBadge(
                                    plateNumber = sighting.plateNumber,
                                    stateOrRegion = sighting.stateOrRegion,
                                    isLarge = true
                                )

                                Column(
                                    modifier = Modifier.padding(start = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    SeverityBadge(sighting.alertSeverity)
                                    Text(
                                        text = if (sighting.isFlagged) (sighting.flagReason ?: "Flagged on watchlist") else "No flags",
                                        color = if (sighting.isFlagged) AlertRed else AlertGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Seen ${historySightings.size} times",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // 2. Car Snapshot Context
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = TechDarkBg,
                            border = BorderStroke(1.dp, TechCardBorder)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = TechCyanPrimary, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = "SNAPSHOT",
                                            color = TechCyanPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "OCR Confidence: ${(sighting.confidenceScore * 100).toInt()}%",
                                        color = if (sighting.confidenceScore >= 0.9f) AlertGreen else AlertOrange,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(TechSurfaceVariant)
                                        .let {
                                            if (!sighting.snapshotUri.isNullOrBlank()) {
                                                it.clickable { showFullImage = true }
                                            } else it
                                        }
                                        .testTag("snapshot_image")
                                ) {
                                    val uri = sighting.snapshotUri
                                    if (!uri.isNullOrBlank() && File(uri).exists()) {
                                        AsyncImage(
                                            model = File(uri),
                                            contentDescription = "Captured photo - tap to view full size",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Surface(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color.Black.copy(alpha = 0.6f)
                                        ) {
                                            Icon(
                                                Icons.Default.ZoomIn,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .padding(4.dp)
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DirectionsCar,
                                                contentDescription = "Vehicle",
                                                tint = TechCyanPrimary,
                                                modifier = Modifier.size(64.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Vehicle Specifications & Geotag telemetry
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = TechDarkBg,
                            border = BorderStroke(1.dp, TechCardBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "DETAILS",
                                    color = TechCyanPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                DossierRow("Vehicle", "${sighting.vehicleColor} ${sighting.vehicleMake} ${sighting.vehicleModel} (${sighting.vehicleType})")
                                DossierRow("Time", formatTimestamp(sighting.timestamp))
                                DossierRow("Location", sighting.locationName)
                                DossierRow("Coordinates", "${sighting.latitude}, ${sighting.longitude}")
                                if (sighting.headingDegrees >= 0f) {
                                    DossierRow("Facing", "${sighting.headingLabel} (${sighting.headingDegrees.toInt()}°)")
                                }
                                DossierRow("Speed", "${sighting.spotSpeedMph} mph")
                                DossierRow("Sync status", if (sighting.isSynced) "Synced (ID: ${sighting.cloudId})" else "Pending sync")
                                if (!sighting.notes.isNullOrBlank()) {
                                    DossierRow("Notes", sighting.notes)
                                }
                            }
                        }
                    }

                    // 4. Sighting history for this vehicle
                    item {
                        Text(
                            text = "SIGHTING HISTORY (${historySightings.size})",
                            color = TechCyanPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    items(historySightings) { hist ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = TechSurfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, TechCardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = hist.locationName,
                                        color = TextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${formatTimestamp(hist.timestamp)} • ${hist.spotSpeedMph} mph",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                if (hist.isFlagged) {
                                    Text(
                                        text = "FLAGGED",
                                        color = AlertRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onViewOnMap(sighting.plateNumber) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("track_route_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TechCyanPrimary,
                            contentColor = Color(0xFF381E72)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Route Map", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onToggleWatchlist(sighting) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("toggle_watchlist_button"),
                        border = BorderStroke(1.dp, if (sighting.isFlagged) AlertGreen else AlertRed),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (sighting.isFlagged) AlertGreen else AlertRed
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            if (sighting.isFlagged) Icons.Default.CheckCircle else Icons.Default.Flag,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (sighting.isFlagged) "Unflag Plate" else "Flag on Watchlist")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPlateNumberDialog(
    currentPlateNumber: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentPlateNumber) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("edit_plate_number_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = TechSurface,
            border = BorderStroke(1.dp, TechCyanPrimary),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Correct plate number",
                    color = TechCyanPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "The OCR read can be wrong - fix it here if it doesn't match what's on the plate.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.uppercase() },
                    label = { Text("Plate number") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_plate_number_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TechCyanPrimary,
                        unfocusedBorderColor = TechCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        border = BorderStroke(1.dp, TextSecondary)
                    ) {
                        Text("Cancel", color = TextPrimary)
                    }
                    Button(
                        onClick = { if (text.isNotBlank()) onConfirm(text) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_edit_plate_number_button"),
                        enabled = text.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = TechCyanPrimary, contentColor = Color(0xFF381E72))
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FullImageViewer(filePath: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.96f))
                .clickable(onClick = onDismiss)
                .testTag("full_image_viewer")
        ) {
            val file = File(filePath)
            if (file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = "Captured photo, full size",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}

@Composable
private fun DossierRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            modifier = Modifier.width(110.dp)
        )
        Text(
            text = value,
            color = TextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}
