package com.local.clicker.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val scheduledAt: Long?,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastRunAt: Long?,
    val lastMessage: String?,
)

@Entity(
    tableName = "steps",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("taskId")],
)
data class StepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val orderIndex: Int,
    val type: String,
    val complete: Boolean,
    val packageName: String?,
    val waitMs: Long?,
    val x: Int?,
    val y: Int?,
    val screenWidth: Int?,
    val screenHeight: Int?,
    val rotation: Int?,
)

@Dao
interface ClickerDao {
    @Query(
        """
        SELECT * FROM tasks
        ORDER BY CASE WHEN scheduledAt IS NULL THEN 1 ELSE 0 END,
                 scheduledAt ASC,
                 updatedAt DESC
        """,
    )
    fun observeTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM steps ORDER BY taskId ASC, orderIndex ASC")
    fun observeSteps(): Flow<List<StepEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeTask(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTask(id: Long): TaskEntity?

    @Query("SELECT * FROM steps WHERE taskId = :taskId ORDER BY orderIndex ASC")
    suspend fun stepsOf(taskId: Long): List<StepEntity>

    @Query("SELECT * FROM tasks WHERE status = :status")
    suspend fun tasksByStatus(status: String): List<TaskEntity>

    @Insert
    suspend fun insertTask(entity: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(entity: TaskEntity)

    @Insert
    suspend fun insertSteps(steps: List<StepEntity>)

    @Query("DELETE FROM steps WHERE taskId = :taskId")
    suspend fun deleteSteps(taskId: Long)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: Long)

    @Transaction
    suspend fun replaceSteps(taskId: Long, steps: List<StepEntity>) {
        deleteSteps(taskId)
        if (steps.isNotEmpty()) insertSteps(steps)
    }
}

@Database(entities = [TaskEntity::class, StepEntity::class], version = 1, exportSchema = false)
abstract class ClickerDatabase : RoomDatabase() {
    abstract fun dao(): ClickerDao
}
