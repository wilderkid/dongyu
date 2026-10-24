package app.dongyu.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SportTypeDao {
    @Query("SELECT * FROM sport_types WHERE archived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<SportTypeEntity>>

    @Query("SELECT * FROM sport_types ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<SportTypeEntity>>

    @Query("SELECT * FROM sport_types WHERE id = :id")
    suspend fun get(id: Long): SportTypeEntity?

    @Query("SELECT * FROM sport_types ORDER BY sortOrder, id")
    suspend fun all(): List<SportTypeEntity>

    @Query("SELECT COUNT(*) FROM sport_types")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM sport_types")
    suspend fun maxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: SportTypeEntity): Long

    @Insert
    suspend fun insertAll(list: List<SportTypeEntity>): List<Long>
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts WHERE status = 'FINISHED' ORDER BY startTime DESC")
    fun observeFinished(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE status = 'FINISHED' AND sportId = :sportId ORDER BY startTime DESC")
    fun observeFinishedBySport(sportId: Long): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observe(id: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun get(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE status = 'FINISHED' AND startTime BETWEEN :from AND :to")
    suspend fun startingBetween(from: Long, to: Long): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE status = 'FINISHED' ORDER BY startTime")
    suspend fun allFinished(): List<WorkoutEntity>

    @Query("SELECT * FROM workouts WHERE status != 'FINISHED' ORDER BY startTime DESC LIMIT 1")
    suspend fun unfinished(): WorkoutEntity?

    @Insert
    suspend fun insert(e: WorkoutEntity): Long

    @Update
    suspend fun update(e: WorkoutEntity)

    @Query("UPDATE workouts SET durationSec = :durationSec, distanceM = :distanceM, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: Long, durationSec: Long, distanceM: Double, status: String, updatedAt: Long)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM workouts")
    suspend fun deleteAll()
}

@Dao
interface TrackPointDao {
    @Insert
    suspend fun insert(p: TrackPointEntity): Long

    @Insert
    suspend fun insertAll(list: List<TrackPointEntity>)

    @Query("SELECT * FROM track_points WHERE workoutId = :workoutId ORDER BY time, id")
    suspend fun forWorkout(workoutId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_points WHERE workoutId = :workoutId ORDER BY time, id")
    fun observeForWorkout(workoutId: Long): Flow<List<TrackPointEntity>>

    @Query("SELECT COUNT(*) FROM track_points WHERE workoutId = :workoutId")
    suspend fun count(workoutId: Long): Int

    @Query("SELECT MAX(segment) FROM track_points WHERE workoutId = :workoutId")
    suspend fun maxSegment(workoutId: Long): Int?
}

@Dao
interface HrSampleDao {
    @Insert
    suspend fun insert(s: HrSampleEntity): Long

    @Insert
    suspend fun insertAll(list: List<HrSampleEntity>)

    @Query("SELECT * FROM hr_samples WHERE workoutId = :workoutId ORDER BY time")
    suspend fun forWorkout(workoutId: Long): List<HrSampleEntity>
}
