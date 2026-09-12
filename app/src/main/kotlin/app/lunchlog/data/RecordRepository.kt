package app.lunchlog.data

import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.restaurant.RestaurantSuggestion
import app.lunchlog.core.restaurant.RestaurantSuggestions
import app.lunchlog.core.validation.RecordValidator
import app.lunchlog.core.validation.ValidationResult
import app.lunchlog.data.local.RecordDao
import app.lunchlog.data.local.toDomain
import app.lunchlog.data.local.toEntity
import app.lunchlog.data.photo.PhotoStore
import app.lunchlog.data.remote.PlacesClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant

/** 記録と、その同期状態。 */
data class SyncedRecord(val record: LunchRecord, val needsSync: Boolean)

/**
 * 記録の読み書き (SPEC §5.2)。
 *
 * Room が Single Source of Truth。保存はローカルで完結させ、Firestore への
 * 反映は SyncWorker に任せる — 保存操作を通信の成否に依存させない (F-111)。
 */
class RecordRepository(
    private val dao: RecordDao,
    private val photoStore: PhotoStore,
    private val placesClient: PlacesClient,
    private val requestSync: () -> Unit,
) {

    fun observeAll(): Flow<List<LunchRecord>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** 一覧用。同期状態を添えて返す (未同期マークの表示に使う)。 */
    fun observeAllWithSync(): Flow<List<SyncedRecord>> =
        dao.observeAll().map { list -> list.map { SyncedRecord(it.toDomain(), it.needsSync) } }

    fun observe(id: String): Flow<LunchRecord?> = dao.observe(id).map { it?.toDomain() }

    suspend fun find(id: String): LunchRecord? = dao.find(id)?.toDomain()

    /**
     * 保存する。検証に通らなければ書き込まず理由を返す。
     * 保存はローカルで完結し、同期は裏で走る。
     */
    suspend fun save(record: LunchRecord): ValidationResult {
        val normalized = RecordValidator.normalize(record)
        val result = RecordValidator.validate(normalized)
        if (result is ValidationResult.Invalid) return result

        dao.upsert(normalized.copy(updatedAt = Instant.now()).toEntity(needsSync = true))
        requestSync()
        return ValidationResult.Valid
    }

    /** 論理削除 (SPEC F-112)。30 日間はゴミ箱に残る。 */
    suspend fun delete(id: String) {
        val existing = dao.find(id) ?: return
        val now = Instant.now()
        dao.upsert(existing.copy(deletedAtMillis = now.toEpochMilli(), updatedAtMillis = now.toEpochMilli(), needsSync = true))
        requestSync()
    }

    /**
     * 店名の候補 (SPEC §6.1)。
     *
     * 位置情報から取れた候補を先に、足りない分を履歴で埋める。
     * 位置情報が使えなくても必ず何かしら返す。
     */
    suspend fun suggestRestaurants(lat: Double?, lng: Double?): List<RestaurantSuggestion> {
        val nearby = if (lat != null && lng != null) placesClient.nearby(lat, lng) else emptyList()
        val history = RestaurantSuggestions.fromHistory(observeAll().first())
        return RestaurantSuggestions.merge(nearby, history)
    }

    /** 完全削除の対象を掃除する。ゴミ箱の保持期間は 30 日 (SPEC F-112)。 */
    suspend fun purgeOldDeleted(now: Instant = Instant.now()) {
        val threshold = now.minusSeconds(30L * 24 * 60 * 60).toEpochMilli()
        // 同期が済んでいないものは消さない (DAO 側の条件で担保)。
        dao.purgeDeletedBefore(threshold)
    }

    fun photoStore(): PhotoStore = photoStore
}
