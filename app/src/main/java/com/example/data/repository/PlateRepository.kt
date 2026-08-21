package com.example.data.repository

import android.content.Context
import com.example.data.local.AppDatabase
import com.example.data.local.FlaggedPlateDao
import com.example.data.local.PlateDao
import com.example.data.model.AlertSeverity
import com.example.data.model.DashboardStats
import com.example.data.model.FlaggedPlate
import com.example.data.model.PlateSighting
import com.example.data.model.RoutePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class PlateRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val plateDao: PlateDao = db.plateDao()
    private val flaggedDao: FlaggedPlateDao = db.flaggedPlateDao()

    val allSightings: Flow<List<PlateSighting>> = plateDao.getAllSightings()
    val flaggedSightings: Flow<List<PlateSighting>> = plateDao.getFlaggedSightings()
    val allWatchlistPlates: Flow<List<FlaggedPlate>> = flaggedDao.getAllFlaggedPlates()

    fun searchSightings(query: String): Flow<List<PlateSighting>> {
        return if (query.isBlank()) {
            plateDao.getAllSightings()
        } else {
            plateDao.searchSightings(query.trim().uppercase())
        }
    }

    fun getSightingsForPlate(plateNumber: String): Flow<List<PlateSighting>> {
        return plateDao.getSightingsForPlate(plateNumber.uppercase())
    }

    val dashboardStats: Flow<DashboardStats> = combine(
        plateDao.getAllSightings(),
        flaggedDao.getAllFlaggedPlates()
    ) { sightings, watchlists ->
        val total = sightings.size
        val unique = sightings.map { it.plateNumber }.distinct().size
        val todayStart = getTodayStartMillis()
        val flaggedToday = sightings.count { it.isFlagged && it.timestamp >= todayStart }
        val activeFlagged = watchlists.count { it.isActive }
        val topLoc = sightings.groupBy { it.locationName }
            .maxByOrNull { it.value.size }?.key ?: "Market St Corridor"
        val synced = sightings.count { it.isSynced }
        val pending = sightings.count { !it.isSynced }

        DashboardStats(
            totalScans = total,
            uniquePlatesCount = unique,
            flaggedCountToday = flaggedToday,
            totalFlaggedActive = activeFlagged,
            topLocation = topLoc,
            syncedCount = synced,
            pendingSyncCount = pending
        )
    }.flowOn(Dispatchers.IO)

    val routePoints: Flow<List<RoutePoint>> = plateDao.getAllSightings().map { list ->
        list.map {
            RoutePoint(
                sightingId = it.id,
                plateNumber = it.plateNumber,
                latitude = it.latitude,
                longitude = it.longitude,
                locationName = it.locationName,
                timestamp = it.timestamp,
                isFlagged = it.isFlagged,
                vehicleColor = it.vehicleColor
            )
        }
    }.flowOn(Dispatchers.IO)

    suspend fun recordSighting(
        plateNumber: String,
        stateOrRegion: String,
        latitude: Double,
        longitude: Double,
        locationName: String,
        vehicleMake: String,
        vehicleModel: String,
        vehicleColor: String,
        vehicleType: String,
        confidenceScore: Float,
        snapshotUri: String?,
        notes: String?,
        speedMph: Int = 25,
        headingDegrees: Float = -1f,
        headingLabel: String = ""
    ): PlateSighting = withContext(Dispatchers.IO) {
        val cleanPlate = plateNumber.trim().uppercase().replace(" ", "")
        // Check if plate matches active watchlist
        val activeFlag = flaggedDao.findActiveFlaggedPlate(cleanPlate)

        val isFlagged = activeFlag != null
        val flagReason = activeFlag?.reason
        val severity = activeFlag?.severity ?: AlertSeverity.INFO.name

        val sighting = PlateSighting(
            plateNumber = cleanPlate,
            stateOrRegion = stateOrRegion,
            timestamp = System.currentTimeMillis(),
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            vehicleMake = vehicleMake,
            vehicleModel = vehicleModel,
            vehicleColor = vehicleColor,
            vehicleType = vehicleType,
            confidenceScore = confidenceScore,
            snapshotUri = snapshotUri,
            isFlagged = isFlagged,
            flagReason = flagReason,
            alertSeverity = severity,
            notes = notes,
            isSynced = false,
            cloudId = UUID.randomUUID().toString().take(8),
            spotSpeedMph = speedMph,
            headingDegrees = headingDegrees,
            headingLabel = headingLabel
        )

        val newId = plateDao.insertSighting(sighting)
        return@withContext sighting.copy(id = newId)
    }

    suspend fun addFlaggedPlate(
        plateNumber: String,
        severity: AlertSeverity,
        reason: String,
        vehicleDesc: String,
        ownerOrCase: String
    ) = withContext(Dispatchers.IO) {
        val clean = plateNumber.trim().uppercase()
        val flagged = FlaggedPlate(
            plateNumber = clean,
            severity = severity.name,
            reason = reason,
            vehicleDescription = vehicleDesc,
            ownerOrCaseNumber = ownerOrCase,
            dateAdded = System.currentTimeMillis(),
            isActive = true
        )
        flaggedDao.insertFlaggedPlate(flagged)
    }

    suspend fun toggleWatchlistActive(flaggedPlate: FlaggedPlate) = withContext(Dispatchers.IO) {
        flaggedDao.updateFlaggedPlate(flaggedPlate.copy(isActive = !flaggedPlate.isActive))
    }

    suspend fun deleteWatchlistPlate(plateNumber: String) = withContext(Dispatchers.IO) {
        flaggedDao.deleteFlaggedPlateByNumber(plateNumber)
    }

    suspend fun deleteSighting(sighting: PlateSighting) = withContext(Dispatchers.IO) {
        plateDao.deleteSighting(sighting)
    }

    suspend fun clearAllSightings() = withContext(Dispatchers.IO) {
        plateDao.deleteAllSightings()
    }

    suspend fun clearAllWatchlist() = withContext(Dispatchers.IO) {
        flaggedDao.deleteAllFlaggedPlates()
    }

    suspend fun syncAllWithCloud(): Int = withContext(Dispatchers.IO) {
        plateDao.markAllAsSynced()
        return@withContext 1
    }

    suspend fun seedInitialDataIfEmpty() = withContext(Dispatchers.IO) {
        // Only seed sample watchlist rules if completely empty
        val existingWatchlist = flaggedDao.getAllFlaggedPlates()
        // No fake sightings seeded: Pure live real-world ALPR recording only
    }


    private fun getTodayStartMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
