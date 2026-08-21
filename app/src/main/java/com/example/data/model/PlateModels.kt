package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class AlertSeverity(val displayName: String, val colorHex: Long) {
    CRITICAL("Critical Alert / Stolen", 0xFFE53935),
    WARNING("Warning / Suspicious", 0xFFFB8C00),
    BOLO("BOLO (Be On Lookout)", 0xFF8E24AA),
    PARKING_VIOLATION("Parking / Tow List", 0xFFD81B60),
    VIP("VIP / Permit Authorized", 0xFF1E88E5),
    INFO("General Watch", 0xFF00897B)
}

@Entity(tableName = "plate_sightings")
data class PlateSighting(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val plateNumber: String,
    val stateOrRegion: String = "CA",
    val timestamp: Long = System.currentTimeMillis(),
    val latitude: Double = 37.7749,
    val longitude: Double = -122.4194,
    val locationName: String = "Market St & 4th St, San Francisco, CA",
    val vehicleMake: String = "Unknown Make",
    val vehicleModel: String = "Unknown Model",
    val vehicleColor: String = "Silver",
    val vehicleType: String = "Sedan",
    val confidenceScore: Float = 0.94f,
    val snapshotUri: String? = null,
    val isFlagged: Boolean = false,
    val flagReason: String? = null,
    val alertSeverity: String = AlertSeverity.INFO.name,
    val notes: String? = null,
    val isSynced: Boolean = false,
    val cloudId: String? = null,
    val spotSpeedMph: Int = 24
)

@Entity(tableName = "flagged_plates")
data class FlaggedPlate(
    @PrimaryKey
    val plateNumber: String,
    val severity: String = AlertSeverity.CRITICAL.name,
    val reason: String,
    val vehicleDescription: String = "",
    val ownerOrCaseNumber: String = "",
    val dateAdded: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

data class DashboardStats(
    val totalScans: Int = 0,
    val uniquePlatesCount: Int = 0,
    val flaggedCountToday: Int = 0,
    val totalFlaggedActive: Int = 0,
    val topLocation: String = "Downtown Main Corridor",
    val syncedCount: Int = 0,
    val pendingSyncCount: Int = 0
)

data class RoutePoint(
    val sightingId: Long,
    val plateNumber: String,
    val latitude: Double,
    val longitude: Double,
    val locationName: String,
    val timestamp: Long,
    val isFlagged: Boolean,
    val vehicleColor: String
)
