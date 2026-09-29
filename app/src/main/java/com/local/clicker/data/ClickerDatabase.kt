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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(tableName = "execution_logs", indices = [Index("taskId")])
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val taskName: String,
    val status: String,
    val message: String,
    val createdAt: Long,
    val trial: Boolean,
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

    @Query("SELECT * FROM execution_logs ORDER BY createdAt DESC, id DESC")
    fun observeLogs(): Flow<List<ExecutionLogEntity>>

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
    suspend fun insertLog(entity: ExecutionLogEntity)

    @Query("DELETE FROM execution_logs")
    suspend fun clearLogs()

    @Transaction
    suspend fun upsertTaskAndLog(task: TaskEntity, log: ExecutionLogEntity) {
        upsertTask(task)
        insertLog(log)
    }

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

@Database(entities = [TaskEntity::class, StepEntity::class, ExecutionLogEntity::class], version = 2, exportSchema = false)
abstract class ClickerDatabase : RoomDatabase() {
    abstract fun dao(): ClickerDao
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `execution_logs` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `taskId` INTEGER NOT NULL,
                `taskName` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `message` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `trial` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_execution_logs_taskId` ON `execution_logs` (`taskId`)")
        db.execSQL(
            """
            INSERT INTO execution_logs (taskId, taskName, status, message, createdAt, trial)
            SELECT id, name, status, lastMessage, COALESCE(lastRunAt, updatedAt), 0
            FROM tasks
            WHERE status IN ('SUCCESS', 'FAILED', 'MISSED', 'CANCELLED') AND lastMessage IS NOT NULL
            """.trimIndent(),
        )
    }
}
