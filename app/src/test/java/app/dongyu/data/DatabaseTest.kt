package app.dongyu.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.dongyu.data.db.AppDatabase
import app.dongyu.data.repo.SportRepository
import app.dongyu.data.repo.WorkoutRepository
import app.dongyu.model.MetricDef
import app.dongyu.model.MetricKind
import app.dongyu.model.SportType
import app.dongyu.model.TrackPoint
import app.dongyu.model.Workout
import app.dongyu.model.WorkoutStatus
import app.dongyu.model.Aggregation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DatabaseTest {
    private lateinit var db: AppDatabase
    private lateinit var sports: SportRepository
    private lateinit var workouts: WorkoutRepository

    @Before fun setUp() {
        db = AppDatabase.inMemory(ApplicationProvider.getApplicationContext())
        sports = SportRepository(db)
        workouts = WorkoutRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun presetsAreSeededOnce() = runTest {
        sports.ensurePresets(); sports.ensurePresets()
        val all = sports.allSports.first()
        assertEquals(5, all.size)
        assertEquals(listOf("run", "hike", "mountaineer", "swim", "cycle"), all.map { it.presetKey })
    }

    @Test fun customSportRoundTripsWithCustomMetric() = runTest {
        sports.ensurePresets()
        val id = sports.save(
            SportType(name = "飞盘", icon = "frisbee", color = 0xFF123456, usesGps = false, met = 6.0,
                metrics = listOf(MetricDef.of(MetricKind.DURATION), MetricDef.custom("c_throws", "传盘", "次", Aggregation.SUM))),
        )
        val s = sports.get(id)!!
        assertEquals("飞盘", s.name)
        assertEquals(5, s.sortOrder)
        assertEquals("传盘", s.metrics[1].customLabel)
        sports.setArchived(id, true)
        assertTrue(sports.activeSports.first().none { it.id == id })
    }

    @Test fun workoutWithTrackAndCascadeDelete() = runTest {
        sports.ensurePresets()
        val run = sports.allSports.first().first()
        val w = Workout(sportId = run.id, startTime = 0, endTime = 600_000, durationSec = 600, distanceM = 2000.0,
            values = mapOf(MetricKind.HEART_RATE.key to 150.0))
        val track = (0..10).map { TrackPoint(time = it * 60_000L, lat = 30.0 + it * 0.0018, lon = 120.0, altitude = 10.0) }
        val id = workouts.insertWithTrack(w, track)
        assertEquals(11, workouts.points(id).size)
        assertEquals(150.0, workouts.get(id)!!.values[MetricKind.HEART_RATE.key]!!, 0.0)
        assertEquals(1, workouts.finished.first().size)
        assertEquals(2, workouts.splits(id, 1000.0).size)
        workouts.delete(id)
        assertNull(workouts.get(id))
        assertEquals(0, workouts.points(id).size)
    }

    @Test fun unfinishedWorkoutIsFoundForRecovery() = runTest {
        sports.ensurePresets()
        val id = workouts.insert(Workout(sportId = 1, startTime = 0, endTime = 0, durationSec = 0, status = WorkoutStatus.RECORDING))
        assertNotNull(workouts.unfinished())
        assertEquals(id, workouts.unfinished()!!.id)
        assertEquals(0, workouts.finished.first().size)
    }
}
