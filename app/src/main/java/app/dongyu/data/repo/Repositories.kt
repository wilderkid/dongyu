package app.dongyu.data.repo

import app.dongyu.data.db.AppDatabase
import app.dongyu.data.db.toEntity
import app.dongyu.data.db.toModel
import app.dongyu.domain.metrics.SplitCalculator
import app.dongyu.model.Presets
import app.dongyu.model.SportType
import app.dongyu.model.TrackPoint
import app.dongyu.model.Workout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SportRepository(private val db: AppDatabase) {
    private val dao = db.sportTypeDao()

    val activeSports: Flow<List<SportType>> = dao.observeActive().map { l -> l.map { it.toModel() } }
    val allSports: Flow<List<SportType>> = dao.observeAll().map { l -> l.map { it.toModel() } }

    suspend fun get(id: Long): SportType? = dao.get(id)?.toModel()

    /** Inserts the built-in presets on first launch. */
    suspend fun ensurePresets() {
        if (dao.count() == 0) dao.insertAll(Presets.all.map { it.toEntity() })
    }

    suspend fun save(sport: SportType): Long {
        val toSave = if (sport.id == 0L) sport.copy(sortOrder = dao.maxSortOrder() + 1) else sport
        return dao.upsert(toSave.toEntity())
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        dao.get(id)?.let { dao.upsert(it.copy(archived = archived)) }
    }
}

class WorkoutRepository(private val db: AppDatabase) {
    private val dao = db.workoutDao()
    private val points = db.trackPointDao()

    val finished: Flow<List<Workout>> = dao.observeFinished().map { l -> l.map { it.toModel() } }
    fun finishedBySport(sportId: Long): Flow<List<Workout>> = dao.observeFinishedBySport(sportId).map { l -> l.map { it.toModel() } }
    fun observe(id: Long): Flow<Workout?> = dao.observe(id).map { it?.toModel() }
    suspend fun get(id: Long): Workout? = dao.get(id)?.toModel()
    suspend fun unfinished(): Workout? = dao.unfinished()?.toModel()

    suspend fun insert(w: Workout): Long = dao.insert(w.toEntity())
    suspend fun update(w: Workout) = dao.update(w.toEntity())
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun updateProgress(id: Long, durationSec: Long, distanceM: Double, status: String) =
        dao.updateProgress(id, durationSec, distanceM, status, System.currentTimeMillis())

    suspend fun addPoint(workoutId: Long, p: TrackPoint) = points.insert(p.toEntity(workoutId))
    suspend fun addPoints(workoutId: Long, ps: List<TrackPoint>) = points.insertAll(ps.map { it.toEntity(workoutId) })
    suspend fun points(workoutId: Long): List<TrackPoint> = points.forWorkout(workoutId).map { it.toModel() }
    suspend fun maxSegment(workoutId: Long): Int? = points.maxSegment(workoutId)

    suspend fun splits(workoutId: Long, splitM: Double) = SplitCalculator.splits(points(workoutId), splitM)

    /** Inserts a finished workout together with its track in one transaction (used by seeding). */
    suspend fun insertWithTrack(w: Workout, track: List<TrackPoint>): Long {
        val id = insert(w)
        if (track.isNotEmpty()) addPoints(id, track)
        return id
    }

    suspend fun deleteAll() = dao.deleteAll()
}
