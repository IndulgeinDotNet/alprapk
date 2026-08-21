package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PlateSighting
import com.example.ui.components.PlateBadge
import com.example.ui.components.RouteMapVisualizer
import com.example.ui.theme.*

@Composable
fun RouteMapScreen(
    sightings: List<PlateSighting>,
    selectedPlateFilter: String?,
    onSelectPlateFilter: (String?) -> Unit,
    onSelectSighting: (PlateSighting) -> Unit,
    modifier: Modifier = Modifier
) {
    val uniquePlates = remember(sightings) {
        sightings.map { it.plateNumber }.distinct()
    }

    val currentFilteredSightings = remember(sightings, selectedPlateFilter) {
        if (selectedPlateFilter.isNullOrBlank()) {
            sightings
        } else {
            sightings.filter { it.plateNumber.equals(selectedPlateFilter, ignoreCase = true) }
        }.sortedBy { it.timestamp }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TechDarkBg)
            .padding(16.dp)
            .testTag("route_map_screen")
    ) {
        // Vehicle filter
        Text(
            text = "FILTER BY VEHICLE",
            color = TechCyanPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            item {
                Surface(
                    modifier = Modifier
                        .clickable { onSelectPlateFilter(null) }
                        .testTag("route_filter_all"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedPlateFilter == null) TechCyanPrimary else TechSurface,
                    border = BorderStroke(1.dp, if (selectedPlateFilter == null) TechCyanPrimary else TechCardBorder)
                ) {
                    Text(
                        text = "All (${sightings.size})",
                        color = if (selectedPlateFilter == null) Color(0xFF381E72) else TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            items(uniquePlates) { plate ->
                val isSelected = selectedPlateFilter.equals(plate, ignoreCase = true)
                val count = sightings.count { it.plateNumber.equals(plate, ignoreCase = true) }
                val isFlagged = sightings.any { it.plateNumber.equals(plate, ignoreCase = true) && it.isFlagged }

                Surface(
                    modifier = Modifier
                        .clickable { onSelectPlateFilter(plate) }
                        .testTag("route_filter_$plate"),
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) TechCyanPrimary else if (isFlagged) AlertRedBg else TechSurface,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) TechCyanPrimary else if (isFlagged) AlertRed else TechCardBorder
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = plate,
                            color = if (isSelected) Color(0xFF381E72) else if (isFlagged) AlertRed else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "($count)",
                            color = if (isSelected) Color(0xFF381E72).copy(alpha = 0.8f) else TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Route Map Visualizer Canvas
        Box(modifier = Modifier.weight(1f)) {
            RouteMapVisualizer(
                sightings = sightings,
                selectedPlateFilter = selectedPlateFilter,
                onSelectSighting = onSelectSighting,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Route Summary Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = TechSurface,
            border = BorderStroke(1.dp, TechCardBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = if (selectedPlateFilter != null) "Route: $selectedPlateFilter" else "All sightings",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${currentFilteredSightings.size} locations mapped",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                if (selectedPlateFilter != null) {
                    OutlinedButton(
                        onClick = { onSelectPlateFilter(null) },
                        border = BorderStroke(1.dp, TechCyanDim),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Reset All", color = TechCyanPrimary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
