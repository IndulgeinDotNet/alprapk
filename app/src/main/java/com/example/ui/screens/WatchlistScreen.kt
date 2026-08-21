package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AlertSeverity
import com.example.data.model.FlaggedPlate
import com.example.ui.components.PlateBadge
import com.example.ui.components.SeverityBadge
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    watchlist: List<FlaggedPlate>,
    onAddPlate: (plateNumber: String, severity: AlertSeverity, reason: String, vehicleDesc: String, caseNum: String) -> Unit,
    onToggleActive: (FlaggedPlate) -> Unit,
    onDeletePlate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TechDarkBg)
            .padding(16.dp)
            .testTag("watchlist_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "WATCHLIST",
                    color = TechCyanPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "${watchlist.count { it.isActive }} active alerts",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Button(
                onClick = { showAddDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AlertRed,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("add_watchlist_plate_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add plate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (watchlist.isEmpty()) {
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
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "Watchlist is currently empty",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add plates to receive real-time detection alerts",
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
                items(watchlist, key = { it.plateNumber }) { item ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("watchlist_item_${item.plateNumber}"),
                        shape = RoundedCornerShape(12.dp),
                        color = if (item.isActive) TechSurface else TechSurfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, if (item.isActive) AlertRed else TechCardBorder),
                        shadowElevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                PlateBadge(plateNumber = item.plateNumber)

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 10.dp)
                                ) {
                                    SeverityBadge(item.severity)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.reason,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (item.vehicleDescription.isNotBlank()) {
                                        Text(
                                            text = item.vehicleDescription,
                                            color = TextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (item.ownerOrCaseNumber.isNotBlank()) {
                                        Text(
                                            text = "Case: ${item.ownerOrCaseNumber}",
                                            color = TechCyanDim,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Switch(
                                        checked = item.isActive,
                                        onCheckedChange = { onToggleActive(item) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = AlertRed,
                                            uncheckedTrackColor = TechSurfaceVariant
                                        ),
                                        modifier = Modifier.testTag("toggle_switch_${item.plateNumber}")
                                    )

                                    IconButton(
                                        onClick = { onDeletePlate(item.plateNumber) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DeleteOutline,
                                            contentDescription = "Delete",
                                            tint = TextMuted,
                                            modifier = Modifier.size(18.dp)
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

    // Add Watchlist Item Dialog
    if (showAddDialog) {
        AddWatchlistDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { plate, sev, reason, desc, caseNum ->
                onAddPlate(plate, sev, reason, desc, caseNum)
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWatchlistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, AlertSeverity, String, String, String) -> Unit
) {
    var plateText by remember { mutableStateOf("") }
    var selectedSeverity by remember { mutableStateOf(AlertSeverity.CRITICAL) }
    var reasonText by remember { mutableStateOf("") }
    var vehicleDescText by remember { mutableStateOf("") }
    var caseNumText by remember { mutableStateOf("") }
    var severityExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_watchlist_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = TechSurface,
            border = BorderStroke(1.dp, AlertRed),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Add to watchlist",
                        color = AlertRed,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                // Plate Number
                OutlinedTextField(
                    value = plateText,
                    onValueChange = { plateText = it.uppercase() },
                    label = { Text("License Plate Number *") },
                    placeholder = { Text("e.g. 7ABC123") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_watchlist_plate"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AlertRed,
                        unfocusedBorderColor = TechCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )

                // Severity Picker
                ExposedDropdownMenuBox(
                    expanded = severityExpanded,
                    onExpandedChange = { severityExpanded = !severityExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedSeverity.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Alert Severity Level") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = severityExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AlertRed,
                            unfocusedBorderColor = TechCardBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    ExposedDropdownMenu(
                        expanded = severityExpanded,
                        onDismissRequest = { severityExpanded = false },
                        modifier = Modifier.background(TechSurface)
                    ) {
                        AlertSeverity.values().forEach { sev ->
                            DropdownMenuItem(
                                text = { Text(sev.displayName, color = TextPrimary) },
                                onClick = {
                                    selectedSeverity = sev
                                    severityExpanded = false
                                }
                            )
                        }
                    }
                }

                // Reason
                OutlinedTextField(
                    value = reasonText,
                    onValueChange = { reasonText = it },
                    label = { Text("Reason *") },
                    placeholder = { Text("e.g. Reported stolen") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_watchlist_reason"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AlertRed,
                        unfocusedBorderColor = TechCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Vehicle Desc
                OutlinedTextField(
                    value = vehicleDescText,
                    onValueChange = { vehicleDescText = it },
                    label = { Text("Vehicle Make/Model/Color") },
                    placeholder = { Text("e.g. Black Toyota Camry 2022") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AlertRed,
                        unfocusedBorderColor = TechCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                // Case Number
                OutlinedTextField(
                    value = caseNumText,
                    onValueChange = { caseNumText = it },
                    label = { Text("Reference (optional)") },
                    placeholder = { Text("e.g. Case #1234") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AlertRed,
                        unfocusedBorderColor = TechCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

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
                        onClick = {
                            if (plateText.isNotBlank() && reasonText.isNotBlank()) {
                                onConfirm(
                                    plateText.trim().uppercase(),
                                    selectedSeverity,
                                    reasonText.trim(),
                                    vehicleDescText.trim(),
                                    caseNumText.trim()
                                )
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("confirm_add_watchlist_button"),
                        enabled = plateText.isNotBlank() && reasonText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AlertRed,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Add plate", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
