package app.dongyu.transfer

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.dongyu.data.db.AppDatabase
import app.dongyu.data.prefs.SettingsStore
import app.dongyu.data.repo.SportRepository
import app.dongyu.data.repo.WorkoutRepository
import app.dongyu.model.SportFamily
import app.dongyu.model.WorkoutSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** End-to-end: fixture files → Room via TransferService, dedupe, backup → wipe → restore. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TransferServiceTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: AppDatabase
    private lateinit var sports: SportRepository
    private lateinit var workouts: WorkoutRepository
    private lateinit var svc: TransferService

    @Before fun setUp() = runBlocking {
        db = AppDatabase.inMemory(ctx)
        sports = SportRepository(db).also { it.ensurePresets() }
        workouts = WorkoutRepository(db)
        svc = TransferService(ctx, sports, workouts, SettingsStore(ctx))
    }

    @After fun tearDown() = db.close()

    private fun fixture(name: String): Uri {
        val f = File(ctx.cacheDir, name)
        javaClass.classLoader!!.getResourceAsStream("fixtures/$name")!!.use { i -> f.outputStream().use { i.copyTo(it) } }
        return Uri.fromFile(f)
    }

    private val names = listOf("morning_run.gpx", "weekend_ride.tcx", "west_lake_hike.fit")

    @Test fun importsFixturesAndSkipsDuplicates() = runBlocking {
        val r = svc.importUris(names.map(::fixture))
        assertEquals(r.failed.toString(), 3, r.imported)
        val all = workouts.allFinished()
        assertEquals(3, all.size)
        assertTrue(all.all { it.source == WorkoutSource.IMPORT })
        val fams = all.map { sports.get(it.sportId)!!.family }.toSet()
        assertEquals(setOf(SportFamily.RUN, SportFamily.CYCLE, SportFamily.HIKE), fams)
        val run = all.first { sports.get(it.sportId)!!.family == SportFamily.RUN }
        assertTrue(workouts.points(run.id).size >= 170)
        assertTrue(workouts.hrSamples(run.id).isNotEmpty())

        val again = svc.importUris(names.map(::fixture))
        assertEquals(0, again.imported)
        assertEquals(3, again.duplicates)
        assertEquals(3, workouts.allFinished().size)
    }

    @Test fun backupWipeRestore() = runBlocking {
        svc.importUris(names.map(::fixture))
        val customId = sports.save(sports.all().first().copy(id = 0, presetKey = null, name = "飞盘"))
        val src = workouts.allFinished().first()
        workouts.update(src.copy(sportId = customId))
        val out = File(ctx.cacheDir, "b.zip").also { it.delete(); it.createNewFile() }
        assertEquals(3, svc.backup(Uri.fromFile(out)))
        assertTrue(out.length() > 1000)

        workouts.deleteAll()
        db.clearAllTables()
        sports.ensurePresets()
        val r = svc.importUris(listOf(Uri.fromFile(out)))
        assertEquals(3, r.imported)
        assertEquals(1, r.restoredSports)
        val restored = workouts.allFinished()
        assertEquals(3, restored.size)
        assertTrue(restored.any { sports.get(it.sportId)!!.name == "飞盘" })
        assertTrue(restored.sumOf { workouts.points(it.id).size } > 400)
        // Restoring the same backup twice only reports duplicates.
        assertEquals(3, svc.importUris(listOf(Uri.fromFile(out))).duplicates)
    }
}
