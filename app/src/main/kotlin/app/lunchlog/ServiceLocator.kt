package app.lunchlog

import android.content.Context
import app.lunchlog.data.RecordRepository
import app.lunchlog.data.local.LunchLogDatabase
import app.lunchlog.data.ocr.MenuOcr
import app.lunchlog.data.photo.PhotoStore
import app.lunchlog.data.remote.FirestoreRecordSource
import app.lunchlog.data.remote.PhotoUploader
import app.lunchlog.data.remote.PlacesClient
import app.lunchlog.location.CurrentLocation
import app.lunchlog.sync.SyncWorker
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.storage.FirebaseStorage

/**
 * 依存の組み立て。
 *
 * DI ライブラリは入れない。MVP で必要な依存は数えるほどで、
 * 注釈処理を足すほどの規模ではない (必要になったら Hilt を検討する)。
 */
class ServiceLocator private constructor(context: Context) {

    private val appContext = context.applicationContext

    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val database: LunchLogDatabase by lazy { LunchLogDatabase.create(appContext) }
    val photoStore: PhotoStore by lazy { PhotoStore(appContext) }
    val photoUploader: PhotoUploader by lazy { PhotoUploader(FirebaseStorage.getInstance()) }
    val recordSource: FirestoreRecordSource by lazy {
        // オフライン永続化は既定で有効だが、MVP の受け入れ条件に関わるため明示する
        // (SPEC F-111: 圏外で記録して復帰したら同期される)。
        val firestore = FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder(firestoreSettings)
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                .build()
        }
        FirestoreRecordSource(firestore)
    }
    val placesClient: PlacesClient by lazy { PlacesClient(auth) }
    val currentLocation: CurrentLocation by lazy { CurrentLocation(appContext) }
    val menuOcr: MenuOcr by lazy { MenuOcr(appContext) }

    val recordRepository: RecordRepository by lazy {
        RecordRepository(
            dao = database.recordDao(),
            photoStore = photoStore,
            placesClient = placesClient,
            requestSync = { SyncWorker.enqueue(appContext) },
        )
    }

    companion object {
        @Volatile
        private var instance: ServiceLocator? = null

        fun from(context: Context): ServiceLocator =
            instance ?: synchronized(this) {
                instance ?: ServiceLocator(context).also { instance = it }
            }
    }
}
