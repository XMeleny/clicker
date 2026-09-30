package com.local.clicker.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
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

@Entity(tableName = "action_templates")
data class ActionTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "template_steps",
    foreignKeys = [ForeignKey(
        entity = ActionTemplateEntity::class,
        parentColumns = ["id"],
        childColumns = ["templateId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("templateId")],
)
data class TemplateStepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
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
    @Query("SELECT * FROM action_templates ORDER BY updatedAt DESC, id DESC")
    fun observeTemplates(): Flow<List<ActionTemplateEntity>>

    @Query("SELECT * FROM template_steps ORDER BY templateId ASC, orderIndex ASC")
    fun observeTemplateSteps(): Flow<List<TemplateStepEntity>>

    @Query("SELECT * FROM action_templates WHERE id = :id")
    suspend fun getTemplate(id: Long): ActionTemplateEntity?

    @Query("SELECT * FROM template_steps WHERE templateId = :id ORDER BY orderIndex ASC")
    suspend fun templateStepsOf(id: Long): List<TemplateStepEntity>

    @Insert
    suspend fun insertTemplate(entity: ActionTemplateEntity): Long

    @Transaction
    suspend fun insertTemplateWithSteps(entity: ActionTemplateEntity, steps: List<TemplateStepEntity>): Long {
        val id = insertTemplate(entity)
        if (steps.isNotEmpty()) insertTemplateSteps(steps.map { it.copy(id = 0, templateId = id) })
        return id
    }

    @Upsert
    suspend fun upsertTemplate(entity: ActionTemplateEntity)

    @Query("DELETE FROM action_templates WHERE id = :id")
    suspend fun deleteTemplate(id: Long)

    @Query("DELETE FROM template_steps WHERE templateId = :id")
    suspend fun deleteTemplateSteps(id: Long)

    @Insert
    suspend fun insertTemplateSteps(steps: List<TemplateStepEntity>)

    @Transaction
    suspend fun replaceTemplateSteps(id: Long, steps: List<TemplateStepEntity>) {
        deleteTemplateSteps(id)
        if (steps.isNotEmpty()) insertTemplateSteps(steps)
    }

    @Transaction
    suspend fun saveTemplate(entity: ActionTemplateEntity, steps: List<TemplateStepEntity>) {
        upsertTemplate(entity)
        replaceTemplateSteps(entity.id, steps)
    }

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

    @Upsert
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

@Database(
    entities = [TaskEntity::class, StepEntity::class, ExecutionLogEntity::class, ActionTemplateEntity::class, TemplateStepEntity::class],
    version = 3,
    exportSchema = false,
)
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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `action_templates` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL)")
        db.execSQL("""CREATE TABLE IF NOT EXISTS `template_steps` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `templateId` INTEGER NOT NULL, `orderIndex` INTEGER NOT NULL, `type` TEXT NOT NULL, `complete` INTEGER NOT NULL, `packageName` TEXT, `waitMs` INTEGER, `x` INTEGER, `y` INTEGER, `screenWidth` INTEGER, `screenHeight` INTEGER, `rotation` INTEGER, FOREIGN KEY(`templateId`) REFERENCES `action_templates`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)""")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_template_steps_templateId` ON `template_steps` (`templateId`)")
    }
}
