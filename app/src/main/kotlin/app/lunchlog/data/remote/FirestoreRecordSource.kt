package app.lunchlog.data.remote

import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.remote.RecordDocument
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * Firestore 上の記録 (SPEC §7, §7.6)。
 *
 * パスは `users/{uid}/records/{recordId}`。セキュリティルールで所有者だけが
 * 読み書きできるようにしてあるため、ここでは uid を間違えないことだけを守る。
 *
 * ドキュメントとの変換は :core の RecordDocument が持つ (端末なしでテスト済み)。
 */
class FirestoreRecordSource(private val firestore: FirebaseFirestore) {

    private fun records(uid: String) = firestore.collection("users").document(uid).collection("records")

    suspend fun put(uid: String, record: LunchRecord) {
        records(uid).document(record.id).set(RecordDocument.toMap(record)).await()
    }

    /** 変更分を取り出す。初回は [since] を null にして全件を読む。 */
    suspend fun fetchUpdatedSince(uid: String, since: String?): List<LunchRecord> {
        val query = if (since == null) {
            records(uid)
        } else {
            records(uid).whereGreaterThan("updatedAt", since)
        }
        return query.get().await().documents.mapNotNull { document ->
            document.data?.let { RecordDocument.fromMap(it) }
        }
    }
}
