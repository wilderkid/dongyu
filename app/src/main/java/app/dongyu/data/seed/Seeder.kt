package app.dongyu.data.seed

import app.dongyu.data.repo.SportRepository
import app.dongyu.data.repo.WorkoutRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Writes demo data into the database (debug builds only). */
class Seeder(private val sports: SportRepository, private val workouts: WorkoutRepository) {
    suspend fun seed() {
        sports.ensurePresets()
        val existing = sports.allSports.first()
        for (c in DemoData.customSports) {
            if (existing.none { it.name == c.name }) sports.save(c)
        }
        val all = sports.allSports.first()
        workouts.deleteAll()
        DemoData.generate(all, LocalDate.now()).forEach { workouts.insertWithTrack(it.workout, it.track) }
    }
}
