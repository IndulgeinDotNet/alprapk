package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.SnapshotStorageManager
import com.example.data.location.LocationHelper
import com.example.data.model.AlertSeverity
import com.example.data.model.DashboardStats
import com.example.data.model.FlaggedPlate
import com.example.data.model.PlateSighting
import com.example.data.remote.LivePlateCandidate
import com.example.data.remote.OfflinePlateScanner
import com.example.data.repository.PlateRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.min

enum class AppTab(val title: String) {
    DASHBOARD("Dashboard"),
    SCANNER("Live Scan"),
    DATABASE("Database"),
    ROUTE_MAP("Route Map"),
    WATCHLIST("Watchlist")
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlateRepository(application)
    private val scanner = OfflinePlateScanner(application)
    private val locationHelper = LocationHelper(application)
    private val snapshotManager = SnapshotStorageManager(application)

    val currentTab = MutableStateFlow(AppTab.DASHBOARD)
    val searchQuery = MutableStateFlow("")
    val selectedPlateRouteFilter = MutableStateFlow<String?>(null)

    val isScanning = MutableStateFlow(false)
    val isAutoContinuousScanEnabled = MutableStateFlow(true)
    val liveTrackingCandidate = MutableStateFlow<LivePlateCandidate?>(null)
    val lastScannedSighting = MutableStateFlow<PlateSighting?>(null)
    val activeRealTimeAlert = MutableStateFlow<PlateSighting?>(null)
    val selectedDetailSighting = MutableStateFlow<PlateSighting?>(null)
    val detailedSightingHistory = MutableStateFlow<List<PlateSighting>>(emptyList())
    val snackbarMessage = MutableStateFlow<String?>(null)

    // Cooldown map to avoid rapid duplicate logging for the same passing vehicle (12 seconds)
    private val detectionCooldowns = java.util.concurrent.ConcurrentHashMap<String, Long>()
    // Global rate limiter between distinct auto-log events (1.5 seconds)
    private var lastGlobalAutoLogTime = 0L

    val offlineScanner: OfflinePlateScanner
        get() = scanner

    val dashboardStats: StateFlow<DashboardStats> = repository.dashboardStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    val allSightings: StateFlow<List<PlateSighting>> = repository.allSightings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchlistPlates: StateFlow<List<FlaggedPlate>> = repository.allWatchlistPlates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleAutoContinuousScan() {
        isAutoContinuousScanEnabled.value = !isAutoContinuousScanEnabled.value
        snackbarMessage.value = if (isAutoContinuousScanEnabled.value) {
            "🔴 Auto-Log Active"
        } else {
            "⏸️ Manual Shutter Mode"
        }
    }

    fun updateLiveCandidate(candidate: LivePlateCandidate?) {
        liveTrackingCandidate.value = candidate
    }

    /**
     * Called by the live video frame analyzer when a license plate is recognized.
     * Snips the vehicle & plate region from the camera frame and logs real GPS coordinates
     * ONLY when temporal multi-frame consensus threshold is satisfied.
     */
    fun onAutoPlateCaptured(candidate: LivePlateCandidate, frameBitmap: Bitmap) {
        if (!candidate.isLockedAndReady) {
            return
        }

        // Mark plate as committed in temporal tracker
        scanner.temporalTracker.markCommitted(candidate.plateNumber)

        viewModelScope.launch {
            try {
                // 1. Get real GPS location from device hardware
                val loc = locationHelper.getCurrentLocation()

                // 2. Snip vehicle & plate bounding area from camera frame
                val snippedVehicle = scanner.snipVehicleFromBitmap(frameBitmap, candidate.boundingBox)
                val savedSnapshotPath = snapshotManager.saveBitmapSnapshot(snippedVehicle, candidate.plateNumber)

                // 3. Detect dominant vehicle paint color from cropped image
                val vehicleColor = scanner.detectDominantColor(snippedVehicle)

                // 4. Zoom into the plate region on the full-resolution captured frame and
                // re-read it, so a vehicle photographed from a normal distance still gets a
                // sharp, close-up character read for the final committed plate number.
                val refinedPlate = scanner.refinePlateFromFrame(frameBitmap, candidate.plateNumber, candidate.boundingBox)
                val finalPlateNumber = refinedPlate ?: candidate.plateNumber
                val finalConfidence = if (refinedPlate != null) min(0.99f, candidate.confidence + 0.05f) else candidate.confidence

                // 5. Save ALPR sighting to Room database
                val newSighting = repository.recordSighting(
                    plateNumber = finalPlateNumber,
                    stateOrRegion = candidate.stateOrRegion,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    locationName = loc.locationName,
                    vehicleMake = candidate.vehicleMake,
                    vehicleModel = candidate.vehicleModel,
                    vehicleColor = vehicleColor,
                    vehicleType = candidate.vehicleType,
                    confidenceScore = finalConfidence,
                    snapshotUri = savedSnapshotPath,
                    notes = "Real-Time Video ALPR Capture",
                    speedMph = loc.speedMph
                )

                lastScannedSighting.value = newSighting

                // 6. Trigger alert if vehicle matches active watchlist
                if (newSighting.isFlagged) {
                    activeRealTimeAlert.value = newSighting
                    snackbarMessage.value = "🚨 WATCHLIST HIT: ${newSighting.plateNumber}"
                } else {
                    snackbarMessage.value = "🎯 Verified Plate: ${newSighting.plateNumber}"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearAllSightings() {
        viewModelScope.launch {
            repository.clearAllSightings()
            lastScannedSighting.value = null
            selectedDetailSighting.value = null
            snackbarMessage.value = "Database cleared"
        }
    }

    fun clearAllWatchlist() {
        viewModelScope.launch {
            repository.clearAllWatchlist()
            snackbarMessage.value = "Watchlist cleared"
        }
    }

    fun setTab(tab: AppTab) {
        currentTab.value = tab
    }

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun setRouteFilter(plateNumber: String?) {
        selectedPlateRouteFilter.value = plateNumber
    }

    fun dismissRealTimeAlert() {
        activeRealTimeAlert.value = null
    }

    fun dismissSnackbar() {
        snackbarMessage.value = null
    }

    fun openSightingDetail(sighting: PlateSighting) {
        selectedDetailSighting.value = sighting
        viewModelScope.launch {
            repository.getSightingsForPlate(sighting.plateNumber).collect { list ->
                detailedSightingHistory.value = list
            }
        }
    }

    fun closeSightingDetail() {
        selectedDetailSighting.value = null
    }

    fun scanImage(bitmap: Bitmap) {
        viewModelScope.launch {
            isScanning.value = true
            try {
                // 1. Run optical ALPR detection on image
                val result = scanner.scanVehicleImage(bitmap)

                if (result == null) {
                    snackbarMessage.value = "No license plate detected in image."
                    return@launch
                }

                // 2. Fetch real GPS coordinates from device
                val loc = locationHelper.getCurrentLocation()

                // 3. Snip vehicle & plate region
                val snippedVehicle = scanner.snipVehicleFromBitmap(bitmap, result.boundingBox)
                val savedSnapshotPath = snapshotManager.saveBitmapSnapshot(snippedVehicle, result.plateNumber)

                // 4. Save Sighting to Room DB
                val newSighting = repository.recordSighting(
                    plateNumber = result.plateNumber,
                    stateOrRegion = result.stateOrRegion,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    locationName = loc.locationName,
                    vehicleMake = result.vehicleMake,
                    vehicleModel = result.vehicleModel,
                    vehicleColor = result.vehicleColor,
                    vehicleType = result.vehicleType,
                    confidenceScore = result.confidence,
                    snapshotUri = savedSnapshotPath,
                    notes = result.notes,
                    speedMph = loc.speedMph
                )

                lastScannedSighting.value = newSighting

                if (newSighting.isFlagged) {
                    activeRealTimeAlert.value = newSighting
                    snackbarMessage.value = "🚨 WATCHLIST HIT: ${newSighting.plateNumber}"
                } else {
                    snackbarMessage.value = "Plate ${newSighting.plateNumber} Logged"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                snackbarMessage.value = "Scan error: ${e.message}"
            } finally {
                isScanning.value = false
            }
        }
    }

    fun syncWithCloud() {
        viewModelScope.launch {
            repository.syncAllWithCloud()
            snackbarMessage.value = "Cloud Sync Complete"
        }
    }

    fun addWatchlistPlate(
        plateNumber: String,
        severity: AlertSeverity,
        reason: String,
        vehicleDesc: String,
        caseNum: String
    ) {
        viewModelScope.launch {
            repository.addFlaggedPlate(
                plateNumber = plateNumber,
                severity = severity,
                reason = reason,
                vehicleDesc = vehicleDesc,
                ownerOrCase = caseNum
            )
            snackbarMessage.value = "Plate $plateNumber added to Watchlist"
        }
    }

    fun toggleWatchlistActive(item: FlaggedPlate) {
        viewModelScope.launch {
            repository.toggleWatchlistActive(item)
            snackbarMessage.value = "Watchlist status updated for ${item.plateNumber}"
        }
    }

    fun deleteWatchlistPlate(plateNumber: String) {
        viewModelScope.launch {
            repository.deleteWatchlistPlate(plateNumber)
            snackbarMessage.value = "Removed $plateNumber from Watchlist"
        }
    }

    fun deleteSighting(sighting: PlateSighting) {
        viewModelScope.launch {
            repository.deleteSighting(sighting)
            if (selectedDetailSighting.value?.id == sighting.id) {
                selectedDetailSighting.value = null
            }
            snackbarMessage.value = "Sighting deleted"
        }
    }

    fun toggleFlagFromSighting(sighting: PlateSighting) {
        viewModelScope.launch {
            if (sighting.isFlagged) {
                repository.deleteWatchlistPlate(sighting.plateNumber)
                snackbarMessage.value = "Unflagged ${sighting.plateNumber}"
            } else {
                repository.addFlaggedPlate(
                    plateNumber = sighting.plateNumber,
                    severity = AlertSeverity.WARNING,
                    reason = "Flagged during patrol inspection",
                    vehicleDesc = "${sighting.vehicleColor} ${sighting.vehicleMake} ${sighting.vehicleModel}",
                    ownerOrCase = "Patrol Unit"
                )
                snackbarMessage.value = "Added ${sighting.plateNumber} to Watchlist"
            }
            selectedDetailSighting.value = null
        }
    }
}
