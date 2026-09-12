package app.lunchlog.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    /** 一覧用。削除済みを除き新しい順 (SPEC F-201, F-112)。 */
    @Query("SELECT * FROM records WHERE deletedAtMillis IS NULL ORDER BY eatenAtMillis DESC")
    fun observeAll(): Flow<List<RecordEntity>>

    @Query("SELECT * FROM records WHERE id = :id")
    fun observe(id: String): Flow<RecordEntity?>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun find(id: String): RecordEntity?

    @Upsert
    suspend fun upsert(record: RecordEntity)

    @Upsert
    suspend fun upsertAll(records: List<RecordEntity>)

    /** 同期待ちの記録。削除も同期する必要があるため deletedAt は問わない。 */
    @Query("SELECT * FROM records WHERE needsSync = 1 ORDER BY updatedAtMillis ASC")
    suspend fun pendingSync(): List<RecordEntity>

    @Query("UPDATE records SET needsSync = 0 WHERE id = :id")
    suspend fun markSynced(id: String)

    /** ゴミ箱からの完全削除 (30 日経過分)。論理削除は updatedAt を進めて同期する。 */
    @Query("DELETE FROM records WHERE deletedAtMillis IS NOT NULL AND deletedAtMillis < :before AND needsSync = 0")
    suspend fun purgeDeletedBefore(before: Long)
}
