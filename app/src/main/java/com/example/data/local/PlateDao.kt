package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.FlaggedPlate
import com.example.data.model.PlateSighting
import kotlinx.coroutines.flow.Flow

@Dao
interface PlateDao {
    @Query("SELECT * FROM plate_sightings ORDER BY timestamp DESC")
    fun getAllSightings(): Flow<List<PlateSighting>>

    @Query("SELECT * FROM plate_sightings WHERE plateNumber = :plateNumber ORDER BY timestamp DESC")
    fun getSightingsForPlate(plateNumber: String): Flow<List<PlateSighting>>

    @Query("""
        SELECT * FROM plate_sightings 
        WHERE plateNumber LIKE '%' || :query || '%' 
           OR vehicleMake LIKE '%' || :query || '%'
           OR vehicleModel LIKE '%' || :query || '%'
           OR locationName LIKE '%' || :query || '%'
           OR flagReason LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
    """)
    fun searchSightings(query: String): Flow<List<PlateSighting>>

    @Query("SELECT * FROM plate_sightings WHERE isFlagged = 1 ORDER BY timestamp DESC")
    fun getFlaggedSightings(): Flow<List<PlateSighting>>

    @Query("SELECT * FROM plate_sightings WHERE timestamp >= :timestamp ORDER BY timestamp DESC")
    fun getSightingsSince(timestamp: Long): Flow<List<PlateSighting>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSighting(sighting: PlateSighting): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSightings(sightings: List<PlateSighting>)

    @Update
    suspend fun updateSighting(sighting: PlateSighting)

    @Delete
    suspend fun deleteSighting(sighting: PlateSighting)

    @Query("DELETE FROM plate_sightings WHERE id = :id")
    suspend fun deleteSightingById(id: Long)

    @Query("DELETE FROM plate_sightings")
    suspend fun deleteAllSightings()

    @Query("SELECT COUNT(DISTINCT plateNumber) FROM plate_sightings")
    fun getUniquePlateCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM plate_sightings")
    fun getTotalScanCount(): Flow<Int>

    @Query("UPDATE plate_sightings SET isSynced = 1")
    suspend fun markAllAsSynced()
}

@Dao
interface FlaggedPlateDao {
    @Query("SELECT * FROM flagged_plates ORDER BY dateAdded DESC")
    fun getAllFlaggedPlates(): Flow<List<FlaggedPlate>>

    @Query("SELECT * FROM flagged_plates WHERE isActive = 1")
    fun getActiveFlaggedPlates(): Flow<List<FlaggedPlate>>

    @Query("SELECT * FROM flagged_plates WHERE plateNumber = :plateNumber AND isActive = 1 LIMIT 1")
    suspend fun findActiveFlaggedPlate(plateNumber: String): FlaggedPlate?

    @Query("SELECT * FROM flagged_plates WHERE plateNumber = :plateNumber LIMIT 1")
    suspend fun getFlaggedPlate(plateNumber: String): FlaggedPlate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlaggedPlate(flaggedPlate: FlaggedPlate)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFlaggedPlates(flaggedPlates: List<FlaggedPlate>)

    @Update
    suspend fun updateFlaggedPlate(flaggedPlate: FlaggedPlate)

    @Delete
    suspend fun deleteFlaggedPlate(flaggedPlate: FlaggedPlate)

    @Query("DELETE FROM flagged_plates WHERE plateNumber = :plateNumber")
    suspend fun deleteFlaggedPlateByNumber(plateNumber: String)

    @Query("DELETE FROM flagged_plates")
    suspend fun deleteAllFlaggedPlates()
}
