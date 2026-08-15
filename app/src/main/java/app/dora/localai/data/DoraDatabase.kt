package app.dora.localai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "model_records")
data class ModelRecord(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val format: String,
    val path: String?,
    val sizeBytes: Long,
    val sha256: String,
    val license: String,
    val verified: Boolean,
    val updatedAt: Long,
    val sourceRepo: String? = null,
    val sourceFilename: String? = null,
    val sourceRevision: String? = null,
    val sourceUrl: String? = null,
    val sourceLicense: String? = null,
)

@Entity(tableName = "job_records")
data class JobRecord(
    @androidx.room.PrimaryKey val id: String,
    val kind: String,
    val label: String,
    val state: String,
    val progress: Float,
    val message: String,
    val updatedAt: Long,
)

@androidx.room.Dao
interface DoraDao {
    @Query("SELECT * FROM model_records ORDER BY updatedAt DESC")
    fun observeModels(): Flow<List<ModelRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertModel(record: ModelRecord)

    @Query("DELETE FROM model_records WHERE id = :id")
    suspend fun deleteModel(id: String)

    @Query("SELECT * FROM model_records WHERE id = :id LIMIT 1")
    suspend fun findModel(id: String): ModelRecord?

    @Query("SELECT * FROM job_records ORDER BY updatedAt DESC")
    fun observeJobs(): Flow<List<JobRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJob(record: JobRecord)

    @Query("DELETE FROM job_records WHERE id = :id")
    suspend fun deleteJob(id: String)
}

@Database(entities = [ModelRecord::class, JobRecord::class], version = 2, exportSchema = false)
abstract class DoraDatabase : RoomDatabase() {
    abstract fun dao(): DoraDao

    companion object {
        @Volatile private var instance: DoraDatabase? = null

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE model_records ADD COLUMN sourceRepo TEXT")
                db.execSQL("ALTER TABLE model_records ADD COLUMN sourceFilename TEXT")
                db.execSQL("ALTER TABLE model_records ADD COLUMN sourceRevision TEXT")
                db.execSQL("ALTER TABLE model_records ADD COLUMN sourceUrl TEXT")
                db.execSQL("ALTER TABLE model_records ADD COLUMN sourceLicense TEXT")
            }
        }

        fun get(context: Context): DoraDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, DoraDatabase::class.java, "dora.db")
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
