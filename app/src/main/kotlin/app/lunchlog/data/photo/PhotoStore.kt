package app.lunchlog.data.photo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import app.lunchlog.core.id.Ulid
import app.lunchlog.core.model.Photo
import app.lunchlog.core.model.PhotoKind
import app.lunchlog.core.photo.ExifOrientation
import app.lunchlog.core.photo.ImageResize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 撮影・選択された画像を端末内に用意する (SPEC §4, §6.7, §10)。
 *
 * 保存操作を待たせないため、ここでは**表示用とサムネイルだけ**を作る。
 * 原本の扱いは Phase 3 (F-117)。
 *
 * EXIF は「GPS だけ落として、向きと撮影日時は残す」(SPEC §10)。
 * 向きはリサイズ時にピクセルへ反映させるため、結果の画像は回転なしになる。
 */
class PhotoStore(private val context: Context) {

    private val photoDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    data class Prepared(val photo: Photo, val takenAt: Instant?)

    suspend fun prepare(source: Uri, kind: PhotoKind, order: Int): Prepared =
        withContext(Dispatchers.IO) {
            val id = Ulid.generate()
            val exif = context.contentResolver.openInputStream(source)?.use { ExifInterface(it) }
            val orientation = ExifOrientation.fromExifValue(
                exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL),
            )
            val takenAt = exif?.takenAt()

            val decoded = decode(source) ?: error("画像を読み込めませんでした: $source")
            val upright = applyOrientation(decoded, orientation)

            val display = writeScaled(upright, id, "display", ImageResize.DISPLAY_MAX_LONG_EDGE, ImageResize.DISPLAY_JPEG_QUALITY)
            val thumb = writeScaled(upright, id, "thumb", ImageResize.THUMBNAIL_MAX_LONG_EDGE, ImageResize.THUMBNAIL_JPEG_QUALITY)
            upright.recycle()

            Prepared(
                photo = Photo(
                    id = id,
                    kind = kind,
                    localPath = display.absolutePath,
                    thumbPath = thumb.absolutePath,
                    width = display.imageWidth(),
                    height = display.imageHeight(),
                    takenAt = takenAt,
                    order = order,
                ),
                takenAt = takenAt,
            )
        }

    fun localFile(path: String?): File? = path?.let(::File)?.takeIf { it.exists() }

    /** 記録を完全に消すときに端末内の画像も消す。 */
    fun deleteLocal(photo: Photo) {
        listOfNotNull(photo.localPath, photo.thumbPath).forEach { File(it).delete() }
    }

    private fun decode(source: Uri): Bitmap? =
        context.contentResolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it) }

    private fun applyOrientation(bitmap: Bitmap, orientation: ExifOrientation): Bitmap {
        if (orientation == ExifOrientation.NORMAL) return bitmap
        val matrix = Matrix().apply {
            postRotate(orientation.rotationDegrees.toFloat())
            if (orientation.mirrored) postScale(-1f, 1f)
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun writeScaled(bitmap: Bitmap, id: String, suffix: String, maxLongEdge: Int, quality: Int): File {
        val target = ImageResize.fit(ImageResize.Size(bitmap.width, bitmap.height), maxLongEdge)
        val scaled = if (target.width == bitmap.width && target.height == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, target.width, target.height, true)
        }
        val file = File(photoDir, "$id-$suffix.jpg")
        file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        if (scaled != bitmap) scaled.recycle()
        // 書き出した JPEG には EXIF を引き継がない = GPS も付かない (SPEC §10)。
        return file
    }

    private fun File.imageWidth(): Int = readBounds().outWidth

    private fun File.imageHeight(): Int = readBounds().outHeight

    private fun File.readBounds(): BitmapFactory.Options =
        BitmapFactory.Options().apply {
            inJustDecodeBounds = true
            BitmapFactory.decodeFile(absolutePath, this)
        }

    private fun ExifInterface.takenAt(): Instant? {
        val raw = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: getAttribute(ExifInterface.TAG_DATETIME)
            ?: return null
        return runCatching {
            LocalDateTime.parse(raw, EXIF_FORMATTER).atZone(ZoneId.systemDefault()).toInstant()
        }.getOrNull()
    }

    private companion object {
        // EXIF の日時はタイムゾーンを持たないため、端末のゾーンとして解釈する。
        val EXIF_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
    }
}
