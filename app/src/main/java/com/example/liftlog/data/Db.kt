package com.example.liftlog.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "workouts")
data class Workout(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: Long,
    val durationSec: Long = 0
)

@Entity(
    tableName = "sets",
    foreignKeys = [ForeignKey(
        entity = Workout::class,
        parentColumns = ["id"],
        childColumns = ["workoutId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("workoutId")]
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exercise: String,
    val weight: Double,
    val reps: Int,
    val position: Int
)

data class WorkoutWithSets(
    @Embedded val workout: Workout,
    @Relation(parentColumn = "id", entityColumn = "workoutId")
    val sets: List<WorkoutSet>
)

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertWorkout(workout: Workout): Long

    @Insert
    suspend fun insertSets(sets: List<WorkoutSet>)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkout(id: Long)

    @Transaction
    @Query("SELECT * FROM workouts ORDER BY date DESC")
    fun observeAll(): Flow<List<WorkoutWithSets>>

    @Query("SELECT DISTINCT exercise FROM sets ORDER BY exercise")
    fun observeExerciseNames(): Flow<List<String>>
}

@Database(entities = [Workout::class, WorkoutSet::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): WorkoutDao

    companion object {
        @Volatile
        private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, AppDb::class.java, "liftlog.db"
            ).build().also { instance = it }
        }
    }
}
