package app.lunchlog.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.lunchlog.ServiceLocator
import app.lunchlog.core.sync.RetryPolicy
import app.lunchlog.data.local.toDomain
import app.lunchlog.data.local.toEntity
import java.util.concurrent.TimeUnit

/**
 * 未同期の記録を Firestore と Cloud Storage へ反映する (SPEC F-111, F-302)。
 *
 * 圏外で記録して、復帰したら同期される、が MVP の受け入れ条件。
 * WorkManager に任せることでプロセスが死んでも再開される。
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val locator = ServiceLocator.from(applicationContext)
        val uid = locator.auth.currentUser?.uid ?: return Result.success() // 未ログインなら何もしない

        val pushFailed = push(locator, uid)
        val pullFailed = pull(locator, uid)

        // 同期が済んだものだけを対象に、30 日を過ぎた削除済みを消す (SPEC F-112)。
        runCatching { locator.recordRepository.purgeOldDeleted() }

        // どちらかが失敗していれば WorkManager のバックオフに任せて再試行する。
        return if (pushFailed || pullFailed) Result.retry() else Result.success()
    }

    /** ローカルの変更を Firestore へ送る。 */
    private suspend fun push(locator: ServiceLocator, uid: String): Boolean {
        val pending = locator.database.recordDao().pendingSync()
        if (pending.isEmpty()) return false

        var failed = false
        for (entity in pending) {
            val record = entity.toDomain()
            try {
                // 写真を先に上げ、その結果 (保存先パス) を記録に反映してから書き込む。
                // 逆にすると、写真のない記録が一度 Firestore に残る。
                val uploaded = record.photos.map { locator.photoUploader.upload(uid, it) }
                val synced = record.copy(photos = uploaded)

                locator.recordSource.put(uid, synced)
                locator.database.recordDao().upsert(synced.toEntity(needsSync = false))
            } catch (error: Exception) {
                // 種類を問わず再試行する。原因の切り分けは Phase 6 の監視で行う。
                failed = true
            }
        }
        return failed
    }

    /**
     * 他の端末で書かれた変更を取り込む (SPEC F-302)。
     *
     * 前回どこまで読んだかを updatedAt の文字列で覚えておき、差分だけを読む。
     * ローカルに未送信の変更がある記録は上書きしない — 手元の入力を消さない。
     */
    private suspend fun pull(locator: ServiceLocator, uid: String): Boolean {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val since = prefs.getString(KEY_PULLED_UNTIL, null)

        return try {
            val remote = locator.recordSource.fetchUpdatedSince(uid, since)
            val dao = locator.database.recordDao()

            val toStore = remote.filter { record ->
                dao.find(record.id)?.needsSync != true
            }
            if (toStore.isNotEmpty()) {
                dao.upsertAll(toStore.map { it.toEntity(needsSync = false) })
            }

            remote.maxOfOrNull { it.updatedAt }?.let {
                prefs.edit().putString(KEY_PULLED_UNTIL, it.toString()).apply()
            }
            false
        } catch (error: Exception) {
            true
        }
    }

    companion object {
        private const val UNIQUE_NAME = "lunch-log-sync"
        private const val PREFS = "sync"
        private const val KEY_PULLED_UNTIL = "pulled-until"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    RetryPolicy.INITIAL_DELAY.seconds,
                    TimeUnit.SECONDS,
                )
                .build()

            // 既に積まれていれば置き換えず追従させる (保存のたびに積み直さない)。
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
