package app.dongyu.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SportTypeEntity::class, WorkoutEntity::class, TrackPointEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sportTypeDao(): SportTypeDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun trackPointDao(): TrackPointDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "dongyu.db").build()

        fun inMemory(context: Context): AppDatabase =
            Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }
}
