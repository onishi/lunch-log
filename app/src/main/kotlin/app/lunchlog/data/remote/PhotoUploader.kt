package app.lunchlog.data.remote

import app.lunchlog.core.model.Photo
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File

/**
 * 写真を Cloud Storage へ上げる (SPEC §6.7, §7.6)。
 *
 * 置き場所は `users/{uid}/...`。ルールで所有者だけがアクセスできる。
 * MVP では表示用とサムネイルのみ。原本は Phase 3 (F-117)。
 */
class PhotoUploader(private val storage: FirebaseStorage) {

    /** 既にアップロード済みならそのまま返す (再同期で二重に上げない)。 */
    suspend fun upload(uid: String, photo: Photo): Photo {
        if (photo.isUploaded) return photo

        val displayPath = photo.localPath?.let { upload(it, "users/$uid/photos/${photo.id}.jpg") }
        val thumbPath = photo.thumbPath?.let { upload(it, "users/$uid/thumbs/${photo.id}.jpg") }

        return photo.copy(displayPath = displayPath, thumbPath = thumbPath ?: photo.thumbPath)
    }

    private suspend fun upload(localPath: String, remotePath: String): String? {
        val file = File(localPath)
        if (!file.exists()) return null
        storage.reference.child(remotePath).putFile(android.net.Uri.fromFile(file)).await()
        return remotePath
    }
}
