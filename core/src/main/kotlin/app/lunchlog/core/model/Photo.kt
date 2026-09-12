package app.lunchlog.core.model

import java.time.Instant

/** 写真の種別 (SPEC §7.2 `photos[].kind`)。 */
enum class PhotoKind(val id: String) {
    DISH("dish"),
    MENU("menu"),
    EXTERIOR("exterior"),
    RECEIPT("receipt"),
    ;

    companion object {
        fun fromId(id: String?): PhotoKind = entries.firstOrNull { it.id == id } ?: DISH
    }
}

/**
 * 記録に添付された写真 1 枚 (SPEC §7.2)。
 *
 * パスは Cloud Storage 上の位置を指す。アップロード前は null で、
 * ローカルのファイルは [localPath] が持つ。
 *
 * 原本 ([originalPath]) は Phase 3 (F-117, SPEC §6.7) で使う。MVP では常に null。
 */
data class Photo(
    val id: String,
    val kind: PhotoKind = PhotoKind.DISH,
    val localPath: String? = null,
    val thumbPath: String? = null,
    val displayPath: String? = null,
    val originalPath: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val takenAt: Instant? = null,
    val order: Int = 0,
) {
    /** 表示用がアップロード済みか。未アップロードならローカルのファイルを表示する。 */
    val isUploaded: Boolean get() = displayPath != null
}
