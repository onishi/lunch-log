package app.lunchlog.data.ocr

import android.content.Context
import android.net.Uri
import app.lunchlog.core.ocr.MenuTextParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.tasks.await

/**
 * メニュー写真から文字を読む (SPEC §6.2, F-110)。
 *
 * ML Kit のオンデバイス認識を使う。端末内で完結するので、写真が外に出ず
 * オフラインでも動く (SPEC §5.2)。
 *
 * 取り出した文字列の解釈は :core の MenuTextParser が持つ (テスト済み)。
 * ここは ML Kit を呼んで文字列にするところまで。
 */
class MenuOcr(private val context: Context) {

    private val recognizer by lazy {
        TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    }

    /**
     * 失敗しても例外を投げず空を返す。候補が出ないだけで、手入力は妨げない
     * (SPEC §13: OCR はあくまで候補の提示に留める)。
     */
    suspend fun candidates(source: Uri): List<MenuTextParser.MenuCandidate> = try {
        val image = InputImage.fromFilePath(context, source)
        val text = recognizer.process(image).await().text
        MenuTextParser.parse(text)
    } catch (_: Exception) {
        emptyList()
    }
}
