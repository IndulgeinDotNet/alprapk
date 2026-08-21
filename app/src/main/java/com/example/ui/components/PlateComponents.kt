package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AlertSeverity
import com.example.data.model.PlateSighting
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PlateBadge(
    plateNumber: String,
    stateOrRegion: String = "CA",
    modifier: Modifier = Modifier,
    isLarge: Boolean = false
) {
    val plateWidth = if (isLarge) 160.dp else 115.dp
    val plateHeight = if (isLarge) 80.dp else 52.dp
    val fontSize = if (isLarge) 24.sp else 16.sp
    val stateFontSize = if (isLarge) 10.sp else 8.sp

    Surface(
        modifier = modifier
            .width(plateWidth)
            .height(plateHeight)
            .testTag("plate_badge_$plateNumber"),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF4F6F9),
        border = BorderStroke(2.dp, Color(0xFF1E293B)),
        shadowElevation = 4.dp
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp)) {
            // Bolt holes
            Box(
                modifier = Modifier
                    .size(if (isLarge) 6.dp else 4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF64748B))
                    .align(Alignment.TopStart)
            )
            Box(
                modifier = Modifier
                    .size(if (isLarge) 6.dp else 4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF64748B))
                    .align(Alignment.TopEnd)
            )

            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stateOrRegion.uppercase(),
                    color = Color(0xFF1E3A8A),
                    fontSize = stateFontSize,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = plateNumber.uppercase(),
                    color = Color(0xFF0F172A),
                    fontSize = fontSize,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = if (isLarge) 3.sp else 1.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun SeverityBadge(
    severityName: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (severityName) {
        AlertSeverity.CRITICAL.name -> Triple(AlertRedBg, AlertRed, "CRITICAL / STOLEN")
        AlertSeverity.WARNING.name -> Triple(AlertOrangeBg, AlertOrange, "WARNING / SUSPICIOUS")
        AlertSeverity.BOLO.name -> Triple(Color(0xFF3B154C), AlertPurple, "BOLO ALERT")
        AlertSeverity.PARKING_VIOLATION.name -> Triple(Color(0xFF38081E), Color(0xFFFF4081), "PARKING / IMPOUND")
        AlertSeverity.VIP.name -> Triple(Color(0xFF0C2444), AlertBlue, "VIP PERMIT")
        else -> Triple(Color(0xFF072B20), AlertGreen, "LOGGED / CLEAR")
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Text(
                text = label,
                color = textColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun RealTimeAlertBanner(
    sighting: PlateSighting?,
    onDismiss: () -> Unit,
    onViewDetail: (PlateSighting) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = sighting != null,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier
    ) {
        if (sighting != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("real_time_alert_banner")
                    .shadow(16.dp, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                color = AlertRedBg,
                border = BorderStroke(2.dp, AlertRed)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alert",
                                tint = AlertRed,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "WATCHLIST MATCH DETECTED",
                                color = AlertRed,
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Alert",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        PlateBadge(
                            plateNumber = sighting.plateNumber,
                            stateOrRegion = sighting.stateOrRegion,
                            isLarge = true
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 12.dp)
                        ) {
                            Text(
                                text = sighting.flagReason ?: "Flagged on active security watchlist",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${sighting.vehicleColor} ${sighting.vehicleMake} ${sighting.vehicleModel}",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "📍 ${sighting.locationName}",
                                color = TechCyanPrimary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onViewDetail(sighting) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("alert_view_details_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AlertRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Dossier & Route", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("alert_ack_button"),
                            border = BorderStroke(1.dp, TextSecondary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Acknowledge", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy • HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(millis))
}

fun formatTimeOnly(millis: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
