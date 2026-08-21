package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlateSighting
import com.example.ui.components.PlateBadge
import com.example.ui.components.SeverityBadge
import com.example.ui.components.formatTimestamp
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatabaseScreen(
    sightings: List<PlateSighting>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectSighting: (PlateSighting) -> Unit,
    onDeleteSighting: (PlateSighting) -> Unit,
    onClearAllSightings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedFilterChip by remember { mutableStateOf("ALL") }
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear All Vehicle Sightings?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("This will permanently delete all recorded sightings and snippets from the local database.", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllSightings()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear All", color = AlertRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = TechSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    val filteredList = remember(sightings, searchQuery, selectedFilterChip) {
        val base = if (searchQuery.isBlank()) {
            sightings
        } else {
            sightings.filter {
                it.plateNumber.contains(searchQuery.trim(), ignoreCase = true) ||
                it.vehicleMake.contains(searchQuery.trim(), ignoreCase = true) ||
                it.vehicleModel.contains(searchQuery.trim(), ignoreCase = true) ||
                it.locationName.contains(searchQuery.trim(), ignoreCase = true) ||
                (it.flagReason?.contains(searchQuery.trim(), ignoreCase = true) == true)
            }
        }

        when (selectedFilterChip) {
            "FLAGGED" -> base.filter { it.isFlagged }
            "SEDAN" -> base.filter { it.vehicleType.equals("Sedan", ignoreCase = true) }
            "SUV" -> base.filter { it.vehicleType.equals("SUV", ignoreCase = true) }
            "TRUCK" -> base.filter { it.vehicleType.equals("Truck", ignoreCase = true) }
            "REPEAT" -> {
                val plateCounts = sightings.groupingBy { it.plateNumber }.eachCount()
                base.filter { (plateCounts[it.plateNumber] ?: 0) > 1 }
            }
            else -> base
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TechDarkBg)
            .padding(horizontal = 16.dp)
            .testTag("database_screen")
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("plate_search_input"),
            placeholder = { Text("Search plate, make, model, location...", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = TechCyanPrimary)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = TechSurface,
                unfocusedContainerColor = TechSurface,
                focusedBorderColor = TechCyanPrimary,
                unfocusedBorderColor = TechCardBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val chips = listOf(
                "ALL" to "All Sightings (${sightings.size})",
                "FLAGGED" to "🚨 Watchlist Only",
                "REPEAT" to "🔁 Repeat Targets",
                "SEDAN" to "Sedans",
                "SUV" to "SUVs",
                "TRUCK" to "Trucks"
            )

            items(chips) { (key, label) ->
                val isSelected = selectedFilterChip == key
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilterChip = key },
                    label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TechCyanPrimary,
                        selectedLabelColor = Color(0xFF381E72),
                        containerColor = TechSurface,
                        labelColor = TextPrimary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) TechCyanPrimary else TechCardBorder
                    ),
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Result Count Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filteredList.size} Sightings Logged",
                color = TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            if (sightings.isNotEmpty()) {
                TextButton(
                    onClick = { showClearDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("clear_all_sightings_button")
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = "Clear All",
                        tint = AlertRed.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Clear Sightings",
                        color = AlertRed.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Text(
                    text = "Live ALPR Ready",
                    color = TechCyanDim,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // List of Sightings
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No matching vehicle sightings found",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Try scanning a new license plate or clearing filters",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(filteredList, key = { it.id }) { sighting ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectSighting(sighting) }
                            .testTag("db_sighting_row_${sighting.plateNumber}"),
                        shape = RoundedCornerShape(12.dp),
                        color = TechSurface,
                        border = BorderStroke(1.dp, if (sighting.isFlagged) AlertRed else TechCardBorder),
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                        .padding(horizontal = 12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${sighting.vehicleColor} ${sighting.vehicleMake} ${sighting.vehicleModel}",
                                            color = TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = "📍 ${sighting.locationName}",
                                        color = TextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatTimestamp(sighting.timestamp),
                                        color = TextMuted,
                                        fontSize = 10.sp
                                    )
                                }

                                if (sighting.isFlagged) {
                                    SeverityBadge(sighting.alertSeverity)
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = TechSurfaceVariant
                                    ) {
                                        Text(
                                            text = "${(sighting.confidenceScore * 100).toInt()}% OCR",
                                            color = TechCyanPrimary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            if (sighting.isFlagged && !sighting.flagReason.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = AlertRedBg,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "🚨 ${sighting.flagReason}",
                                        color = AlertRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
