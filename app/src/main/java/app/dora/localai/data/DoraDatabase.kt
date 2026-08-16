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
    val downloadId: String? = null,
    val modelId: String? = null,
    val repositoryId: String? = null,
    val filename: String? = null,
    val sourceRevision: String? = null,
    val sourceLicense: String? = null,
    val url: String? = null,
    val expectedSha256: String? = null,
    val downloadState: String? = null,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val speedBytesPerSecond: Long? = null,
    val estimatedRemainingTimeMillis: Long? = null,
    val elapsedTimeMillis: Long = 0L,
    val startedAt: Long? = null,
    val retryCount: Int = 0,
    val errorMessage: String? = null,
    val temporaryPath: String? = null,
    val finalPath: String? = null,
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

    @Query("SELECT * FROM model_records ORDER BY updatedAt DESC")
    suspend fun allModels(): List<ModelRecord>

    @Query("SELECT * FROM job_records ORDER BY updatedAt DESC")
    fun observeJobs(): Flow<List<JobRecord>>

    @Query("SELECT * FROM job_records WHERE id = :id LIMIT 1")
    suspend fun findJob(id: String): JobRecord?

    @Query("SELECT * FROM job_records WHERE kind = 'DOWNLOAD' ORDER BY updatedAt DESC")
    suspend fun allDownloadJobs(): List<JobRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJob(record: JobRecord)

    @Query("DELETE FROM job_records WHERE id = :id")
    suspend fun deleteJob(id: String)
}

@Database(entities = [ModelRecord::class, JobRecord::class], version = 3, exportSchema = true)
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

        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE job_records ADD COLUMN downloadId TEXT")
                db.execSQL("ALTER TABLE job_records ADD COLUMN modelId TEXT")
                db.execSQL("ALTER TABLE job_records ADD COLUMN repositoryId TEXT")
                db.execSQL("ALTER TABLE job_records ADD COLUMN filename TEXT")
                db.execSQL("ALTER TABLE job_records ADD COLUMN sourceRevision TEXT")
                db.execSQL("ALTER TABLE job_records ADD COLUMN sourceLicense TEXT")
                db.execSQL("ALTER TABLE job_records ADD COLUMN url TEXT")
                db.execSQL("ALTER TABLE job_records ADD COLUMN expectedSha256 TEXT")
                db.execSQL("ALTER TABLE job_records ADD COLUMN downloadState TEXT")
                db.execSQL("ALTER TABLE job_records ADD COLUMN bytesDownloaded INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE job_records ADD COLUMN totalBytes INTEGER")
                db.execSQL("ALTER TABLE job_records ADD COLUMN speedBytesPerSecond INTEGER")
                db.execSQL("ALTER TABLE job_records ADD COLUMN estimatedRemainingTimeMillis INTEGER")
                db.execSQL("ALTER TABLE job_records ADD COLUMN elapsedTimeMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE job_records ADD COLUMN startedAt INTEGER")
                db.execSQL("ALTER TABLE job_records ADD COLUMN retryCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE job_records ADD COLUMN errorMessage TEXT")
                db.execSQL("ALTER TABLE job_records ADD COLUMN temporaryPath TEXT")
                db.execSQL("ALTER TABLE job_records ADD COLUMN finalPath TEXT")
            }
        }

        fun get(context: Context): DoraDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, DoraDatabase::class.java, "dora.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
