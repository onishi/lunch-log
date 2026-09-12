package app.lunchlog.core

import app.lunchlog.core.photo.ExifOrientation
import app.lunchlog.core.photo.ImageResize
import app.lunchlog.core.photo.ImageResize.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageResizeTest {

    @Test
    fun `表示用は長辺2048に収める`() {
        // SPEC §4: 長辺 2048px / JPEG 85
        assertEquals(Size(2048, 1536), ImageResize.display(Size(4032, 3024)))
        assertEquals(Size(1536, 2048), ImageResize.display(Size(3024, 4032)))
    }

    @Test
    fun `サムネイルは長辺320に収める`() {
        assertEquals(Size(320, 240), ImageResize.thumbnail(Size(4032, 3024)))
    }

    @Test
    fun `元が小さければ拡大しない`() {
        // 引き伸ばしても情報は増えず、ファイルサイズだけ増える。
        val small = Size(800, 600)
        assertEquals(small, ImageResize.display(small))
    }

    @Test
    fun `ちょうど上限のときはそのまま`() {
        val exact = Size(2048, 1152)
        assertEquals(exact, ImageResize.display(exact))
    }

    @Test
    fun `縦横比を保つ`() {
        val resized = ImageResize.display(Size(6000, 4000))
        assertEquals(2048, resized.width)
        assertEquals(1365, resized.height) // 4000 * 2048 / 6000 = 1365.3
    }

    @Test
    fun `極端に細長くても0pxにならない`() {
        val resized = ImageResize.thumbnail(Size(10000, 3))
        assertEquals(320, resized.width)
        assertTrue("高さが1px以上であること", resized.height >= 1)
    }

    @Test
    fun `不正な寸法は例外`() {
        listOf({ Size(0, 100) }, { Size(100, -1) }).forEach {
            try {
                it()
                throw AssertionError("例外が投げられるはず")
            } catch (expected: IllegalArgumentException) {
                // ok
            }
        }
    }
}

class ExifOrientationTest {

    @Test
    fun `回転角を読み取る`() {
        assertEquals(0, ExifOrientation.fromExifValue(1).rotationDegrees)
        assertEquals(90, ExifOrientation.fromExifValue(6).rotationDegrees)
        assertEquals(180, ExifOrientation.fromExifValue(3).rotationDegrees)
        assertEquals(270, ExifOrientation.fromExifValue(8).rotationDegrees)
    }

    @Test
    fun `未知の値や欠損は回転なしとして扱う`() {
        // 壊れた EXIF で写真の取り込みが止まらないようにする。
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.fromExifValue(null))
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.fromExifValue(0))
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.fromExifValue(99))
    }

    @Test
    fun `90度と270度では幅と高さが入れ替わる`() {
        assertTrue(ExifOrientation.ROTATE_90.swapsDimensions)
        assertTrue(ExifOrientation.ROTATE_270.swapsDimensions)
        assertFalse(ExifOrientation.NORMAL.swapsDimensions)
        assertFalse(ExifOrientation.ROTATE_180.swapsDimensions)

        assertEquals(Size(3024, 4032), ExifOrientation.ROTATE_90.applyTo(Size(4032, 3024)))
        assertEquals(Size(4032, 3024), ExifOrientation.ROTATE_180.applyTo(Size(4032, 3024)))
    }

    @Test
    fun `反転を伴う向きも解釈できる`() {
        assertTrue(ExifOrientation.fromExifValue(2).mirrored)
        assertTrue(ExifOrientation.fromExifValue(5).mirrored)
        assertFalse(ExifOrientation.fromExifValue(6).mirrored)
    }
}
