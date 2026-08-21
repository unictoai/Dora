package app.dora.localai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
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

@Entity(tableName = "conversation_records")
data class ConversationRecord(
    @androidx.room.PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val systemPrompt: String,
    val maxTokens: Int,
    val threads: Int,
    val temperature: Float,
    val topK: Int,
    val topP: Float,
)

@Entity(tableName = "message_records", indices = [Index(value = ["conversationId"])])
data class MessageRecord(
    @androidx.room.PrimaryKey val id: String,
    val conversationId: String,
    val role: String,
    val text: String,
    val ordinal: Long,
    val createdAt: Long,
    val firstTokenLatencyMillis: Long? = null,
    val generationTimeMillis: Long? = null,
    val tokensGenerated: Int? = null,
    val tokensPerSecond: Float? = null,
    val contextTokenEstimate: Int? = null,
)

@Entity(tableName = "document_records")
data class DocumentRecord(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val chunkCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val enabled: Boolean,
    val errorMessage: String? = null,
)

@Entity(tableName = "document_chunks", indices = [Index(value = ["documentId"]), Index(value = ["documentId", "ordinal"], unique = true)])
data class DocumentChunkRecord(
    @androidx.room.PrimaryKey val id: String,
    val documentId: String,
    val ordinal: Int,
    val text: String,
    val searchableText: String,
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

    @Query("SELECT * FROM conversation_records ORDER BY updatedAt DESC")
    suspend fun allConversations(): List<ConversationRecord>

    @Query("SELECT * FROM message_records WHERE conversationId = :conversationId ORDER BY ordinal ASC")
    suspend fun messagesForConversation(conversationId: String): List<MessageRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(record: ConversationRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(record: MessageRecord)

    @Query("DELETE FROM message_records WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM conversation_records WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("DELETE FROM message_records WHERE conversationId IN (SELECT id FROM conversation_records WHERE updatedAt < :cutoff)")
    suspend fun deleteMessagesOlderThan(cutoff: Long)

    @Query("DELETE FROM conversation_records WHERE updatedAt < :cutoff")
    suspend fun deleteConversationsOlderThan(cutoff: Long)

    @Query("DELETE FROM message_records")
    suspend fun deleteAllMessages()

    @Query("DELETE FROM conversation_records")
    suspend fun deleteAllConversations()

    @Query("DELETE FROM job_records")
    suspend fun deleteAllJobs()

    @Query("SELECT * FROM document_records ORDER BY updatedAt DESC")
    suspend fun allDocuments(): List<DocumentRecord>

    @Query("SELECT * FROM document_chunks ORDER BY documentId ASC, ordinal ASC")
    suspend fun allDocumentChunks(): List<DocumentChunkRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocument(record: DocumentRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDocumentChunks(records: List<DocumentChunkRecord>)

    @Query("DELETE FROM document_chunks WHERE documentId = :documentId")
    suspend fun deleteDocumentChunks(documentId: String)

    @Query("DELETE FROM document_records WHERE id = :id")
    suspend fun deleteDocument(id: String)

    @Query("DELETE FROM document_chunks")
    suspend fun deleteAllDocumentChunks()

    @Query("DELETE FROM document_records")
    suspend fun deleteAllDocuments()
}

@Database(entities = [ModelRecord::class, JobRecord::class, ConversationRecord::class, MessageRecord::class, DocumentRecord::class, DocumentChunkRecord::class], version = 6, exportSchema = true)
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

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS conversation_records (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, systemPrompt TEXT NOT NULL, maxTokens INTEGER NOT NULL, threads INTEGER NOT NULL, temperature REAL NOT NULL, topK INTEGER NOT NULL, topP REAL NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS message_records (id TEXT NOT NULL PRIMARY KEY, conversationId TEXT NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, ordinal INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_message_records_conversationId ON message_records (conversationId)")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS document_records (id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, mimeType TEXT NOT NULL, sizeBytes INTEGER NOT NULL, sha256 TEXT NOT NULL, chunkCount INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, enabled INTEGER NOT NULL, errorMessage TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS document_chunks (id TEXT NOT NULL PRIMARY KEY, documentId TEXT NOT NULL, ordinal INTEGER NOT NULL, text TEXT NOT NULL, searchableText TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_document_chunks_documentId ON document_chunks (documentId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_document_chunks_documentId_ordinal ON document_chunks (documentId, ordinal)")
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE message_records ADD COLUMN firstTokenLatencyMillis INTEGER")
                db.execSQL("ALTER TABLE message_records ADD COLUMN generationTimeMillis INTEGER")
                db.execSQL("ALTER TABLE message_records ADD COLUMN tokensGenerated INTEGER")
                db.execSQL("ALTER TABLE message_records ADD COLUMN tokensPerSecond REAL")
                db.execSQL("ALTER TABLE message_records ADD COLUMN contextTokenEstimate INTEGER")
            }
        }

        fun get(context: Context): DoraDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context, DoraDatabase::class.java, "dora.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()
                .also { instance = it }
        }
    }
}
